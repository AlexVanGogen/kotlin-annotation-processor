package summer.practice.kapt

import kotlinx.metadata.*
import kotlinx.metadata.jvm.KotlinClassMetadata
import java.io.File

internal typealias AnnotationClass = Class<out Annotation>

internal val file = File("phphp.txt")
internal fun File.addLine(s: String) {
    appendText(s + "\n")
}

internal val kotlinPrimitives = arrayOf(
        "Int",
        "Short",
        "Long",
        "Byte",
        "Char",
        "Boolean",
        "Float",
        "Double"
)

internal val kotlinImmutableCollections = arrayOf(
        "List",
        "Set",
        "Map",
        "Collection",
        "Iterable",
        "Iterator",
        "ListIterator"
)

internal val kotlinPrimitivesToJavaObjectsMap = mapOf<String, String>(
        Pair("kotlin/Int", "java.lang.Integer"),
        Pair("kotlin/Short", "java.lang.Short"),
        Pair("kotlin/Long", "java.lang.Long"),
        Pair("kotlin/Byte", "java.lang.Byte"),
        Pair("kotlin/Char", "java.lang.Character"),
        Pair("kotlin/Boolean", "java.lang.Boolean"),
        Pair("kotlin/Float", "java.lang.Float"),
        Pair("kotlin/Double", "java.lang.Double")
)

enum class KotlinTypeKind {
    CLASS_TYPE,
    TYPE_ALIAS,
    TYPE_PARAMETER
}

enum class ElementVisibility {
    INTERNAL,
    PRIVATE,
    PRIVATE_TO_INSTANCE,
    PROTECTED,
    PUBLIC
}

sealed class KotlinElement {
    var flags: Flags? = null
    var enclosingElement: KotlinElement? = null
    internal var annotations: MutableSet<Annotation> = mutableSetOf()

    val visibilityModifier: ElementVisibility
        get() {
            return when {
                Flag.IS_INTERNAL(flags ?: 0) -> ElementVisibility.INTERNAL
                Flag.IS_PRIVATE(flags ?: 0) -> ElementVisibility.PRIVATE
                Flag.IS_PRIVATE_TO_THIS(flags ?: 0) -> ElementVisibility.PRIVATE_TO_INSTANCE
                Flag.IS_PROTECTED(flags ?: 0) -> ElementVisibility.PROTECTED
                Flag.IS_PUBLIC(flags ?: 0) -> ElementVisibility.PUBLIC
                else -> ElementVisibility.PRIVATE
            }
        }

    private val Annotation.simpleName: String get() = toString().take(toString().indexOfFirst { it == '(' }).drop(1)

    fun addAnnotation(annotation: Annotation) = annotations.add(annotation)
    fun <T: Annotation> getAnnotation(annotationClass: AnnotationClass): T? = annotations.find {
        it.simpleName == annotationClass.name
    } as? T

    fun hasAnnotation(annotationClass: AnnotationClass): Boolean = annotations.any {
        it.simpleName == annotationClass.name
    }

    fun hasAnnotations(): Boolean = Flag.HAS_ANNOTATIONS(flags ?: 0)
    fun isFinal(): Boolean = Flag.IS_FINAL(flags ?: 0)
    fun isOpen(): Boolean = Flag.IS_OPEN(flags ?: 0)
    fun isAbstract(): Boolean = Flag.IS_ABSTRACT(flags ?: 0)
}

open class KotlinDeclarationContainer: KotlinElement() {
    var functions: MutableList<KotlinFunction> = mutableListOf()
    var properties: MutableList<KotlinProperty> = mutableListOf()
    var typeAliases: MutableList<KotlinTypeAlias> = mutableListOf()
}

class KotlinClass: KotlinDeclarationContainer() {
    var name: ClassName? = null
    var supertype: KotlinType? = null
    var typeParameters: MutableList<KotlinTypeParameter> = mutableListOf()
    var companionObjectsNames: MutableList<String> = mutableListOf()
    var nestedClassesNames: MutableList<String> = mutableListOf()
    var enumEntriesNames: MutableList<String> = mutableListOf()
    var sealedClassesNames: MutableList<ClassName> = mutableListOf()
    var constructors: MutableList<KotlinConstructor> = mutableListOf()

