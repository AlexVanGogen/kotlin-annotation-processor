package summer.practice.kapt

@Target(AnnotationTarget.CONSTRUCTOR)
annotation class DumpConstructor(val checkPrimary: Boolean)