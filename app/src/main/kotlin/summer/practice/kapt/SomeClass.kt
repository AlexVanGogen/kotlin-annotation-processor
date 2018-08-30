package summer.practice.kapt

import kotlin.math.PI

@summer.practice.kapt.DumpNullable
@Suppress("unused")
class SomeClass {
    val s: String? = null
    val a: Int = 2
    fun lala(): Double = PI
    var getInt: Int @summer.practice.kapt.SomeAnno get() = 42; set(value) {}
    @summer.practice.kapt.SomeAnno
    val someClass: summer.practice.kapt.SomeClass? = summer.practice.kapt.SomeClass()
}