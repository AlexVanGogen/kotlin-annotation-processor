package summer.practice.kapt.classes

import summer.practice.kapt.DumpFunction
import summer.practice.kapt.IgnoreParameter

class Class7<T> {
    fun <U: T, V: U> someFun(u: List<U>, v: MutableMap<out V, Map<T, List<*>>>) = if (u.isNotEmpty()) u.first() else null
    @DumpFunction fun <U: T, V: U> someFun(@IgnoreParameter u: U, v: MutableMap<out V, Map<T, List<*>>>) = u
    @DumpFunction inline fun <reified T, S: T> Map<T, S>.nextFun() {}
}