    fun isCommonClass(): Boolean = Flag.Class.IS_CLASS(flags ?: 0)
    fun isInterface(): Boolean = Flag.Class.IS_INTERFACE(flags ?: 0)
    fun isEnumClass(): Boolean = Flag.Class.IS_ENUM_CLASS(flags ?: 0)
    fun isEnumEntry(): Boolean = Flag.Class.IS_ENUM_ENTRY(flags ?: 0)
    fun isAnnotationClass(): Boolean = Flag.Class.IS_ANNOTATION_CLASS(flags ?: 0)
    fun isObject(): Boolean = Flag.Class.IS_OBJECT(flags ?: 0)
    fun isCompanionObject(): Boolean = Flag.Class.IS_COMPANION_OBJECT(flags ?: 0)

    fun isInnerClass(): Boolean = Flag.Class.IS_INNER(flags ?: 0)
    fun isDataClass(): Boolean = Flag.Class.IS_DATA(flags ?: 0)
    fun isExternal(): Boolean = Flag.Class.IS_EXTERNAL(flags ?: 0)
    fun isExpect(): Boolean = Flag.Class.IS_EXTERNAL(flags ?: 0)
    fun isInline(): Boolean = Flag.Class.IS_INLINE(flags ?: 0)
}

class KotlinPackage: KotlinDeclarationContainer() {
    internal var name: String? = null
}

class KotlinLambda: KotlinElement() {
    var wrappedFunction: KotlinFunction? = null
}

class KotlinConstructor: KotlinElement() {
    var valueParameters: MutableList<KotlinValueParameter> = mutableListOf()

    val typeRepresentation: String get() {
        val valueParameters = valueParameters.map { it.type?.javaName }.joinToString(",")
        val returnValue = "void"
        return "($valueParameters)$returnValue".replace('/', '.').replace('*', '?')
    }

    val deliveredProperties: MutableSet<KotlinProperty> get() =
        if (isPrimary())
            (enclosingElement as? KotlinClass)?.properties?.filter {
                it.name in valueParameters.map { value -> value.name }
            }?.toMutableSet() ?: mutableSetOf()
        else mutableSetOf()

    fun isPrimary(): Boolean = Flag.Constructor.IS_PRIMARY(flags ?: 0)
}

class KotlinFunction: KotlinElement() {
    var name: String? = null
    var typeParameters: MutableList<KotlinTypeParameter> = mutableListOf()
    var receiverParameterType: KotlinType? = null
    var valueParameters: MutableList<KotlinValueParameter> = mutableListOf()
    var returnType: KotlinType? = null

    val typeRepresentation: String get() {
        val typeParameters = if (typeParameters.isNotEmpty()) "<${typeParameters.map { it.name }.joinToString(",")}>" else ""
        val receiverParameterAsValue = if (receiverParameterType != null) "${receiverParameterType?.javaName}${if (valueParameters.isNotEmpty()) "," else ""}" else ""
        val valueParameters = valueParameters.map { it.type?.javaName }.joinToString(",")
        val returnValue = returnType?.javaName ?: "void"
        return "$typeParameters($receiverParameterAsValue$valueParameters)$returnValue".replace('/', '.').replace('*', '?')
    }

    fun isExplicitlyDeclared(): Boolean = Flag.Function.IS_DECLARATION(flags ?: 0)
    fun isDelegation(): Boolean = Flag.Function.IS_DELEGATION(flags ?: 0)
    fun isSynthesized(): Boolean = Flag.Function.IS_SYNTHESIZED(flags ?: 0)

