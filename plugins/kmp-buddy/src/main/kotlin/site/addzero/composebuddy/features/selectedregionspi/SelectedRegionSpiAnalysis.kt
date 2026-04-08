package site.addzero.composebuddy.features.selectedregionspi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import site.addzero.composebuddy.support.ComposePsiSupport

data class SelectedRegionSpiCapturedParameter(
    val name: String,
    val typeText: String,
)

data class SelectedRegionSpiAnalysisResult(
    val file: KtFile,
    val function: KtNamedFunction,
    val selectedStatements: List<PsiElement>,
    val interfaceName: String,
    val implementationName: String,
    val receiverTypeText: String?,
    val capturedParameters: List<SelectedRegionSpiCapturedParameter>,
)

object SelectedRegionSpiAnalysis {
    fun analyze(element: PsiElement, editor: Editor?): SelectedRegionSpiAnalysisResult? {
        val currentEditor = editor ?: return null
        if (!currentEditor.selectionModel.hasSelection()) {
            return null
        }
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return null
        if (!ComposePsiSupport.isComposable(function)) {
            return null
        }
        val selectionRange = TextRange(
            currentEditor.selectionModel.selectionStart,
            currentEditor.selectionModel.selectionEnd,
        )
        val bodyContainer = findSelectionContainer(function, selectionRange) ?: return null
        val selectedStatements = bodyContainer.statements
            .filter { statement -> ComposePsiSupport.selectedRangeContains(selectionRange.startOffset, selectionRange.endOffset, statement) }
        if (selectedStatements.isEmpty()) {
            return null
        }
        val file = function.containingKtFile
        val usedNames = collectDeclaredTypeNames(file)
        val baseName = selectedStatements.first().preferredSpiName()
        val interfaceName = allocateTypeName(
            usedNames = usedNames,
            preferred = "${function.name.orEmpty().capitalizeAscii()}${baseName.capitalizeAscii()}Spi",
        )
        val implementationName = allocateTypeName(
            usedNames = usedNames,
            preferred = "Default${interfaceName}",
        )
        val selectedRange = TextRange(
            selectedStatements.first().textRange.startOffset,
            selectedStatements.last().textRange.endOffset,
        )
        return SelectedRegionSpiAnalysisResult(
            file = file,
            function = function,
            selectedStatements = selectedStatements,
            interfaceName = interfaceName,
            implementationName = implementationName,
            receiverTypeText = resolveReceiverTypeText(bodyContainer),
            capturedParameters = collectCapturedParameters(function, selectedStatements, selectedRange),
        )
    }

    private fun findSelectionContainer(
        function: KtNamedFunction,
        selectionRange: TextRange,
    ): KtBlockExpression? {
        return function.collectDescendantsOfType<KtBlockExpression>()
            .filter { block -> containsRange(block.textRange, selectionRange) }
            .minByOrNull { block -> block.textRange.length }
    }

    private fun resolveReceiverTypeText(block: KtBlockExpression): String? {
        val lambdaExpression = block.getStrictParentOfType<KtLambdaExpression>() ?: return null
        val valueArgument = lambdaExpression.parent as? KtValueArgument
        val lambdaArgument = lambdaExpression.parent as? KtLambdaArgument
        val parentCall = valueArgument?.getStrictParentOfType<KtCallExpression>()
            ?: lambdaArgument?.getStrictParentOfType<KtCallExpression>()
            ?: return null
        val slotName = valueArgument?.getArgumentName()?.asName?.identifier
            ?: resolveTrailingLambdaSlotName(parentCall)
        resolveReceiverTypeFromSource(parentCall, slotName)?.let { return it }
        val resolvedCall = runCatching { parentCall.resolveToCall() }.getOrNull() ?: return null
        val parameter = resolvedCall.resultingDescriptor.valueParameters.firstOrNull { it.name.asString() == slotName } ?: return null
        if (parameter.type.isBuiltinFunctionalType) {
            val receiverType = parameter.type.getReceiverTypeFromFunctionType()
            if (receiverType != null) {
                return DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(receiverType)
            }
        }
        return null
    }

    private fun resolveTrailingLambdaSlotName(call: KtCallExpression): String {
        val resolvedCall = runCatching { call.resolveToCall() }.getOrNull()
        return resolvedCall
            ?.resultingDescriptor
            ?.valueParameters
            ?.lastOrNull()
            ?.name
            ?.asString()
            .orEmpty()
            .ifBlank { "content" }
    }

