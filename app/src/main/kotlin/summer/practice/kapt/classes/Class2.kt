package summer.practice.kapt.classes

import summer.practice.kapt.DumpConstructor
import summer.practice.kapt.DumpFunction
import summer.practice.kapt.DumpIfClass

@DumpIfClass
data class Class2 @DumpConstructor(checkPrimary = true) constructor(var x: Int = 1, val y: summer.practice.kapt.classes.Class1?) {
    @DumpConstructor(checkPrimary = true) constructor(z: List<MutableMap<String, Int?>>, vararg xs: Int?) : this(z.size, summer.practice.kapt.classes.Class1()) {}
    @DumpFunction tailrec infix fun Int.haha(x: String?): String? = toString() + x
}