    fun isOperator(): Boolean = Flag.Function.IS_OPERATOR(flags ?: 0)
    fun isInfix(): Boolean = Flag.Function.IS_INFIX(flags ?: 0)
    fun isInline(): Boolean = Flag.Function.IS_INLINE(flags ?: 0)
    fun isTailrec(): Boolean = Flag.Function.IS_TAILREC(flags ?: 0)
    fun isExternal(): Boolean = Flag.Function.IS_EXTERNAL(flags ?: 0)
    fun isDelegated(): Boolean = Flag.Function.IS_DELEGATION(flags ?: 0)
    fun isExpect(): Boolean = Flag.Function.IS_EXPECT(flags ?: 0)
    fun isExtension(): Boolean = receiverParameterType != null
}

class KotlinProperty: KotlinElement() {
    var name: String? = null
    var getterFlags: Flags? = null
    var setterFlags: Flags? = null
    var typeParameters: MutableList<KotlinTypeParameter> = mutableListOf()
    var receiverParameterType: KotlinType? = null
    var settersParameters: MutableList<KotlinValueParameter> = mutableListOf()
    var returnType: KotlinType? = null

    fun isExplicitlyDeclared(): Boolean = Flag.Property.IS_DECLARATION(flags ?: 0)
    fun isDelegation(): Boolean = Flag.Property.IS_DELEGATION(flags ?: 0)
    fun isSynthesized(): Boolean = Flag.Property.IS_SYNTHESIZED(flags ?: 0)

    fun isModifiable(): Boolean = Flag.Property.IS_VAR(flags ?: 0)
    fun hasGetter(): Boolean = Flag.Property.HAS_GETTER(flags ?: 0)
    fun hasSetter(): Boolean = Flag.Property.HAS_SETTER(flags ?: 0)
    fun isConstant(): Boolean = Flag.Property.IS_CONST(flags ?: 0)
    fun isLateinit(): Boolean = Flag.Property.IS_LATEINIT(flags ?: 0)
    fun hasConstant(): Boolean = Flag.Property.HAS_CONSTANT(flags ?: 0)
    fun isExternal(): Boolean = Flag.Property.IS_EXTERNAL(flags ?: 0)
    fun isDelegated(): Boolean = Flag.Property.IS_DELEGATED(flags ?: 0)
    fun isExpect(): Boolean = Flag.Property.IS_EXPECT(flags ?: 0)

    fun isGetterNotDefault(): Boolean = Flag.PropertyAccessor.IS_NOT_DEFAULT(getterFlags ?: 0)
    fun isSetterNotDefault(): Boolean = Flag.PropertyAccessor.IS_NOT_DEFAULT(setterFlags ?: 0)

    fun isGetterExternal(): Boolean = Flag.PropertyAccessor.IS_EXTERNAL(getterFlags ?: 0)
    fun isSetterExternal(): Boolean = Flag.PropertyAccessor.IS_EXTERNAL(setterFlags ?: 0)

    fun isGetterInline(): Boolean = Flag.PropertyAccessor.IS_INLINE(getterFlags ?: 0)
    fun isSetterInline(): Boolean = Flag.PropertyAccessor.IS_INLINE(setterFlags ?: 0)
}

/*
    TODO: make explicit classes for getter and setter to determine whether annotation was added directly to getter/setter.
    Example of usage: mark get() as @AddExplicitGetterMethod to add new extension function
 */
class KotlinPropertyGetter(val property: KotlinProperty)
class KotlinPropertySetter(val property: KotlinProperty)

class KotlinTypeAlias: KotlinElement() {
    var name: String? = null
    var typeParameters: MutableList<KotlinTypeParameter> = mutableListOf()
    var underlyingType: KotlinType? = null
    var expandedType: KotlinType? = null
    var kAnnotations: MutableList<KmAnnotation> = mutableListOf()
}

class KotlinValueParameter: KotlinElement() {
    var name: String? = null
    var type: KotlinType? = null
    var varargType: KotlinType? = null

    fun declaresDefaultValue(): Boolean = Flag.ValueParameter.DECLARES_DEFAULT_VALUE(flags ?: 0)
    fun isCrossInline(): Boolean = Flag.ValueParameter.IS_CROSSINLINE(flags ?: 0)
    fun isNoInline(): Boolean = Flag.ValueParameter.IS_NOINLINE(flags ?: 0)
    fun isVararg(): Boolean = varargType != null

