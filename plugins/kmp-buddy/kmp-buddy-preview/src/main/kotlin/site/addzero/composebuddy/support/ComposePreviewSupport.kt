package site.addzero.composebuddy.support

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import kotlin.math.absoluteValue

object ComposePreviewSupport {
    fun canRenderQuickPreview(function: KtNamedFunction): Boolean {
        if (function.receiverTypeReference != null) {
            return false
        }
        return function.valueParameters
            .filterNot { it.hasDefaultValue() }
            .all { sampleValue(it).supported }
    }

    fun renderCallExpression(
        function: KtNamedFunction,
        variant: String = "default",
        includeDefaulted: Boolean = true,
        indent: String = "    ",
    ): String {
        val functionName = function.name ?: "Composable"
        val parameters = function.valueParameters.filter { includeDefaulted || !it.hasDefaultValue() }
        if (parameters.isEmpty()) {
            return "$functionName()"
        }
        val arguments = parameters.joinToString(",\n") { parameter ->
            val name = parameter.name ?: "value"
            "$indent$name = ${sampleExpression(parameter, variant)}"
        }
        return buildString {
            appendLine("$functionName(")
            appendLine(arguments)
            append(")")
        }
    }

    fun sampleExpression(parameter: KtParameter, variant: String = "default"): String {
        return sampleValue(parameter, variant).expression
    }

    fun sampleValue(parameter: KtParameter, variant: String = "default"): PreviewSampleValue {
        return PreviewSampleGenerator(parameter.containingKtFile)
            .sampleValueForParameter(parameter, variant)
    }
}

