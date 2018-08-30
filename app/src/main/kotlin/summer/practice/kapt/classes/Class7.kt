package summer.practice.kapt.classes

import summer.practice.kapt.DumpFunction
import summer.practice.kapt.IgnoreParameter

class Class7<T> {
    @DumpFunction fun <U: T, V: U> someFun(u: List<U>, v: MutableMap<out V, Map<T, out List<*>>>) = if (u.isNotEmpty()) u.first() else null
    fun <U: T, V: U> someFun(u: U, @IgnoreParameter v: MutableMap<out V, Map<T, out List<*>>>) = u
    @DumpFunction inline fun <reified T, S: T> Map<T, S>.nextFun() {}
}