package summer.practice.kapt

class AnotherClass @summer.practice.kapt.SomeAnno constructor(s: String, t: Int) {
    val y = 2

    @summer.practice.kapt.SomeAnno
    class SomeClass @summer.practice.kapt.SomeAnno constructor(val n: Int) {

        @summer.practice.kapt.SomeAnno constructor(s: String) : this(s.length)
        val x: Int @summer.practice.kapt.SomeAnno get() = 1
    }
}