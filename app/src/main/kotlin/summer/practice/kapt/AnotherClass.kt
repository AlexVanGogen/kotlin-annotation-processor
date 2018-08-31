package summer.practice.kapt

class AnotherClass @SomeAnno constructor(s: String, t: Int) {
    val y = 2

    @SomeAnno
    class SomeClass @SomeAnno constructor(val n: Int) {

        @SomeAnno constructor(s: String) : this(s.length)
        val x: Int @summer.practice.kapt.SomeAnno get() = 1
    }
}