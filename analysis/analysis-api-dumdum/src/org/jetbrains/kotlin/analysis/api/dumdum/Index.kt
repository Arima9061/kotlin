package org.jetbrains.kotlin.analysis.api.dumdum

interface Index {
    fun <S> value(documentId: DocumentId<*>, valueDescriptor: ValueDescriptor<S>): S?

    fun <K> documents(key: IndexKey<K>): Sequence<DocumentId<*>>

    fun <K> keys(keyDescriptor: KeyDescriptor<K>): Sequence<K>
}

data class IndexUpdate<T>(
    val documentId: DocumentId<*>,
    val valueType: ValueDescriptor<T>,
    val value: T,
    val keys: List<IndexKey<*>>,
)

data class DocumentId<T>(
    val descriptor: DocumentIdDescriptor<T>,
    val value: T,
)

data class DocumentIdDescriptor<T>(
    val type: String,
    val serializer: Serializer<T>,
)

data class IndexKey<K>(
    val keyDescriptor: KeyDescriptor<K>,
    val key: K,
)

data class KeyDescriptor<K>(
    val id: String,
    val serializer: Serializer<K>,
)

data class ValueDescriptor<S>(
    val id: String,
    val serializer: Serializer<S>,
)

interface Serializer<T> {

    fun serialize(t: T): ByteArray

    fun deserialize(bytes: ByteArray): T

    companion object {

        private val DUMMY = object : Serializer<Any> {
            override fun serialize(t: Any): ByteArray {
                throw UnsupportedOperationException()
            }

            override fun deserialize(bytes: ByteArray): Any {
                throw UnsupportedOperationException()
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> dummy(): Serializer<T> = DUMMY as Serializer<T>
    }
}
