package site.addzero.kcp.spreadpack.ide

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.types.Variance

@OptIn(KaExperimentalApi::class)
internal class SpreadPackStubGenerator(
    private val project: Project,
) {

    private val logger = Logger.getInstance(SpreadPackStubGenerator::class.java)

    fun generate(): List<IdeGeneratedFile> {
        val topLevelScopes = linkedMapOf<String, MutableList<IdeFunctionModel>>()
        val memberScopes = mutableListOf<MemberTargetScope>()
        val functionsByCallableFqName = linkedMapOf<String, MutableList<IdeFunctionModel>>()
        val annotatedCarrierSeeds = linkedMapOf<String, IdeAnnotatedCarrierSeed>()

        collectKotlinFiles().forEach { ktFile ->
            analyze(ktFile) {
                collectFromFile(
                    file = ktFile,
                    topLevelScopes = topLevelScopes,
                    memberScopes = memberScopes,
                    functionsByCallableFqName = functionsByCallableFqName,
                    annotatedCarrierSeeds = annotatedCarrierSeeds,
                )
            }
        }

        val generatedCarrierStubs = buildList {
            annotatedCarrierSeeds.values.forEach { seed ->
                try {
                    add(resolveAnnotatedCarrierStub(seed, functionsByCallableFqName))
                } catch (error: InvalidSpreadPackTargetException) {
                    logger.warn(error.message)
                }
            }
        }
        val carrierFieldsByClassId = generatedCarrierStubs.associate { stub ->
            stub.classId to stub.fields
        }
        val resolvedFunctionsByCallableFqName = functionsByCallableFqName.mapValues { (_, originals) ->
            originals.map { function ->
                function.withResolvedCarrierFields(carrierFieldsByClassId)
            }
        }
        val resolvedTopLevelScopes = topLevelScopes.mapValues { (_, originals) ->
            originals.map { function ->
                function.withResolvedCarrierFields(carrierFieldsByClassId)
            }
        }
        val resolvedMemberScopes = memberScopes.map { scope ->
            scope.copy(
                originals = scope.originals.map { function ->
                    function.withResolvedCarrierFields(carrierFieldsByClassId)
                },
            )
        }

        val generatedFunctions = buildList {
            resolvedTopLevelScopes.values.forEach { originals ->
                addAll(
                    buildCandidates(
                        originals = originals,
                        existingJvmKeys = originals.mapTo(linkedSetOf()) { function ->
                            jvmSignatureKey(function.name, function.parameters.map { parameter -> parameter.type })
                        },
                        functionsByCallableFqName = resolvedFunctionsByCallableFqName,
                    ),
                )
            }
            resolvedMemberScopes.forEach { scope ->
                addAll(
                    buildCandidates(
                        originals = scope.originals,
                        existingJvmKeys = scope.existingJvmKeys,
                        functionsByCallableFqName = resolvedFunctionsByCallableFqName,
                    ),
                )
            }
        }

        return SpreadPackStubRenderer.renderGeneratedFiles(
            candidates = generatedFunctions,
            carrierStubs = generatedCarrierStubs,
        )
    }

    private fun collectKotlinFiles(): List<KtFile> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        return FilenameIndex.getAllFilesByExt(
            project,
            "kt",
            GlobalSearchScope.projectScope(project),
        ).asSequence()
            .filter { virtualFile -> fileIndex.isInSourceContent(virtualFile) }
            .mapNotNull { virtualFile -> PsiManager.getInstance(project).findFile(virtualFile) as? KtFile }
            .toList()
    }

    private fun KaSession.collectFromFile(
        file: KtFile,
        topLevelScopes: MutableMap<String, MutableList<IdeFunctionModel>>,
        memberScopes: MutableList<MemberTargetScope>,
        functionsByCallableFqName: MutableMap<String, MutableList<IdeFunctionModel>>,
        annotatedCarrierSeeds: MutableMap<String, IdeAnnotatedCarrierSeed>,
    ) {
        file.declarations.forEach { declaration ->
            when (declaration) {
                is KtNamedFunction -> {
                    val symbol = declaration.symbol as? KaNamedFunctionSymbol ?: return@forEach
                    val functionModel = createFunctionModel(
                        function = declaration,
                        symbol = symbol,
                        ownerClass = null,
                    ) ?: return@forEach
                    functionsByCallableFqName.getOrPut(functionModel.callableFqName) { mutableListOf() } += functionModel
                    if (
                        hasAnnotation(symbol, SpreadPackIdeaConstants.generateSpreadPackOverloadsAnnotation) &&
                        functionModel.hasSpreadPackParameters
                    ) {
                        topLevelScopes.getOrPut(functionModel.packageName) { mutableListOf() } += functionModel
                    }
                }

                is KtClassOrObject -> collectFromClass(
                    klass = declaration,
                    memberScopes = memberScopes,
                    functionsByCallableFqName = functionsByCallableFqName,
                    annotatedCarrierSeeds = annotatedCarrierSeeds,
                )
            }
        }
    }

    private fun KaSession.collectFromClass(
        klass: KtClassOrObject,
        memberScopes: MutableList<MemberTargetScope>,
        functionsByCallableFqName: MutableMap<String, MutableList<IdeFunctionModel>>,
        annotatedCarrierSeeds: MutableMap<String, IdeAnnotatedCarrierSeed>,
    ) {
        val classSymbol = klass.symbol as? KaNamedClassSymbol ?: return
        if (classSymbol.isInner) {
            logger.warn(
                "Skipping inner class ${classSymbol.requireClassId().asSingleFqName().asString()} " +
                    "for spread-pack IDEA stubs",
            )
            return
        }
        collectAnnotatedCarrierSeed(
            klass = klass,
            classSymbol = classSymbol,
            annotatedCarrierSeeds = annotatedCarrierSeeds,
        )

        val processWholeClass = hasAnnotation(
            classSymbol,
            SpreadPackIdeaConstants.generateSpreadPackOverloadsAnnotation,
        )
        val originals = mutableListOf<IdeFunctionModel>()
        val existingJvmKeys = linkedSetOf<String>()

        klass.declarations.forEach { declaration ->
            when (declaration) {
                is KtNamedFunction -> {
                    val functionSymbol = declaration.symbol as? KaNamedFunctionSymbol ?: return@forEach
                    val functionModel = createFunctionModel(
                        function = declaration,
                        symbol = functionSymbol,
                        ownerClass = classSymbol,
                    ) ?: return@forEach
                    functionsByCallableFqName.getOrPut(functionModel.callableFqName) { mutableListOf() } += functionModel
                    existingJvmKeys += jvmSignatureKey(
                        functionModel.name,
                        functionModel.parameters.map { parameter -> parameter.type },
                    )
                    if (
                        (processWholeClass ||
                            hasAnnotation(functionSymbol, SpreadPackIdeaConstants.generateSpreadPackOverloadsAnnotation)) &&
                        functionModel.hasSpreadPackParameters
                    ) {
                        originals += functionModel
                    }
                }

                is KtClassOrObject -> collectFromClass(
                    klass = declaration,
                    memberScopes = memberScopes,
                    functionsByCallableFqName = functionsByCallableFqName,
                    annotatedCarrierSeeds = annotatedCarrierSeeds,
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

    private fun KaSession.createFunctionModel(
        function: KtNamedFunction,
        symbol: KaNamedFunctionSymbol,
        ownerClass: KaNamedClassSymbol?,
    ): IdeFunctionModel? {
        if (!isSupportedFunction(function, symbol, ownerClass)) {
            return null
        }

        val callableId = symbol.requireCallableId()
        val ownerClassFqName = ownerClass?.requireClassId()?.asSingleFqName()?.asString()
        val ownerTypeParameters = ownerClass.orEmptyTypeParameters().mapIndexed { index, typeParameter ->
            createTypeParameterModel(
                typeParameter = typeParameter,
                ownerId = "owner:${ownerClassFqName.orEmpty()}:$index",
            )
        }
        val functionTypeParameters = symbol.typeParameters.mapIndexed { index, typeParameter ->
            createTypeParameterModel(
                typeParameter = typeParameter,
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

        val parameters = function.valueParameters.zip(symbol.valueParameters).map { (parameterPsi, parameterSymbol) ->
            IdeValueParameterModel(
                name = parameterSymbol.name.asString(),
                type = toIdeType(parameterSymbol.returnType, typeParameterMap),
                hasDefaultValue = parameterSymbol.hasDefaultValue,
                isVararg = parameterSymbol.isVararg,
                spreadPack = createSpreadPackParameterModel(parameterSymbol),
            )
        }

        return IdeFunctionModel(
            callableFqName = callableId.asString(),
            packageName = callableId.packageName.asString(),
            ownerClassFqName = ownerClassFqName,
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

    private fun KaSession.createSpreadPackParameterModel(
        symbol: org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol,
    ): IdeSpreadPackParameterModel? {
        val spreadPackAnnotation = findAnnotation(symbol, SpreadPackIdeaConstants.spreadPackAnnotation) ?: return null
        val classType = symbol.returnType as? KaClassType
            ?: return null
        if (classType.typeArguments.isNotEmpty()) {
            return null
        }
        val carrierClassSymbol = classType.symbol as? KaNamedClassSymbol
            ?: return null
        if (carrierClassSymbol.typeParameters.isNotEmpty()) {
            return null
        }
        val carrierClass = carrierClassSymbol.psi as? KtClass
            ?: return null
        if (carrierClass.isInterface() || carrierClass.isEnum() || carrierClass.isAnnotation()) {
            return null
        }
        val carrierAnnotation = findAnnotation(
            carrierClassSymbol,
            SpreadPackIdeaConstants.spreadPackCarrierOfAnnotation,
        )
        if (carrierAnnotation != null) {
            val spreadPackSelector = spreadPackAnnotation.selectorKind()
            val spreadPackExclude = spreadPackAnnotation.stringArrayArgument("exclude").toSet()
            val spreadArgsOfAnnotation = findAnnotation(symbol, SpreadPackIdeaConstants.spreadArgsOfAnnotation)
            return IdeSpreadPackParameterModel(
                carrierClassId = carrierClassSymbol.requireClassId().asSingleFqName().asString(),
                carrierClassShortName = carrierClassSymbol.requireClassId().shortClassName.asString(),
                carrierFields = emptyList(),
                selectorKind = spreadArgsOfAnnotation?.selectorKind() ?: spreadPackSelector,
                excludedNames = spreadArgsOfAnnotation?.stringArrayArgument("exclude")?.toSet() ?: spreadPackExclude,
                reference = spreadArgsOfAnnotation?.spreadArgsReference(),
                spreadPackAtDefault = spreadPackSelector == SelectorKind.PROPS && spreadPackExclude.isEmpty(),
            )
        }
        val primaryConstructor = carrierClass.primaryConstructor
            ?: return null
        val carrierFields = primaryConstructor.valueParameters.mapNotNull { constructorParameter ->
            createCarrierFieldModel(constructorParameter)
        }

        val spreadPackSelector = spreadPackAnnotation.selectorKind()
        val spreadPackExclude = spreadPackAnnotation.stringArrayArgument("exclude").toSet()
        val spreadArgsOfAnnotation = findAnnotation(symbol, SpreadPackIdeaConstants.spreadArgsOfAnnotation)

        return IdeSpreadPackParameterModel(
            carrierClassId = carrierClassSymbol.requireClassId().asSingleFqName().asString(),
            carrierClassShortName = carrierClassSymbol.requireClassId().shortClassName.asString(),
            carrierFields = carrierFields,
            selectorKind = spreadArgsOfAnnotation?.selectorKind() ?: spreadPackSelector,
            excludedNames = spreadArgsOfAnnotation?.stringArrayArgument("exclude")?.toSet() ?: spreadPackExclude,
            reference = spreadArgsOfAnnotation?.spreadArgsReference(),
            spreadPackAtDefault = spreadPackSelector == SelectorKind.PROPS && spreadPackExclude.isEmpty(),
        )
    }

    private fun KaSession.createCarrierFieldModel(
        parameter: KtParameter,
    ): IdeCarrierFieldModel? {
        val parameterSymbol = parameter.symbol
        return IdeCarrierFieldModel(
            name = parameterSymbol.name.asString(),
            type = toIdeType(parameterSymbol.returnType, emptyMap()),
            hasDefaultValue = parameter.hasDefaultValue(),
        )
    }

    private fun KaSession.collectAnnotatedCarrierSeed(
        klass: KtClassOrObject,
        classSymbol: KaNamedClassSymbol,
        annotatedCarrierSeeds: MutableMap<String, IdeAnnotatedCarrierSeed>,
    ) {
        if (klass !is KtClass) {
            return
        }
        val annotation = findAnnotation(
            classSymbol,
            SpreadPackIdeaConstants.spreadPackCarrierOfAnnotation,
        ) ?: return
        val classId = classSymbol.requireClassId().asSingleFqName().asString()
        annotatedCarrierSeeds[classId] = IdeAnnotatedCarrierSeed(
            packageName = classSymbol.requireClassId().packageFqName.asString(),
            classId = classId,
            classShortName = classSymbol.requireClassId().shortClassName.asString(),
            reference = annotation.spreadArgsReference()
                ?: throw InvalidSpreadPackTargetException(
                    "Invalid @SpreadPackCarrierOf target $classId: must specify target function",
                ),
            selectorKind = annotation.selectorKind(),
            excludedNames = annotation.stringArrayArgument("exclude").toSet(),
        )
    }

    private fun resolveAnnotatedCarrierStub(
        seed: IdeAnnotatedCarrierSeed,
        functionsByCallableFqName: Map<String, List<IdeFunctionModel>>,
    ): IdeGeneratedCarrierStub {
        val validationOwner = seed.validationOwner()
        val referencedOverload = resolveReferencedOverload(
            owner = validationOwner,
            reference = seed.reference,
            functionsByCallableFqName = functionsByCallableFqName,
        )
        val overloadKey = overloadKey(referencedOverload)
        val flattenedFields = flattenFunctionParameters(
            owner = validationOwner,
            function = referencedOverload,
            functionsByCallableFqName = functionsByCallableFqName,
            visitedOverloads = linkedSetOf(overloadKey),
        )
        return IdeGeneratedCarrierStub(
            packageName = seed.packageName,
            classId = seed.classId,
            classShortName = seed.classShortName,
            fields = selectFlattenedFields(
                owner = validationOwner,
                fields = flattenedFields,
                selectorKind = seed.selectorKind,
                excludedNames = seed.excludedNames,
                contextLabel = referencedOverload.callableFqName,
            ),
        )
    }

    private fun buildCandidates(
        originals: List<IdeFunctionModel>,
        existingJvmKeys: Set<String>,
        functionsByCallableFqName: Map<String, List<IdeFunctionModel>>,
    ): List<IdeGeneratedOverloadCandidate> {
        val occupiedJvmKeys = linkedSetOf<String>().apply {
            addAll(existingJvmKeys)
        }
        val generatedCandidates = mutableListOf<IdeGeneratedOverloadCandidate>()
        for (original in originals) {
            val rawCandidate = try {
                buildCandidate(original, functionsByCallableFqName)
            } catch (error: InvalidSpreadPackTargetException) {
                logger.warn(error.message)
                null
            } ?: continue
            val sameNameKey = jvmSignatureKey(
                rawCandidate.generatedName,
                rawCandidate.generatedParameters.map { parameter -> parameter.type },
            )
            if (sameNameKey in occupiedJvmKeys) {
                val renamed = rawCandidate.copy(
                    generatedName = buildRenamedFunctionName(rawCandidate.original, rawCandidate.expansions),
                )
                val renamedKey = jvmSignatureKey(
                    renamed.generatedName,
                    renamed.generatedParameters.map { parameter -> parameter.type },
                )
                if (renamedKey in occupiedJvmKeys) {
                    logger.warn(
                        "Spread pack rename conflict for ${original.callableFqName} -> ${renamed.generatedName}",
                    )
                    continue
                }
                occupiedJvmKeys += renamedKey
                generatedCandidates += renamed
            } else {
                occupiedJvmKeys += sameNameKey
                generatedCandidates += rawCandidate
            }
        }
        return generatedCandidates
    }

    private fun buildCandidate(
        original: IdeFunctionModel,
        functionsByCallableFqName: Map<String, List<IdeFunctionModel>>,
    ): IdeGeneratedOverloadCandidate? {
        val expansions = original.parameters.mapIndexedNotNull { index, parameter ->
            createExpansion(
                owner = original,
                parameterIndex = index,
                parameter = parameter,
                functionsByCallableFqName = functionsByCallableFqName,
            )
        }
        if (expansions.isEmpty()) {
            return null
        }

        val expansionsByIndex = expansions.associateBy { expansion -> expansion.parameterIndex }
        val generatedParameters = mutableListOf<IdeValueParameterModel>()
        val generatedParameterNames = linkedSetOf<String>()

        original.parameters.forEachIndexed { index, parameter ->
            val expansion = expansionsByIndex[index]
            if (expansion == null) {
                requireUniqueName(original, generatedParameterNames, parameter.name)
                generatedParameters += parameter.copy(spreadPack = null)
            } else {
                expansion.fields.forEach { field ->
                    requireUniqueName(original, generatedParameterNames, field.name)
                    generatedParameters += IdeValueParameterModel(
                        name = field.name,
                        type = field.type,
                        hasDefaultValue = field.hasDefaultValue,
                        isVararg = false,
                    )
                }
            }
        }

        return IdeGeneratedOverloadCandidate(
            original = original,
            generatedName = original.name,
            generatedParameters = generatedParameters,
            expansions = expansions,
        )
    }

    private fun createExpansion(
        owner: IdeFunctionModel,
        parameterIndex: Int,
        parameter: IdeValueParameterModel,
        functionsByCallableFqName: Map<String, List<IdeFunctionModel>>,
    ): IdeSpreadPackExpansion? {
        val spreadPack = parameter.spreadPack ?: return null
        if (spreadPack.reference != null && !spreadPack.spreadPackAtDefault) {
            invalidTarget(
                owner,
                "parameter ${parameter.name} must keep @SpreadPack at default values when @SpreadArgsOf is present",
            )
        }
        val fields = if (spreadPack.reference == null) {
            buildCarrierFields(
                owner = owner,
                spreadPack = spreadPack,
                selectorKind = spreadPack.selectorKind,
                excludedNames = spreadPack.excludedNames,
            )
        } else {
            val referencedOverload = resolveReferencedOverload(
                owner = owner,
                reference = spreadPack.reference,
                functionsByCallableFqName = functionsByCallableFqName,
            )
            val overloadKey = overloadKey(referencedOverload)
            val flattenedFields = flattenFunctionParameters(
                owner = owner,
                function = referencedOverload,
                functionsByCallableFqName = functionsByCallableFqName,
                visitedOverloads = linkedSetOf(overloadKey),
            )
            buildReferencedCarrierFields(
                owner = owner,
                spreadPack = spreadPack,
                flattenedFields = flattenedFields,
                selectorKind = spreadPack.selectorKind,
                excludedNames = spreadPack.excludedNames,
                referenceDescription = referencedOverload.callableFqName,
            )
        }

        return IdeSpreadPackExpansion(
            parameterIndex = parameterIndex,
            carrierClassShortName = spreadPack.carrierClassShortName,
            selectorKind = spreadPack.selectorKind,
            fields = fields,
        )
    }

    private fun buildCarrierFields(
        owner: IdeFunctionModel,
        spreadPack: IdeSpreadPackParameterModel,
        selectorKind: SelectorKind,
        excludedNames: Set<String>,
    ): List<IdeCarrierFieldModel> {
        validateExcludedNames(
            owner = owner,
            excludedNames = excludedNames,
            availableNames = spreadPack.carrierFields.map { field -> field.name },
            contextLabel = spreadPack.carrierClassShortName,
        )
        val selectedFields = spreadPack.carrierFields.filter { field ->
            shouldIncludeField(field.type, selectorKind) && field.name !in excludedNames
        }
        validateCarrierOmissions(
            owner = owner,
            spreadPack = spreadPack,
            selectedCarrierNames = selectedFields.mapTo(linkedSetOf()) { field -> field.name },
        )
        return selectedFields
    }

    private fun buildReferencedCarrierFields(
        owner: IdeFunctionModel,
        spreadPack: IdeSpreadPackParameterModel,
        flattenedFields: List<IdeCarrierFieldModel>,
        selectorKind: SelectorKind,
        excludedNames: Set<String>,
        referenceDescription: String,
    ): List<IdeCarrierFieldModel> {
        val selectedFields = selectFlattenedFields(
            owner = owner,
            fields = flattenedFields,
            selectorKind = selectorKind,
            excludedNames = excludedNames,
            contextLabel = referenceDescription,
        )
        val carrierFieldsByName = spreadPack.carrierFields.associateBy { field -> field.name }
        val selectedCarrierNames = linkedSetOf<String>()
        val matchedFields = selectedFields.map { field ->
            val carrierField = carrierFieldsByName[field.name]
                ?: invalidTarget(
                    owner,
                    "spread-pack carrier ${spreadPack.carrierClassShortName} is missing argsof field ${field.name} " +
                        "from $referenceDescription",
                )
            if (carrierField.type.renderTypeText() != field.type.renderTypeText()) {
                invalidTarget(
                    owner,
                    "spread-pack carrier ${spreadPack.carrierClassShortName} field ${field.name} " +
                        "type ${carrierField.type.renderTypeText()} does not match " +
                        "$referenceDescription field type ${field.type.renderTypeText()}",
                )
            }
            selectedCarrierNames += field.name
            carrierField
        }
        validateCarrierOmissions(
            owner = owner,
            spreadPack = spreadPack,
            selectedCarrierNames = selectedCarrierNames,
        )
        validateUniqueFieldNames(
            owner = owner,
            names = matchedFields.map { field -> field.name },
            contextLabel = referenceDescription,
        )
        return matchedFields
    }

    private fun flattenFunctionParameters(
        owner: IdeFunctionModel,
        function: IdeFunctionModel,
        functionsByCallableFqName: Map<String, List<IdeFunctionModel>>,
        visitedOverloads: Set<String>,
    ): List<IdeCarrierFieldModel> {
        return function.parameters.flatMap { parameter ->
            flattenValueParameter(
                owner = owner,
                parameter = parameter,
                functionsByCallableFqName = functionsByCallableFqName,
                visitedOverloads = visitedOverloads,
            )
        }.also { fields ->
            validateUniqueFieldNames(
                owner = owner,
                names = fields.map { field -> field.name },
                contextLabel = function.callableFqName,
            )
        }
    }

    private fun flattenValueParameter(
        owner: IdeFunctionModel,
        parameter: IdeValueParameterModel,
        functionsByCallableFqName: Map<String, List<IdeFunctionModel>>,
        visitedOverloads: Set<String>,
    ): List<IdeCarrierFieldModel> {
        val spreadPack = parameter.spreadPack
            ?: return listOf(
                IdeCarrierFieldModel(
                    name = parameter.name,
                    type = parameter.type,
                    hasDefaultValue = parameter.hasDefaultValue,
                ),
            )
        if (spreadPack.reference != null && !spreadPack.spreadPackAtDefault) {
            invalidTarget(
                owner,
                "nested spread-pack parameter ${parameter.name} must keep @SpreadPack at default values when @SpreadArgsOf is present",
            )
        }
        if (spreadPack.reference == null) {
            return buildCarrierFields(
                owner = owner,
                spreadPack = spreadPack,
                selectorKind = spreadPack.selectorKind,
                excludedNames = spreadPack.excludedNames,
            )
        }

        val referencedOverload = resolveReferencedOverload(
            owner = owner,
            reference = spreadPack.reference,
            functionsByCallableFqName = functionsByCallableFqName,
        )
        val overloadKey = overloadKey(referencedOverload)
        if (overloadKey in visitedOverloads) {
            invalidTarget(
                owner,
                "detected argsof overload cycle at ${referencedOverload.callableFqName}",
            )
        }
        val flattenedFields = flattenFunctionParameters(
            owner = owner,
            function = referencedOverload,
            functionsByCallableFqName = functionsByCallableFqName,
            visitedOverloads = visitedOverloads + overloadKey,
        )
        return buildReferencedCarrierFields(
            owner = owner,
            spreadPack = spreadPack,
            flattenedFields = flattenedFields,
            selectorKind = spreadPack.selectorKind,
            excludedNames = spreadPack.excludedNames,
            referenceDescription = referencedOverload.callableFqName,
        )
    }

    private fun selectFlattenedFields(
        owner: IdeFunctionModel,
        fields: List<IdeCarrierFieldModel>,
        selectorKind: SelectorKind,
        excludedNames: Set<String>,
        contextLabel: String,
    ): List<IdeCarrierFieldModel> {
        validateExcludedNames(
            owner = owner,
            excludedNames = excludedNames,
            availableNames = fields.map { field -> field.name },
            contextLabel = contextLabel,
        )
        return fields.filter { field ->
            shouldIncludeField(field.type, selectorKind) && field.name !in excludedNames
        }
    }

    private fun validateExcludedNames(
        owner: IdeFunctionModel,
        excludedNames: Set<String>,
        availableNames: List<String>,
        contextLabel: String,
    ) {
        val unknownExcludedNames = excludedNames - availableNames.toSet()
        if (unknownExcludedNames.isNotEmpty()) {
            invalidTarget(
                owner,
                "unknown spread-pack exclude names for $contextLabel: ${unknownExcludedNames.sorted().joinToString()}",
            )
        }
    }

    private fun validateCarrierOmissions(
        owner: IdeFunctionModel,
        spreadPack: IdeSpreadPackParameterModel,
        selectedCarrierNames: Set<String>,
    ) {
        spreadPack.carrierFields.forEach { field ->
            if (field.name !in selectedCarrierNames && !field.hasDefaultValue) {
                invalidTarget(
                    owner,
                    "spread-pack carrier ${spreadPack.carrierClassShortName} cannot omit required field ${field.name}",
                )
            }
        }
    }

    private fun validateUniqueFieldNames(
        owner: IdeFunctionModel,
        names: List<String>,
        contextLabel: String,
    ) {
        val duplicates = names.groupingBy { it }.eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        if (duplicates.isNotEmpty()) {
            invalidTarget(
                owner,
                "duplicate flattened parameter names in $contextLabel: ${duplicates.joinToString()}",
            )
        }
    }

    private fun resolveReferencedOverload(
        owner: IdeFunctionModel,
        reference: IdeSpreadArgsReference,
        functionsByCallableFqName: Map<String, List<IdeFunctionModel>>,
    ): IdeFunctionModel {
        val overloads = functionsByCallableFqName[reference.functionFqName].orEmpty()
        if (overloads.isEmpty()) {
            invalidTarget(owner, "unable to resolve argsof overload set ${reference.functionFqName}")
        }
        if (reference.parameterTypes.isEmpty()) {
            if (overloads.size != 1) {
                invalidTarget(
                    owner,
                    "argsof overload set ${reference.functionFqName} is ambiguous; specify parameterTypes",
                )
            }
            return overloads.single()
        }
        val matches = overloads.filter { overload ->
            if (overload.parameters.size != reference.parameterTypes.size) {
                return@filter false
            }
            overload.parameters.zip(reference.parameterTypes).all { (actualParameter, expectedType) ->
                actualParameter.type.classIdOrNull() == expectedType.classId
            }
        }
        if (matches.size != 1) {
            invalidTarget(
                owner,
                "unable to select a unique argsof overload for ${reference.functionFqName} with parameterTypes=" +
                    reference.parameterTypes.joinToString { parameterType -> parameterType.classId },
            )
        }
        return matches.single()
    }

    private fun buildRenamedFunctionName(
        original: IdeFunctionModel,
        expansions: List<IdeSpreadPackExpansion>,
    ): String {
        val suffix = expansions.joinToString(separator = "And") { expansion ->
            val selectorSuffix = when (expansion.selectorKind) {
                SelectorKind.PROPS -> ""
                SelectorKind.ATTRS -> "Attrs"
                SelectorKind.CALLBACKS -> "Callbacks"
            }
            expansion.carrierClassShortName.toPascalCase() + selectorSuffix + "Pack"
        }
        return "${original.name}Via$suffix"
    }

    private fun shouldIncludeField(
        type: IdeTypeModel,
        selectorKind: SelectorKind,
    ): Boolean {
        return when (selectorKind) {
            SelectorKind.PROPS -> true
            SelectorKind.ATTRS -> !isFunctionLike(type)
            SelectorKind.CALLBACKS -> isFunctionLike(type)
        }
    }

    private fun isFunctionLike(
        type: IdeTypeModel,
    ): Boolean {
        val erasure = type.jvmErasure()
        return erasure.startsWith("kotlin.Function") || erasure.startsWith("kotlin.reflect.KFunction")
    }

    private fun requireUniqueName(
        owner: IdeFunctionModel,
        names: MutableSet<String>,
        name: String,
    ) {
        if (!names.add(name)) {
            invalidTarget(owner, "duplicate expanded parameter name $name")
        }
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

    private fun isSupportedFunction(
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

    private fun hasAnnotation(
        symbol: KaAnnotatedSymbol,
        fqName: String,
    ): Boolean {
        return findAnnotation(symbol, fqName) != null
    }

    private fun findAnnotation(
        symbol: KaAnnotatedSymbol,
        fqName: String,
    ): KaAnnotation? {
        return symbol.annotations.firstOrNull { annotation ->
            annotation.classId?.asSingleFqName()?.asString() == fqName
        }
    }

    private fun KaAnnotation.argumentValue(name: String): KaAnnotationValue? {
        return arguments.firstOrNull { argument -> argument.name.asString() == name }?.expression
    }

    private fun KaAnnotation.stringArgument(name: String): String? {
        val value = argumentValue(name) as? KaAnnotationValue.ConstantValue ?: return null
        return value.value.value as? String
    }

    private fun KaAnnotation.stringArrayArgument(name: String): List<String> {
        val value = argumentValue(name) as? KaAnnotationValue.ArrayValue ?: return emptyList()
        return value.values.mapNotNull { element ->
            (element as? KaAnnotationValue.ConstantValue)?.value?.value as? String
        }
    }

    private fun KaAnnotation.selectorKind(): SelectorKind {
        val enumEntry = argumentValue("selector") as? KaAnnotationValue.EnumEntryValue ?: return SelectorKind.PROPS
        return when (enumEntry.callableId?.callableName?.asString()) {
            SelectorKind.ATTRS.name -> SelectorKind.ATTRS
            SelectorKind.CALLBACKS.name -> SelectorKind.CALLBACKS
            else -> SelectorKind.PROPS
        }
    }

    private fun KaAnnotation.classLiteralArrayArgument(name: String): List<IdeReferencedParameterType> {
        val value = argumentValue(name) as? KaAnnotationValue.ArrayValue ?: return emptyList()
        return value.values.mapNotNull { element ->
            val classLiteral = element as? KaAnnotationValue.ClassLiteralValue ?: return@mapNotNull null
            val classId = classLiteral.classId?.asSingleFqName()?.asString() ?: return@mapNotNull null
            IdeReferencedParameterType(classId = classId)
        }
    }

    private fun KaAnnotation.spreadArgsReference(): IdeSpreadArgsReference? {
        val directFunctionFqName = stringArgument("value")?.takeIf { functionFqName ->
            functionFqName.isNotBlank()
        }
        val directParameterTypes = classLiteralArrayArgument("parameterTypes")
        if (directFunctionFqName == null) {
            error("spread-pack target function must not be blank")
        }
        return IdeSpreadArgsReference(
            functionFqName = directFunctionFqName,
            parameterTypes = directParameterTypes,
        )
    }

    private fun jvmSignatureKey(
        name: String,
        parameterTypes: List<IdeTypeModel>,
    ): String {
        return buildString {
            append(name)
            append("|")
            parameterTypes.forEach { type ->
                append(type.jvmErasure())
                append(";")
            }
        }
    }

    private fun overloadKey(
        function: IdeFunctionModel,
    ): String {
        return buildString {
            append(function.callableFqName)
            append("|")
            function.parameters.forEach { parameter ->
                append(parameter.type.jvmErasure())
                append(";")
            }
        }
    }

    private fun invalidTarget(
        function: IdeFunctionModel,
        reason: String,
    ): Nothing {
        throw InvalidSpreadPackTargetException(
            "Invalid @GenerateSpreadPackOverloads target ${function.callableFqName}: $reason",
        )
    }

    private fun String.toPascalCase(): String {
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
        return callableId ?: error("Local functions are not supported for spread-pack IDEA stubs")
    }

    private fun KaClassSymbol.requireClassId() =
        classId ?: error("Anonymous classes are not supported for spread-pack IDEA stubs")

    private fun IdeTypeModel.classIdOrNull(): String? {
        return (this as? IdeClassTypeModel)?.classId
    }

    private fun IdeFunctionModel.withResolvedCarrierFields(
        carrierFieldsByClassId: Map<String, List<IdeCarrierFieldModel>>,
    ): IdeFunctionModel {
        return copy(
            parameters = parameters.map { parameter ->
                val spreadPack = parameter.spreadPack ?: return@map parameter
                val resolvedFields = carrierFieldsByClassId[spreadPack.carrierClassId] ?: spreadPack.carrierFields
                parameter.copy(
                    spreadPack = spreadPack.copy(
                        carrierFields = resolvedFields,
                    ),
                )
            },
        )
    }

    private fun IdeAnnotatedCarrierSeed.validationOwner(): IdeFunctionModel {
        return IdeFunctionModel(
            callableFqName = classId,
            packageName = packageName,
            ownerClassFqName = null,
            ownerTypeParameters = emptyList(),
            name = classShortName,
            typeParameters = emptyList(),
            parameters = emptyList(),
            returnType = IdeOpaqueTypeModel(
                renderedText = classId,
                erasureKey = classId,
            ),
            visibilityKeyword = null,
            isSuspend = false,
            isOperator = false,
            isInfix = false,
        )
    }

    private data class MemberTargetScope(
        val originals: List<IdeFunctionModel>,
        val existingJvmKeys: Set<String>,
    )

    private data class IdeAnnotatedCarrierSeed(
        val packageName: String,
        val classId: String,
        val classShortName: String,
        val reference: IdeSpreadArgsReference,
        val selectorKind: SelectorKind,
        val excludedNames: Set<String>,
    )

    private class InvalidSpreadPackTargetException(
        message: String,
    ) : IllegalStateException(message)
}
