package summer.practice.kapt

import kotlinx.metadata.*
import kotlinx.metadata.jvm.KotlinClassMetadata
import java.io.File

internal typealias AnnotationClass = Class<out Annotation>

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

sealed class KotlinElement(
        private val flags: Flags? = null
) {

    var enclosingElement: KotlinElement? = null
        internal set

    private val annotations: MutableSet<Annotation> = mutableSetOf()

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

    internal fun addAnnotation(annotation: Annotation) = annotations.add(annotation)

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

open class KotlinDeclarationContainer internal constructor(private val flags: Flags? = null): KotlinElement(flags) {
    open val functions: List<KotlinFunction> = listOf()
    open val properties: List<KotlinProperty> = listOf()
    open val typeAliases: List<KotlinTypeAlias> = listOf()
}

class KotlinClass internal constructor(
        val name: ClassName? = null,
        override val functions: List<KotlinFunction> = listOf(),
        override val properties: List<KotlinProperty> = listOf(),
        override val typeAliases: List<KotlinTypeAlias> = listOf(),
        val supertype: KotlinType? = null,
        val typeParameters: List<KotlinTypeParameter> = listOf(),
        val companionObjectName: String? = null,
        val nestedClassesNames: List<String> = listOf(),
        val enumEntriesNames: List<String> = listOf(),
        val sealedClassesNames: List<ClassName> = listOf(),
        val constructors: List<KotlinConstructor> = listOf(),
        private val flags: Flags? = null

): KotlinDeclarationContainer(flags) {

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


class KotlinPackage internal constructor(
        internal var name: String? = null,
        override val functions: List<KotlinFunction> = listOf(),
        override val properties: List<KotlinProperty> = listOf(),
        override val typeAliases: List<KotlinTypeAlias> = listOf(),
        private val flags: Flags? = null
): KotlinDeclarationContainer(flags)


class KotlinLambda internal constructor(
        var wrappedFunction: KotlinFunction? = null,
        private val flags: Flags? = null
): KotlinElement(flags)


class KotlinConstructor internal constructor(
        val valueParameters: List<KotlinValueParameter> = listOf(),
        private val flags: Flags? = null
): KotlinElement(flags) {

    val typeRepresentation: String get() {
        val valueParameters = valueParameters.map { it.type?.javaName }.joinToString(",")
        val returnValue = "void"
        return "($valueParameters)$returnValue".replace('/', '.').replace('*', '?')
    }

    val deliveredProperties: Set<KotlinProperty> get() =
        if (isPrimary())
            (enclosingElement as? KotlinClass)?.properties?.filter {
                it.name in valueParameters.map { value -> value.name }
            }?.toSet() ?: setOf()
        else setOf()

    fun isPrimary(): Boolean = Flag.Constructor.IS_PRIMARY(flags ?: 0)
}


class KotlinFunction internal constructor(
        val name: String? = null,
        val typeParameters: List<KotlinTypeParameter> = listOf(),
        val receiverParameterType: KotlinType? = null,
        val valueParameters: List<KotlinValueParameter> = listOf(),
        val returnType: KotlinType? = null,
        private val flags: Flags? = null
): KotlinElement(flags) {

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


class KotlinProperty internal constructor(
        val name: String? = null,
        val typeParameters: List<KotlinTypeParameter> = listOf(),
        val receiverParameterType: KotlinType? = null,
        val setterParameter: KotlinValueParameter? = null,
        val returnType: KotlinType? = null,
        private val flags: Flags? = null,
        private val getterFlags: Flags? = null,
        private val setterFlags: Flags? = null
): KotlinElement(flags) {

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
class KotlinPropertyGetter internal constructor(val property: KotlinProperty)
class KotlinPropertySetter internal constructor(val property: KotlinProperty)


class KotlinTypeAlias internal constructor(
        val name: String? = null,
        val typeParameters: List<KotlinTypeParameter> = listOf(),
        val underlyingType: KotlinType? = null,
        val expandedType: KotlinType? = null,
        private val flags: Flags? = null
): KotlinElement(flags) {

    val kAnnotations: MutableList<KmAnnotation> = mutableListOf()
}


class KotlinValueParameter internal constructor(
        val name: String? = null,
        val type: KotlinType? = null,
        val varargType: KotlinType? = null,
        private val flags: Flags? = null
): KotlinElement(flags) {

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
            result += "vararg $name: $varargType"
        } else {
            result += "$name: ${type.toString()}"
        }
        return result
    }
}


class KotlinType internal constructor(
        private val _name: String? = null,
        val kind: KotlinTypeKind? = null,
        val arguments: List<KotlinType> = listOf(),
        val typeParameterId: Int? = null,
        val variance: KmVariance? = null,
        val abbreviatedType: KotlinType? = null,
        val flexibleTypeUpperBound: KotlinType? = null,
        val typeFlexibilityId: String? = null,
        val outerClassType: KotlinType? = null,
        private val flags: Flags? = null
): KotlinElement(flags) {

    private var typeParameterName: String? = null
    val name: String? get() = if (kind == KotlinTypeKind.TYPE_PARAMETER) typeParameterName else _name

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

    var wrappedTypeParameter_: KotlinTypeParameter? = null

    val kotlinName: String? get() = "$name${if (arguments.isNotEmpty()) "<${arguments.joinToString { it.toString() }}>" else ""}${if (isNullable()) "?" else ""}"

    fun isNullable(): Boolean = Flag.Type.IS_NULLABLE(flags ?: 0)
    fun isSuspend(): Boolean = Flag.Type.IS_SUSPEND(flags ?: 0)

    fun bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
        if (kind == KotlinTypeKind.TYPE_PARAMETER) {
            wrappedTypeParameter_ = parametersStack.find { it.id == typeParameterId }
            typeParameterName = wrappedTypeParameter_?.name
        }
        outerClassType?.bindNestedTypeParametersWithWrappingType(parametersStack)
        flexibleTypeUpperBound?.bindNestedTypeParametersWithWrappingType(parametersStack)
        abbreviatedType?.bindNestedTypeParametersWithWrappingType(parametersStack)
        arguments.forEach { it.bindNestedTypeParametersWithWrappingType(parametersStack) }
    }

    companion object {
        val STAR_PROJECTION = KotlinType(_name = "*")
    }
}


class KotlinTypeParameter internal constructor(
        val name: String? = null,
        val id: Int? = null,
        val variance: KmVariance? = null,
        val upperBound: KotlinType? = null,
        private val flags: Flags? = null
): KotlinElement(flags) {

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
            result += ": $upperBound"
        return result
    }
}


