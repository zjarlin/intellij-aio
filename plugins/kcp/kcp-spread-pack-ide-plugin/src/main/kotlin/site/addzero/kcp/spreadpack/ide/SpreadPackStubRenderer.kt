package site.addzero.kcp.spreadpack.ide

internal object SpreadPackStubRenderer {

    fun renderGeneratedFiles(
        candidates: List<IdeGeneratedOverloadCandidate>,
        carrierStubs: List<IdeGeneratedCarrierStub>,
    ): List<IdeGeneratedFile> {
        if (candidates.isEmpty() && carrierStubs.isEmpty()) {
            return emptyList()
        }
        val candidatesByPackage = candidates.groupBy { candidate -> candidate.original.packageName }
        val carrierStubsByPackage = carrierStubs.groupBy { stub -> stub.packageName }
        val packageNames = linkedSetOf<String>().apply {
            addAll(candidatesByPackage.keys)
            addAll(carrierStubsByPackage.keys)
        }
        return packageNames.map { packageName ->
            val packageCandidates = candidatesByPackage[packageName].orEmpty()
            val packageCarrierStubs = carrierStubsByPackage[packageName].orEmpty()
            val relativePath = buildString {
                if (packageName.isNotBlank()) {
                    append(packageName.replace('.', '/'))
                    append("/")
                }
                append(SpreadPackIdeaConstants.stubFileName)
            }
            IdeGeneratedFile(
                relativePath = relativePath,
                packageName = packageName,
                topLevelCallableNames = linkedSetOf<String>().apply {
                    addAll(packageCandidates.map { candidate -> candidate.generatedName })
                    addAll(
                        packageCarrierStubs.flatMap { stub ->
                            stub.fields.map { field -> field.name }
                        },
                    )
                },
                topLevelClassifierNames = emptySet(),
                content = buildString {
                    appendLine("@file:Suppress(\"unused\")")
                    if (packageName.isNotBlank()) {
                        appendLine("package $packageName")
                        appendLine()
                    }
                    packageCarrierStubs
                        .sortedWith(compareBy({ it.classId }, { it.classShortName }))
                        .forEachIndexed { index, stub ->
                            if (index > 0) {
                                appendLine()
                            }
                            append(renderCarrierProperties(stub))
                            appendLine()
                        }
                    if (packageCarrierStubs.isNotEmpty() && packageCandidates.isNotEmpty()) {
                        appendLine()
                    }
                    packageCandidates
                        .sortedWith(compareBy({ it.original.name }, { it.generatedName }))
                        .forEachIndexed { index, candidate ->
                            if (index > 0) {
                                appendLine()
                            }
                            append(renderFunction(candidate))
                            appendLine()
                        }
                },
            )
        }
    }

    private fun renderCarrierProperties(
        stub: IdeGeneratedCarrierStub,
    ): String {
        return buildString {
            stub.fields.forEachIndexed { index, field ->
                if (index > 0) {
                    appendLine()
                }
                append("var ")
                append(stub.classId)
                append(".")
                append(field.name)
                append(": ")
                append(field.type.renderTypeText())
                appendLine()
                append("    get() = kotlin.error(\"")
                append(SpreadPackIdeaConstants.stubErrorMessage)
                appendLine("\")")
                appendLine("    set(value) {")
                append("        kotlin.error(\"")
                append(SpreadPackIdeaConstants.stubErrorMessage)
                appendLine("\")")
                append("    }")
            }
        }
    }

    private fun renderFunction(
        candidate: IdeGeneratedOverloadCandidate,
    ): String {
        val function = candidate.original
        return buildString {
            val modifiers = buildList {
                function.visibilityKeyword?.let(::add)
                if (function.isSuspend) {
                    add("suspend")
                }
                if (function.isOperator) {
                    add("operator")
                }
                if (function.isInfix) {
                    add("infix")
                }
            }
            if (modifiers.isNotEmpty()) {
                append(modifiers.joinToString(separator = " "))
                append(" ")
            }
            append("fun ")
            if (function.allTypeParameters.isNotEmpty()) {
                append(
                    function.allTypeParameters.joinToString(
                        prefix = "<",
                        postfix = "> ",
                    ) { typeParameter -> typeParameter.declarationText },
                )
            }
            function.receiverTypeText?.let { receiverType ->
                append(receiverType)
                append(".")
            }
            append(candidate.generatedName)
            append("(")
            append(
                candidate.generatedParameters.joinToString(separator = ", ") { parameter ->
                    val pieces = mutableListOf<String>()
                    if (parameter.isVararg) {
                        pieces += "vararg"
                    }
                    pieces += "${parameter.name}: ${parameter.type.renderTypeText()}"
                    if (parameter.hasDefaultValue) {
                        pieces += "= kotlin.TODO()"
                    }
                    pieces.joinToString(separator = " ")
                },
            )
            append("): ")
            append(function.returnType.renderTypeText())
            append(" = kotlin.error(\"")
            append(SpreadPackIdeaConstants.stubErrorMessage)
            append("\")")
        }
    }
}
