/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.optimizations

import org.jetbrains.kotlin.backend.common.atMostOne
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.backend.konan.*
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.DirectedGraphCondensationBuilder
import org.jetbrains.kotlin.backend.konan.DirectedGraphMultiNode
import org.jetbrains.kotlin.backend.konan.ir.actualCallee
import org.jetbrains.kotlin.backend.konan.ir.isUnconditional
import org.jetbrains.kotlin.backend.konan.ir.isVirtualCall
import org.jetbrains.kotlin.backend.konan.llvm.Lifetime
import org.jetbrains.kotlin.backend.konan.logMultiple
import org.jetbrains.kotlin.backend.konan.lower.erasedUpperBound
import org.jetbrains.kotlin.backend.konan.lower.isStaticInitializer
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irDoWhile
import org.jetbrains.kotlin.ir.builders.irWhile
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.trimToSize
import java.util.*
import kotlin.collections.ArrayList

private fun <T> MutableList<T>.removeAll(element: T) {
    var i = 0
    for (j in indices) {
        val item = this[j]
        if (item != element)
            this[i++] = item
    }
    while (size != i)
        removeAt(size - 1)
}

private fun <T> MutableList<T?>.ensureSize(newSize: Int) {
    for (i in size until newSize) add(null)
}

private fun <T> MutableList<T>.trimSize(newSize: Int) {
    for (i in newSize until size) removeAt(size - 1)
}

internal object ControlFlowSensibleEscapeAnalysis {
    private val escapesField = IrFieldSymbolImpl()
    private val intestinesField = IrFieldSymbolImpl()

    private val IrFieldSymbol.name: String
        get() = when {
            this == escapesField -> "<escapes>"
            this == intestinesField -> "<intestines>"
            isBound -> owner.name.asString()
            else -> "<unbound>"
        }

    private class InterproceduralAnalysis(val context: Context, val callGraph: CallGraph, val moduleDFG: ModuleDFG, val needDebug: (IrFunction) -> Boolean) {
        fun analyze() {
            // TODO: To common function.
            context.logMultiple {
                +"CALL GRAPH"
                callGraph.directEdges.forEach { (t, u) ->
                    +"    FUN $t"
                    u.callSites.forEach {
                        val label = when {
                            it.isVirtual -> "VIRTUAL"
                            callGraph.directEdges.containsKey(it.actualCallee) -> "LOCAL"
                            else -> "EXTERNAL"
                        }
                        +"        CALLS $label ${it.actualCallee}"
                    }
                    callGraph.reversedEdges[t]!!.forEach { +"        CALLED BY $it" }
                }
                +""
            }

            val condensation = DirectedGraphCondensationBuilder(callGraph).build()

            context.logMultiple {
                +"CONDENSATION"
                condensation.topologicalOrder.forEach { multiNode ->
                    +"    MULTI-NODE"
                    multiNode.nodes.forEach { +"        $it" }
                }
                +""
                +"CONDENSATION(DETAILED)"
                condensation.topologicalOrder.forEach { multiNode ->
                    +"    MULTI-NODE"
                    multiNode.nodes.forEach {
                        +"        $it"
                        callGraph.directEdges[it]!!.callSites
                                .filter { callGraph.directEdges.containsKey(it.actualCallee) }
                                .forEach { +"            CALLS ${it.actualCallee}" }
                        callGraph.reversedEdges[it]!!.forEach { +"            CALLED BY $it" }
                    }
                }
                +""
            }

//            val functions = buildSet {
//                callGraph.directEdges.values.forEach {
//                    add(it.symbol.irFunction)
//                    it.callSites.forEach { callSite -> add(callSite.actualCallee.irFunction) }
//                }
//            }

            val escapeAnalysisResults = mutableMapOf<IrFunctionSymbol, EscapeAnalysisResult>()
            for (multiNode in condensation.topologicalOrder.reversed())
                analyze(multiNode, escapeAnalysisResults)
        }

        private enum class ComputationState {
            NEW,
            PENDING,
            DONE
        }

        object DivergenceResolutionParams {
            const val MaxAttempts = 3
            const val NegligibleSize = 100
            const val SwellingFactor = 25
        }

        // TODO: Compute from IR.
        private fun maxPointsToGraphSizeOf(function: DataFlowIR.FunctionSymbol) = with(DivergenceResolutionParams) {
            // A heuristic: the majority of functions have their points-to graph size linear to the number of IR (or DFG) nodes,
            // there are exceptions, but it's a trade-off we have to make.
            // The trick with [NegligibleSize] handles functions that basically delegate their work to other functions.
            val numberOfNodes = moduleDFG.functions[function]!!.body.allScopes.sumOf { it.nodes.size }
            NegligibleSize + numberOfNodes * SwellingFactor
        }

        private fun analyze(multiNode: DirectedGraphMultiNode<DataFlowIR.FunctionSymbol.Declared>,
                            escapeAnalysisResults: MutableMap<IrFunctionSymbol, EscapeAnalysisResult>) {
            val nodes = multiNode.nodes.filter { callGraph.directEdges.containsKey(it) && it.irFunction != null }.toMutableSet()

            nodes.forEach {
                val function = it.irFunction!!
                escapeAnalysisResults[function.symbol] = EscapeAnalysisResult.optimistic(function)
            }

            context.logMultiple {
                +"Analyzing multiNode:\n    ${multiNode.nodes.joinToString("\n   ") { it.toString() }}"
                nodes.forEach { from ->
                    +"IR"
                    +(from.irFunction?.dump() ?: "")
                    callGraph.directEdges[from]!!.callSites.forEach { to ->
                        +"CALL"
                        +"   from $from"
                        +"   to ${to.actualCallee}"
                    }
                }
            }

            var failedToConverge = false
            val toAnalyze = mutableSetOf<DataFlowIR.FunctionSymbol.Declared>()
            toAnalyze.addAll(nodes)
            val numberOfRuns = nodes.associateWith { 0 }.toMutableMap()
            while (!failedToConverge && toAnalyze.isNotEmpty()) {
                val node = toAnalyze.first()
                toAnalyze.remove(node)
                val function = node.irFunction!!
                numberOfRuns[node] = numberOfRuns[node]!! + 1
                context.log { "Processing function $node" }

                val startResult = escapeAnalysisResults[function.symbol]!!
                context.log { "Start escape analysis result:" }
                startResult.logDigraph(context)

//                val pointsToGraph = PointsToGraph(node)
//                pointsToGraphs[node] = pointsToGraph

                if (!intraproceduralAnalysis(callGraph.directEdges[node]!!, escapeAnalysisResults, maxPointsToGraphSizeOf(node))) {
                    failedToConverge = true
                } else {
                    val endResult = escapeAnalysisResults[function.symbol]!!
                    if (startResult == endResult) {
                        context.log { "Escape analysis is not changed" }
                    } else {
                        context.log { "Escape analysis was refined:" }
                        endResult.logDigraph(context)
                        if (numberOfRuns[node]!! > DivergenceResolutionParams.MaxAttempts)
                            failedToConverge = true
                        else {
                            callGraph.reversedEdges[node]?.forEach {
                                if (nodes.contains(it))
                                    toAnalyze.add(it)
                            }
                        }
                    }
                }

                if (failedToConverge)
                    context.log { "WARNING: Escape analysis for $node seems not to be converging. Falling back to conservative strategy." }
            }

            if (failedToConverge) {
                /*pointsToGraphs = */analyzePessimistically(multiNode, escapeAnalysisResults)
            }


//            val nodes = multiNode.nodes.toList()
//
//            if (nodes.size != 1)
//                return // TODO
//
//            if (callGraph.directEdges[nodes[0]]?.symbol?.irFunction?.fileOrNull?.name?.endsWith("z.kt") != true) return
//
//            context.logMultiple {
//                +"Analyzing multiNode:\n    ${nodes.joinToString("\n   ") { it.toString() }}"
//                nodes.forEach { from ->
//                    +"IR"
//                    +(from.irFunction?.dump() ?: "")
//                    callGraph.directEdges[from]!!.callSites.forEach { to ->
//                        +"CALL"
//                        +"   from $from"
//                        +"   to ${to.actualCallee}"
//                    }
//                }
//            }
//
//            if (nodes.size == 1)
//                intraproceduralAnalysis(callGraph.directEdges[nodes[0]] ?: return, escapeAnalysisResults, Int.MAX_VALUE)
//            else {
//                TODO()
//            }
        }

        private fun analyzePessimistically(multiNode: DirectedGraphMultiNode<DataFlowIR.FunctionSymbol.Declared>,
                                           escapeAnalysisResults: MutableMap<IrFunctionSymbol, EscapeAnalysisResult>) {
            val nodes = multiNode.nodes.filter { callGraph.directEdges.containsKey(it) }.toMutableSet()
            //val pointsToGraphs = mutableMapOf<DataFlowIR.FunctionSymbol.Declared, InterproceduralAnalysis.PointsToGraph>()
            val computationStates = mutableMapOf<DataFlowIR.FunctionSymbol.Declared, ComputationState>()
            nodes.forEach { computationStates[it] = ComputationState.NEW }
            val toAnalyze = nodes.toMutableList()
            while (toAnalyze.isNotEmpty()) {
                val node = toAnalyze.peek()!!
                val function = node.irFunction!!
                val state = computationStates[node]!!
                val callGraphNode = callGraph.directEdges[node]!!
                val callSites = callGraphNode.callSites
                when (state) {
                    ComputationState.NEW -> {
                        computationStates[node] = ComputationState.PENDING
                        for (callSite in callSites) {
                            val next = callSite.actualCallee
                            val calleeComputationState = computationStates[next]
                            if (callSite.isVirtual
                                    || next !is DataFlowIR.FunctionSymbol.Declared // An external call.
                                    || calleeComputationState == null // A call to a function from other component.
                                    || calleeComputationState == ComputationState.DONE // Already analyzed.
                            ) {
                                continue
                            }

                            if (calleeComputationState == ComputationState.PENDING) {
                                // A cycle - break it by assuming nothing about the callee.
                                // This is not the callee's final result - it will be recomputed later in the loop.
                                val callee = next.irFunction ?: error("No IR for $next")
                                escapeAnalysisResults[callee.symbol] = EscapeAnalysisResult.pessimistic(callee)
                            } else {
                                computationStates[next] = ComputationState.NEW
                                toAnalyze.push(next)
                            }
                        }
                    }

                    ComputationState.PENDING -> {
                        toAnalyze.pop()
                        computationStates[node] = ComputationState.DONE
//                        val pointsToGraph = PointsToGraph(node)
                        if (intraproceduralAnalysis(callGraphNode, escapeAnalysisResults, maxPointsToGraphSizeOf(node))) {
                            //pointsToGraphs[function] = pointsToGraph
                        } else {
                            // TODO: suboptimal. May be it is possible somehow handle the entire component at once?
                            context.log {
                                "WARNING: Escape analysis for $node seems not to be converging." +
                                        " Assuming conservative results."
                            }
                            escapeAnalysisResults[function.symbol] = EscapeAnalysisResult.pessimistic(function)
                            // Invalidate the points-to graph.
//                            pointsToGraphs[function] = PointsToGraph(function).apply {
//                                allNodes.forEach { it.depth = EscapeAnalysis.Depths.GLOBAL }
//                            }
                        }
                    }

                    ComputationState.DONE -> {
                        toAnalyze.pop()
                    }
                }
            }

//            return pointsToGraphs
        }

