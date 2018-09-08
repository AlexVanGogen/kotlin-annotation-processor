package summer.practice.kapt

import kotlinx.metadata.internal.metadata.jvm.deserialization.JvmMetadataVersion
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.*

abstract class KAbstractProcessor: AbstractProcessor() {

    /**
     * Set of elements that were already handled in previous rounds
     */
    internal val handledKotlinElements: MutableSet<Element> = mutableSetOf()

    /**
     * All Kotlin classes and packages handled during annotation processing
     */
    internal val handledKotlinClasses: MutableSet<KotlinDeclarationContainer> = mutableSetOf()

    /**
     * Structure that contains all elements marked by supported annotations
     */
    internal val annotationMappings: MutableMap<Class<out Annotation>, Set<KotlinElement>> = mutableMapOf()

    /**
     * Checks if class is Kotlin class.
     */
    internal fun Element.isKotlinElement() = getAnnotation(KOTLIN_METADATA_ANNOTATION) != null

    /**
     * Visits all elements and reads metadata from found Kotlin classes.
     */
    internal fun Element.visitClass() {
        if (this !in handledKotlinElements) {
            handledKotlinElements.add(this)
            if (isKotlinElement()) {
                val declarationContainer = KotlinClassMetadata.read(getKotlinClassMetadata() ?: throw InvalidMetadataException()).collectKotlinSpecificInformationFromClass()
                if (declarationContainer is KotlinPackage)
                    declarationContainer.name = canonicalName
                handledKotlinClasses.add(declarationContainer)
            }
            enclosedElements.forEach {
                it.visitClass()
            }
        }
    }

    /**
     * Annotations that annotation processor will support
     */
    abstract val supportedAnnotations: Set<Class<out Annotation>>

    /**
     * Qualified name of element (includes package and outer classes).
     */
    val Element.canonicalName get(): String = if (this is PackageElement) qualifiedName.toString() else "${enclosingElement.canonicalName}.$simpleName"

    fun RoundEnvironment.rootKotlinElements() = handledKotlinClasses

    /**
     * Collects all information about Kotlin classes.
     * It must be the first thing that [process] must do in every round.
     */
    fun RoundEnvironment.preprocess() {
        rootElements.forEach { it.visitClass() }
        handledKotlinClasses.forEach {
            when (it) {
                is KotlinClass -> it.bindNestedTypeParametersWithWrappingType()
                is KotlinPackage -> it.bindNestedTypeParametersWithWrappingType()
            }
        }
        supportedAnnotations.forEach { annotationMappings[it] = getKotlinElementsAnnotatedWith(it) }
    }

    /**
     * Analogue to [RoundEnvironment.getElementsAnnotatedWith] for Kotlin classes.
     */
    fun RoundEnvironment.getKotlinElementsAnnotatedWith(annotationClass: Class<out Annotation>): Set<KotlinElement> =
            if (annotationMappings[annotationClass] == null) {
                rootElements
                        .filter { it.isKotlinElement() }
                        .flatMap { it.getEnclosedElementsAnnotatedWith(annotationClass, handledKotlinClasses.findAppropriateClass(it)) }
                        .toSet()
                        .plus(
                                rootElements
                                        .filter { it.getAnnotation(annotationClass) != null }
                                        .map { handledKotlinClasses.findAppropriateClass(it) }
                                        .toSet()
                        )
                        .toSet()
                        .plus(handledKotlinClasses.flatMap { it.typeAliases.filter { alias ->
                            annotationClass.name in alias.kAnnotations.map { annotation -> annotation.className.replace('/', '.') } } })
            } else annotationMappings[annotationClass] ?: throw UnsupportedAnnotationException("Annotation not found: ${annotationClass.name}")