    override fun toString(): String {
        var result: String = ""
        if (isCrossInline())
            result += "crossinline "
        else if (isNoInline())
            result += "noinline "
        if (varargType != null) {
            result += "vararg $name: ${varargType.toString()}"
        } else {
            result += "$name: ${type.toString()}"
        }
        return result
    }
}

class KotlinType: KotlinElement() {
    var name: String? = null
    var abbreviatedType: KotlinType? = null
    var flexibleTypeUpperBound: KotlinType? = null
    var kind: KotlinTypeKind? = null
    var typeFlexibilityId: String? = null
    val arguments: MutableList<KotlinType> = mutableListOf()
    var outerClassType: KotlinType? = null

    var typeParameterId: Int? = null
    var variance: KmVariance? = null

    val javaName: String? get() {
        return when (name) {
            "kotlin/String" -> "java.lang.String"
            "kotlin/Any" -> "java.lang.Object"
            "kotlin/Unit" -> "void"
            "kotlin/Array" -> arguments.map { "${it.javaName}[]" }.joinToString()
            in kotlinPrimitives.map { "kotlin/${it}Array" } ->
                "${name?.drop(7)?.dropLast(5)?.decapitalize()}[]"
            in kotlinPrimitives.map { "kotlin/$it" } ->
                if (!isNullable()) name?.drop(7)?.decapitalize() else kotlinPrimitivesToJavaObjectsMap.get(name)
            in kotlinImmutableCollections.map { "kotlin/collections/$it" } ->
                "java.util.${name?.drop(19)}<${arguments.map { it.javaName }.joinToString(",")}>"
            in kotlinImmutableCollections.map { "kotlin/collections/Mutable$it" } ->
                "java.util.${name?.drop(26)}<${arguments.map { it.javaName }.joinToString(",")}>"
            else -> name
        }
    }

    override fun toString(): String {
        var result: String = ""
        if (isSuspend())
            result += "suspend "
        if (variance == KmVariance.OUT)
            result += "out "
        else if (variance == KmVariance.IN)
            result += "in "
        result += name
        if (arguments.isNotEmpty()) {
            result += "<${arguments.joinToString { it.toString() }}>"
        }
        if (isNullable()) result += "?"
        return result
    }

    companion object {
        val STAR_PROJECTION = KotlinType()
        init {
            STAR_PROJECTION.name = "*"
        }
    }

    val wrappedTypeParameter: KotlinTypeParameter?
        get() {
            val nestedElementsStack = mutableListOf<KotlinElement>()
            var nextElement = enclosingElement
            while (nextElement != null) {
                when (nextElement) {
                    is KotlinClass -> nestedElementsStack.add(nextElement)
                    is KotlinTypeAlias -> nestedElementsStack.add(nextElement)
                    is KotlinFunction -> nestedElementsStack.add(nextElement)
                    is KotlinProperty -> nestedElementsStack.add(nextElement)
                }
                nextElement = nextElement.enclosingElement
            }
            var id = typeParameterId!!
            for (lowerElement in nestedElementsStack.asReversed()) {
//                File("haha.txt").appendText("${(lowerElement as? KotlinFunction)?.typeParameters?.size ?: (lowerElement as? KotlinClass)?.typeParameters?.size} $typeParameterId\n")
                when (lowerElement) {
                    is KotlinClass -> if (lowerElement.typeParameters.size <= id) id -= lowerElement.typeParameters.size else return lowerElement.typeParameters[id]
                    is KotlinTypeAlias -> if (lowerElement.typeParameters.size <= id) id -= lowerElement.typeParameters.size else return lowerElement.typeParameters[id]
                    is KotlinFunction -> if (lowerElement.typeParameters.size <= id) id -= lowerElement.typeParameters.size else return lowerElement.typeParameters[id]
                    is KotlinProperty -> if (lowerElement.typeParameters.size <= id) id -= lowerElement.typeParameters.size else return lowerElement.typeParameters[id]
                }
            }
            return null
        }