fun KotlinClassMetadata?.collectKotlinSpecificInformationFromClass(): KotlinDeclarationContainer {

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


private class KotlinTypeVisitor(var kotlinType: KotlinType = KotlinType()): KmTypeVisitor() {

    var flags: Flags? = null
    var variance: KmVariance? = null
    var kind: KotlinTypeKind? = null
    var name: String? = null
    var typeFlexibilityId: String? = null
    var typeParameterId: Int? = null

    var abbreviatedTypeVisitor: KotlinTypeVisitor? = null
    val argumentsVisitors = mutableListOf<KotlinTypeVisitor>()
    var flexibleTypeUpperBoundVisitor: KotlinTypeVisitor? = null
    var outerClassTypeVisitor: KotlinTypeVisitor? = null

    override fun visitAbbreviatedType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                abbreviatedTypeVisitor = this
                this
            }

    override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                this.variance = variance
                this@KotlinTypeVisitor.argumentsVisitors.add(this)
                this
            }

    override fun visitClass(name: ClassName) {
        kind = KotlinTypeKind.CLASS_TYPE
        this.name = name
    }

    override fun visitFlexibleTypeUpperBound(flags: Flags, typeFlexibilityId: String?): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                this.typeFlexibilityId = typeFlexibilityId
                flexibleTypeUpperBoundVisitor = this
                this
            }


    override fun visitOuterType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                outerClassTypeVisitor = this
                this
            }

    override fun visitTypeAlias(name: ClassName) {
        kind = KotlinTypeKind.TYPE_ALIAS
        this.name = name
    }

    override fun visitTypeParameter(id: Int) {
        kind = KotlinTypeKind.TYPE_PARAMETER
        typeParameterId = id
    }

    override fun visitEnd() {
        kotlinType = KotlinType(
                name,
                kind,
                argumentsVisitors.map { it.kotlinType }.toList(),
                typeParameterId,
                variance,
                abbreviatedTypeVisitor?.kotlinType,
                flexibleTypeUpperBoundVisitor?.kotlinType,
                typeFlexibilityId,
                outerClassTypeVisitor?.kotlinType,
                flags
        )
        kotlinType.run {
            arguments.forEach { it.enclosingElement = kotlinType }
            abbreviatedType?.enclosingElement = kotlinType
            flexibleTypeUpperBound?.enclosingElement = kotlinType
            outerClassType?.enclosingElement = kotlinType
        }
    }

    override fun visitExtensions(type: KmExtensionType): KmTypeExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitStarProjection() {
        argumentsVisitors.add(KotlinTypeVisitor(KotlinType.STAR_PROJECTION))
    }
}