        private class EscapeAnalysisResult(val graph: PointsToGraph, val returnValue: Node, val objectsReferencedFromThrown: BitSet) {
            fun logDigraph(context: Context) {
                graph.logDigraph(context) {
                    thrownNodes = objectsReferencedFromThrown
                    +returnValue
                }
            }

            companion object {
                fun optimistic(function: IrFunction): EscapeAnalysisResult {
                    val pointsToGraph = PointsToGraph(PointsToGraphForest())
                    function.allParameters.forEachIndexed { index, parameter -> pointsToGraph.addParameter(parameter, index) }
                    val returnValue = with(pointsToGraph) {
                        when {
                            function.returnType.isNothing() -> Node.Nothing
                            function is IrConstructor || function.returnType.isUnit() -> Node.Unit
                            else -> newTempVariable("RET@${function.name}")
                        }
                    }
                    return EscapeAnalysisResult(pointsToGraph, returnValue, BitSet())
                }

                fun pessimistic(function: IrFunction): EscapeAnalysisResult {
                    val result = optimistic(function)
                    with(result.graph) {
                        val globalEscapes = globalNode.getField(escapesField)
                        parameterNodes.values.forEach { globalEscapes.addEdge(it) }
                        (result.returnValue as? Node.Variable)?.let { globalEscapes.addEdge(it) }
                    }
                    return result
                }

                fun fromBits(function: IrFunction, escapesMask: Int, pointsToMasks: List<Int>): EscapeAnalysisResult {
                    val result = optimistic(function)
                    var returnObject: Node.Object? = null
                    val parameterNodes = arrayOfNulls<Node.Parameter>(result.graph.parameterNodes.size)
                    result.graph.parameterNodes.values.forEach { parameterNodes[it.index] = it }

                    fun getIntestines(index: Int): Node.FieldValue {
                        val obj = if (index < parameterNodes.size)
                            parameterNodes[index]!!
                        else {
                            returnObject ?: result.graph.newObject().also {
                                returnObject = it
                                (result.returnValue as Node.Variable).addEdge(it)
                            }
                        }
                        return with(result.graph) { obj.getField(intestinesField) }
                    }

                    if (escapesMask != 0) {
                        val globalEscapes = with(result.graph) { globalNode.getField(escapesField) }
                        for (parameterNode in parameterNodes) {
                            if (escapesMask and (1 shl parameterNode!!.index) != 0)
                                globalEscapes.addEdge(parameterNode)
                        }
                        if (escapesMask and (1 shl parameterNodes.size) != 0)
                            (result.returnValue as? Node.Variable)?.let { globalEscapes.addEdge(it) }
                    }
                    pointsToMasks.forEachIndexed { fromIndex, mask ->
                        for (toIndex in pointsToMasks.indices) {
                            // Read a nibble at position [toIndex].
                            val pointsToKind = (mask shr (4 * toIndex)) and 15
                            require(pointsToKind <= 4) { "Invalid pointsTo kind $pointsToKind" }
                            if (pointsToKind == 0) continue
                            val fromVariable = when {
                                pointsToKind >= 3 -> getIntestines(fromIndex)
                                fromIndex == parameterNodes.size -> result.returnValue as Node.Variable
                                else -> error("A parameter can point to something only through its fields")
                            }
                            val toNode = when {
                                pointsToKind % 2 == 0 -> getIntestines(toIndex)
                                toIndex == parameterNodes.size -> result.returnValue
                                else -> parameterNodes[toIndex]!!
                            }
                            fromVariable.addEdge(toNode)
                        }
                    }
                    return result
                }
            }
        }

        private sealed class Node(var id: Int) {
//            @Suppress("LeakingThis")
//            var mirroredNode = this

            abstract fun shallowCopy(): Node

            // TODO: Do we need to distinguish fictitious objects and materialized ones?
            open class Object(id: Int, var loop: IrLoop?, val label: String? = null) : Node(id) {
                val fields = mutableMapOf<IrFieldSymbol, FieldValue>()

                val isFictitious get() = label == null

                override fun shallowCopy() = Object(id, loop, label)
                override fun toString() = "${label ?: "D"}$id"
            }

            class Parameter(id: Int, val index: Int, val irValueParameter: IrValueParameter) : Object(id, null) {
                override fun shallowCopy() = Parameter(id, index, irValueParameter)
                override fun toString() = "<P:${irValueParameter.name}[$index]>$id"
            }

            sealed class Reference(id: Int) : Node(id) {
                // TODO: Rename?
                val assignedWith = mutableListOf<Node>()
                val assignedTo = mutableListOf<Reference>()

                fun addEdge(to: Node) {
                    assignedWith.add(to)
                    (to as? Reference)?.assignedTo?.add(this)
                }
            }

            class FieldValue(id: Int, val ownerId: Int, val field: IrFieldSymbol) : Reference(id) {
                override fun shallowCopy() = FieldValue(id, ownerId, field)
                override fun toString() = "F$id"
            }

            class Variable(id: Int, val irVariable: IrVariable?, val label: String) : Reference(id) {
                constructor(id: Int, irVariable: IrVariable) : this(id, irVariable, "<V:${irVariable.name}>")
                constructor(id: Int, label: String) : this(id, null, label)

                override fun shallowCopy() = Variable(id, irVariable, label)
                override fun toString() = "$label$id"
            }

            // TODO: Make separate inheritor (SpecialObject isn't allowed to have any fields, force this with type hierarchy).
            open class SpecialObject(id: Int, label: String) : Object(id, null, label) {
                override fun toString() = label!!
                override fun shallowCopy() = this
            }

            object Nothing : SpecialObject(NOTHING_ID, "⊥")
            object Null : SpecialObject(NULL_ID, "NULL")
            object Unit : SpecialObject(UNIT_ID, "( )")

            inline fun forEachPointee(block: (Node) -> kotlin.Unit) {
                when (this) {
                    is Object -> this.fields.values.forEach { block(it) }
                    is Reference -> this.assignedWith.forEach { block(it) }
                }
            }

            companion object {
                const val NOTHING_ID = 0
                const val NULL_ID = 1
                const val UNIT_ID = 2
                const val GLOBAL_ID = 3
                const val LOWEST_NODE_ID = 4
            }
        }

        private class PointsToGraphForest(startNodeId: Int = Node.LOWEST_NODE_ID) {
            private var currentNodeId = startNodeId
            fun nextNodeId() = currentNodeId++
            val totalNodes get() = currentNodeId

            // TODO: Optimize.
            private val ids = mutableMapOf<Pair<Int, Any>, Int>()
            fun getAssociatedId(nodeId: Int, obj: Any) = ids.getOrPut(Pair(nodeId, obj)) { nextNodeId() }

            fun cloneGraph(graph: PointsToGraph, otherNodesToMap: MutableList<Node>): PointsToGraph {
                val parameterNodes = mutableMapOf<IrValueParameter, Node.Parameter>()
                val variableNodes = mutableMapOf<IrVariable, Node.Variable>()
                val newNodes = ArrayList<Node?>(graph.nodes.size)
                graph.nodes.mapTo(newNodes) { node ->
                    node?.shallowCopy()?.also { copy ->
                        (copy as? Node.Parameter)?.irValueParameter?.let { parameterNodes[it] = copy }
                        (copy as? Node.Variable)?.irVariable?.let { variableNodes[it] = copy }
                    }
                }
                for (node in graph.nodes) {
                    if (node == null) continue
                    when (val newNode = newNodes[node.id]!!) {
                        is Node.Object -> {
                            (node as Node.Object).fields.entries.forEach { (field, fieldNode) ->
                                newNode.fields[field] = newNodes[fieldNode.id] as Node.FieldValue
                            }
                        }
                        is Node.Reference -> {
                            (node as Node.Reference).assignedWith.mapTo(newNode.assignedWith) { newNodes[it.id]!! }
                            node.assignedTo.mapTo(newNode.assignedTo) { newNodes[it.id] as Node.Reference }
                        }
                    }
                }
                for (i in otherNodesToMap.indices)
                    otherNodesToMap[i] = newNodes[otherNodesToMap[i].id]!!
                return PointsToGraph(this, newNodes, parameterNodes, variableNodes)
            }

            private fun isPrime(x: Int): Boolean {
                if (x <= 3) return true
                if (x % 2 == 0) return false
                var r = 3
                while (r * r <= x) {
                    if (x % r == 0) return false
                    r += 2
                }
                return true
            }

            private fun makePrime(p: Int): Int {
                var x = p
                while (true) {
                    if (isPrime(x)) return x
                    ++x
                }
            }