private class PreviewSampleGenerator(
    private val file: KtFile,
) {
    fun sampleValueForParameter(parameter: KtParameter, variant: String): PreviewSampleValue {
        val parameterName = parameter.name ?: "value"
        val typeReference = parameter.typeReference
        return sampleValueForType(
            typeText = typeReference?.text.orEmpty(),
            parameterName = parameterName,
            variant = variant,
            context = parameter,
            typeReference = typeReference,
            depth = 0,
            typeBindings = emptyMap(),
        )
    }

    private fun sampleValueForType(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        typeReference: KtTypeReference?,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        if (depth > 4) {
            return unsupportedValue()
        }
        val normalizedType = substituteBindings(normalizeType(typeText), typeBindings)
        if (normalizedType.isBlank()) {
            return unsupportedValue()
        }
        if (normalizedType == "*") {
            return PreviewSampleValue("kotlin.Any()", supported = true)
        }
        if (normalizedType.endsWith("?")) {
            return PreviewSampleValue("null", supported = true)
        }
        if (normalizedType.contains("->")) {
            return lambdaValue(normalizedType, parameterName, variant, context, depth, typeBindings)
        }

        val baseType = normalizedType.substringBefore("<").substringBefore(" ").trim()
        val simpleBaseType = baseType.removePrefix("kotlin.").substringAfterLast('.')
        return when (simpleBaseType) {
            "String" -> PreviewSampleValue(stringValue(parameterName, variant), true)
            "Int" -> PreviewSampleValue(intValue(parameterName, variant).toString(), true)
            "Long" -> PreviewSampleValue("${intValue(parameterName, variant).toLong()}L", true)
            "Float" -> PreviewSampleValue("${floatValue(parameterName, variant)}f", true)
            "Double" -> PreviewSampleValue("${floatValue(parameterName, variant).toDouble()}", true)
            "Boolean" -> PreviewSampleValue(booleanValue(parameterName, variant).toString(), true)
            "Char" -> PreviewSampleValue("'${charValue(parameterName)}'", true)
            "Modifier" -> PreviewSampleValue("androidx.compose.ui.Modifier", true)
            "Color" -> PreviewSampleValue(colorValue(parameterName), true)
            "TextUnit" -> PreviewSampleValue("androidx.compose.ui.unit.TextUnit.Unspecified", true)
            "Dp" -> PreviewSampleValue("androidx.compose.ui.unit.Dp.Unspecified", true)
            "Painter" -> PreviewSampleValue(
                "androidx.compose.ui.graphics.painter.ColorPainter(${colorValue(parameterName)})",
                true,
            )
            "Brush" -> PreviewSampleValue("androidx.compose.ui.graphics.SolidColor(${colorValue(parameterName)})", true)
            "Shape" -> PreviewSampleValue("androidx.compose.foundation.shape.RoundedCornerShape(50)", true)
            "TextStyle" -> PreviewSampleValue("androidx.compose.ui.text.TextStyle.Default", true)
            "FontWeight" -> PreviewSampleValue("androidx.compose.ui.text.font.FontWeight.Medium", true)
            "Alignment" -> PreviewSampleValue("androidx.compose.ui.Alignment.Center", true)
            "State", "MutableState" -> stateValue(normalizedType, parameterName, variant, context, depth, typeBindings)
            "List", "MutableList", "Collection", "Iterable" -> listValue(normalizedType, parameterName, variant, context, depth, typeBindings)
            "Set", "MutableSet" -> setValue(normalizedType, parameterName, variant, context, depth, typeBindings)
            "Map", "MutableMap" -> mapValue(normalizedType, parameterName, variant, context, depth, typeBindings)
            "Array" -> arrayValue(normalizedType, parameterName, variant, context, depth, typeBindings)
            "Pair" -> pairValue(normalizedType, parameterName, variant, context, depth, typeBindings)
            "Triple" -> tripleValue(normalizedType, parameterName, variant, context, depth, typeBindings)
            else -> resolvedTypeValue(normalizedType, parameterName, variant, context, typeReference, depth, typeBindings)
        }
    }

    private fun resolvedTypeValue(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        typeReference: KtTypeReference?,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        resolveTypeAlias(typeText, context, typeReference)?.let { alias ->
            val aliasBindings = buildTypeBindings(alias.typeParameters.mapNotNull { it.name }, typeText)
            val expandedType = substituteBindings(alias.getTypeReference()?.text.orEmpty(), aliasBindings)
            return sampleValueForType(
                typeText = expandedType,
                parameterName = parameterName,
                variant = variant,
                context = alias,
                typeReference = alias.getTypeReference(),
                depth = depth + 1,
                typeBindings = typeBindings + aliasBindings,
            )
        }
        resolveKtClassOrObject(typeText, context, typeReference)?.let { declaration ->
            return when (declaration) {
                is KtObjectDeclaration -> PreviewSampleValue(renderKotlinReference(declaration, typeText), true)
                is KtClass -> classValue(declaration, typeText, parameterName, variant, depth, typeBindings)
                else -> unsupportedValue()
            }
        }
        resolvePsiClass(typeText, context)?.let { psiClass ->
            return psiClassValue(psiClass, typeText)
        }
        return unsupportedValue()
    }

    private fun stateValue(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        val nestedType = typeArgumentOrNull(typeText, index = 0) ?: return unsupportedValue()
        val nestedValue = sampleValueForType(
            typeText = nestedType,
            parameterName = parameterName,
            variant = variant,
            context = context,
            typeReference = null,
            depth = depth + 1,
            typeBindings = typeBindings,
        )
        if (!nestedValue.supported) {
            return unsupportedValue()
        }
        return PreviewSampleValue("androidx.compose.runtime.mutableStateOf(${nestedValue.expression})", true)
    }

    private fun listValue(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        val nestedType = typeArgumentOrNull(typeText, index = 0) ?: return PreviewSampleValue("emptyList()", true)
        val first = sampleValueForType(nestedType, "${parameterName}First", variant, context, null, depth + 1, typeBindings)
        val second = sampleValueForType(nestedType, "${parameterName}Second", variant, context, null, depth + 1, typeBindings)
        if (!first.supported || !second.supported) {
            return PreviewSampleValue("emptyList()", true)
        }
        return PreviewSampleValue("listOf(${first.expression}, ${second.expression})", true)
    }

    private fun setValue(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        val listValue = listValue(typeText.replaceFirst("Set", "List"), parameterName, variant, context, depth, typeBindings)
        if (!listValue.supported) {
            return PreviewSampleValue("emptySet()", true)
        }
        return PreviewSampleValue(listValue.expression.replaceFirst("listOf", "setOf"), true)
    }

    private fun mapValue(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        val keyType = typeArgumentOrNull(typeText, index = 0) ?: return PreviewSampleValue("emptyMap()", true)
        val valueType = typeArgumentOrNull(typeText, index = 1) ?: return PreviewSampleValue("emptyMap()", true)
        val keyValue = sampleValueForType(keyType, "${parameterName}Key", variant, context, null, depth + 1, typeBindings)
        val mappedValue = sampleValueForType(valueType, "${parameterName}Value", variant, context, null, depth + 1, typeBindings)
        if (!keyValue.supported || !mappedValue.supported) {
            return PreviewSampleValue("emptyMap()", true)
        }
        return PreviewSampleValue("mapOf(${keyValue.expression} to ${mappedValue.expression})", true)
    }

    private fun arrayValue(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        val nestedType = typeArgumentOrNull(typeText, index = 0) ?: return PreviewSampleValue("emptyArray()", true)
        val first = sampleValueForType(nestedType, "${parameterName}First", variant, context, null, depth + 1, typeBindings)
        val second = sampleValueForType(nestedType, "${parameterName}Second", variant, context, null, depth + 1, typeBindings)
        if (!first.supported || !second.supported) {
            return PreviewSampleValue("emptyArray()", true)
        }
        return PreviewSampleValue("arrayOf(${first.expression}, ${second.expression})", true)
    }

    private fun pairValue(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        val firstType = typeArgumentOrNull(typeText, index = 0) ?: return unsupportedValue()
        val secondType = typeArgumentOrNull(typeText, index = 1) ?: return unsupportedValue()
        val firstValue = sampleValueForType(firstType, "${parameterName}First", variant, context, null, depth + 1, typeBindings)
        val secondValue = sampleValueForType(secondType, "${parameterName}Second", variant, context, null, depth + 1, typeBindings)
        if (!firstValue.supported || !secondValue.supported) {
            return unsupportedValue()
        }
        return PreviewSampleValue("${firstValue.expression} to ${secondValue.expression}", true)
    }

    private fun tripleValue(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        val firstType = typeArgumentOrNull(typeText, index = 0) ?: return unsupportedValue()
        val secondType = typeArgumentOrNull(typeText, index = 1) ?: return unsupportedValue()
        val thirdType = typeArgumentOrNull(typeText, index = 2) ?: return unsupportedValue()
        val firstValue = sampleValueForType(firstType, "${parameterName}First", variant, context, null, depth + 1, typeBindings)
        val secondValue = sampleValueForType(secondType, "${parameterName}Second", variant, context, null, depth + 1, typeBindings)
        val thirdValue = sampleValueForType(thirdType, "${parameterName}Third", variant, context, null, depth + 1, typeBindings)
        if (!firstValue.supported || !secondValue.supported || !thirdValue.supported) {
            return unsupportedValue()
        }
        return PreviewSampleValue(
            "Triple(${firstValue.expression}, ${secondValue.expression}, ${thirdValue.expression})",
            true,
        )
    }

    private fun lambdaValue(
        typeText: String,
        parameterName: String,
        variant: String,
        context: PsiElement,
        depth: Int,
        typeBindings: Map<String, String>,
    ): PreviewSampleValue {
        val normalized = typeText
            .replace("suspend ", "")
            .replace("@Composable", "")
            .trim()
        val returnType = normalized.substringAfterLast("->", "Unit").trim()
        val parameterSection = normalized.substringBeforeLast("->", "").trim()
        val lambdaPrefix = buildLambdaPrefix(parameterSection)
        if (returnType == "Unit") {
            return PreviewSampleValue(
                if (lambdaPrefix.isEmpty()) "{ }" else "{ $lambdaPrefix -> }",
                true,
            )
        }
        val returnValue = sampleValueForType(
            typeText = returnType,
            parameterName = "${parameterName}Result",
            variant = variant,
            context = context,
            typeReference = null,
            depth = depth + 1,
            typeBindings = typeBindings,
        )
        if (!returnValue.supported) {
            return unsupportedValue()
        }
        return PreviewSampleValue(
            if (lambdaPrefix.isEmpty()) "{ ${returnValue.expression} }" else "{ $lambdaPrefix -> ${returnValue.expression} }",
            true,
        )
    }

    private fun classValue(
        declaration: KtClass,
        typeText: String,
        parameterName: String,
        variant: String,
        depth: Int,
        inheritedTypeBindings: Map<String, String>,
    ): PreviewSampleValue {
        if (declaration.isEnum()) {
            val enumEntry = declaration.declarations.filterIsInstance<KtEnumEntry>().firstOrNull()
                ?: return unsupportedValue()
            return PreviewSampleValue("${renderKotlinReference(declaration, typeText)}.${enumEntry.name}", true)
        }
        if (declaration.isInterface() || declaration.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD)) {
            return unsupportedValue()
        }
        val reference = renderKotlinReference(declaration, typeText)
        val typeBindings = inheritedTypeBindings + buildTypeBindings(declaration.typeParameters.mapNotNull { it.name }, typeText)
        val constructorParameters = declaration.primaryConstructorParameters
        if (constructorParameters.isEmpty()) {
            return PreviewSampleValue("$reference()", true)
        }
        val arguments = mutableListOf<String>()
        constructorParameters
            .filterNot { it.hasDefaultValue() }
            .forEach { parameter ->
                val nestedTypeReference = parameter.typeReference
                val nestedType = nestedTypeReference?.text.orEmpty()
                val nestedName = parameter.name ?: parameterName
                val nestedValue = sampleValueForType(
                    typeText = nestedType,
                    parameterName = nestedName,
                    variant = variant,
                    context = parameter,
                    typeReference = nestedTypeReference,
                    depth = depth + 1,
                    typeBindings = typeBindings,
                )
                if (!nestedValue.supported) {
                    return unsupportedValue()
                }
                arguments += "$nestedName = ${nestedValue.expression}"
            }
        return if (arguments.isEmpty()) {
            PreviewSampleValue("$reference()", true)
        } else {
            PreviewSampleValue("$reference(${arguments.joinToString(", ")})", true)
        }
    }

    private fun psiClassValue(psiClass: PsiClass, typeText: String): PreviewSampleValue {
        val reference = psiClass.qualifiedName ?: typeText.substringBefore("<").trim()
        if (psiClass.isEnum) {
            val enumConstant = psiClass.fields.filterIsInstance<PsiEnumConstant>().firstOrNull()
                ?: return unsupportedValue()
            return PreviewSampleValue("$reference.${enumConstant.name}", true)
        }
        if (psiClass.isInterface || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return unsupportedValue()
        }
        if (psiClass.constructors.isEmpty() || psiClass.constructors.any { it.parameterList.parametersCount == 0 }) {
            return PreviewSampleValue("$reference()", true)
        }
        return unsupportedValue()
    }

    private fun resolveKtClassOrObject(
        typeText: String,
        context: PsiElement,
        typeReference: KtTypeReference?,
    ): KtClassOrObject? {
        val resolved = resolveReference(typeReference)
        if (resolved is KtClassOrObject) {
            return resolved
        }
        val shortName = typeText.substringBefore("<").substringAfterLast('.').trim()
        if (shortName.isBlank()) {
            return null
        }
        file.collectDescendantsOfType<KtClassOrObject>().firstOrNull { it.name == shortName }?.let { return it }
        return PsiShortNamesCache.getInstance(context.project)
            .getClassesByName(shortName, GlobalSearchScope.projectScope(context.project))
            .mapNotNull { it.navigationElement as? KtClassOrObject }
            .firstOrNull()
    }

    private fun resolveTypeAlias(
        typeText: String,
        context: PsiElement,
        typeReference: KtTypeReference?,
    ): KtTypeAlias? {
        val resolved = resolveReference(typeReference)
        if (resolved is KtTypeAlias) {
            return resolved
        }
        val shortName = typeText.substringBefore("<").substringAfterLast('.').trim()
        if (shortName.isBlank()) {
            return null
        }
        return file.collectDescendantsOfType<KtTypeAlias>().firstOrNull { it.name == shortName }
    }

    private fun resolvePsiClass(typeText: String, context: PsiElement): PsiClass? {
        val shortName = typeText.substringBefore("<").substringAfterLast('.').trim()
        if (shortName.isBlank()) {
            return null
        }
        return PsiShortNamesCache.getInstance(context.project)
            .getClassesByName(shortName, GlobalSearchScope.projectScope(context.project))
            .firstOrNull()
    }

    private fun resolveReference(typeReference: KtTypeReference?): PsiElement? {
        val userType = typeReference?.typeElement as? KtUserType ?: return null
        return runCatching { userType.referenceExpression?.mainReference?.resolve() }.getOrNull()
    }

    private fun renderKotlinReference(declaration: KtClassOrObject, typeText: String): String {
        return declaration.fqName?.asString()
            ?: typeText.substringBefore("<").trim()
    }

    private fun buildTypeBindings(parameterNames: List<String>, typeText: String): Map<String, String> {
        if (parameterNames.isEmpty()) {
            return emptyMap()
        }
        val argumentsText = typeText.substringAfter("<", "").substringBeforeLast(">", "")
        val arguments = splitTypeArguments(argumentsText)
        if (arguments.isEmpty()) {
            return emptyMap()
        }
        return parameterNames.mapIndexedNotNull { index, name ->
            val argument = arguments.getOrNull(index)?.takeUnless { it == "*" } ?: return@mapIndexedNotNull null
            name to argument
        }.toMap()
    }

    private fun substituteBindings(typeText: String, typeBindings: Map<String, String>): String {
        if (typeBindings.isEmpty()) {
            return typeText
        }
        var resolved = typeText
        typeBindings.forEach { (name, actualType) ->
            resolved = resolved.replace(Regex("\\b${Regex.escape(name)}\\b"), actualType)
        }
        return resolved
    }

    private fun buildLambdaPrefix(parameterSection: String): String {
        if (parameterSection.isBlank()) {
            return ""
        }
        if (parameterSection.endsWith(".()")) {
            return ""
        }
        if (parameterSection.endsWith("()")) {
            return ""
        }
        val argumentSection = when {
            parameterSection.contains(".(") -> parameterSection.substringAfter(".(").substringBeforeLast(")")
            parameterSection.startsWith("(") && parameterSection.endsWith(")") -> parameterSection.removePrefix("(").removeSuffix(")")
            else -> parameterSection
        }
        val argumentCount = splitTopLevel(argumentSection, ',').count { it.isNotBlank() }
        if (argumentCount <= 0) {
            return ""
        }
        return List(argumentCount) { "_" }.joinToString(", ")
    }

    private fun stringValue(parameterName: String, variant: String): String {
        val label = parameterName
            .replaceFirstChar { it.uppercase() }
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        return when (variant) {
            "error" -> "\"$label error\""
            "loading" -> "\"$label loading\""
            "success" -> "\"$label ready\""
            else -> {
                val bank = listOf("Aurora", "Nimbus", "Quartz", "Harbor", "Pixel", "Velvet")
                "\"${bank[seedIndex(parameterName, bank.size)]} ${10 + seedIndex(parameterName + variant, 90)}\""
            }
        }
    }

    private fun intValue(parameterName: String, variant: String): Int {
        return when (variant) {
            "error" -> 0
            "loading" -> 1
            else -> 2 + seedIndex(parameterName + variant, 97)
        }
    }

    private fun floatValue(parameterName: String, variant: String): String {
        val raw = when (variant) {
            "loading" -> 0.4
            "error" -> 0.0
            else -> 1.0 + seedIndex(parameterName + variant, 90) / 10.0
        }
        return "%.1f".format(raw)
    }

    private fun booleanValue(parameterName: String, variant: String): Boolean {
        return when (variant) {
            "loading" -> true
            "error" -> false
            else -> seedIndex(parameterName + variant, 2) == 0
        }
    }

    private fun charValue(parameterName: String): Char {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        return alphabet[seedIndex(parameterName, alphabet.length)]
    }

    private fun colorValue(parameterName: String): String {
        val colors = listOf(
            "0xFF5B8DEF",
            "0xFFE77C7C",
            "0xFF58B88A",
            "0xFFF1A94E",
            "0xFF8B7CF6",
            "0xFF4F9DA6",
        )
        return "androidx.compose.ui.graphics.Color(${colors[seedIndex(parameterName, colors.size)]})"
    }

    private fun seedIndex(seedSource: String, size: Int): Int {
        if (size <= 1) {
            return 0
        }
        return seedSource.hashCode().absoluteValue % size
    }

    private fun normalizeType(typeText: String): String {
        return typeText
            .replace("@androidx.compose.runtime.Composable", "")
            .replace("@Composable", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun typeArgumentOrNull(typeText: String, index: Int): String? {
        val argumentsText = typeText.substringAfter("<", missingDelimiterValue = "")
            .substringBeforeLast(">", missingDelimiterValue = "")
        return splitTypeArguments(argumentsText).getOrNull(index)
    }

    private fun splitTypeArguments(argumentsText: String): List<String> {
        if (argumentsText.isBlank()) {
            return emptyList()
        }
        return splitTopLevel(argumentsText, ',')
            .map { it.removePrefix("out ").removePrefix("in ").trim() }
            .filter { it.isNotBlank() }
    }

    private fun splitTopLevel(text: String, delimiter: Char): List<String> {
        if (text.isBlank()) {
            return emptyList()
        }
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var angleDepth = 0
        var roundDepth = 0
        text.forEach { char ->
            when (char) {
                '<' -> angleDepth++
                '>' -> angleDepth--
                '(' -> roundDepth++
                ')' -> roundDepth--
            }
            if (char == delimiter && angleDepth == 0 && roundDepth == 0) {
                result += current.toString()
                current.setLength(0)
            } else {
                current.append(char)
            }
        }
        result += current.toString()
        return result
    }

    private fun unsupportedValue(): PreviewSampleValue {
        return PreviewSampleValue("TODO(\"preview\")", supported = false)
    }
}

data class PreviewSampleValue(
    val expression: String,
    val supported: Boolean,
)