private class KotlinTypeParameterVisitor: KmTypeParameterVisitor() {

    var kotlinTypeParameter = KotlinTypeParameter()

    var flags: Flags? = null
    var variance: KmVariance? = null
    var name: String? = null
    var id: Int? = null

    var upperBoundVisitor: KotlinTypeVisitor? = null

    override fun visitEnd() {
        kotlinTypeParameter = KotlinTypeParameter(
                name,
                id,
                variance,
                upperBoundVisitor?.kotlinType,
                flags
        )
        kotlinTypeParameter.upperBound?.enclosingElement = kotlinTypeParameter
    }

    override fun visitExtensions(type: KmExtensionType): KmTypeParameterExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitUpperBound(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                upperBoundVisitor = this
                this
            }
}


private class KotlinValueParameterVisitor: KmValueParameterVisitor() {

    var kotlinValueParameter = KotlinValueParameter()

    var name: String? = null
    var flags: Flags? = null

    var typeVisitor: KotlinTypeVisitor? = null
    var varargTypeVisitor: KotlinTypeVisitor? = null

    override fun visitEnd() {
        kotlinValueParameter = KotlinValueParameter(
                name,
                typeVisitor?.kotlinType,
                varargTypeVisitor?.kotlinType,
                flags
        )
        kotlinValueParameter.run {
            type?.enclosingElement = kotlinValueParameter
            varargType?.enclosingElement = kotlinValueParameter
        }
    }


    override fun visitType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                typeVisitor = this
                this
            }


    override fun visitVarargElementType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                varargTypeVisitor = this
                this
            }
}


private class KotlinTypeAliasVisitor: KmTypeAliasVisitor() {

    var kotlinTypeAlias = KotlinTypeAlias()

    var name: String? = null
    var flags: Flags? = null

    val typeParametersVisitors = mutableListOf<KotlinTypeParameterVisitor>()
    var underlyingTypeVisitor: KotlinTypeVisitor? = null
    var expandedTypeVisitor: KotlinTypeVisitor? = null

    val annotations = mutableListOf<KmAnnotation>()

    override fun visitAnnotation(annotation: KmAnnotation) {
        annotations.add(annotation)
    }

    override fun visitEnd() {
        kotlinTypeAlias = KotlinTypeAlias(
                name,
                typeParametersVisitors.map { it.kotlinTypeParameter },
                underlyingTypeVisitor?.kotlinType,
                expandedTypeVisitor?.kotlinType,
                flags
        )
        kotlinTypeAlias.run {
            kAnnotations.addAll(annotations)
            typeParameters.forEach { it.enclosingElement = kotlinTypeAlias }
            underlyingType?.enclosingElement = kotlinTypeAlias
            expandedType?.enclosingElement = kotlinTypeAlias
        }
    }


    override fun visitExpandedType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                expandedTypeVisitor = this
                this
            }


    override fun visitTypeParameter(flags: Flags, name: String, id: Int, variance: KmVariance): KmTypeParameterVisitor? =
            KotlinTypeParameterVisitor().run {
                this.flags = flags
                this.name = name
                this.id = id
                this.variance = variance
                typeParametersVisitors.add(this)
                this
            }


    override fun visitUnderlyingType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                underlyingTypeVisitor = this
                this
            }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}


private class KotlinPropertyVisitor: KmPropertyVisitor() {

    var kotlinProperty = KotlinProperty()

    var name: String? = null
    var flags: Flags? = null
    var getterFlags: Flags? = null
    var setterFlags: Flags? = null

    val typeParametersVisitors = mutableListOf<KotlinTypeParameterVisitor>()
    var receiverParameterTypeVisitor: KotlinTypeVisitor? = null
    var setterParameterVisitor: KotlinValueParameterVisitor? = null
    var returnTypeVisitor: KotlinTypeVisitor? = null

