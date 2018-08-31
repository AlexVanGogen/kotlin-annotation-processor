package summer.practice.kapt

import com.google.auto.service.AutoService
import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.*

@AutoService(Processor::class)
class ExampleProcessor: KAbstractProcessor() {

    private val dumpNullableAnnotation = DumpNullable::class.java
    private val someAnnoAnnotation = SomeAnno::class.java
    private val dumpIfClassAnnotation = DumpIfClass::class.java
    private val dumpConstructor = DumpConstructor::class.java
    private val dumpFunction = DumpFunction::class.java
    private val ignoreParameter = IgnoreParameter::class.java
    private val printTypeAlias = PrintTypeAlias::class.java

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
    }

    override val supportedAnnotations: Set<Class<out Annotation>> = setOf(
            dumpNullableAnnotation,
            someAnnoAnnotation,
            dumpIfClassAnnotation,
            dumpConstructor,
            dumpFunction,
            ignoreParameter,
            printTypeAlias
    )

    override fun getSupportedAnnotationTypes() = setOf(
            dumpNullableAnnotation.canonicalName,
            someAnnoAnnotation.canonicalName,
            dumpIfClassAnnotation.canonicalName,
            dumpConstructor.canonicalName,
            dumpFunction.canonicalName,
            ignoreParameter.canonicalName,
            printTypeAlias.canonicalName
    )

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    val haha = File("haha.txt")

    fun dumpAllElements(element: Element, indent: Int = 0) {
        haha.appendText("\t".repeat(indent) + "${element.kind} ${element.canonicalName} ${element.annotationMirrors.map { it.toString() }}\n")
        element.enclosedElements.forEach {
            dumpAllElements(it, indent + 1)
        }
        if (element.kind in arrayOf(ElementKind.CONSTRUCTOR, ElementKind.METHOD)) {
            (element as ExecutableElement).typeParameters.forEach {
                dumpAllElements(it, indent + 1)
            }
            element.parameters.forEach {
                dumpAllElements(it, indent + 1)
            }
        }
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        roundEnv.preprocess()

        val constructorsFile = File("constructors.txt")
        constructorsFile.delete()
        roundEnv.getKotlinElementsAnnotatedWith(dumpConstructor)
                .map { it as KotlinConstructor }
                .forEach {
                    val annotation: DumpConstructor? = it.getAnnotation(dumpConstructor)
                    var constructorInfo: String = (it.enclosingElement!! as KotlinClass).name + "; " + (if (annotation == null || annotation.checkPrimary == false) "" else (if (it.isPrimary()) "primary " else "secondary ")) + "constructor; "
                    constructorInfo += "value parameters: "
                    if (it.valueParameters.isEmpty() && it.deliveredProperties.isEmpty())
                        constructorInfo += "none; "
                    for (value in it.deliveredProperties) {
                        constructorInfo += if (value.isModifiable()) "var " else "val "
                        constructorInfo += "${value.name}: "
                        val type = value.returnType
                        constructorInfo += "${type?.name}${if (type?.isNullable() == true) "?" else ""}"
                        constructorInfo += ", "
                    }
                    if (!it.isPrimary()) {
                        constructorInfo += it.valueParameters.map { value -> value.toString() }.joinToString()
                    }
                    constructorInfo += "\n"
                    constructorsFile.appendText(constructorInfo)
                }

        val functionsFile = File("functions.txt")
        functionsFile.delete()
        fun File.addLine(s: String, indent: Int = 0): File {
            if (indent != 0)
                appendText("\t".repeat(indent - 1) + "\\---" + " ")
            appendText(s + "\n")
            return this
        }

        roundEnv.getKotlinElementsAnnotatedWith(dumpFunction)
                .map { it as KotlinFunction }
                .forEach {
                    val modifiers: MutableList<String> = mutableListOf()
                    if (it.isInfix()) modifiers.add("infix")
                    if (it.isTailrec()) modifiers.add("tailrec")
                    if (it.isInline()) modifiers.add("inline")
                    if (it.isOperator()) modifiers.add("operator")
                    functionsFile
                            .addLine("Function ${it.name}")
                            .addLine("Declared explicitly: ${if (it.isExplicitlyDeclared()) "yes" else "no"}")
                            .addLine("Modifiers: ${if (modifiers.isEmpty()) "none" else modifiers.joinToString()}", 1)
                            .addLine("Is extension: ${if (it.receiverParameterType != null) "yes" else "no"}", 1)
                    if (it.receiverParameterType != null) {
                        val receiver = it.receiverParameterType!!
                        functionsFile.addLine("Receiver: ${receiver.kotlinName}", 2)
                    }
                    functionsFile
                            .addLine("Return type: ${it.returnType?.kotlinName}", 1)
                            .addLine("Value parameters: ${if (it.valueParameters.isEmpty()) "none" else ""}", 1)
                    for (value in it.valueParameters) {
                        if (!value.hasAnnotation(ignoreParameter))
                            functionsFile.addLine("${value.name}: ${value.type?.toString()}", 2)
                    }
                    functionsFile
                            .addLine("Type parameters: ${if (it.typeParameters.isEmpty()) "none" else ""}", 1)
                    for (type in it.typeParameters)
                        if (!type.hasAnnotation(ignoreParameter))
                            functionsFile.addLine("${type.name}, upper bound: ${type.upperBound?.kotlinName}", 2)
                }

        val aliasesFile = File("aliases.txt")
        aliasesFile.delete()
        roundEnv.getKotlinElementsAnnotatedWith(printTypeAlias)
                .map { it as KotlinTypeAlias }
                .forEach {
                    aliasesFile.addLine("${it.name} ${it.expandedType?.javaName}")
                }

        return false
    }
}