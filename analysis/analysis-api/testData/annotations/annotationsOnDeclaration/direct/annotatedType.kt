import kotlin.reflect.KClass

@Target(AnnotationTarget.TYPE)
annotation class Foo

annotation class Anno(val value: KClass<*>)

@Anno(Array<@Foo String>::class)
class T<caret>est