            fun mergeGraphs(graphs: List<PointsToGraph>, otherNodesToMap: MutableList<Node>): PointsToGraph {
                val newNodes = ArrayList<Node?>(totalNodes).also { it.ensureSize(totalNodes) }
                val parameterNodes = mutableMapOf<IrValueParameter, Node.Parameter>()
                val variableNodes = mutableMapOf<IrVariable, Node.Variable>()
                var edgesCount = 0
                graphs.flatMap { it.nodes }.forEach { node ->
                    if (node == null) return@forEach
                    (node as? Node.Reference)?.let { edgesCount += it.assignedWith.size }
                    if (newNodes[node.id] == null)
                        newNodes[node.id] = node.shallowCopy().also { copy ->
                            (copy as? Node.Parameter)?.irValueParameter?.let { parameterNodes[it] = copy }
                            (copy as? Node.Variable)?.irVariable?.let { variableNodes[it] = copy }
                        }
                }

                fun addEdge(from: Int, to: Int, bagOfEdges: LongArray): Boolean {
                    val value = from.toLong() or (to.toLong() shl 32)
                    // This is 64-bit extension of a hashing method from Knuth's "The Art of Computer Programming".
                    // The magic constant is the closest prime to 2^64 * phi, where phi is the golden ratio.
                    var index = ((value.toULong() * 11400714819323198393UL) % bagOfEdges.size.toULong()).toInt()
                    while (bagOfEdges[index] != 0L && bagOfEdges[index] != value) {
                        ++index
                        if (index == bagOfEdges.size) index = 0
                    }
                    if (bagOfEdges[index] != 0L) return false
                    bagOfEdges[index] = value
                    return true
                }

                val edges = LongArray(makePrime(5 * edgesCount))
                graphs.flatMap { it.nodes }.forEach { node ->
                    if (node == null) return@forEach
                    when (val newNode = newNodes[node.id]!!) {
                        is Node.Object -> {
                            (node as Node.Object).fields.forEach { (field, fieldNode) ->
                                newNode.fields.getOrPut(field) { newNodes[fieldNode.id] as Node.FieldValue }
                            }
                        }
                        is Node.Reference -> {
                            (node as Node.Reference).assignedWith.forEach {
                                if (addEdge(node.id, it.id, edges))
                                    newNode.addEdge(newNodes[it.id]!!)
                            }
                        }
                    }
                }

                for (i in otherNodesToMap.indices)
                    otherNodesToMap[i] = newNodes[otherNodesToMap[i].id]!!
                return PointsToGraph(this, newNodes, parameterNodes, variableNodes)
            }

            companion object {
                fun graphsAreEqual(first: PointsToGraph, second: PointsToGraph): Boolean {
                    val size = kotlin.math.min(first.nodes.size, second.nodes.size)
                    for (id in size until first.nodes.size)
                        if (first.nodes[id] != null) return false
                    for (id in size until second.nodes.size)
                        if (second.nodes[id] != null) return false
                    val firstPointsTo = BitSet(size)
                    val secondPointsTo = BitSet(size)
                    for (id in 0 until size) {
                        val firstNode = first.nodes[id]
                        val secondNode = second.nodes[id]
                        when (firstNode) {
                            null -> if (secondNode != null) return false
                            is Node.Object -> {
                                if (secondNode !is Node.Object) return false
                                if (firstNode.fields.size != secondNode.fields.size) return false
                                firstNode.fields.forEach { (field, fieldValue) ->
                                    if (fieldValue.id != secondNode.fields[field]?.id) return false
                                }
                            }
                            is Node.Reference -> {
                                if (secondNode !is Node.Reference) return false
                                if (firstNode::class.java != secondNode::class.java) return false
                                firstNode.assignedWith.forEach { firstPointsTo.set(it.id) }
                                secondNode.assignedWith.forEach { secondPointsTo.set(it.id) }
                                firstNode.assignedWith.forEach { if (!secondPointsTo.get(it.id)) return false }
                                secondNode.assignedWith.forEach { if (!firstPointsTo.get(it.id)) return false }
                                firstNode.assignedWith.forEach { firstPointsTo.clear(it.id) }
                                secondNode.assignedWith.forEach { secondPointsTo.clear(it.id) }
                            }
                        }
                    }

                    return true
                }
            }
        }

        private interface NodeFactory {
            fun newTempVariable(label: String = "T"): Node.Variable
            fun newObject(label: String? = null): Node.Object
        }

        private fun NodeFactory.newPhiNode() = newTempVariable("φ")

        private class PointsToGraph(
                val forest: PointsToGraphForest,
                val nodes: MutableList<Node?>,
                val parameterNodes: MutableMap<IrValueParameter, Node.Parameter>,
                val variableNodes: MutableMap<IrVariable, Node.Variable>
        ) : NodeFactory {
            constructor(forest: PointsToGraphForest)
                    : this(forest, mutableListOf(Node.Nothing, Node.Null, Node.Unit, Node.Object(Node.GLOBAL_ID, null, "G")), mutableMapOf(), mutableMapOf())

            private inline fun <reified T : Node> getOrPutNodeAt(id: Int, nodeBuilder: (Int) -> T): T {
                nodes.ensureSize(id + 1)
                nodes[id]?.let { return it as T }
                return nodeBuilder(id).also { nodes[id] = it }
            }

            private inline fun <reified T : Node> putNewNodeAt(id: Int, nodeBuilder: (Int) -> T): T {
                nodes.ensureSize(id + 1)
                val node = nodeBuilder(id)
                require(nodes[id] == null) { "Duplicate node at $id: ${nodes[id]} and $node" }
                nodes[id] = node
                return node
            }

            inner class NodeContext(var currentNodeId: Int, val loop: IrLoop) : NodeFactory {
                private fun getNodeId(): Int {
                    require(currentNodeId != -1) { "Context can only create one node" }
                    return currentNodeId.also { currentNodeId = -1 }
                }

                override fun newTempVariable(label: String) = getOrPutNodeAt(getNodeId()) { Node.Variable(it, label) }
                override fun newObject(label: String?) = getOrPutNodeAt(getNodeId()) { Node.Object(it, loop, label) }
            }

            val globalNode get() = nodes[Node.GLOBAL_ID] as Node.Object

            fun copyFrom(other: PointsToGraph) {
                nodes.clear()
                nodes.addAll(other.nodes)
                other.parameterNodes.forEach { (parameter, parameterNode) -> parameterNodes[parameter] = parameterNode }
                other.variableNodes.forEach { (variable, variableNode) -> variableNodes[variable] = variableNode }
            }

            fun clear() {
                globalNode.fields.clear()
                for (id in Node.LOWEST_NODE_ID until nodes.size) {
                    val node = nodes[id] ?: continue
                    when {
                        node is Node.Variable && node.irVariable != null -> {
                            node.assignedWith.clear()
                            node.assignedTo.clear()
                        }
                        node is Node.Parameter -> node.fields.clear()
                        else -> nodes[id] = null
                    }
                }
            }

            fun addParameter(parameter: IrValueParameter, index: Int) = putNewNodeAt(forest.nextNodeId()) { id ->
                Node.Parameter(id, index, parameter).also { parameterNodes[parameter] = it }
            }

            fun getOrAddVariable(irVariable: IrVariable) = variableNodes.getOrPut(irVariable) {
                putNewNodeAt(forest.nextNodeId()) { Node.Variable(it, irVariable) }
            }

            override fun newTempVariable(label: String) = getOrPutNodeAt(forest.nextNodeId()) { Node.Variable(it, label) }
            override fun newObject(label: String?) = getOrPutNodeAt(forest.nextNodeId()) { Node.Object(it, null, label) }

            fun at(loop: IrLoop?, element: IrElement?, anchorNodeId: Int = 0): NodeFactory =
                    loop?.let { NodeContext(forest.getAssociatedId(anchorNodeId, element!!), it) } ?: this

            fun Node.Object.getField(field: IrFieldSymbol): Node.FieldValue {
                require(this !is Node.SpecialObject)
                return fields.getOrPut(field) {
                    putNewNodeAt(forest.getAssociatedId(id, field)) { Node.FieldValue(it, id, field) }
                }
            }

            fun clone(otherNodesToMap: MutableList<Node> = mutableListOf()) = forest.cloneGraph(this, otherNodesToMap)

            // Bypasses node to its immediate pointees.
            //
            //                    *-*-*-> W2
            //                    |
            // Operation: U1 -*-> V -> O
            //                    ^
            //                    *
            //                    |
            //            U2 -*-*-*
            //
            //            *-*-*-*-*-*-*-*-*-> W1
            //            |                   ^
            //            U1 --------> O      *
            //                         ^      |
            //                         |      *
            //                         |      |
            //            U2 ----------+      *
            //            |                   |
            //            *-*-*-*-*-*-*-*-*-*-*
            //
            // Nodes naming: u -> v -> w
            fun bypass(v: Node.Reference, loop: IrLoop?, element: IrElement?, anchorNodeId: Int = 0) {
                require(v.assignedTo.isNotEmpty())
                val assignedWith = v.assignedWith.ifEmpty { listOf(at(loop, element, anchorNodeId).newObject()) }
                for (u in v.assignedTo) {
                    u.assignedWith.removeAll(v)
                    u.assignedWith.addAll(assignedWith)
                }
                assignedWith.forEach { w ->
                    (w as? Node.Reference)?.assignedTo?.run {
                        removeAll(v)
                        addAll(v.assignedTo)
                    }
                }
                v.assignedWith.clear()
                v.assignedTo.clear()
            }

            // Called only for get/set field of [node] further.
            fun getObjectNodes(node: Node, loop: IrLoop?, element: IrElement?, anchorNodeId: Int = 0) = when (node) {
                is Node.SpecialObject -> emptyList()
                is Node.Object -> listOf(node)
                is Node.Reference -> {
                    val visited = BitSet()
                    visited.set(Node.NULL_ID) // Skip null, as getting/setting its field would lead to NPE.
                    visited.set(Node.UNIT_ID) // Unit might get here because of generics and non-exact devirtualization.
                    val reachable = mutableListOf<Node.Reference>()

                    fun findReachable(node: Node.Reference) {
                        visited.set(node.id)
                        reachable.add(node)
                        for (pointee in node.assignedWith) {
                            if (pointee is Node.Reference && !visited.get(pointee.id))
                                findReachable(pointee)
                        }
                    }

                    findReachable(node)
                    buildList {
                        fun tryAddObject(obj: Node.Object) {
                            if (!visited.get(obj.id)) {
                                visited.set(obj.id)
                                add(obj)
                            }
                        }

                        for (reachableNode in reachable) {
                            if (reachableNode.assignedWith.isNotEmpty()) {
                                reachableNode.assignedWith.forEach { pointee ->
                                    (pointee as? Node.Object)?.let { tryAddObject(it) }
                                }
                            } else {
                                val fictitiousObject = at(loop, element, anchorNodeId).newObject()
                                reachableNode.assignedWith.add(fictitiousObject)
                                tryAddObject(fictitiousObject)
                            }
                        }
                    }
                }
            }

            fun log(context: Context) = context.logMultiple {
                fun Node.format() = "$id ${toString()} ${System.identityHashCode(this)}"

                fun Node.formatIncomingEdge() = when (this) {
                    is Node.Object -> "--->"
                    is Node.Reference -> "-*->"
                }

                +"parameters:"
                parameterNodes.values.forEach { +"    ${it.format()}" }
                +"variables:"
                variableNodes.values.forEach { +"    ${it.format()}" }
                +"nodes:"
                nodes.forEach { node -> node?.let { +"    ${it.format()}" } }
                +"edges:"
                nodes.forEach { node ->
                    when (node) {
                        null -> return@forEach
                        is Node.Object -> {
                            node.fields.forEach { (field, fieldValue) ->
                                +"    ${node.format()} ---> ${fieldValue.format()}[field=${field.name}]"
                            }
                        }
                        is Node.Reference -> {
                            node.assignedWith.forEach { +"    ${node.format()} ${it.formatIncomingEdge()} ${it.format()}" }
                            node.assignedTo.forEach { +"    ${node.format()} <-*- ${it.format()}" }
                        }
                    }
                }
            }

            class LoggingOptions(val highlightLifetimes: Boolean, val markedNodes: BitSet, val thrownNodes: BitSet)

            class LoggingOptionsBuilder {
                var highlightLifetimes = false
                val markedNodes = BitSet()
                var thrownNodes = BitSet()

                operator fun Node.unaryPlus() = markedNodes.set(this.id)

                fun build() = LoggingOptions(highlightLifetimes, markedNodes, thrownNodes)
            }

            fun logDigraph(context: Context, block: LoggingOptionsBuilder.() -> Unit) = context.logMultiple {
//                log(context)

                val builder = LoggingOptionsBuilder()
                builder.block()
                val options = builder.build()

                +"digraph {"
                +"rankdir=\"LR\";"

                fun Node.format(): String {
                    val name = when (this) {
                        Node.Nothing -> "nothing"
                        Node.Null -> "null"
                        Node.Unit -> "unit"
                        globalNode -> "global"
                        is Node.Parameter -> "param$id"
                        is Node.Object -> "obj$id"
                        is Node.FieldValue -> "field$id"
                        is Node.Variable -> "var$id"
                    }

                    val label = "label=\"$this\""
                    val shape = " shape=${if (this is Node.Object) "rect" else "oval"}"
                    val colors = buildString {
                        if ((this@format as? Node.Object)?.loop != null)
                            append(" penwidth=3.0 color=deepskyblue")
                        val referencedFromThrown = options.thrownNodes.get(this@format.id)
                        val marked = options.markedNodes.get(this@format.id)
                        when {
                            referencedFromThrown && marked -> append(" fillcolor=orangered style=filled")
                            referencedFromThrown -> append(" fillcolor=crimson style=filled")
                            marked -> append(" fillcolor=yellow style=filled")
                        }
                    }
                    +"$name[$label$shape$colors]"
                    return name
                }

                val notNullNodes = nodes.filterNotNull()
                val vertices = notNullNodes.associateWith { it.format() }

                notNullNodes.forEach { node ->
                    val vertex = vertices[node]!!
                    when (node) {
                        is Node.Object ->
                            node.fields.forEach { (field, fieldValue) -> +"$vertex -> ${vertices[fieldValue] ?: error("No node ${fieldValue.id} in graph")}[label=\"${field.name}\"];" }
                        is Node.Reference ->
                            node.assignedWith.forEach { +"$vertex -> ${vertices[it] ?: error("No node ${it.id} in graph")};" }
                    }
                }

                +"}"
            }
        }

