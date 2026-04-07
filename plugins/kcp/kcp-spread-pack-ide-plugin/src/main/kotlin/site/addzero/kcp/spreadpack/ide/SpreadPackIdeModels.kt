package site.addzero.kcp.spreadpack.ide

internal enum class SelectorKind {
    PROPS,
    ATTRS,
    CALLBACKS,
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

internal data class IdeCarrierFieldModel(
    val name: String,
    val type: IdeTypeModel,
    val hasDefaultValue: Boolean,
)

internal data class IdeReferencedParameterType(
    val classId: String,
)

internal data class IdeSpreadArgsReference(
    val functionFqName: String,
    val parameterTypes: List<IdeReferencedParameterType>,
)

internal data class IdeSpreadPackParameterModel(
    val carrierClassId: String,
    val carrierClassShortName: String,
    val carrierFields: List<IdeCarrierFieldModel>,
    val selectorKind: SelectorKind,
    val excludedNames: Set<String>,
    val reference: IdeSpreadArgsReference?,
    val spreadPackAtDefault: Boolean,
)

internal data class IdeValueParameterModel(
    val name: String,
    val type: IdeTypeModel,
    val hasDefaultValue: Boolean,
    val isVararg: Boolean,
    val spreadPack: IdeSpreadPackParameterModel? = null,
)

internal data class IdeFunctionModel(
    val callableFqName: String,
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
    val isTopLevel
        get() = ownerClassFqName == null

    val hasSpreadPackParameters
        get() = parameters.any { parameter -> parameter.spreadPack != null }

    val receiverTypeText
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

    val allTypeParameters
        get() = ownerTypeParameters + typeParameters
}

internal data class IdeSpreadPackExpansion(
    val parameterIndex: Int,
    val carrierClassShortName: String,
    val selectorKind: SelectorKind,
    val fields: List<IdeCarrierFieldModel>,
)

internal data class IdeGeneratedOverloadCandidate(
    val original: IdeFunctionModel,
    val generatedName: String,
    val generatedParameters: List<IdeValueParameterModel>,
    val expansions: List<IdeSpreadPackExpansion>,
)

internal data class IdeGeneratedCarrierStub(
    val packageName: String,
    val classId: String,
    val classShortName: String,
    val fields: List<IdeCarrierFieldModel>,
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

internal fun IdeTypeModel.jvmErasure(): String {
    return when (this) {
        is IdeClassTypeModel -> classId
        is IdeTypeParameterTypeModel -> "typeParameter"
        is IdeOpaqueTypeModel -> erasureKey
    }
}