    /**
     * Visits all subelements of receiver and receives those that marked with [annotationClass].
     * [relativeKotlinElement] is Kotlin element related with receiver element.
     *
     * @return all subelements marked with [annotationClass]
     */
    private fun Element.getEnclosedElementsAnnotatedWith(annotationClass: Class<out Annotation>, relativeKotlinElement: KotlinElement): MutableSet<KotlinElement> {
        val elements: MutableSet<KotlinElement> = mutableSetOf()
        for (enclosedElement in enclosedElements) {
            val appropriateKotlinElement = when (enclosedElement.kind) {
                ElementKind.METHOD -> relativeKotlinElement.getAppropriateFunctionOrProperty(enclosedElement)
                ElementKind.CONSTRUCTOR -> relativeKotlinElement.getAppropriateConstructor(enclosedElement)
                in arrayOf(
                        ElementKind.CLASS,
                        ElementKind.ANNOTATION_TYPE,
                        ElementKind.ENUM,
                        ElementKind.INTERFACE,
                        ElementKind.ENUM_CONSTANT
                ) -> relativeKotlinElement.getAppropriateClass(enclosedElement)
                else -> null
            }
            if (enclosedElement.getAnnotation(annotationClass) != null && appropriateKotlinElement != null) {
                elements.add(appropriateKotlinElement)
                appropriateKotlinElement.addAnnotation(enclosedElement.getAnnotation(annotationClass))
            }
            if (appropriateKotlinElement != null)
                elements.addAll(enclosedElement.getEnclosedElementsAnnotatedWith(annotationClass, appropriateKotlinElement))
        }
        if (kind in arrayOf(ElementKind.METHOD, ElementKind.CONSTRUCTOR)) {
            for (enclosedElement in (this as ExecutableElement).parameters) {
                val appropriateKotlinElement = when (enclosedElement.kind) {
                    ElementKind.PARAMETER -> relativeKotlinElement.getAppropriateValueParameter(enclosedElement)
                    ElementKind.TYPE_PARAMETER -> relativeKotlinElement.getAppropriateTypeParameter(enclosedElement)
                    else -> null
                }
                if (enclosedElement.getAnnotation(annotationClass) != null && appropriateKotlinElement != null) {
                    elements.add(appropriateKotlinElement)
                    appropriateKotlinElement.addAnnotation(enclosedElement.getAnnotation(annotationClass))
                }
                if (appropriateKotlinElement != null)
                    elements.addAll(enclosedElement.getEnclosedElementsAnnotatedWith(annotationClass, appropriateKotlinElement))
            }
            for (enclosedElement in typeParameters) {
                val appropriateKotlinElement = when (enclosedElement.kind) {
                    ElementKind.PARAMETER -> relativeKotlinElement.getAppropriateValueParameter(enclosedElement)
                    ElementKind.TYPE_PARAMETER -> relativeKotlinElement.getAppropriateTypeParameter(enclosedElement)
                    else -> null
                }
                if (enclosedElement.getAnnotation(annotationClass) != null && appropriateKotlinElement != null) {
                    elements.add(appropriateKotlinElement)
                    appropriateKotlinElement.addAnnotation(enclosedElement.getAnnotation(annotationClass))
                }
                if (appropriateKotlinElement != null)
                    elements.addAll(enclosedElement.getEnclosedElementsAnnotatedWith(annotationClass, appropriateKotlinElement))
            }
        }
        return elements
    }

    /**
     * Finds top-level class among classes in [handledKotlinClasses], appropriate to Java class [element].
     * @return appropriate class
     * @throws KotlinClassNotFoundException if class has not found
     */
    private fun MutableSet<out KotlinElement>.findAppropriateClass(element: Element): KotlinDeclarationContainer =
            find {
                it is KotlinClass && it.name == element.canonicalName
                        || it is KotlinPackage && it.name == element.canonicalName
            } as? KotlinDeclarationContainer ?: throw KotlinClassNotFoundException("Class not found: ${element.canonicalName}")

