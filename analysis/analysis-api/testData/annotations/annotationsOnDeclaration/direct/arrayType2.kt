import kotlin.reflect.KClass

annotation class Anno(val value: KClass<*>)

@Anno(Array<Array<String>>::class)
class F<caret>oo