    val kotlinName: String? get() = "$name${if (arguments.isNotEmpty()) "<${arguments.joinToString { it.toString() }}>" else ""}${if (isNullable()) "?" else ""}"

    fun isNullable(): Boolean = Flag.Type.IS_NULLABLE(flags ?: 0)
    fun isSuspend(): Boolean = Flag.Type.IS_SUSPEND(flags ?: 0)
}

data class KotlinTypeParameter(
        var name: String? = null,
        var id: Int? = null,
        var variance: KmVariance? = null,
        var upperBound: KotlinType? = null
): KotlinElement() {

    fun isReified(): Boolean = Flag.TypeParameter.IS_REIFIED(flags ?: 0)

    val javaName: String?
        get() = if (name == "*") "?" else name

    override fun toString(): String {
        var result: String = ""
        if (variance == KmVariance.IN)
            result += "in "
        else if (variance == KmVariance.OUT)
            result += "out "
        result += name
        if (upperBound != null)
            result += ": ${upperBound?.toString()}"
        return result
    }
}

fun KotlinClassMetadata.collectKotlinSpecificInformationFromClass(): KotlinDeclarationContainer {

    return when (this) {
        is KotlinClassMetadata.Class -> {
            val kotlinClassVisitor = KotlinClassVisitor()
            accept(kotlinClassVisitor)
            kotlinClassVisitor.kotlinClass
        }
        is KotlinClassMetadata.FileFacade -> {
            val kotlinPackageVisitor = KotlinPackageVisitor()
            accept(kotlinPackageVisitor)
            kotlinPackageVisitor.kotlinPackage
        }
        else -> throw ClassCastException()
    }
}

class KotlinTypeVisitor: KmTypeVisitor() {

    val kotlinType = KotlinType()

    override fun visitAbbreviatedType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinType
        kotlinType.abbreviatedType = kType.kotlinType
        return kType
    }

    override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.variance = variance
        kType.kotlinType.enclosingElement = kotlinType
        kotlinType.arguments.add(kType.kotlinType)
        return kType
    }

    override fun visitClass(name: ClassName) {
        kotlinType.kind = KotlinTypeKind.CLASS_TYPE
        kotlinType.name = name
    }

    override fun visitFlexibleTypeUpperBound(flags: Flags, typeFlexibilityId: String?): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.run {
            this.flags = flags
            this.typeFlexibilityId = typeFlexibilityId
            this.enclosingElement = kotlinType
        }
        kotlinType.flexibleTypeUpperBound = kType.kotlinType
        return kType
    }

    override fun visitOuterType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinType
        kotlinType.outerClassType = kType.kotlinType
        return kType
    }

    override fun visitTypeAlias(name: ClassName) {
        kotlinType.kind = KotlinTypeKind.CLASS_TYPE
        kotlinType.name = name
    }

    override fun visitTypeParameter(id: Int) {
        kotlinType.kind = KotlinTypeKind.TYPE_PARAMETER
        kotlinType.typeParameterId = id
        kotlinType.name = kotlinType.wrappedTypeParameter?.name
    }

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitExtensions(type: KmExtensionType): KmTypeExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitStarProjection() {
        kotlinType.arguments.add(KotlinType.STAR_PROJECTION)
    }
}

class KotlinTypeParameterVisitor: KmTypeParameterVisitor() {

    val kotlinTypeParameter = KotlinTypeParameter()

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitExtensions(type: KmExtensionType): KmTypeParameterExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitUpperBound(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinTypeParameter
        kotlinTypeParameter.upperBound = kType.kotlinType
        return kType
    }
}

class KotlinValueParameterVisitor: KmValueParameterVisitor() {

    val kotlinValueParameter = KotlinValueParameter()

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinValueParameter
        kotlinValueParameter.type = kType.kotlinType
        return kType
    }

    override fun visitVarargElementType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinValueParameter
        kotlinValueParameter.varargType = kType.kotlinType
        return kType
    }
}

class KotlinTypeAliasVisitor: KmTypeAliasVisitor() {

