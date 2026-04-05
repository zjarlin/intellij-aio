package site.addzero.kcp.transformoverload.idea

internal enum class ConverterParameterKind {
    EXTENSION_RECEIVER,
    VALUE_PARAMETER,
}

internal enum class LiftKind {
    NONE,
    ITERABLE,
    COLLECTION,
    LIST,
    SET,
    SEQUENCE,
}

internal enum class IdeVariance {
    INVARIANT,
    IN,
    OUT,
}

internal data class IdeTypeParameter(
    val id: String,
    val name: String,
    val declarationText: String,
)

internal sealed interface IdeTypeModel {
    val renderedText: String
}

internal data class IdeClassTypeModel(
    val classId: String,
    val arguments: List<IdeTypeArgumentModel>,
    val nullable: Boolean,
    override val renderedText: String,
) : IdeTypeModel

internal data class IdeTypeParameterTypeModel(
    val typeParameterId: String,
    val name: String,
    val nullable: Boolean,
    override val renderedText: String,
) : IdeTypeModel

internal data class IdeOpaqueTypeModel(
    override val renderedText: String,
    val erasureKey: String,
) : IdeTypeModel

internal data class IdeTypeArgumentModel(
    val variance: IdeVariance?,
    val type: IdeTypeModel?,
)

internal data class IdeValueParameterModel(
    val name: String,
    val type: IdeTypeModel,
    val hasDefaultValue: Boolean,
    val isVararg: Boolean,
)

internal data class IdeFunctionModel(
    val packageName: String,
    val ownerClassFqName: String?,
    val ownerTypeParameters: List<IdeTypeParameter>,
    val name: String,
    val typeParameters: List<IdeTypeParameter>,
    val parameters: List<IdeValueParameterModel>,
    val returnType: IdeTypeModel,
    val visibilityKeyword: String?,
    val isSuspend: Boolean,
    val isOperator: Boolean,
    val isInfix: Boolean,
) {
    val isTopLevel: Boolean
        get() = ownerClassFqName == null

    val receiverTypeText: String?
        get() = ownerClassFqName?.let { fqName ->
            if (ownerTypeParameters.isEmpty()) {
                fqName
            } else {
                fqName + ownerTypeParameters.joinToString(
                    prefix = "<",
                    postfix = ">",
                ) { typeParameter -> typeParameter.name }
            }
        }

    val allTypeParameters: List<IdeTypeParameter>
        get() = ownerTypeParameters + typeParameters
}

internal data class IdeRawConverterSpec(
    val parameterKind: ConverterParameterKind,
    val sourceType: IdeTypeModel,
    val targetType: IdeTypeModel,
    val defaultSuffix: String,
    val containerPrefix: String,
    val debugName: String,
)

internal data class IdeConverterSpec(
    val parameterKind: ConverterParameterKind,
    val sourceType: IdeTypeModel,
    val targetType: IdeTypeModel,
    val uniqueSuffix: String,
    val debugName: String,
)

internal data class IdeParameterTransform(
    val converter: IdeConverterSpec,
    val parameterIndex: Int,
    val generatedParameterType: IdeTypeModel,
    val liftKind: LiftKind,
)

internal data class IdeOverloadCandidate(
    val original: IdeFunctionModel,
    val generatedName: String,
    val parameterTransforms: List<IdeParameterTransform>,
)

internal data class IdeGeneratedFile(
    val relativePath: String,
    val packageName: String,
    val topLevelCallableNames: Set<String>,
    val topLevelClassifierNames: Set<String>,
    val content: String,
)

internal fun IdeTypeModel.renderTypeText(): String {
    return when (this) {
        is IdeClassTypeModel -> buildString {
            append(classId)
            if (arguments.isNotEmpty()) {
                append(arguments.joinToString(prefix = "<", postfix = ">") { argument ->
                    argument.renderTypeText()
                })
            }
            if (nullable) {
                append("?")
            }
        }

        is IdeTypeParameterTypeModel -> buildString {
            append(name)
            if (nullable) {
                append("?")
            }
        }

        is IdeOpaqueTypeModel -> renderedText
    }
}