    override fun visitEnd() {
        kotlinProperty = KotlinProperty(
                name,
                typeParametersVisitors.map { it.kotlinTypeParameter },
                receiverParameterTypeVisitor?.kotlinType,
                setterParameterVisitor?.kotlinValueParameter,
                returnTypeVisitor?.kotlinType,
                flags,
                getterFlags,
                setterFlags
        )
        kotlinProperty.run {
            typeParameters.forEach { it.enclosingElement = kotlinProperty}
            receiverParameterType?.enclosingElement = kotlinProperty
            setterParameter?.enclosingElement = kotlinProperty
            returnType?.enclosingElement = kotlinProperty
        }
    }

    override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
        return super.visitExtensions(type)
    }


    override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                receiverParameterTypeVisitor = this
                this
            }

    override fun visitReturnType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                returnTypeVisitor = this
                this
            }

    override fun visitSetterParameter(flags: Flags, name: String): KmValueParameterVisitor? =
            KotlinValueParameterVisitor().run {
                this.flags = flags
                this.name = name
                setterParameterVisitor = this
                this
            }

    override fun visitTypeParameter(flags: Flags, name: String, id: Int, variance: KmVariance): KmTypeParameterVisitor? =
            KotlinTypeParameterVisitor().run {
                this.flags = flags
                this.name = name
                this.id = id
                this.variance = variance
                typeParametersVisitors.add(this)
                this
            }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}


private class KotlinFunctionVisitor: KmFunctionVisitor() {

    var kotlinFunction = KotlinFunction()

    var name: String? = null
    var flags: Flags? = null

    val typeParametersVisitors = mutableListOf<KotlinTypeParameterVisitor>()
    var receiverParameterTypeVisitor: KotlinTypeVisitor? = null
    val valueParametersVisitors = mutableListOf<KotlinValueParameterVisitor>()
    var returnTypeVisitor: KotlinTypeVisitor? = null

    override fun visitContract(): KmContractVisitor? {
        return super.visitContract()
    }

    override fun visitEnd() {
        kotlinFunction = KotlinFunction(
                name,
                typeParametersVisitors.map { it.kotlinTypeParameter },
                receiverParameterTypeVisitor?.kotlinType,
                valueParametersVisitors.map { it.kotlinValueParameter },
                returnTypeVisitor?.kotlinType,
                flags
        )
        kotlinFunction.run {
            typeParameters.forEach { it.enclosingElement = kotlinFunction }
            valueParameters.forEach { it.enclosingElement = kotlinFunction }
            receiverParameterType?.enclosingElement = kotlinFunction
            returnType?.enclosingElement = kotlinFunction
        }
    }

    override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                receiverParameterTypeVisitor = this
                this
            }


    override fun visitReturnType(flags: Flags): KmTypeVisitor? =
            KotlinTypeVisitor().run {
                this.flags = flags
                returnTypeVisitor = this
                this
            }


    override fun visitTypeParameter(flags: Flags, name: String, id: Int, variance: KmVariance): KmTypeParameterVisitor? =
            KotlinTypeParameterVisitor().run {
                this.flags = flags
                this.name = name
                this.id = id
                this.variance = variance
                typeParametersVisitors.add(this)
                this
            }


    override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor? =
            KotlinValueParameterVisitor().run {
                this.flags = flags
                this.name = name
                valueParametersVisitors.add(this)
                this
            }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}


private class KotlinConstructorVisitor: KmConstructorVisitor() {

    var kotlinConstructor = KotlinConstructor()

    var flags: Flags? = null

    val valueParametersVisitors = mutableListOf<KotlinValueParameterVisitor>()

    override fun visitEnd() {
        kotlinConstructor = KotlinConstructor(
                valueParametersVisitors.map { it.kotlinValueParameter },
                flags
        )
        kotlinConstructor.run {
            valueParameters.forEach { it.enclosingElement = kotlinConstructor }
        }
    }

    override fun visitExtensions(type: KmExtensionType): KmConstructorExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitValueParameter(flags: Flags, name: String): KmValueParameterVisitor? =
            KotlinValueParameterVisitor().run {
                this.flags = flags
                this.name = name
                valueParametersVisitors.add(this)
                this
            }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}


private class KotlinLambdaVisitor: KmLambdaVisitor() {