    val kotlinTypeAlias = KotlinTypeAlias()

    override fun visitAnnotation(annotation: KmAnnotation) {
        kotlinTypeAlias.kAnnotations.add(annotation)
    }

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitExpandedType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinTypeAlias
        kotlinTypeAlias.expandedType = kType.kotlinType
        return kType
    }

    override fun visitTypeParameter(flags: Flags, name: String, id: Int, variance: KmVariance): KmTypeParameterVisitor? {
        val kpType = KotlinTypeParameterVisitor()
        kpType.kotlinTypeParameter.run {
            this.flags = flags
            this.name = name
            this.id = id
            this.variance = variance
            this.enclosingElement = kotlinTypeAlias
        }
        kotlinTypeAlias.typeParameters.add(kpType.kotlinTypeParameter)
        return kpType
    }

    override fun visitUnderlyingType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinTypeAlias
        kotlinTypeAlias.underlyingType = kType.kotlinType
        return kType
    }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}

class KotlinPropertyVisitor: KmPropertyVisitor() {

    val kotlinProperty = KotlinProperty()

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinProperty
        kotlinProperty.receiverParameterType = kType.kotlinType
        return kType
    }

    override fun visitReturnType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinProperty
        kotlinProperty.returnType = kType.kotlinType
        return kType
    }

    override fun visitSetterParameter(flags: Flags, name: String): KmValueParameterVisitor? {
        val vpType = KotlinValueParameterVisitor()
        vpType.kotlinValueParameter.run {
            this.name = name
            this.flags = flags
            this.enclosingElement = kotlinProperty
        }
        kotlinProperty.settersParameters.add(vpType.kotlinValueParameter)
        return vpType
    }

    override fun visitTypeParameter(flags: Flags, name: String, id: Int, variance: KmVariance): KmTypeParameterVisitor? {
        val kpType = KotlinTypeParameterVisitor()
        kpType.kotlinTypeParameter.run {
            this.flags = flags
            this.name = name
            this.id = id
            this.variance = variance
            this.enclosingElement = kotlinProperty
        }
        kotlinProperty.typeParameters.add(kpType.kotlinTypeParameter)
        return kpType
    }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}

class KotlinFunctionVisitor: KmFunctionVisitor() {

    val kotlinFunction = KotlinFunction()

    override fun visitContract(): KmContractVisitor? {
        return super.visitContract()
    }

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinFunction
        kotlinFunction.receiverParameterType = kType.kotlinType
        return kType
    }

    override fun visitReturnType(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinFunction
        kotlinFunction.returnType = kType.kotlinType
        return kType
    }

    override fun visitTypeParameter(flags: Flags, name: String, id: Int, variance: KmVariance): KmTypeParameterVisitor? {
        val kpType = KotlinTypeParameterVisitor()
        kpType.kotlinTypeParameter.run {
            this.flags = flags
            this.name = name
            this.id = id
            this.variance = variance
            this.enclosingElement = kotlinFunction
        }
        kotlinFunction.typeParameters.add(kpType.kotlinTypeParameter)
        return kpType
    }

    override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor? {
        val vpType = KotlinValueParameterVisitor()
        vpType.kotlinValueParameter.run {
            this.name = name
            this.flags = flags
            this.enclosingElement = kotlinFunction
        }
        kotlinFunction.valueParameters.add(vpType.kotlinValueParameter)
        return vpType
    }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}

class KotlinConstructorVisitor: KmConstructorVisitor() {

    val kotlinConstructor = KotlinConstructor()

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitExtensions(type: KmExtensionType): KmConstructorExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor? {
        val vpType = KotlinValueParameterVisitor()
        vpType.kotlinValueParameter.run {
            this.name = name
            this.flags = flags
            this.enclosingElement = kotlinConstructor
        }
        kotlinConstructor.valueParameters.add(vpType.kotlinValueParameter)
        return vpType
    }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}

class KotlinLambdaVisitor: KmLambdaVisitor() {