    /**
     * Finds function, or property, or property getter/setter among elements inside [handledKotlinClasses], appropriate to [element].
     * @return appropriate Kotlin element
     * KotlinClassNotFoundException if function/property has not found
     */
    private fun KotlinElement.getAppropriateFunctionOrProperty(element: Element): KotlinElement {
        element.simpleName.run {
            // Deprecated
            if (endsWith("\$annotations"))
                return getAppropriatePropertyByName(toString())
            // Could be property/property getter/function (due to Java interoperability)
            else if (startsWith("get") || startsWith("set")) {
                if (length > 3 && get(3).isUpperCase()) {
                    // All variants possible
                    val upperCaseChar = get(3)
                    val reducedName = drop(4)
                    return getAppropriatePropertyByName(reducedName.padStart(reducedName.length + 1, upperCaseChar.toLowerCase()).toString())
                            ?: getAppropriateFunctionByName(element)
                } else {
                    // It cannot be property
                    return getAppropriateFunctionByName(element)
                }
            } else return getAppropriateFunctionByName(element)
        }
    }

    /**
     * Finds property, or property getter/setter among elements inside [handledKotlinClasses], appropriate to element with name [propertyName].
     * By the way, it must be known that [propertyName] begins with "get" and has next letter in upper case (and therefore it's property getter),
     * or begins with "set" and has next letter in upper case (and therefore it's property setter),
     * or ends with "$kAnnotations" (and therefore it's property).
     * Kotlin compiler makes synthesized method "<name>$kAnnotations" (marked as deprecated in bytecode)
     * when finds property marked with annotation.
     * @return appropriate property, or null if it has not found.
     */
    private fun KotlinElement.getAppropriatePropertyByName(propertyName: String): KotlinProperty {
        return if (this is KotlinDeclarationContainer)
            properties.find { it.name + "\$kAnnotations" == propertyName } ?: getAppropriatePropertyGetterOrSetterByName(propertyName)
        else throw KotlinPropertyNotFoundException("Property not found: $propertyName")
    }

    /**
     * Finds property getter/setter among elements inside [handledKotlinClasses], appropriate to Java method with name [propertyName].
     * @return appropriate property, or null if it has not found.
     */
    private fun KotlinElement.getAppropriatePropertyGetterOrSetterByName(propertyName: String): KotlinProperty =
            (this as? KotlinDeclarationContainer)?.properties?.find { it.name == propertyName } ?: throw KotlinPropertyGetterOrSetterNotFoundException("Getter/setter for property $propertyName not found")


    /**
     * Finds function among elements inside [handledKotlinClasses], appropriate to Java method with name [functionName].
     * @return appropriate function, or null if it has not found.
     */
    private fun KotlinElement.getAppropriateFunctionByName(element: Element): KotlinFunction =
        (this as? KotlinDeclarationContainer)
                ?.functions?.find {
            it.name == element.simpleName.toString() && it.typeRepresentation == element.asType().toString().replace("? extends ", "")
        } ?: throw KotlinFunctionNotFoundException("Function not found: ${element.canonicalName}")

    /**
     * Finds constructor among elements inside [handledKotlinClasses], appropriate to Java constructor [element].
     * @return appropriate constructor, or null if it has not found.
     */
    private fun KotlinElement.getAppropriateConstructor(element: Element): KotlinConstructor =
        (this as? KotlinClass)
                ?.constructors?.find {
            it.typeRepresentation == element.asType().toString().replace("? extends ", "")
        } ?: throw KotlinConstructorNotFoundException("Constructor not found: ${element.canonicalName}")

    /**
     * Finds class among elements inside [handledKotlinClasses], appropriate to Java class/interface/enum [element].
     * Note that next Kotlin class kinds compiled to usual Java class:
     *  - common classes (class A)
     *  - data classes
     *  - inner classes
     *  - objects and companion objects
     * Other Java objects, such as interfaces, enums, and enum entries, are Kotlin classes too.
     * These classes have [KotlinClass.isInterface], [KotlinClass.isEnumClass], and [KotlinClass.isEnumEntry]
     * flags set, respectively.
     *
     * @return appropriate class, or null if it has not found.
     */
    private fun KotlinElement.getAppropriateClass(element: Element): KotlinClass {
        return handledKotlinClasses.findAppropriateClass(element) as KotlinClass
    }