    var kotlinLambda = KotlinLambda()

    var flags: Flags? = null
    var wrappedFunction: KotlinFunction? = null

    var wrappedFunctionVisitor: KotlinFunctionVisitor? = null

    override fun visitEnd() {
        kotlinLambda = KotlinLambda(
                wrappedFunctionVisitor?.kotlinFunction,
                flags
        )
        kotlinLambda.enclosingElement = wrappedFunction
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
            KotlinFunctionVisitor().run {
                this.flags = flags
                this.name = name
                wrappedFunctionVisitor = this
                this
            }
}


private class KotlinClassVisitor: KmClassVisitor() {

    var kotlinClass = KotlinClass()

    var name: ClassName? = null
    var flags: Flags? = null
    var companionObjectName: String? = null
    val nestedClassesNames: MutableList<String> = mutableListOf()
    val enumEntriesNames: MutableList<String> = mutableListOf()
    val sealedClassesNames: MutableList<ClassName> = mutableListOf()

    val functionsVisitors = mutableListOf<KotlinFunctionVisitor>()
    val propertiesVisitors = mutableListOf<KotlinPropertyVisitor>()
    val typeAliasesVisitors = mutableListOf<KotlinTypeAliasVisitor>()
    var supertypeVisitor: KotlinTypeVisitor? = null
    val typeParametersVisitors = mutableListOf<KotlinTypeParameterVisitor>()
    val constructorsVisitors = mutableListOf<KotlinConstructorVisitor>()

    override fun visit(flags: Flags, name: ClassName) {
        this.flags = flags
        this.name = name.replace('/', '.')
    }

    override fun visitCompanionObject(name: String) {
        companionObjectName = name
    }

    override fun visitConstructor(flags: Flags): KmConstructorVisitor? =
        KotlinConstructorVisitor().run {
            this.flags = flags
            constructorsVisitors.add(this)
            this
        }

    override fun visitEnd() {
        kotlinClass = KotlinClass(
                name,
                functionsVisitors.map { it.kotlinFunction },
                propertiesVisitors.map { it.kotlinProperty },
                typeAliasesVisitors.map { it.kotlinTypeAlias },
                supertypeVisitor?.kotlinType,
                typeParametersVisitors.map { it.kotlinTypeParameter },
                companionObjectName,
                nestedClassesNames,
                enumEntriesNames,
                sealedClassesNames,
                constructorsVisitors.map { it.kotlinConstructor },
                flags
        )
        kotlinClass.run {
            functions.forEach { it.enclosingElement = kotlinClass }
            properties.forEach { it.enclosingElement = kotlinClass }
            typeAliases.forEach { it.enclosingElement = kotlinClass }
            typeParameters.forEach { it.enclosingElement = kotlinClass }
            constructors.forEach { it.enclosingElement = kotlinClass }
            supertype?.enclosingElement = kotlinClass
        }
    }

    override fun visitEnumEntry(name: String) {
        enumEntriesNames.add(name)
    }

    override fun visitExtensions(type: KmExtensionType): KmClassExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
        KotlinFunctionVisitor().run {
            this.flags = flags
            this.name = name
            functionsVisitors.add(this)
            this
        }

    override fun visitNestedClass(name: String) {
        nestedClassesNames.add(name)
    }

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
            KotlinPropertyVisitor().run {
                this.flags = flags
                this.name = name
                this.getterFlags = getterFlags
                this.setterFlags = setterFlags
                propertiesVisitors.add(this)
                this
            }

    override fun visitSealedSubclass(name: ClassName) {
        sealedClassesNames.add(name)
    }

    override fun visitSupertype(flags: Flags): KmTypeVisitor? =
        KotlinTypeVisitor().run {
            this.flags = flags
            supertypeVisitor = this
            this
        }

    override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor? =
            KotlinTypeAliasVisitor().run {
                this.flags = flags
                this.name = name
                typeAliasesVisitors.add(this)
                this
            }

    override fun visitTypeParameter(flags: Flags, name: String, id: Int, variance: KmVariance): KmTypeParameterVisitor? =
        KotlinTypeParameterVisitor().run {
            this.flags = flags
            this.name = name
            this.id = id
            this.variance = variance
            typeParametersVisitors.add(this)
            this
        }

    override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
        return super.visitVersionRequirement()
    }
}