    val kotlinLambda = KotlinLambda()

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? {
        val kFunction = KotlinFunctionVisitor()
        kFunction.kotlinFunction.run {
            this.flags = flags
            this.name = name
            kotlinLambda.wrappedFunction = this
        }
        return kFunction
    }
}

class KotlinClassVisitor: KmClassVisitor() {

    val kotlinClass = KotlinClass()

    override fun visit(flags: Flags, name: ClassName) {
        kotlinClass.flags = flags
        kotlinClass.name = name.replace('/', '.')
    }

    override fun visitCompanionObject(name: String) {
        kotlinClass.companionObjectsNames.add(name)
    }

    override fun visitConstructor(flags: Flags): KmConstructorVisitor? {
        val kConstructor = KotlinConstructorVisitor()
        kConstructor.kotlinConstructor.run {
            this.flags = flags
            this.enclosingElement = kotlinClass
            kotlinClass.constructors.add(this)
        }
        return kConstructor
    }

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitEnumEntry(name: String) {
        kotlinClass.enumEntriesNames.add(name)
    }

    override fun visitExtensions(type: KmExtensionType): KmClassExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? {
        val kFunction = KotlinFunctionVisitor()
        kFunction.kotlinFunction.run {
            this.flags = flags
            this.name = name
            this.enclosingElement = kotlinClass
            kotlinClass.functions.add(this)
        }
        return kFunction
    }

    override fun visitNestedClass(name: String) {
        kotlinClass.nestedClassesNames.add(name)
    }

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
        val kProperty = KotlinPropertyVisitor()
        kProperty.kotlinProperty.run {
            this.flags = flags
            this.name = name
            this.getterFlags = getterFlags
            this.setterFlags = setterFlags
            this.enclosingElement = kotlinClass
            kotlinClass.properties.add(this)
        }
        return kProperty
    }

    override fun visitSealedSubclass(name: ClassName) {
        kotlinClass.sealedClassesNames.add(name)
    }

    override fun visitSupertype(flags: Flags): KmTypeVisitor? {
        val kType = KotlinTypeVisitor()
        kType.kotlinType.flags = flags
        kType.kotlinType.enclosingElement = kotlinClass
        kotlinClass.supertype = kType.kotlinType
        return kType
    }

    override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor? {
        val kTypeAlias = KotlinTypeAliasVisitor()
        kTypeAlias.kotlinTypeAlias.run {
            this.flags = flags
            this.name = name
            this.enclosingElement = kotlinClass
            kotlinClass.typeAliases.add(this)
        }
        return kTypeAlias
    }

    override fun visitTypeParameter(flags: Flags, name: String, id: Int, variance: KmVariance): KmTypeParameterVisitor? {
        val kpType = KotlinTypeParameterVisitor()
        kpType.kotlinTypeParameter.run {
            this.flags = flags
            this.name = name
            this.id = id
            this.variance = variance
            this.enclosingElement = kotlinClass
        }
        kotlinClass.typeParameters.add(kpType.kotlinTypeParameter)
        return kpType
    }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}

class KotlinPackageVisitor: KmPackageVisitor() {

    val kotlinPackage = KotlinPackage()

    override fun visitEnd() {
        super.visitEnd()
    }

    override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? {
        val kFunction = KotlinFunctionVisitor()
        kFunction.kotlinFunction.run {
            this.flags = flags
            this.name = name
            this.enclosingElement = kotlinPackage
            kotlinPackage.functions.add(this)
        }
        return kFunction
    }

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
        val kProperty = KotlinPropertyVisitor()
        kProperty.kotlinProperty.run {
            this.flags = flags
            this.name = name
            this.getterFlags = getterFlags
            this.setterFlags = setterFlags
            this.enclosingElement = kotlinPackage
            kotlinPackage.properties.add(this)
        }
        return kProperty
    }

    override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor? {
        val kTypeAlias = KotlinTypeAliasVisitor()
        kTypeAlias.kotlinTypeAlias.run {
            this.flags = flags
            this.name = name
            this.enclosingElement = kotlinPackage
            kotlinPackage.typeAliases.add(this)
        }
        return kTypeAlias
    }
}