    private fun resolveReceiverTypeFromSource(call: KtCallExpression, slotName: String): String? {
        val resolved = call.calleeExpression?.mainReference?.resolve() ?: return null
        val function = when (resolved) {
            is KtNamedFunction -> {
                resolved
            }

            is KtNamedDeclaration -> {
                resolved.getStrictParentOfType<KtNamedFunction>()
            }

            else -> null
        } ?: return null
        val typeText = function.valueParameters
            .firstOrNull { parameter -> parameter.name == slotName }
            ?.typeReference
            ?.text
            ?: return null
        return parseReceiverTypeText(typeText)
    }

    private fun parseReceiverTypeText(typeText: String): String? {
        val normalized = typeText
            .replace("@androidx.compose.runtime.Composable", "")
            .replace("@Composable", "")
            .trim()
            .removeSuffix("?")
        val marker = ".() ->"
        if (!normalized.contains(marker)) {
            return null
        }
        return normalized.substringBefore(marker)
            .trim()
            .removePrefix("(")
            .trim()
            .ifBlank { null }
    }

    private fun collectCapturedParameters(
        function: KtNamedFunction,
        selectedStatements: List<PsiElement>,
        selectedRange: TextRange,
    ): List<SelectedRegionSpiCapturedParameter> {
        val result = linkedMapOf<String, SelectedRegionSpiCapturedParameter>()
        selectedStatements.forEach { statement ->
            statement.collectDescendantsOfType<KtNameReferenceExpression>()
                .forEach { reference ->
                    val name = reference.getReferencedName()
                    if (name.isBlank() || name == "this" || name == "super") {
                        return@forEach
                    }
                    val resolved = reference.mainReference.resolve() ?: return@forEach
                    val parameter = when {
                        resolved is KtParameter && resolved.getStrictParentOfType<KtNamedFunction>() == function -> {
                            resolveCapturedParameter(reference, resolved.name, resolved.typeReference?.text)
                        }

                        resolved is KtProperty && resolved.getStrictParentOfType<KtNamedFunction>() == function &&
                            !containsRange(selectedRange, resolved.textRange) -> {
                            resolveCapturedParameter(reference, resolved.name, resolved.typeReference?.text)
                        }

                        resolved is KtDestructuringDeclarationEntry &&
                            resolved.getStrictParentOfType<KtNamedFunction>() == function &&
                            !containsRange(selectedRange, resolved.textRange) -> {
                            resolveCapturedParameter(reference, resolved.name, null)
                        }

                        else -> null
                    } ?: return@forEach
                    result.putIfAbsent(parameter.name, parameter)
                }
        }
        return result.values.toList()
    }

    private fun resolveCapturedParameter(
        reference: KtNameReferenceExpression,
        name: String?,
        declaredTypeText: String?,
    ): SelectedRegionSpiCapturedParameter? {
        val safeName = name?.takeIf(String::isNotBlank) ?: return null
        val typeText = resolveTypeText(reference, declaredTypeText)
        return SelectedRegionSpiCapturedParameter(
            name = safeName,
            typeText = typeText,
        )
    }

    private fun resolveTypeText(reference: KtNameReferenceExpression, declaredTypeText: String?): String {
        if (!declaredTypeText.isNullOrBlank()) {
            return declaredTypeText
        }
        val resolvedCall = runCatching { reference.resolveToCall() }.getOrNull()
        val descriptor = resolvedCall?.resultingDescriptor
        val returnType = descriptor?.returnType
        if (returnType != null) {
            return DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(returnType)
        }
        return "kotlin.Any"
    }

    private fun collectDeclaredTypeNames(file: KtFile): MutableSet<String> {
        return file.declarations
            .mapNotNull { declaration -> declaration.name }
            .toMutableSet()
    }

    private fun allocateTypeName(usedNames: MutableSet<String>, preferred: String): String {
        var candidate = preferred
        var index = 2
        while (!usedNames.add(candidate)) {
            candidate = preferred + index
            index++
        }
        return candidate
    }

    private fun PsiElement.preferredSpiName(): String {
        val call = when (this) {
            is KtCallExpression -> {
                this
            }

            is KtDotQualifiedExpression -> {
                selectorExpression as? KtCallExpression
            }

            else -> null
        }
        return call?.calleeExpression?.text?.takeIf(String::isNotBlank) ?: "SelectedRegion"
    }

    private fun String.capitalizeAscii(): String {
        if (isBlank()) {
            return "Region"
        }
        return replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase()
            } else {
                char.toString()
            }
        }.replace(Regex("[^A-Za-z0-9_]"), "")
    }

    private fun containsRange(outer: TextRange, inner: TextRange): Boolean {
        return outer.startOffset <= inner.startOffset && outer.endOffset >= inner.endOffset
    }
}
