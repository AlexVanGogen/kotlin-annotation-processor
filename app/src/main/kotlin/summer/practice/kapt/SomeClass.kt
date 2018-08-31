package summer.practice.kapt

import kotlin.math.PI

@DumpNullable
@Suppress("unused")
class SomeClass {
    val s: String? = null
    val a: Int = 2
    fun lala(): Double = PI
    var getInt: Int @SomeAnno get() = 42; set(value) {}
    @SomeAnno
    val someClass: summer.practice.kapt.SomeClass? = summer.practice.kapt.SomeClass()
}