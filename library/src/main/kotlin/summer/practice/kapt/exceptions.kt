package summer.practice.kapt

class InvalidMetadataException: Exception()

sealed class KotlinElementNotFoundException(message: String): Exception(message)

class KotlinClassNotFoundException(message: String): KotlinElementNotFoundException(message)

class KotlinFunctionNotFoundException(message: String): KotlinElementNotFoundException(message)

class KotlinConstructorNotFoundException(message: String): KotlinElementNotFoundException(message)

class KotlinPropertyNotFoundException(message: String): KotlinElementNotFoundException(message)

class KotlinPropertyGetterOrSetterNotFoundException(message: String): KotlinElementNotFoundException(message)

class KotlinValueParameterNotFoundException(message: String): KotlinElementNotFoundException(message)

class KotlinTypeParameterNotFoundException(message: String): KotlinElementNotFoundException(message)

class UnsupportedAnnotationException(message: String): Exception(message)