        private class ExpressionResult(val value: Node, val graph: PointsToGraph)

        private data class BuilderState(val graph: PointsToGraph, val loop: IrLoop?, val insideATry: Boolean)

        private inner class PointsToGraphBuilder(
                val function: IrFunction,
                val forest: PointsToGraphForest,
                val escapeAnalysisResults: Map<IrFunctionSymbol, EscapeAnalysisResult>,
                val devirtualizedCallSites: Map<IrCall, List<IrFunctionSymbol>>,
                val maxAllowedGraphSize: Int,
                val needDebug: Boolean,
        ) : IrElementVisitor<Node, BuilderState> {

            private inline fun debug(block: () -> Unit) =
                    if (needDebug) block() else Unit

            val irBuilder = context.createIrBuilder(function.symbol)
            val fictitiousLoopsStarts = mutableMapOf<IrLoop, IrElement>()
            val fictitiousLoopsEnds = mutableMapOf<IrLoop, IrElement>()
            val devirtualizedFictitiousCallSites = mutableMapOf<IrCall, List<IrFunctionAccessExpression>>()

            val returnTargetsResults = mutableMapOf<IrReturnTargetSymbol, MutableList<ExpressionResult>>()
            val loopsContinueResults = mutableMapOf<IrLoop, MutableList<ExpressionResult>>()
            val loopsBreakResults = mutableMapOf<IrLoop, MutableList<ExpressionResult>>()
            val objectsReferencedFromThrown = BitSet()

            fun build(): EscapeAnalysisResult {
                require(Node.Nothing.fields.isEmpty())
                require(Node.Null.fields.isEmpty())
                require(Node.Unit.fields.isEmpty())
                val pointsToGraph = PointsToGraph(forest)
                function.allParameters.forEachIndexed { index, parameter -> pointsToGraph.addParameter(parameter, index) }
                val returnResults = mutableListOf<ExpressionResult>()
                returnTargetsResults[function.symbol] = returnResults
                (function.body as IrBlockBody).statements.forEach { it.accept(this, BuilderState(pointsToGraph, null, false)) }
                val functionResult = controlFlowMergePoint(pointsToGraph, null, null, function.returnType, returnResults)
                return EscapeAnalysisResult(pointsToGraph, functionResult, objectsReferencedFromThrown)
            }

            fun PointsToGraph.logDigraph(vararg markedNodes: Node) =
                    logDigraph(context) {
                        thrownNodes = objectsReferencedFromThrown
                        for (node in markedNodes) +node
                    }

            fun PointsToGraph.logDigraph(context: Context, markedNodes: List<Node>) =
                    logDigraph(context) {
                        thrownNodes = objectsReferencedFromThrown
                        for (node in markedNodes) +node
                    }

            fun PointsToGraph.unreachable(): Node {
                clear()
                return Node.Nothing
            }

            fun controlFlowMergePoint(graph: PointsToGraph, loop: IrLoop?, element: IrElement?, type: IrType, results: List<ExpressionResult>) =
                    controlFlowMergePointImpl(graph, loop, element, type, results.filterNot { it.value == Node.Nothing })

            fun controlFlowMergePointImpl(graph: PointsToGraph, loop: IrLoop?, element: IrElement?,
                                          type: IrType, results: List<ExpressionResult>
            ): Node = when (results.size) {
                0 -> {
                    graph.unreachable()
                }
                1 -> {
                    graph.copyFrom(results[0].graph)
                    when {
                        type.isNothing() -> Node.Nothing
                        type.isUnit() -> Node.Unit
                        else -> results[0].value
                    }
                }
                else -> {
                    debug {
                        context.log { "before CFG merge" }
                        results.forEachIndexed { index, result ->
                            context.log { "#$index:" }
                            result.graph.logDigraph(result.value)
                        }
                    }
                    val resultNodes = results.map { it.value }.toMutableList()
                    val mergedGraph = forest.mergeGraphs(results.map { it.graph }, resultNodes)
                    graph.copyFrom(mergedGraph)
                    when {
                        type.isNothing() -> Node.Nothing
                        type.isUnit() -> Node.Unit
                        else -> graph.at(loop, element).newPhiNode().also { phiNode ->
                            resultNodes.forEach { phiNode.addEdge(it) }
                        }
                    }.also {
                        debug {
                            context.log { "after CFG merge" }
                            graph.logDigraph(it)
                        }
                    }
                }
            }

            override fun visitElement(element: IrElement, data: BuilderState): Node = TODO(element.render())
            override fun visitExpression(expression: IrExpression, data: BuilderState): Node = TODO(expression.render())
            override fun visitDeclaration(declaration: IrDeclarationBase, data: BuilderState): Node = TODO(declaration.render())

            fun BuilderState.constObjectNode(expression: IrExpression) =
                    graph.at(loop, expression).newObject("<C:${expression.type.erasedUpperBound.name}>")

            override fun visitConst(expression: IrConst<*>, data: BuilderState) =
                    expression.value?.let { data.constObjectNode(expression) } ?: Node.Null
            override fun visitConstantValue(expression: IrConstantValue, data: BuilderState) = data.constObjectNode(expression)
            override fun visitFunctionReference(expression: IrFunctionReference, data: BuilderState) = data.constObjectNode(expression)
            override fun visitVararg(expression: IrVararg, data: BuilderState) = data.constObjectNode(expression)

            override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: BuilderState) = Node.Unit

            override fun visitTypeOperator(expression: IrTypeOperatorCall, data: BuilderState): Node {
                val argResult = expression.argument.accept(this, data)
                return when (expression.operator) {
                    IrTypeOperator.CAST, IrTypeOperator.IMPLICIT_CAST, IrTypeOperator.SAFE_CAST -> argResult
                    IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> Node.Unit
                    IrTypeOperator.INSTANCEOF, IrTypeOperator.NOT_INSTANCEOF -> data.constObjectNode(expression)
                    else -> error("Not expected: ${expression.operator}")
                }
            }

            override fun visitGetValue(expression: IrGetValue, data: BuilderState) = when (val owner = expression.symbol.owner) {
                is IrValueParameter -> data.graph.parameterNodes[owner] ?: error("Unknown value parameter: ${owner.render()}")
                is IrVariable -> data.graph.variableNodes[owner] ?: error("Unknown variable: ${owner.render()}")
                else -> error("Unknown value declaration: ${owner.render()}")
            }

            fun PointsToGraph.eraseValue(reference: Node.Reference, loop: IrLoop?, element: IrElement, anchorNodeId: Int = 0) {
                if (reference.assignedTo.isNotEmpty())
                    bypass(reference, loop, element, anchorNodeId)
                else
                    reference.assignedWith.clear()
            }

            override fun visitSetValue(expression: IrSetValue, data: BuilderState): Node = with(data.graph) {
                debug {
                    context.log { "before ${expression.dump()}" }
                    logDigraph()
                }

                val valueNode = expression.value.accept(this@PointsToGraphBuilder, data)
                debug {
                    context.log { "after evaluating value" }
                    logDigraph(valueNode)
                }

                val variable = expression.symbol.owner as IrVariable
                val variableNode = variableNodes[variable] ?: error("Unknown variable: ${variable.render()}")
                if (!data.insideATry)
                    eraseValue(variableNode, data.loop, expression)
                variableNode.addEdge(valueNode)
                debug {
                    context.log { "after ${expression.dump()}" }
                    logDigraph(variableNode)
                }

                Node.Unit
            }