internal fun IdeTypeArgumentModel.renderTypeText(): String {
    val typeText = type?.renderTypeText() ?: "*"
    return when (variance) {
        null -> "*"
        IdeVariance.INVARIANT -> typeText
        IdeVariance.IN -> "in $typeText"
        IdeVariance.OUT -> "out $typeText"
    }
}

internal fun IdeTypeModel.substitute(
    substitution: Map<String, IdeTypeModel>,
): IdeTypeModel {
    return when (this) {
        is IdeClassTypeModel -> copy(
            arguments = arguments.map { argument ->
                argument.copy(type = argument.type?.substitute(substitution))
            },
        )

        is IdeTypeParameterTypeModel -> {
            val substituted = substitution[typeParameterId] ?: return this
            if (!nullable) {
                substituted
            } else {
                when (substituted) {
                    is IdeClassTypeModel -> substituted.copy(nullable = true)
                    is IdeTypeParameterTypeModel -> substituted.copy(nullable = true)
                    is IdeOpaqueTypeModel -> IdeOpaqueTypeModel(
                        renderedText = substituted.renderedText + "?",
                        erasureKey = substituted.erasureKey,
                    )
                }
            }
        }

        is IdeOpaqueTypeModel -> this
    }
}

internal fun matchTypePattern(
    pattern: IdeTypeModel,
    actual: IdeTypeModel,
    bindings: MutableMap<String, IdeTypeModel> = linkedMapOf(),
): Map<String, IdeTypeModel>? {
    return when (pattern) {
        is IdeTypeParameterTypeModel -> {
            val existing = bindings[pattern.typeParameterId]
            if (existing == null) {
                bindings[pattern.typeParameterId] = actual
                bindings
            } else if (existing.renderTypeText() == actual.renderTypeText()) {
                bindings
            } else {
                null
            }
        }

        is IdeClassTypeModel -> {
            val actualClass = actual as? IdeClassTypeModel ?: return null
            if (pattern.classId != actualClass.classId) {
                return null
            }
            if (pattern.nullable != actualClass.nullable) {
                return null
            }
            if (pattern.arguments.size != actualClass.arguments.size) {
                return null
            }
            pattern.arguments.zip(actualClass.arguments).forEach { (patternArgument, actualArgument) ->
                when {
                    patternArgument.type == null && actualArgument.type == null -> Unit
                    patternArgument.type != null && actualArgument.type != null -> {
                        if (patternArgument.variance != actualArgument.variance) {
                            return null
                        }
                        matchTypePattern(patternArgument.type, actualArgument.type, bindings) ?: return null
                    }

                    else -> return null
                }
            }
            bindings
        }

        is IdeOpaqueTypeModel -> {
            if (pattern.renderedText == actual.renderedText) {
                bindings
            } else {
                null
            }
        }
    }
}

internal fun IdeTypeModel.jvmErasure(): String {
    return when (this) {
        is IdeClassTypeModel -> classId
        is IdeTypeParameterTypeModel -> "typeParameter"
        is IdeOpaqueTypeModel -> erasureKey
    }
}

internal fun IdeTypeModel.toLiftKind(): LiftKind? {
    return (this as? IdeClassTypeModel)
        ?.classId
        ?.let(TransformOverloadIdeaConstants.liftKindsByClassId::get)
}

internal fun buildRenamedFunctionName(
    originalName: String,
    transforms: List<IdeParameterTransform>,
): String {
    val suffix = transforms.joinToString(separator = "And") { transform ->
        transform.converter.uniqueSuffix
    }
    return "${originalName}Via$suffix"
}

internal fun String.toPascalCase(): String {
    if (isEmpty()) {
        return this
    }
    val builder = StringBuilder(length)
    var uppercaseNext = true
    forEach { character ->
        if (!character.isLetterOrDigit()) {
            uppercaseNext = true
        } else if (uppercaseNext) {
            builder.append(character.uppercaseChar())
            uppercaseNext = false
        } else {
            builder.append(character)
        }
    }
    return builder.toString()
}
