package summer.practice.kapt.classes

import summer.practice.kapt.DumpIfClass

@DumpIfClass
open class Class1 {
    @DumpIfClass
    inner class InnerClass

    @DumpIfClass
    companion object A
}