    /**
     * Finds value parameter among elements inside [handledKotlinClasses], appropriate to Java variable [element].
     * @return appropriate constructor, or null if it has not found.
     */
    private fun KotlinElement.getAppropriateValueParameter(element: Element): KotlinValueParameter {
        element as VariableElement
        val parameter = if (this is KotlinConstructor) {
            valueParameters.find { it.name == element.simpleName.toString() }
        } else if (this is KotlinFunction) {
            valueParameters.find { it.name == element.simpleName.toString() }
        } else null

        return parameter ?: throw KotlinValueParameterNotFoundException("Value parameter not found: ${element.canonicalName}")
    }

    /**
     * Finds type parameter among elements inside [handledKotlinClasses], appropriate to Java type parameter [element].
     * @return appropriate constructor, or null if it has not found.
     */
    private fun KotlinElement.getAppropriateTypeParameter(element: Element): KotlinTypeParameter {
        element as TypeParameterElement
        val parameter = if (this is KotlinClass) {
            typeParameters.find { it.name == element.simpleName.toString() }
        } else if (this is KotlinTypeAlias) {
            typeParameters.find { it.name == element.simpleName.toString() }
        } else if (this is KotlinProperty) {
            typeParameters.find { it.name == element.simpleName.toString() }
        } else if (this is KotlinFunction) {
            typeParameters.find { it.name == element.simpleName.toString() }
        } else null

        return parameter ?: throw KotlinTypeParameterNotFoundException("Type parameter not found: ${element.canonicalName}")
    }

    companion object {
        val KOTLIN_METADATA_CLASS = Class.forName("kotlin.Metadata")
        val KOTLIN_METADATA_ANNOTATION = KOTLIN_METADATA_CLASS.asSubclass(Annotation::class.java)
    }
}

/**
 * Reads Metadata annotation values computed for instance of receiver.
 * It's copy of an idea that used here: https://github.com/Takhion/kotlin-metadata
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
internal fun Element.getKotlinClassMetadata(): KotlinClassHeader? {
    var headerKind: Int? = null
    var metadataVersion: IntArray? = null
    var bytecodeVersion: IntArray? = null
    var incompatibleData: Array<String>? = null
    var data1: Array<String>? = null
    var data2: Array<String>? = null
    var extraString: String? = null
    var packageName: String? = null
    var extraInt: Int? = null

    for (annotation in annotationMirrors) {
        if ((annotation.annotationType.asElement() as TypeElement).qualifiedName.toString() != "kotlin.Metadata")
            continue

        for ((element, value) in annotation.elementValues) {

            val name = element.simpleName.toString().takeIf { it.isNotEmpty() } ?: continue
            val _value: Any? = unwrapAnnotationValue(value)

            when {
                name == "k" && _value is Int ->
                    headerKind = _value
                name == "mv" ->
                    metadataVersion = @Suppress("UNCHECKED_CAST") (_value as List<Int>).toIntArray()
                name == "bv" ->
                    bytecodeVersion = @Suppress("UNCHECKED_CAST") (_value as List<Int>).toIntArray()
                name == "d1" ->
                    data1 = @Suppress("UNCHECKED_CAST") (_value as List<String>).toTypedArray()
                name == "d2" ->
                    data2 = @Suppress("UNCHECKED_CAST") (_value as List<String>).toTypedArray()
                name == "xs" && _value is String ->
                    extraString = _value
                name == "pn" && _value is String ->
                    packageName = _value
                name == "xi" && _value is Int ->
                    extraInt = _value
            }
        }
    }

    if (headerKind == null)
        return null

    if (metadataVersion == null || !JvmMetadataVersion(metadataVersion[0], metadataVersion[1], metadataVersion[2]).isCompatible()) {
        incompatibleData = data1
        data1 = null
    } else if (headerKind in listOf(1, 2, 5) && data1 == null) {
        return null
    }

    return KotlinClassHeader(
            headerKind,
            metadataVersion,
            bytecodeVersion,
            data1 ?: incompatibleData,
            data2,
            extraString,
            packageName,
            extraInt
    )
}

private tailrec fun unwrapAnnotationValue(value: Any?): Any? =
        when (value) {
            is AnnotationValue -> unwrapAnnotationValue(value.value)
            is List<*> -> value.map(::unwrapAnnotationValue)
            else -> value
        }