            override fun visitVariable(declaration: IrVariable, data: BuilderState): Node = with(data.graph) {
                debug {
                    context.log { "before ${declaration.dump()}" }
                    logDigraph()
                }

                require(data.loop != null || variableNodes[declaration] == null) {
                    "Duplicate variable declaration: ${declaration.render()}"
                }

                val valueNode = declaration.initializer?.accept(this@PointsToGraphBuilder, data)
                debug {
                    valueNode?.let {
                        context.log { "after evaluating initializer" }
                        logDigraph(it)
                    }
                }

                val variableNode = getOrAddVariable(declaration)
                valueNode?.let {
                    if (!data.insideATry)
                        eraseValue(variableNode, data.loop, declaration)
                    variableNode.addEdge(it)
                }
                debug {
                    context.log { "after ${declaration.dump()}" }
                    logDigraph(variableNode)
                }

                Node.Unit
            }

            override fun visitGetField(expression: IrGetField, data: BuilderState): Node = with(data.graph) {
                debug {
                    context.log { "before ${expression.dump()}" }
                    logDigraph()
                }

                val receiverNode = expression.receiver?.accept(this@PointsToGraphBuilder, data) ?: globalNode
                debug {
                    context.log { "after evaluating receiver" }
                    logDigraph(receiverNode)
                }

                val receiverObjects = getObjectNodes(receiverNode, data.loop, expression.receiver)
                debug {
                    context.log { "after getting receiver's objects" }
                    logDigraph(context, receiverObjects)
                }

                return (when (receiverObjects.size) {
                    0 -> { // This will lead to NPE at runtime.
                        unreachable()
                    }
                    1 -> {
                        receiverObjects[0].getField(expression.symbol)
                    }
                    else -> at(data.loop, expression).newPhiNode().also { phiNode ->
                        for (receiver in receiverObjects)
                            phiNode.addEdge(receiver.getField(expression.symbol))
                    }
                }).also {
                    debug {
                        context.log { "after ${expression.dump()}" }
                        logDigraph(it)
                    }
                }
            }

            override fun visitSetField(expression: IrSetField, data: BuilderState): Node = with(data.graph) {
                debug {
                    context.log { "before ${expression.dump()}" }
                    logDigraph()
                }

                val receiverNode = expression.receiver?.accept(this@PointsToGraphBuilder, data) ?: globalNode
                debug {
                    context.log { "after evaluating receiver" }
                    logDigraph(receiverNode)
                }

                val valueNode = expression.value.accept(this@PointsToGraphBuilder, data)
                debug {
                    context.log { "after evaluating value" }
                    logDigraph(valueNode)
                }

                val receiverObjects =
                        if (data.graph.forest.totalNodes > maxAllowedGraphSize)
                            emptyList()
                        else
                            getObjectNodes(data.graph.nodes[receiverNode.id]!!, data.loop, expression.receiver)
                debug {
                    context.log { "after getting receiver's objects" }
                    logDigraph(context, receiverObjects)
                }

                return (if (receiverObjects.isEmpty()) { // This will lead to NPE at runtime.
                    unreachable()
                } else {
                    receiverObjects.forEach { receiverObject ->
                        val fieldNode = receiverObject.getField(expression.symbol)
                        // TODO: Probably can do for any outer loop as well (not only for the current).
                        if (receiverObjects.size == 1 && data.loop == receiverObjects[0].loop && !data.insideATry)
                            eraseValue(fieldNode, data.loop, expression)
                        fieldNode.addEdge(valueNode)
                    }
                    Node.Unit
                }).also {
                    debug {
                        context.log { "after ${expression.dump()}" }
                        logDigraph()
                    }
                }
            }

            override fun visitReturn(expression: IrReturn, data: BuilderState): Node {
                val result = expression.value.accept(this, data)
                // TODO: Looks clumsy.
                val list = mutableListOf(result)
                val clone = data.graph.clone(list)
                (returnTargetsResults[expression.returnTargetSymbol] ?: error("Unknown return target: ${expression.render()}"))
                        .add(ExpressionResult(list[0], clone))
                return data.graph.unreachable()
            }

            override fun visitThrow(expression: IrThrow, data: BuilderState) = with(data.graph) {
                debug {
                    context.log { "before ${expression.dump()}" }
                    data.graph.logDigraph()
                }
                val value = expression.value.accept(this@PointsToGraphBuilder, data)
                val visited = BitSet()
                val newObjectsReferencedFromThrown = mutableListOf<Node.Object>()

                fun markReachableObjects(node: Node) {
                    visited.set(node.id)
                    if (node is Node.Object && !objectsReferencedFromThrown.get(node.id)) {
                        objectsReferencedFromThrown.set(node.id)
                        newObjectsReferencedFromThrown.add(node)
                    }
                    node.forEachPointee {
                        if (!visited.get(it.id)) markReachableObjects(it)
                    }
                }

                markReachableObjects(value)
                debug {
                    context.log { "after ${expression.dump()}" }
                    data.graph.logDigraph(context, newObjectsReferencedFromThrown)
                }

                data.graph.unreachable()
            }

            override fun visitContainerExpression(expression: IrContainerExpression, data: BuilderState): Node {
                val returnableBlockSymbol = (expression as? IrReturnableBlock)?.symbol
                returnableBlockSymbol?.let { returnTargetsResults[it] = mutableListOf() }
                expression.statements.forEachIndexed { index, statement ->
                    val result = statement.accept(this, data)
                    if (index == expression.statements.size - 1 && returnableBlockSymbol == null)
                        return result
                }
                return returnableBlockSymbol?.let {
                    controlFlowMergePoint(data.graph, data.loop, expression, expression.type, returnTargetsResults[it]!!)
                } ?: Node.Unit
            }

            override fun visitWhen(expression: IrWhen, data: BuilderState): Node {
                debug {
                    context.log { "before ${expression.dump()}" }
                    data.graph.logDigraph()
                }
                val branchResults = mutableListOf<ExpressionResult>()
                expression.branches.forEach { branch ->
                    branch.condition.accept(this, data)
                    val branchGraph = data.graph.clone()
                    val branchState = BuilderState(branchGraph, data.loop, data.insideATry)
                    branchResults.add(ExpressionResult(branch.result.accept(this, branchState), branchGraph))
                }
                val isExhaustive = expression.branches.last().isUnconditional()
                require(isExhaustive || expression.type.isUnit())
                if (!isExhaustive) {
                    // Reflecting the case when none of the clauses have been executed.
                    branchResults.add(ExpressionResult(Node.Unit, data.graph.clone()))
                }
                return controlFlowMergePoint(data.graph, data.loop, expression, expression.type, branchResults).also {
                    debug {
                        context.log { "after ${expression.dump()}" }
                        data.graph.logDigraph()
                    }
                }
            }

            override fun visitCatch(aCatch: IrCatch, data: BuilderState): Node {
                require(aCatch.catchParameter.initializer == null) {
                    "Non-null initializer of a catch parameter: ${aCatch.catchParameter.render()}"
                }
                data.graph.getOrAddVariable(aCatch.catchParameter)
                return aCatch.result.accept(this, data)
            }

            override fun visitTry(aTry: IrTry, data: BuilderState): Node {
                require(aTry.finallyExpression == null) { "All finally clauses should've been lowered out" }
                debug {
                    context.log { "before ${aTry.dump()}" }
                    data.graph.logDigraph()
                }

                val tryGraph = data.graph.clone()
                val tryResult = aTry.tryResult.accept(this, BuilderState(tryGraph, data.loop, true))
                val catchesResults = aTry.catches.map {
                    val catchGraph = tryGraph.clone()
                    val catchResult = it.accept(this, BuilderState(catchGraph, data.loop, data.insideATry))
                    ExpressionResult(catchResult, catchGraph)
                }

                /* TODO: Optimize this case.
                   b.a = A()
                   try {
                       if (f) throw ..
                       b.a = A()
                   } catch (..) { b.a = a }
                   Here after the try block b.a gets rewritten but now we conservatively assume all the possible values,
                   because the catch clause result isn't Nothing.
                 */
                return if (catchesResults.all { it.value == Node.Nothing }) {
                    // We can get here only if no exception has been thrown at the try block
                    // (otherwise, it either would've been caught by one of the catch blocks or
                    // would've been thrown to upper scope and, since they all return nothing,
                    // the control flow wouldn't have gotten to this point).
                    aTry.tryResult.accept(this, BuilderState(data.graph, data.loop, false))
                } else {
                    controlFlowMergePoint(data.graph, data.loop, aTry, aTry.type,
                            listOf(ExpressionResult(tryResult, tryGraph)) + catchesResults)
                }.also {
                    debug {
                        context.log { "after ${aTry.dump()}" }
                        data.graph.logDigraph()
                    }
                }
            }

            override fun visitSuspensionPoint(expression: IrSuspensionPoint, data: BuilderState): Node {
                expression.suspensionPointIdParameter.accept(this, data)
                val normalResultGraph = data.graph.clone()
                val normalResult = expression.result.accept(this, BuilderState(normalResultGraph, data.loop, data.insideATry))
                val resumeResultGraph = data.graph.clone()
                val resumeResult = expression.resumeResult.accept(this, BuilderState(resumeResultGraph, data.loop, data.insideATry))
                return controlFlowMergePoint(data.graph, data.loop, expression, expression.type,
                        listOf(ExpressionResult(normalResult, normalResultGraph), ExpressionResult(resumeResult, resumeResultGraph))
                )
            }

            override fun visitSuspendableExpression(expression: IrSuspendableExpression, data: BuilderState) =
                    expression.result.accept(this, data)

            override fun visitContinue(jump: IrContinue, data: BuilderState): Node {
                (loopsContinueResults[jump.loop] ?: error("A continue from an unknown loop: ${jump.loop}"))
                        .add(ExpressionResult(Node.Unit, data.graph.clone()))
                return data.graph.unreachable()
            }

            override fun visitBreak(jump: IrBreak, data: BuilderState): Node {
                (loopsBreakResults[jump.loop] ?: error("A break from an unknown loop: ${jump.loop}"))
                        .add(ExpressionResult(Node.Unit, data.graph.clone()))
                return data.graph.unreachable()
            }