private class KotlinPackageVisitor: KmPackageVisitor() {

    var kotlinPackage = KotlinPackage()

    var name: String? = null
    var flags: Flags? = null

    val functionsVisitors = mutableListOf<KotlinFunctionVisitor>()
    val propertiesVisitors = mutableListOf<KotlinPropertyVisitor>()
    val typeAliasesVisitors = mutableListOf<KotlinTypeAliasVisitor>()

    override fun visitEnd() {
        kotlinPackage = KotlinPackage(
                name,
                functionsVisitors.map { it.kotlinFunction },
                propertiesVisitors.map { it.kotlinProperty },
                typeAliasesVisitors.map { it.kotlinTypeAlias },
                flags
        )
        kotlinPackage.run {
            functions.forEach { it.enclosingElement = kotlinPackage }
            properties.forEach { it.enclosingElement = kotlinPackage }
            typeAliases.forEach { it.enclosingElement = kotlinPackage }
        }
    }

    override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? {
        return super.visitExtensions(type)
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
            KotlinFunctionVisitor().run {
                this.flags = flags
                this.name = name
                functionsVisitors.add(this)
                this
            }

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
            KotlinPropertyVisitor().run {
                this.flags = flags
                this.name = name
                this.getterFlags = getterFlags
                this.setterFlags = setterFlags
                propertiesVisitors.add(this)
                this
            }

    override fun visitTypeAlias(flags: Flags, name: String): KmTypeAliasVisitor? =
            KotlinTypeAliasVisitor().run {
                this.flags = flags
                this.name = name
                typeAliasesVisitors.add(this)
                this
            }
}


fun KotlinClass.bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
    val stack = parametersStack.plus(typeParameters)
    typeParameters.forEach { it.bindNestedTypeParametersWithWrappingType(stack) }
    functions.forEach { it.bindNestedTypeParametersWithWrappingType(stack) }
    properties.forEach { it.bindNestedTypeParametersWithWrappingType(stack) }
    typeAliases.forEach { it.bindNestedTypeParametersWithWrappingType(stack) }
    constructors.forEach { it.bindNestedTypeParametersWithWrappingType(stack) }
}

fun KotlinPackage.bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
    functions.forEach { it.bindNestedTypeParametersWithWrappingType(parametersStack) }
    properties.forEach { it.bindNestedTypeParametersWithWrappingType(parametersStack) }
    typeAliases.forEach { it.bindNestedTypeParametersWithWrappingType(parametersStack) }
}

fun KotlinLambda.bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
    wrappedFunction?.bindNestedTypeParametersWithWrappingType(parametersStack)
}

fun KotlinConstructor.bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
    valueParameters.forEach { it.bindNestedTypeParametersWithWrappingType(parametersStack) }
}

fun KotlinFunction.bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
    val stack = parametersStack.plus(typeParameters)
    typeParameters.forEach { it.bindNestedTypeParametersWithWrappingType(stack) }
    receiverParameterType?.bindNestedTypeParametersWithWrappingType(stack)
    returnType?.bindNestedTypeParametersWithWrappingType(stack)
    valueParameters.forEach { it.bindNestedTypeParametersWithWrappingType(stack) }
}

fun KotlinProperty.bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
    val stack = parametersStack.plus(typeParameters)
    typeParameters.forEach { it.bindNestedTypeParametersWithWrappingType(stack) }
    receiverParameterType?.bindNestedTypeParametersWithWrappingType(stack)
    returnType?.bindNestedTypeParametersWithWrappingType(stack)
    setterParameter?.bindNestedTypeParametersWithWrappingType(stack)
}

fun KotlinTypeAlias.bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
    val stack = parametersStack.plus(typeParameters)
    typeParameters.forEach { it.bindNestedTypeParametersWithWrappingType(stack) }
    expandedType?.bindNestedTypeParametersWithWrappingType(stack)
    underlyingType?.bindNestedTypeParametersWithWrappingType(stack)
}

fun KotlinValueParameter.bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
    type?.bindNestedTypeParametersWithWrappingType(parametersStack)
    varargType?.bindNestedTypeParametersWithWrappingType(parametersStack)
}

fun KotlinTypeParameter.bindNestedTypeParametersWithWrappingType(parametersStack: List<KotlinTypeParameter> = listOf()) {
    upperBound?.bindNestedTypeParametersWithWrappingType(parametersStack)
}