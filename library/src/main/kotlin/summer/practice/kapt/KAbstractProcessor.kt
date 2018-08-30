package summer.practice.kapt

import kotlinx.metadata.internal.metadata.jvm.deserialization.JvmMetadataVersion
import kotlinx.metadata.jvm.KotlinClassHeader
import javax.annotation.processing.AbstractProcessor
import javax.lang.model.element.*

abstract class KAbstractProcessor: AbstractProcessor() {

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