            override fun visitLoop(loop: IrLoop, data: BuilderState): Node {
                debug {
                    context.log { "before ${loop.dump()}" }
                    data.graph.logDigraph()
                }
                val fictitiousLoopStart = fictitiousLoopsStarts.getOrPut(loop) {
                    when (loop) {
                        is IrWhileLoop -> irBuilder.irWhile()
                        is IrDoWhileLoop -> irBuilder.irDoWhile()
                        else -> error("Unsupported loop ${loop.render()}")
                    }
                }
                val fictitiousLoopEnd = fictitiousLoopsEnds.getOrPut(loop) {
                    when (loop) {
                        is IrWhileLoop -> irBuilder.irWhile()
                        is IrDoWhileLoop -> irBuilder.irDoWhile()
                        else -> error("Unsupported loop ${loop.render()}")
                    }
                }
                val continueResults = loopsContinueResults.getOrPut(loop) { mutableListOf() }
                val breakResults = loopsBreakResults.getOrPut(loop) { mutableListOf() }

                val iterationResults = mutableListOf<ExpressionResult>()
                var prevGraph = data.graph
                if (loop is IrWhileLoop) {
                    loop.condition.accept(this, BuilderState(prevGraph, loop, data.insideATry))
                    // A while loop might not execute even a single iteration.
                    iterationResults.add(ExpressionResult(Node.Unit, prevGraph.clone()))
                }
                val returnTargetsResultsSizes = returnTargetsResults.entries.associate { it.key to it.value.size }
                val loopsContinueResultsSizes = loopsContinueResults.entries.associate { it.key to it.value.size }
                val loopsBreakResultsSizes = loopsBreakResults.entries.associate { it.key to it.value.size }
                val savedObjectsReferencedFromThrown = objectsReferencedFromThrown.copy()
                var iterations = 0
                do {
                    debug {
                        context.log { "iter#$iterations:" }
                        prevGraph.logDigraph()
                    }
                    ++iterations

                    returnTargetsResultsSizes.forEach { (key, size) -> returnTargetsResults[key]!!.trimSize(size) }
                    loopsContinueResultsSizes.forEach { (key, size) -> loopsContinueResults[key]!!.trimSize(size) }
                    loopsBreakResultsSizes.forEach { (key, size) -> loopsBreakResults[key]!!.trimSize(size) }
                    objectsReferencedFromThrown.clear()
                    objectsReferencedFromThrown.or(savedObjectsReferencedFromThrown)

                    val curGraph = prevGraph.clone()
                    loop.body?.accept(this, BuilderState(curGraph, loop, data.insideATry))
                    continueResults.add(ExpressionResult(Node.Unit, curGraph))
                    val nextGraph = PointsToGraph(prevGraph.forest)
                    controlFlowMergePoint(nextGraph, loop, fictitiousLoopStart, context.irBuiltIns.unitType, continueResults)
                    loop.condition.accept(this, BuilderState(nextGraph, loop, data.insideATry))
                    val graphHasChanged = !PointsToGraphForest.graphsAreEqual(prevGraph, nextGraph)
                    prevGraph = nextGraph
                    if (graphHasChanged) {
                        breakResults.add(ExpressionResult(Node.Unit, prevGraph))
                        val iterationGraph = PointsToGraph(prevGraph.forest)
                        controlFlowMergePoint(iterationGraph, loop, fictitiousLoopEnd, context.irBuiltIns.unitType, breakResults)
                        iterationResults.add(ExpressionResult(Node.Unit, iterationGraph))
                    }
                } while (graphHasChanged && iterations < 10)

                if (iterations >= 10)
                    error("BUGBUGBUG: ${loop.dump()}")
                controlFlowMergePoint(data.graph, data.loop, loop, context.irBuiltIns.unitType, iterationResults)
                debug {
                    context.log { "after ${loop.dump()}" }
                    data.graph.logDigraph()
                }
                return Node.Unit
            }

            fun processCall(
                    state: BuilderState,
                    callSite: IrFunctionAccessExpression,
                    callee: IrFunction,
                    argumentNodeIds: List<Int>,
                    calleeEscapeAnalysisResult: EscapeAnalysisResult,
            ): Node {
                if (state.graph.forest.totalNodes > maxAllowedGraphSize) {
                    context.log { "The graph is bigger than expected - skipping call to ${callee.render()}" }
                    return state.graph.unreachable()
                }

                if (argumentNodeIds.any { it == Node.NOTHING_ID }) {
                    context.log { "Unreachable code - skipping call to ${callee.render()}" }
                    return state.graph.unreachable()
                }

                val arguments = argumentNodeIds.map { state.graph.nodes[it]!! }

                debug {
                    context.log { "before calling ${callee.render()}" }
                    state.graph.logDigraph()
                    context.log { "callee EA result" }
                    calleeEscapeAnalysisResult.graph.logDigraph(calleeEscapeAnalysisResult.returnValue)
                    context.log { "arguments: ${arguments.joinToString { it.toString() }}" }
                }

                val calleeGraph = calleeEscapeAnalysisResult.graph
                require(arguments.size == calleeGraph.parameterNodes.size)

                val referencesCount = IntArray(calleeGraph.nodes.size)
                for (node in calleeGraph.nodes) {
                    if (node !is Node.Reference) continue
                    for (pointee in node.assignedWith) {
                        if (pointee is Node.Object) ++referencesCount[pointee.id]
                    }
                }

                val handledNodes = BitSet(calleeGraph.nodes.size)
                val inMirroredNodes = arrayOfNulls<Node>(calleeGraph.nodes.size) // Incoming edges.
                val outMirroredNodes = arrayOfNulls<Node>(calleeGraph.nodes.size) // Outgoing edges.

                fun reflectNode(node: Node, inMirroredNode: Node, outMirroredNode: Node = inMirroredNode) {
                    require(inMirroredNodes[node.id] == null) {
                        "Node $node has already been reflected to (${inMirroredNodes[node.id]} ${outMirroredNodes[node.id]})" +
                                " but is attempted to be reflected again to ($inMirroredNode $outMirroredNode)"
                    }

                    debug {
                        context.log { "Reflecting $node to ($inMirroredNode, $outMirroredNode)" }
                    }

                    inMirroredNodes[node.id] = inMirroredNode
                    outMirroredNodes[node.id] = outMirroredNode
                }

                fun reflectFieldsOf(fictitiousObject: Node.Object, actualObjects: List<Node.Object>) {
                    class PossiblySplitMirroredNode {
                        var inNode: Node.Variable? = null
                        var outNode: Node.Variable? = null

                        fun reflect(node: Node) {
                            require(inNode != null || outNode != null) { "Cannot reflect $node" }
                            reflectNode(node, inNode ?: outNode!!, outNode ?: inNode!!)
                        }
                    }

                    if (calleeEscapeAnalysisResult.objectsReferencedFromThrown.get(fictitiousObject.id))
                        actualObjects.forEach { objectsReferencedFromThrown.set(it.id) }

                    fictitiousObject.fields.forEach { (field, fieldValue) ->
                        val fieldPointee = fieldValue.assignedWith.atMostOne()
                        if (fieldPointee == null && fieldValue.assignedTo.isEmpty())
                            require(fieldValue == calleeEscapeAnalysisResult.returnValue) {
                                "The node $fieldValue should've been optimized away"
                            }

                        val hasIncomingEdges = fieldValue.assignedTo.isNotEmpty()
                        val canOmitFictitiousObject = fieldPointee is Node.Object
                                && fieldPointee.isFictitious
                                && inMirroredNodes[fieldPointee.id] == null // Skip cycles.

                        val mirroredNode = when {
                            actualObjects.size == 1 && actualObjects[0].loop == state.loop && !state.insideATry -> {
                                // Can reflect directly field value to field value.
                                null
                            }
                            actualObjects.isEmpty() -> { // This is going to be NPE at runtime.
                                reflectNode(fieldValue, Node.Null)
                                null
                            }
                            else -> PossiblySplitMirroredNode().also {
                                if (hasIncomingEdges || fieldPointee == null)
                                    it.inNode = state.graph.at(state.loop, callSite, fieldValue.id).newPhiNode()
                                if (fieldPointee != null && !canOmitFictitiousObject)
                                    it.outNode = state.graph.at(state.loop, callSite, fieldValue.id + calleeGraph.nodes.size).newTempVariable()
                            }.takeIf { it.inNode != null || it.outNode != null }
                        }
                        mirroredNode?.reflect(fieldValue)

                        if (canOmitFictitiousObject) {
                            fieldPointee as Node.Object
                            val nextActualObjects = mutableListOf<Node.Object>()
                            for (obj in actualObjects) {
                                val objFieldValue = with(state.graph) { obj.getField(field) }
                                if (hasIncomingEdges) {
                                    if (mirroredNode == null)
                                        reflectNode(fieldValue, objFieldValue)
                                    else {
                                        require(mirroredNode.outNode == null)
                                        mirroredNode.inNode!!.addEdge(objFieldValue)
                                    }
                                }
                                nextActualObjects.addAll(
                                        state.graph.getObjectNodes(objFieldValue,
                                                fieldPointee.loop ?: state.loop, callSite, fieldValue.id + calleeGraph.nodes.size)
                                )
                            }
                            handledNodes.set(fieldValue.id)

                            // TODO: What if have to do this for more than one callsite (which loop to take?)
                            fieldPointee.loop?.let {
                                nextActualObjects.forEach { obj -> obj.loop = it }
                            }
                            if (referencesCount[fieldPointee.id] > 1) {
                                when (nextActualObjects.size) {
                                    0 -> reflectNode(fieldPointee, Node.Null)
                                    1 -> reflectNode(fieldPointee, nextActualObjects[0])
                                    else -> {
                                        val mirroredFieldPointee = state.graph.at(state.loop, callSite, fieldPointee.id).newPhiNode()
                                        mirroredFieldPointee.assignedWith.addAll(nextActualObjects)
                                        reflectNode(fieldPointee, mirroredFieldPointee)
                                    }
                                }
                            }
                            reflectFieldsOf(fieldPointee, nextActualObjects)
                        } else {
                            for (obj in actualObjects) {
                                val objFieldValue = with(state.graph) { obj.getField(field) }
                                if (fieldPointee != null) { // An actual field rewrite.
                                    // TODO: Probably can do for any outer loop as well (not only for the current).
                                    if (actualObjects.size == 1 && state.loop == obj.loop && !state.insideATry) {
                                        state.graph.eraseValue(objFieldValue, state.loop, callSite, fieldValue.id + 2 * calleeGraph.nodes.size)
                                    } else {
                                        require(mirroredNode?.outNode != null)
                                    }
                                }

                                if (mirroredNode == null)
                                    reflectNode(fieldValue, objFieldValue)
                                else {
                                    mirroredNode.inNode?.addEdge(objFieldValue)
                                    mirroredNode.outNode?.let { objFieldValue.addEdge(it) }
                                }
                            }
                        }
                    }
                    handledNodes.set(fictitiousObject.id)
                }

                reflectNode(Node.Nothing, Node.Nothing)
                reflectNode(Node.Null, Node.Null)
                reflectNode(Node.Unit, Node.Unit)
                reflectNode(calleeEscapeAnalysisResult.graph.globalNode, state.graph.globalNode)
                for (parameter in calleeEscapeAnalysisResult.graph.parameterNodes.values)
                    reflectNode(parameter, arguments[parameter.index])
                for (parameter in calleeEscapeAnalysisResult.graph.parameterNodes.values)
                    reflectFieldsOf(parameter, state.graph.getObjectNodes(arguments[parameter.index], state.loop, callSite, parameter.id))

                for (node in calleeGraph.nodes) {
                    if (handledNodes.get(node!!.id) || inMirroredNodes[node.id] != null) continue
                    when (node) {
                        is Node.Parameter -> error("Parameter $node should've been handled earlier")
                        is Node.Variable -> reflectNode(node, state.graph.at(state.loop, callSite, node.id).newTempVariable(node.label))
                        is Node.FieldValue -> Unit // Will be mirrored along with its owner object.
                        is Node.Object -> {
                            val mirroredObject = state.graph.at(node.loop ?: state.loop, callSite, node.id).newObject(node.label)
                            reflectNode(node, mirroredObject)
                            if (calleeEscapeAnalysisResult.objectsReferencedFromThrown.get(node.id))
                                objectsReferencedFromThrown.set(mirroredObject.id)
                            node.fields.forEach { (field, fieldValue) ->
                                reflectNode(fieldValue, with(state.graph) { mirroredObject.getField(field) })
                            }
                        }
                    }
                }

                for (node in calleeGraph.nodes) {
                    if (handledNodes.get(node!!.id) || inMirroredNodes[node.id] != null) continue
                    require(node is Node.FieldValue) { "The node $node should've been reflected at this point" }
                    reflectNode(node, state.graph.at(state.loop, callSite, node.id).newTempVariable())
                }

                for (node in calleeGraph.nodes) {
                    if (handledNodes.get(node!!.id) || node !is Node.Reference) continue

                    val outMirroredNode = outMirroredNodes[node.id]
                    if (outMirroredNode == Node.Null) continue
                    require(outMirroredNode is Node.Reference)
                    for (pointee in node.assignedWith) {
                        val inMirroredNode = inMirroredNodes[pointee.id]
                        if (inMirroredNode == Node.Null && pointee != Node.Null) continue
                        outMirroredNode.addEdge(inMirroredNode ?: error("Node $pointee hasn't been reflected"))
                    }
                }

                val returnValue = outMirroredNodes[calleeEscapeAnalysisResult.returnValue.id]
                        ?: error("Node ${calleeEscapeAnalysisResult.returnValue} hasn't been reflected")

                debug {
                    context.log { "after calling ${callee.render()}" }
                    state.graph.logDigraph(returnValue)
                }

                return returnValue
            }

