package summer.practice.kapt.facades

import summer.practice.kapt.DumpConstructor
import summer.practice.kapt.DumpFunction
import summer.practice.kapt.IgnoreParameter
import summer.practice.kapt.PrintTypeAlias

class X1(val i: Int)

class X2 @DumpConstructor(checkPrimary = true) constructor(val i: Int, val j: Int)

fun x3() {
    // TODO: javax.lang.model API cannot find local classes (?)
    class X3 @DumpConstructor(checkPrimary = true) constructor(val k: Int)
    fun x4() = 2
}

@DumpFunction infix fun <@IgnoreParameter T: Comparable<T>, U> Stroka?.getX(x: U): String? = x.toString() + plus("lala")

val x5: Int get() = 42

@PrintTypeAlias typealias Stroka = String