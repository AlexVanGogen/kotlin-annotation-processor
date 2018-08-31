# kotlin-annotation-processor

Annotation processor that can operate with Kotlin-specific features.

During annotation processing phase each Kotlin element (e.g. nullable type, or extension function) is already represented as Java element. All information about Kotlin features is stored inside @Metadata annotation. So the goal is to parse this annotation and store found data in special structure (similar to [javax.lang.model.element API](https://docs.oracle.com/javase/8/docs/api/javax/lang/model/element/package-summary.html))

Repository consists of three modules:

* **library** -- contains structures and functions that collect information about Kotlin features from @Metadata annotation and provides API for working with Kotlin elements during annotation processing phase in the same way as when working with Java annotation processing.
* **processor** -- test module; contains test annotations and annotation processor
* **app** -- test module; contains Kotlin code with annotations from **processor** module

## Usage

```
git clone https://github.com/AlexVanGogen/kotlin-annotation-processor.git
cd kotlin-annotation-processor
gradle build
```

The last command will make [`ExampleProcessor`](https://github.com/AlexVanGogen/kotlin-annotation-processor/blob/master/processor/src/main/kotlin/summer/practice/kapt/ExampleProcessor.kt) to run and do the following with classes in **app** module:

* find constructors annotated with [`@DumpConstructor`](https://github.com/AlexVanGogen/kotlin-annotation-processor/blob/master/processor/src/main/kotlin/summer/practice/kapt/DumpConstructor.kt) annotation and print it's canonical name and information about parameters (including nullability). Depending on `checkPrimary` value, it can additionally print if constructor primary or not.
* find functions annotated with [`@DumpFunction`](https://github.com/AlexVanGogen/kotlin-annotation-processor/blob/master/processor/src/main/kotlin/summer/practice/kapt/DumpFunction.kt) annotation and print all information about it (excluding value parameters annotated with [`@IgnoreParameter`](https://github.com/AlexVanGogen/kotlin-annotation-processor/blob/master/processor/src/main/kotlin/summer/practice/kapt/IgnoreParameter.kt)) (see example below).
* find type aliases annotated with [`@PrintTypeAlias`](https://github.com/AlexVanGogen/kotlin-annotation-processor/blob/master/processor/src/main/kotlin/summer/practice/kapt/PrintTypeAlias.kt) annotation and print pseudotype and underlying type.

## Example

Suppose we have the next class:

```kotlin
class Class7<T> {
    fun <U: T, V: U> someFun(u: List<U>, v: MutableMap<out V, Map<T, List<*>>>) = if (u.isNotEmpty()) u.first() else null
    @DumpFunction fun <U: T, V: U> someFun(@IgnoreParameter u: U, v: MutableMap<out V, Map<T, List<*>>>) = u
    @DumpFunction inline fun <reified T, S: T> Map<T, S>.nextFun() {}
}
```

We will operate with Kotlin elements this way:

```kotlin
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
                            functionsFile.addLine("${type.name}, id: ${type.id}, variance: ${type.variance}, upper bound: ${type.upperBound?.kotlinName}", 2)
                }
```

And here is the result:

```
Function someFun
Declared explicitly: yes
\--- Modifiers: none
\--- Is extension: no
\--- Return type: U
\--- Value parameters: 
	\--- v: kotlin/collections/MutableMap<out V, kotlin/collections/Map<T, kotlin/collections/List<*>>>
\--- Type parameters: 
	\--- U, id: 1, variance: INVARIANT, upper bound: T
	\--- V, id: 2, variance: INVARIANT, upper bound: U
Function nextFun
Declared explicitly: yes
\--- Modifiers: inline
\--- Is extension: yes
	\--- Receiver: kotlin/collections/Map<T, S>
\--- Return type: kotlin/Unit
\--- Value parameters: none
\--- Type parameters: 
	\--- T, id: 1, variance: INVARIANT, upper bound: null
	\--- S, id: 2, variance: INVARIANT, upper bound: T
```

For more details see [`ExampleProcessor.process`](https://github.com/AlexVanGogen/kotlin-annotation-processor/blob/master/processor/src/main/kotlin/summer/practice/kapt/ExampleProcessor.kt) method.