            // TODO: Move to KonanFqNames or something.
            private val NAME_ESCAPES = Name.identifier("Escapes")
            private val NAME_POINTS_TO = Name.identifier("PointsTo")
            private val FQ_NAME_KONAN = FqName.fromSegments(listOf("kotlin", "native", "internal"))

            private val FQ_NAME_ESCAPES = FQ_NAME_KONAN.child(NAME_ESCAPES)
            private val FQ_NAME_POINTS_TO = FQ_NAME_KONAN.child(NAME_POINTS_TO)

            fun getExternalFunctionEAResult(function: IrFunction): EscapeAnalysisResult {
                context.log { "An external call: ${function.render()}" }
                // TODO: use package instead.
                val fqName = function.fqNameForIrSerialization.asString()
                return if (function.isStaticInitializer) // TODO: Is this correct?
                    EscapeAnalysisResult.optimistic(function)
                else if (fqName.startsWith("kotlin.")
                        // TODO: Is it possible to do it in a more fine-grained fashion?
                        && !fqName.startsWith("kotlin.native.concurrent")) {
                    context.log { "A function from K/N runtime - can use annotations" }
                    val escapesAnnotation = function.annotations.findAnnotation(FQ_NAME_ESCAPES)
                    val pointsToAnnotation = function.annotations.findAnnotation(FQ_NAME_POINTS_TO)
                    @Suppress("UNCHECKED_CAST")
                    val escapesBitMask = (escapesAnnotation?.getValueArgument(0) as? IrConst<Int>)?.value
                    @Suppress("UNCHECKED_CAST")
                    val pointsToBitMask = (pointsToAnnotation?.getValueArgument(0) as? IrVararg)?.elements?.map { (it as IrConst<Int>).value }
                    EscapeAnalysisResult.fromBits(
                            function,
                            escapesBitMask ?: 0,
                            (0..function.allParameters.size).map { pointsToBitMask?.elementAtOrNull(it) ?: 0 }
                    )
                } else {
                    context.log { "An unknown function - assume pessimistic result" }
                    EscapeAnalysisResult.pessimistic(function)
                }
            }

            fun processConstructorCall(
                    callee: IrConstructor,
                    thisNode: Node,
                    callSite: IrFunctionAccessExpression,
                    state: BuilderState
            ) {
                val arguments = callSite.getArgumentsWithIr()
                val argumentNodeIds = listOf(thisNode.id) + arguments.map { it.second.accept(this, state).id }
                val calleeEscapeAnalysisResult = escapeAnalysisResults[callee.symbol]
                        ?: getExternalFunctionEAResult(callee)
                processCall(state, callSite, callee, argumentNodeIds, calleeEscapeAnalysisResult)
            }

            fun BuilderState.allocObjectNode(expression: IrExpression, irClass: IrClass) =
                    graph.at(loop, expression).newObject("<A:${irClass.name}>")

            override fun visitConstructorCall(expression: IrConstructorCall, data: BuilderState): Node {
                val thisObject = data.allocObjectNode(expression, expression.symbol.owner.constructedClass)
                processConstructorCall(expression.symbol.owner, thisObject, expression, data)
                return if (data.graph.forest.totalNodes > maxAllowedGraphSize)
                    Node.Nothing
                else
                    data.graph.nodes[thisObject.id]!!
            }

            override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: BuilderState): Node {
                val constructor = expression.symbol.owner
                val thisReceiver = (function as IrConstructor).constructedClass.thisReceiver!!
                val irThis = IrGetValueImpl(SYNTHETIC_OFFSET, SYNTHETIC_OFFSET, thisReceiver.type, thisReceiver.symbol)
                processConstructorCall(constructor, irThis.accept(this, data), expression, data)
                return Node.Unit
            }

            val createUninitializedInstanceSymbol = context.ir.symbols.createUninitializedInstance
            val initInstanceSymbol = context.ir.symbols.initInstance
            val reinterpretSymbol = context.ir.symbols.reinterpret

