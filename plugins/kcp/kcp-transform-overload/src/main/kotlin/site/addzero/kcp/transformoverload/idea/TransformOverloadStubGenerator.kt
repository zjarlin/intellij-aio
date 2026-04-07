package site.addzero.kcp.transformoverload.idea

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.types.Variance

@OptIn(KaExperimentalApi::class)
internal class TransformOverloadStubGenerator(
    private val project: Project,
) {

    private val logger = Logger.getInstance(TransformOverloadStubGenerator::class.java)

    fun generate(): List<IdeGeneratedFile> {
        val rawConverters = mutableListOf<IdeRawConverterSpec>()
        val memberScopes = mutableListOf<MemberTargetScope>()
        val topLevelScopes = linkedMapOf<String, MutableList<IdeFunctionModel>>()

        collectKotlinFiles().forEach { ktFile ->
            analyze(ktFile) {
                collectFromFile(
                    file = ktFile,
                    rawConverters = rawConverters,
                    memberScopes = memberScopes,
                    topLevelScopes = topLevelScopes,
                )
            }
        }

        val converters = uniquifyConverterSuffixes(rawConverters)
        val generatedFunctions = buildList {
            memberScopes.forEach { scope ->
                addAll(buildCandidates(scope.originals, scope.existingJvmKeys, converters))
            }
            topLevelScopes.values.forEach { originals ->
                val existingJvmKeys = originals
                    .mapTo(linkedSetOf()) { function -> jvmSignatureKey(function, function.name) }
                addAll(buildCandidates(originals, existingJvmKeys, converters))
            }
        }

        return renderGeneratedFiles(generatedFunctions)
    }

    private fun collectKotlinFiles(): List<KtFile> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        return FilenameIndex.getAllFilesByExt(
            project,
            "kt",
            GlobalSearchScope.projectScope(project),
        ).asSequence()
            .filter { virtualFile -> fileIndex.isInSourceContent(virtualFile) }
            .filter(::mayContainTransformOverloadMarkers)
            .mapNotNull { virtualFile -> PsiManager.getInstance(project).findFile(virtualFile) as? KtFile }
            .toList()
    }

    private fun mayContainTransformOverloadMarkers(virtualFile: VirtualFile): Boolean {
        val text = try {
            String(virtualFile.contentsToByteArray(false))
        } catch (_: Exception) {
            return true
        }
        return text.contains("GenerateTransformOverloads") ||
            text.contains("OverloadTransform") ||
            text.contains("TransformProvider")
    }

    private fun KaSession.collectFromFile(
        file: KtFile,
        rawConverters: MutableList<IdeRawConverterSpec>,
        memberScopes: MutableList<MemberTargetScope>,
        topLevelScopes: MutableMap<String, MutableList<IdeFunctionModel>>,
    ) {
        file.declarations.forEach { declaration ->
            when (declaration) {
                is KtNamedFunction -> {
                    val symbol = declaration.symbol as? KaNamedFunctionSymbol ?: return@forEach
                    if (hasAnnotation(symbol, TransformOverloadIdeaConstants.overloadTransformAnnotation)) {
                        createRawConverterSpec(
                            function = declaration,
                            symbol = symbol,
                            ownerClass = null,
                        )?.let(rawConverters::add)
                    }
                    if (hasAnnotation(symbol, TransformOverloadIdeaConstants.generateTransformOverloadsAnnotation)) {
                        createFunctionModel(
                            function = declaration,
                            symbol = symbol,
                            ownerClass = null,
                        )?.let { functionModel ->
                            topLevelScopes.getOrPut(functionModel.packageName) { mutableListOf() } += functionModel
                        }
                    }
                }

                is KtClassOrObject -> collectFromClass(
                    declaration,
                    rawConverters,
                    memberScopes,
                )
            }
        }
    }

    private fun KaSession.collectFromClass(
        klass: KtClassOrObject,
        rawConverters: MutableList<IdeRawConverterSpec>,
        memberScopes: MutableList<MemberTargetScope>,
    ) {
        val classSymbol = klass.symbol as? KaNamedClassSymbol ?: return
        if (classSymbol.isInner) {
            logger.warn(
                "Skipping inner class ${classSymbol.requireClassId().asSingleFqName().asString()} " +
                    "for transform-overload IDEA stubs",
            )
            return
        }

        val processWholeClass = hasAnnotation(
            classSymbol,
            TransformOverloadIdeaConstants.generateTransformOverloadsAnnotation,
        )
        val originals = mutableListOf<IdeFunctionModel>()
        val existingJvmKeys = linkedSetOf<String>()

        klass.declarations.forEach { declaration ->
            when (declaration) {
                is KtNamedFunction -> {
                    val functionSymbol = declaration.symbol as? KaNamedFunctionSymbol ?: return@forEach
                    val functionCallableId = functionSymbol.requireCallableId()
                    existingJvmKeys += jvmSignatureKey(
                        function = createJvmSignatureFunctionModel(declaration, functionSymbol, classSymbol),
                        name = functionCallableId.callableName.asString(),
                    )

                    if (hasAnnotation(functionSymbol, TransformOverloadIdeaConstants.overloadTransformAnnotation)) {
                        createRawConverterSpec(
                            function = declaration,
                            symbol = functionSymbol,
                            ownerClass = classSymbol,
                        )?.let(rawConverters::add)
                    }

                    if (
                        processWholeClass ||
                        hasAnnotation(
                            functionSymbol,
                            TransformOverloadIdeaConstants.generateTransformOverloadsAnnotation,
                        )
                    ) {
                        createFunctionModel(
                            function = declaration,
                            symbol = functionSymbol,
                            ownerClass = classSymbol,
                        )?.let(originals::add)
                    }
                }

                is KtClassOrObject -> collectFromClass(
                    declaration,
                    rawConverters,
                    memberScopes,
                )
            }
        }

        if (originals.isNotEmpty()) {
            memberScopes += MemberTargetScope(
                originals = originals,
                existingJvmKeys = existingJvmKeys,
            )
        }
    }

    private fun KaSession.createRawConverterSpec(
        function: KtNamedFunction,
        symbol: KaNamedFunctionSymbol,
        ownerClass: KaNamedClassSymbol?,
    ): IdeRawConverterSpec? {
        val callableId = symbol.requireCallableId()
        val debugName = callableId.asString()
        if (symbol.contextParameters.isNotEmpty()) {
            invalidConverter(debugName, "context parameters are not supported")
            return null
        }
        if (function.bodyExpression == null) {
            invalidConverter(debugName, "converter body is required")
            return null
        }

        val hasExtensionReceiver = symbol.receiverParameter != null
        val valueParameterCount = symbol.valueParameters.size
        if (hasExtensionReceiver && valueParameterCount > 0) {
            invalidConverter(debugName, "extension converter cannot declare value parameters")
            return null
        }
        if (!hasExtensionReceiver && valueParameterCount != 1) {
            invalidConverter(debugName, "converter must declare exactly one source parameter")
            return null
        }

        if (ownerClass != null) {
            if (!implementsTransformProvider(ownerClass)) {
                invalidConverter(
                    debugName,
                    "member converter must be declared inside TransformProvider",
                )
                return null
            }
            if (ownerClass.typeParameters.isNotEmpty()) {
                invalidConverter(
                    debugName,
                    "generic TransformProvider containers are not supported in v1",
                )
                return null
            }
        }

        val typeParameterMap = symbol.typeParameters.mapIndexed { index, typeParameter ->
            typeParameter to createTypeParameterModel(
                typeParameter,
                ownerId = "converter:${debugName}:$index",
            )
        }.toMap()

        val sourceType = if (hasExtensionReceiver) {
            symbol.receiverParameter!!.returnType
        } else {
            symbol.valueParameters.single().returnType
        }

        return IdeRawConverterSpec(
            parameterKind = if (hasExtensionReceiver) {
                ConverterParameterKind.EXTENSION_RECEIVER
            } else {
                ConverterParameterKind.VALUE_PARAMETER
            },
            sourceType = toIdeType(sourceType, typeParameterMap),
            targetType = toIdeType(symbol.returnType, typeParameterMap),
            defaultSuffix = callableId.callableName.asString().toPascalCase(),
            containerPrefix = ownerClass?.requireClassId()?.shortClassName?.asString() ?: "TopLevel",
            debugName = debugName,
        )
    }

    private fun KaSession.createFunctionModel(
        function: KtNamedFunction,
        symbol: KaNamedFunctionSymbol,
        ownerClass: KaNamedClassSymbol?,
    ): IdeFunctionModel? {
        if (!isSupportedOriginalFunction(function, symbol, ownerClass)) {
            return null
        }

        val ownerClassIdText = ownerClass?.requireClassId()?.asSingleFqName()?.asString()
        val callableId = symbol.requireCallableId()

        val ownerTypeParameters = ownerClass.orEmptyTypeParameters().mapIndexed { index, typeParameter ->
            createTypeParameterModel(
                typeParameter,
                ownerId = "owner:$ownerClassIdText:$index",
            )
        }
        val functionTypeParameters = symbol.typeParameters.mapIndexed { index, typeParameter ->
            createTypeParameterModel(
                typeParameter,
                ownerId = "function:${callableId.asString()}:$index",
            )
        }

        val typeParameterMap = buildMap {
            ownerClass.orEmptyTypeParameters().zip(ownerTypeParameters).forEach { (symbolKey, model) ->
                put(symbolKey, model)
            }
            symbol.typeParameters.zip(functionTypeParameters).forEach { (symbolKey, model) ->
                put(symbolKey, model)
            }
        }

        val parameters = symbol.valueParameters.map { valueParameter ->
            IdeValueParameterModel(
                name = valueParameter.name.asString(),
                type = toIdeType(valueParameter.returnType, typeParameterMap),
                hasDefaultValue = valueParameter.hasDefaultValue,
                isVararg = valueParameter.isVararg,
            )
        }

        return IdeFunctionModel(
            packageName = callableId.packageName.asString(),
            ownerClassFqName = ownerClassIdText,
            ownerTypeParameters = ownerTypeParameters,
            name = callableId.callableName.asString(),
            typeParameters = functionTypeParameters,
            parameters = parameters,
            returnType = toIdeType(symbol.returnType, typeParameterMap),
            visibilityKeyword = if (function.hasModifier(KtTokens.INTERNAL_KEYWORD)) "internal" else null,
            isSuspend = symbol.isSuspend,
            isOperator = symbol.isOperator,
            isInfix = symbol.isInfix,
        )
    }

    private fun KaSession.createJvmSignatureFunctionModel(
        function: KtNamedFunction,
        symbol: KaNamedFunctionSymbol,
        ownerClass: KaNamedClassSymbol,
    ): IdeFunctionModel {
        val callableId = symbol.requireCallableId()
        val ownerClassId = ownerClass.requireClassId()
        return createFunctionModel(function, symbol, ownerClass)
            ?: IdeFunctionModel(
                packageName = callableId.packageName.asString(),
                ownerClassFqName = ownerClassId.asSingleFqName().asString(),
                ownerTypeParameters = emptyList(),
                name = callableId.callableName.asString(),
                typeParameters = emptyList(),
                parameters = symbol.valueParameters.map { parameter ->
                    IdeValueParameterModel(
                        name = parameter.name.asString(),
                        type = IdeOpaqueTypeModel(
                            renderedText = renderType(parameter.returnType),
                            erasureKey = renderType(parameter.returnType).substringBefore("<").substringBefore("?"),
                        ),
                        hasDefaultValue = parameter.hasDefaultValue,
                        isVararg = parameter.isVararg,
                    )
                },
                returnType = IdeOpaqueTypeModel(
                    renderedText = renderType(symbol.returnType),
                    erasureKey = renderType(symbol.returnType).substringBefore("<").substringBefore("?"),
                ),
                visibilityKeyword = null,
                isSuspend = false,
                isOperator = false,
                isInfix = false,
            )
    }

    private fun KaSession.toIdeType(
        type: KaType,
        typeParameters: Map<KaTypeParameterSymbol, IdeTypeParameter>,
    ): IdeTypeModel {
        val rendered = renderType(type)
        return when (type) {
            is KaClassType -> IdeClassTypeModel(
                classId = type.classId.asSingleFqName().asString(),
                arguments = type.typeArguments.map { projection ->
                    when (projection) {
                        is KaStarTypeProjection -> IdeTypeArgumentModel(
                            variance = null,
                            type = null,
                        )

                        is KaTypeArgumentWithVariance -> IdeTypeArgumentModel(
                            variance = projection.variance.toIdeVariance(),
                            type = toIdeType(projection.type, typeParameters),
                        )
                    }
                },
                nullable = type.isMarkedNullable,
                renderedText = rendered,
            )

            is KaTypeParameterType -> {
                val model = typeParameters[type.symbol]
                if (model != null) {
                    IdeTypeParameterTypeModel(
                        typeParameterId = model.id,
                        name = model.name,
                        nullable = type.isMarkedNullable,
                        renderedText = rendered,
                    )
                } else {
                    IdeOpaqueTypeModel(
                        renderedText = rendered,
                        erasureKey = rendered.substringBefore("<").substringBefore("?"),
                    )
                }
            }

            else -> IdeOpaqueTypeModel(
                renderedText = rendered,
                erasureKey = rendered.substringBefore("<").substringBefore("?"),
            )
        }
    }

    private fun KaSession.renderType(type: KaType): String {
        return type.render(
            KaTypeRendererForSource.WITH_QUALIFIED_NAMES_WITHOUT_PARAMETER_NAMES,
            Variance.INVARIANT,
        )
    }

    private fun KaSession.createTypeParameterModel(
        typeParameter: KaTypeParameterSymbol,
        ownerId: String,
    ): IdeTypeParameter {
        val renderedBounds = typeParameter.upperBounds
            .map { upperBound -> renderType(upperBound) }
            .filterNot { bound -> bound == "kotlin.Any?" || bound == "Any?" }
        val declarationText = renderedBounds.firstOrNull()?.let { bound ->
            "${typeParameter.name.asString()} : $bound"
        } ?: typeParameter.name.asString()
        return IdeTypeParameter(
            id = ownerId,
            name = typeParameter.name.asString(),
            declarationText = declarationText,
        )
    }

    private fun isSupportedOriginalFunction(
        function: KtNamedFunction,
        symbol: KaNamedFunctionSymbol,
        ownerClass: KaNamedClassSymbol?,
    ): Boolean {
        if (symbol.receiverParameter != null) {
            return false
        }
        if (symbol.contextParameters.isNotEmpty()) {
            return false
        }
        if (hasAnnotation(symbol, TransformOverloadIdeaConstants.overloadTransformAnnotation)) {
            return false
        }
        if (ownerClass == null && function.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
            return false
        }
        if (ownerClass != null && (
                function.hasModifier(KtTokens.PRIVATE_KEYWORD) ||
                    function.hasModifier(KtTokens.PROTECTED_KEYWORD)
                )
        ) {
            return false
        }
        return true
    }

    private fun KaSession.implementsTransformProvider(
        classSymbol: KaClassSymbol,
        visited: MutableSet<String> = linkedSetOf(),
    ): Boolean {
        val classIdText = classSymbol.requireClassId().asSingleFqName().asString()
        if (!visited.add(classIdText)) {
            return false
        }
        if (classIdText == TransformOverloadIdeaConstants.transformProvider) {
            return true
        }
        return classSymbol.superTypes.any { superType ->
            val classType = superType as? KaClassType ?: return@any false
            val superClassId = classType.classId
            val superClassIdText = superClassId.asSingleFqName().asString()
            if (superClassIdText == TransformOverloadIdeaConstants.transformProvider) {
                return@any true
            }
            val superClassSymbol = classType.symbol as? KaClassSymbol ?: return@any false
            implementsTransformProvider(superClassSymbol, visited)
        }
    }

    private fun hasAnnotation(
        symbol: KaAnnotatedSymbol,
        fqName: String,
    ): Boolean {
        return symbol.annotations.any { annotation ->
            annotation.classId?.asSingleFqName()?.asString() == fqName
        }
    }

    private fun uniquifyConverterSuffixes(
        rawConverters: List<IdeRawConverterSpec>,
    ): List<IdeConverterSpec> {
        val suffixUsage = rawConverters.groupBy { raw -> raw.defaultSuffix }
        return rawConverters.map { raw ->
            val uniqueSuffix = if ((suffixUsage[raw.defaultSuffix]?.size ?: 0) > 1) {
                raw.containerPrefix + raw.defaultSuffix
            } else {
                raw.defaultSuffix
            }
            IdeConverterSpec(
                parameterKind = raw.parameterKind,
                sourceType = raw.sourceType,
                targetType = raw.targetType,
                uniqueSuffix = uniqueSuffix,
                debugName = raw.debugName,
            )
        }
    }

    private fun buildCandidates(
        originals: List<IdeFunctionModel>,
        existingJvmKeys: Set<String>,
        converters: List<IdeConverterSpec>,
    ): List<IdeOverloadCandidate> {
        if (originals.isEmpty() || converters.isEmpty()) {
            return emptyList()
        }
        val occupiedJvmKeys = linkedSetOf<String>().apply {
            addAll(existingJvmKeys)
            originals.forEach { original ->
                add(jvmSignatureKey(original, original.name))
            }
        }
        val generatedCandidates = mutableListOf<IdeOverloadCandidate>()
        for (original in originals) {
            val parameterOptions = original.parameters.mapIndexed { index, parameter ->
                if (parameter.isVararg) {
                    emptyList()
                } else {
                    findParameterTransforms(index, parameter.type, converters)
                }
            }
            val rawCandidates = enumerateCandidates(original, parameterOptions)
            for (candidate in rawCandidates) {
                val sameNameKey = jvmSignatureKey(
                    function = candidate.original,
                    name = candidate.generatedName,
                    parameterTransforms = candidate.parameterTransforms,
                )
                val mustRenameTopLevel =
                    candidate.original.isTopLevel && candidate.generatedName == candidate.original.name
                if (mustRenameTopLevel || sameNameKey in occupiedJvmKeys) {
                    val renamed = candidate.copy(
                        generatedName = buildRenamedFunctionName(
                            originalName = candidate.original.name,
                            transforms = candidate.parameterTransforms,
                        ),
                    )
                    val renamedKey = jvmSignatureKey(
                        function = renamed.original,
                        name = renamed.generatedName,
                        parameterTransforms = renamed.parameterTransforms,
                    )
                    if (renamedKey in occupiedJvmKeys) {
                        logger.warn(
                            "Transform overload rename conflict for ${candidate.original.packageName}.${candidate.original.name} " +
                                "-> ${renamed.generatedName}",
                        )
                        continue
                    }
                    occupiedJvmKeys += renamedKey
                    generatedCandidates += renamed
                } else {
                    occupiedJvmKeys += sameNameKey
                    generatedCandidates += candidate
                }
            }
        }
        return generatedCandidates
    }

    private fun findParameterTransforms(
        parameterIndex: Int,
        parameterType: IdeTypeModel,
        converters: List<IdeConverterSpec>,
    ): List<IdeParameterTransform> {
        return converters.mapNotNull { converter ->
            createParameterTransform(parameterIndex, parameterType, converter)
        }
    }

    private fun createParameterTransform(
        parameterIndex: Int,
        parameterType: IdeTypeModel,
        converter: IdeConverterSpec,
    ): IdeParameterTransform? {
        val liftKind = parameterType.toLiftKind()
        if (parameterType is IdeClassTypeModel && liftKind != null) {
            val argument = parameterType.arguments.singleOrNull()?.type
            if (argument != null) {
                val liftedBindings = matchTypePattern(converter.targetType, argument)
                if (liftedBindings != null) {
                    val liftedArgument = converter.sourceType.substitute(liftedBindings)
                    val liftedType = parameterType.copy(
                        arguments = listOf(
                            IdeTypeArgumentModel(
                                variance = IdeVariance.INVARIANT,
                                type = liftedArgument,
                            ),
                        ),
                    )
                    return IdeParameterTransform(
                        converter = converter,
                        parameterIndex = parameterIndex,
                        generatedParameterType = liftedType,
                        liftKind = liftKind,
                    )
                }
            }
        }

        val directBindings = matchTypePattern(converter.targetType, parameterType) ?: return null
        return IdeParameterTransform(
            converter = converter,
            parameterIndex = parameterIndex,
            generatedParameterType = converter.sourceType.substitute(directBindings),
            liftKind = LiftKind.NONE,
        )
    }

    private fun enumerateCandidates(
        original: IdeFunctionModel,
        parameterOptions: List<List<IdeParameterTransform>>,
    ): List<IdeOverloadCandidate> {
        if (parameterOptions.all(List<IdeParameterTransform>::isEmpty)) {
            return emptyList()
        }
        val results = mutableListOf<IdeOverloadCandidate>()
        fun walk(index: Int, chosen: MutableList<IdeParameterTransform>) {
            if (index == parameterOptions.size) {
                if (chosen.isNotEmpty()) {
                    results += IdeOverloadCandidate(
                        original = original,
                        generatedName = original.name,
                        parameterTransforms = chosen.sortedBy(IdeParameterTransform::parameterIndex),
                    )
                }
                return
            }
            walk(index + 1, chosen)
            parameterOptions[index].forEach { transform ->
                chosen += transform
                walk(index + 1, chosen)
                chosen.removeAt(chosen.lastIndex)
            }
        }
        walk(0, mutableListOf())
        return results
    }

    private fun jvmSignatureKey(
        function: IdeFunctionModel,
        name: String,
        parameterTransforms: List<IdeParameterTransform> = emptyList(),
    ): String {
        val transformedByIndex = parameterTransforms.associateBy(IdeParameterTransform::parameterIndex)
        return buildString {
            append(name)
            append("|")
            function.parameters.forEachIndexed { index, parameter ->
                val type = transformedByIndex[index]?.generatedParameterType ?: parameter.type
                append(type.jvmErasure())
                append(";")
            }
        }
    }

    private fun renderGeneratedFiles(
        candidates: List<IdeOverloadCandidate>,
    ): List<IdeGeneratedFile> {
        if (candidates.isEmpty()) {
            return emptyList()
        }
        return candidates.groupBy { candidate -> candidate.original.packageName }
            .map { (packageName, packageCandidates) ->
                val relativePath = buildString {
                    if (packageName.isNotBlank()) {
                        append(packageName.replace('.', '/'))
                        append("/")
                    }
                    append(TransformOverloadIdeaConstants.stubFileName)
                }
                IdeGeneratedFile(
                    relativePath = relativePath,
                    packageName = packageName,
                    topLevelCallableNames = packageCandidates
                        .mapTo(linkedSetOf()) { candidate -> candidate.generatedName },
                    topLevelClassifierNames = emptySet(),
                    content = buildString {
                        appendLine("@file:Suppress(\"unused\")")
                        if (packageName.isNotBlank()) {
                            appendLine("package $packageName")
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

    private fun renderFunction(candidate: IdeOverloadCandidate): String {
        val transformedByIndex = candidate.parameterTransforms.associateBy(IdeParameterTransform::parameterIndex)
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
                function.parameters.mapIndexed { index, parameter ->
                    val transform = transformedByIndex[index]
                    val pieces = mutableListOf<String>()
                    if (parameter.isVararg) {
                        pieces += "vararg"
                    }
                    val parameterType = transform?.generatedParameterType?.renderTypeText()
                        ?: parameter.type.renderTypeText()
                    pieces += "${parameter.name}: $parameterType"
                    if (transform == null && parameter.hasDefaultValue) {
                        pieces += "= kotlin.TODO()"
                    }
                    pieces.joinToString(separator = " ")
                }.joinToString(separator = ", "),
            )
            append("): ")
            append(function.returnType.renderTypeText())
            append(" = kotlin.error(\"")
            append(TransformOverloadIdeaConstants.stubErrorMessage)
            append("\")")
        }
    }

    private fun invalidConverter(
        debugName: String,
        reason: String,
    ) {
        logger.warn("Invalid @OverloadTransform converter $debugName: $reason")
    }

    private fun CallableId.asString(): String {
        return if (classId != null) {
            classId!!.asSingleFqName().asString() + "." + callableName.asString()
        } else {
            packageName.asString() + "." + callableName.asString()
        }
    }

    private fun Variance.toIdeVariance(): IdeVariance {
        return when (this) {
            Variance.IN_VARIANCE -> IdeVariance.IN
            Variance.OUT_VARIANCE -> IdeVariance.OUT
            else -> IdeVariance.INVARIANT
        }
    }

    private fun KaNamedClassSymbol?.orEmptyTypeParameters(): List<KaTypeParameterSymbol> {
        return this?.typeParameters.orEmpty()
    }

    private fun KaNamedFunctionSymbol.requireCallableId(): CallableId {
        return callableId ?: error("Local functions are not supported for transform-overload IDEA stubs")
    }

    private fun KaClassSymbol.requireClassId() =
        classId ?: error("Anonymous classes are not supported for transform-overload IDEA stubs")

    private data class MemberTargetScope(
        val originals: List<IdeFunctionModel>,
        val existingJvmKeys: Set<String>,
    )
}
