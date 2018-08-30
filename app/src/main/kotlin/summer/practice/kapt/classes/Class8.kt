package summer.practice.kapt.classes

class Class8<A, B: List<Int>, C> {
    fun foo(a: Number) {}
    fun foo(b: B) {}
    fun <S> foo(s: S) {}
}