            // TODO: What about staticInitializer, executeImpl, getContinuation?
            override fun visitCall(expression: IrCall, data: BuilderState) = when (expression.symbol) {
                createUninitializedInstanceSymbol -> {
                    data.allocObjectNode(expression, expression.getTypeArgument(0)!!.classOrNull!!.owner)
                }
                initInstanceSymbol -> {
                    val irThis = expression.getValueArgument(0)!!
                    val thisNode = irThis.accept(this, data)
                    val irInitializer = expression.getValueArgument(1) as IrConstructorCall
                    processConstructorCall(irInitializer.symbol.owner, thisNode, irInitializer, data)
                    Node.Unit
                }
                reinterpretSymbol -> {
                    expression.extensionReceiver!!.accept(this, data)
                }
                else -> {
                    val arguments = expression.getArgumentsWithIr()
                    val argumentNodeIds = arguments.map { it.second.accept(this, data).id }
                    val actualCallee = expression.actualCallee
                    if (!expression.isVirtualCall) {
                        val calleeEscapeAnalysisResult = escapeAnalysisResults[actualCallee.symbol]
                                ?: getExternalFunctionEAResult(actualCallee)
                        processCall(data, expression, actualCallee, argumentNodeIds, calleeEscapeAnalysisResult)
                    } else {
                        val devirtualizedCallSite = devirtualizedCallSites[expression]
                        if (devirtualizedCallSite == null) {
                            // Non-devirtualized call.
                            processCall(data, expression, expression.actualCallee, argumentNodeIds,
                                    EscapeAnalysisResult.pessimistic(actualCallee))
                        } else when (devirtualizedCallSite.size) {
                            0 -> { // TODO: This looks like unreachable code, isn't it? Should we return here Nothing then?
                                processCall(data, expression, actualCallee, argumentNodeIds,
                                        EscapeAnalysisResult.optimistic(actualCallee))
                            }
                            1 -> {
                                processCall(data, expression, actualCallee, argumentNodeIds,
                                        escapeAnalysisResults[devirtualizedCallSite[0]] ?: getExternalFunctionEAResult(devirtualizedCallSite[0].owner))
                            }
                            else -> {
                                // Multiple possible callees - model this as a when clause.
                                val fictitiousCallSites = devirtualizedFictitiousCallSites.getOrPut(expression) {
                                    // Don't bother with the arguments - it is only used as a key.
                                    devirtualizedCallSite.map { irBuilder.irCall(it) }
                                }
                                val clonedGraphs = devirtualizedCallSite.indices.map { data.graph.clone() }
                                val callResults = devirtualizedCallSite.mapIndexed { index, calleeSymbol ->
                                    val callSite = fictitiousCallSites[index]
                                    val clonedGraph = clonedGraphs[index]
                                    val callee = calleeSymbol.owner
                                    val resultNode = processCall(
                                            BuilderState(clonedGraph, data.loop, data.insideATry),
                                            callSite, callee,
                                            argumentNodeIds,
                                            escapeAnalysisResults[calleeSymbol] ?: getExternalFunctionEAResult(callee))
                                    ExpressionResult(resultNode, clonedGraph)
                                }
                                controlFlowMergePoint(data.graph, data.loop, expression, expression.type, callResults)
                            }
                        }
                    }
                }
            }
        }

        private fun intraproceduralAnalysis(
                callGraphNode: CallGraphNode,
                escapeAnalysisResults: MutableMap<IrFunctionSymbol, EscapeAnalysisResult>,
                maxAllowedGraphSize: Int,
        ): Boolean {
            val function = callGraphNode.symbol.irFunction!!
            if (function.body == null) return true

            val producerInvocations = mutableMapOf<IrExpression, IrCall>()
            val jobInvocations = mutableMapOf<IrCall, IrCall>()
            val devirtualizedCallSites = mutableMapOf<IrCall, MutableList<IrFunctionSymbol>>()
            for (callSite in callGraphNode.callSites) {
                val call = callSite.call
                val irCall = call.irCallSite as? IrCall ?: continue
                if (irCall.origin == STATEMENT_ORIGIN_PRODUCER_INVOCATION)
                    producerInvocations[irCall.dispatchReceiver!!] = irCall
                else if (irCall.origin == STATEMENT_ORIGIN_JOB_INVOCATION)
                    jobInvocations[irCall.getValueArgument(0) as IrCall] = irCall
                if (call !is DataFlowIR.Node.VirtualCall) continue
                devirtualizedCallSites.getOrPut(irCall) { mutableListOf() }.add(
                        callSite.actualCallee.irFunction?.symbol ?: error("No IR for ${callSite.actualCallee}")
                )
            }

            val forest = PointsToGraphForest()
            val functionResult = PointsToGraphBuilder(
                    function, forest, escapeAnalysisResults, devirtualizedCallSites,
                    maxAllowedGraphSize, needDebug(function)
            ).build()

            if (forest.totalNodes > maxAllowedGraphSize) return false

            /*
             * Now, do the following:
             * 1. Add an object node to each leaf variable node.
             * 2. Remove all variable nodes (but no sooner than bypassing them) except the field nodes.
             * 3. Find all object nodes, reachable from the parameters (including the return parameter).
             * 4. Observation 1: if a field node have no incoming (except the edge from its parent)
             *                   or outgoing edges, it can be omitted.
             *    Observation 2: if an object node has no field nodes and has only one incoming edge,
             *                   it also can be omitted.
             *    So, we can eliminate nodes until none of the above observations is true.
             */

            context.log { "after analyzing body:" }
            functionResult.logDigraph(context)

            // TODO: compute lifetimes.
            context.log { "lifetimes computation results:" }
            functionResult.graph.logDigraph(context) {
                highlightLifetimes = true
                +functionResult.returnValue
            }

            functionResult.graph.variableNodes.clear() // No need in this map anymore.
            functionResult.removeUnreachable()
            context.log { "after removing unreachable:" }
            functionResult.logDigraph(context)

            // TODO: Remove phi nodes with either one incoming or one outgoing edge.
            functionResult.bypassAndRemoveVariables()
            context.log { "after bypassing variables:" }
            functionResult.logDigraph(context)

            // Remove multi-edges.
            functionResult.reenumerateNodes()
            val list = mutableListOf(functionResult.returnValue)
            val graph = PointsToGraphForest(functionResult.graph.nodes.size).mergeGraphs(listOf(functionResult.graph), list)
            val returnValue = list[0]
            val optimizedFunctionResult = EscapeAnalysisResult(graph, returnValue, functionResult.objectsReferencedFromThrown)
            context.log { "after removing multi-edges:" }
            optimizedFunctionResult.logDigraph(context)

            optimizedFunctionResult.removeRedundantNodes()
            optimizedFunctionResult.reenumerateNodes()
            context.log { "after removing redundant nodes:" }
            optimizedFunctionResult.logDigraph(context)

            val escapeAnalysisResult = EscapeAnalysisResult(
                    PointsToGraph(PointsToGraphForest(optimizedFunctionResult.graph.nodes.size))
                            .apply { copyFrom(optimizedFunctionResult.graph) },
                    optimizedFunctionResult.returnValue,
                    optimizedFunctionResult.objectsReferencedFromThrown
            )
            escapeAnalysisResult.simplifyFieldValues()
            context.log { "EA result for ${function.render()}" }
            escapeAnalysisResult.logDigraph(context)

            escapeAnalysisResults[function.symbol] = escapeAnalysisResult
            return true
        }

        private fun EscapeAnalysisResult.reenumerateNodes() {
            var id = 0
            val objectsReferencedFromThrown = mutableListOf<Node.Object>()
            for (index in graph.nodes.indices) {
                val node = graph.nodes[index]
                if (node != null) {
                    if (this.objectsReferencedFromThrown.get(node.id))
                        objectsReferencedFromThrown.add(node as Node.Object)
                    graph.nodes[id] = node
                    node.id = id++
                }
            }
            graph.nodes.trimSize(id)
            this.objectsReferencedFromThrown.clear()
            objectsReferencedFromThrown.forEach { this.objectsReferencedFromThrown.set(it.id) }
        }

        private fun EscapeAnalysisResult.removeUnreachable() {
            val nodeSet = BitSet(graph.forest.totalNodes)
            nodeSet.set(Node.NOTHING_ID)
            nodeSet.set(Node.NULL_ID)
            nodeSet.set(Node.UNIT_ID)

            fun findReachable(node: Node) {
                nodeSet.set(node.id)
                node.forEachPointee { pointee ->
                    if (!nodeSet.get(pointee.id)) findReachable(pointee)
                }
            }

            if (returnValue != Node.Nothing && returnValue != Node.Null && returnValue != Node.Unit)
                findReachable(returnValue)
            findReachable(graph.globalNode)
            graph.parameterNodes.values.forEach {
                if (!nodeSet.get(it.id)) findReachable(it)
            }
            for (id in 0 until graph.nodes.size)
                if (!nodeSet.get(id))
                    graph.nodes[id] = null
        }

        private fun EscapeAnalysisResult.bypassAndRemoveVariables() {
            val forbiddenToBypass = BitSet()
            forbiddenToBypass.set(returnValue.id)
            for (node in graph.nodes) {
                (node as? Node.Object)?.fields?.values?.forEach { forbiddenToBypass.set(it.id) }
            }
            for (id in 0 until graph.nodes.size) {
                val reference = graph.nodes[id] as? Node.Reference ?: continue
                if (forbiddenToBypass.get(reference.id)) continue
                require(reference.assignedTo.isNotEmpty())
                if (reference.assignedWith.size <= 1) {
                    graph.bypass(reference, null, null)
                    graph.nodes[id] = null
//                    context.log { "after bypassing $variable:" }
//                    graph.log(context)
                }
            }
        }

        // TODO: Remove incoming edges to Unit/Null/Nothing.
        private fun EscapeAnalysisResult.removeRedundantNodes() {
            val singleNodesPointingAt = arrayOfNulls<Node>(graph.nodes.size)
            val incomingEdgesCounts = IntArray(graph.nodes.size)

            fun checkIncomingEdge(from: Node, toId: Int) {
                singleNodesPointingAt[toId] = from.takeIf { ++incomingEdgesCounts[toId] == 1 }
            }

            val leaves = mutableListOf<Node>()
            for (node in graph.nodes) {
                require(node != null)
                when (node) {
                    is Node.Object -> node.fields.values.let { fieldValues ->
                        if (fieldValues.isEmpty())
                            leaves.add(node)
                        else
                            fieldValues.forEach { checkIncomingEdge(node, it.id) }
                    }
                    is Node.Reference -> node.assignedWith.let { assignedWith ->
                        if (assignedWith.isEmpty())
                            leaves.add(node)
                        else
                            assignedWith.forEach { checkIncomingEdge(node, it.id) }
                    }
                }
            }

            val forbiddenToRemove = BitSet(graph.nodes.size)
            val removed = BitSet(graph.nodes.size)
            forbiddenToRemove.set(Node.NOTHING_ID)
            forbiddenToRemove.set(Node.NULL_ID)
            forbiddenToRemove.set(Node.UNIT_ID)
            forbiddenToRemove.set(Node.GLOBAL_ID)
            forbiddenToRemove.set(returnValue.id)
            graph.parameterNodes.values.forEach { forbiddenToRemove.set(it.id) }
            while (leaves.isNotEmpty()) {
                val leafNode = leaves.pop()
                val leafNodeId = leafNode.id
                if (forbiddenToRemove.get(leafNodeId)) continue
                val singleNodePointingAtLeaf = singleNodesPointingAt[leafNodeId] ?: continue
                removed.set(leafNodeId)
                val hasBecomeALeaf = when (singleNodePointingAtLeaf) {
                    is Node.Object -> singleNodePointingAtLeaf.fields.let { fields ->
                        fields.remove((leafNode as Node.FieldValue).field)
                        fields.isEmpty()
                    }
                    is Node.Reference -> singleNodePointingAtLeaf.assignedWith.let { assignedWith ->
                        assignedWith.remove(leafNode)
                        assignedWith.isEmpty()
                    }
                }
                if (hasBecomeALeaf)
                    leaves.push(singleNodePointingAtLeaf)
            }

            for (id in 0 until graph.nodes.size)
                if (removed.get(id))
                    graph.nodes[id] = null
        }

        private fun EscapeAnalysisResult.simplifyFieldValues() {
            val fieldsWithMultipleValues = graph.nodes.filter {
                ((it as? Node.FieldValue)?.assignedWith?.size ?: 0) > 1
            }
            if (fieldsWithMultipleValues.isEmpty()) return

            fieldsWithMultipleValues.forEach { fieldValue ->
                val phiNode = graph.newPhiNode()
                (fieldValue as Node.FieldValue).assignedWith.forEach { fieldPointee ->
                    phiNode.assignedWith.add(fieldPointee)
                    if (fieldPointee is Node.Reference)
                        fieldPointee.assignedTo[fieldPointee.assignedTo.indexOf(fieldValue)] = phiNode
                }
                fieldValue.assignedWith.clear()
                fieldValue.assignedWith.add(phiNode)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun computeLifetimes(context: Context, callGraph: CallGraph, moduleDFG: ModuleDFG, lifetimes: MutableMap<IrElement, Lifetime>) {
        try {
            InterproceduralAnalysis(context, callGraph, moduleDFG) { false }.analyze()
        } catch (t: Throwable) {
            val extraUserInfo =
                    """
                        Please try to disable escape analysis and rerun the build. To do it add the following snippet to the gradle script:

                            kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
                                binaries.all {
                                    freeCompilerArgs += "-Xdisable-phases=EscapeAnalysis"
                                }
                            }

                        In case of using command line compiler add this option: "-Xdisable-phases=EscapeAnalysis".
                        Also, consider filing an issue with full Gradle log here: https://kotl.in/issue
                        """.trimIndent()
            context.reportCompilationError("Escape analysis failure:\n$extraUserInfo\n\n${t.message}\n${t.stackTraceToString()}")
        }
    }
}
