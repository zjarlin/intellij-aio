package site.addzero.composebuddy.features.slotspi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
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

data class SlotSpiCapturedParameter(
    val name: String,
    val typeText: String,
)

data class SlotSpiTarget(
    val slotName: String,
    val interfaceName: String,
    val implementationName: String,
    val lambdaExpression: KtLambdaExpression,
    val receiverTypeText: String?,
    val capturedParameters: List<SlotSpiCapturedParameter>,
)

data class SlotSpiAnalysisResult(
    val file: KtFile,
    val function: KtNamedFunction,
    val call: KtCallExpression,
    val targets: List<SlotSpiTarget>,
)

object SlotSpiAnalysis {
    fun analyze(element: PsiElement, editor: Editor? = null): SlotSpiAnalysisResult? {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return null
        if (!ComposePsiSupport.isComposable(function)) {
            return null
        }
        analyzeSelection(function, editor)?.let { return it }
        return analyzeCaret(function, element)
    }

    private fun analyzeCaret(function: KtNamedFunction, element: PsiElement): SlotSpiAnalysisResult? {
        val slotArgument = findSlotArgument(element)
        if (slotArgument != null) {
            val call = slotArgument.parentCall ?: return null
            return buildAnalysis(function, call, listOf(slotArgument))
        }

        val call = element.getStrictParentOfType<KtCallExpression>() ?: return null
        val slots = collectSlotArguments(call)
        if (slots.isEmpty()) {
            return null
        }
        return buildAnalysis(function, call, slots)
    }

    private fun analyzeSelection(function: KtNamedFunction, editor: Editor?): SlotSpiAnalysisResult? {
        if (editor == null || !editor.selectionModel.hasSelection()) {
            return null
        }
        val selectionRange = TextRange(editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)
        val matchedSlots = function.collectDescendantsOfType<KtCallExpression>()
            .flatMap(::collectSlotArguments)
            .filter { slot -> selectionMatchesSlot(selectionRange, slot.lambdaExpression) }
        val selectedSlots = matchedSlots
            .filterNot { slot ->
                matchedSlots.any { other ->
                    other != slot && containsRange(other.lambdaExpression.textRange, slot.lambdaExpression.textRange)
                }
            }
        if (selectedSlots.isEmpty()) {
            return null
        }
        val selectedCalls = selectedSlots.mapNotNull { it.parentCall }.distinct()
        if (selectedCalls.size != 1) {
            return null
        }
        return buildAnalysis(function, selectedCalls.single(), selectedSlots)
    }

    private fun buildAnalysis(
        function: KtNamedFunction,
        call: KtCallExpression,
        slots: List<SlotArgumentCandidate>,
    ): SlotSpiAnalysisResult? {
        val file = function.containingKtFile
        val usedNames = collectDeclaredTypeNames(file)
        val targets = slots.mapNotNull { slot ->
            buildTarget(
                function = function,
                call = call,
                slot = slot,
                usedNames = usedNames,
            )
        }
        if (targets.isEmpty()) {
            return null
        }
        return SlotSpiAnalysisResult(
            file = file,
            function = function,
            call = call,
            targets = targets,
        )
    }

    private fun selectionMatchesSlot(selectionRange: TextRange, lambdaExpression: KtLambdaExpression): Boolean {
        val lambdaRange = lambdaExpression.textRange
        val bodyRange = lambdaExpression.bodyExpression?.textRange
        return containsRange(selectionRange, lambdaRange) ||
            (bodyRange != null && containsRange(selectionRange, bodyRange)) ||
            (bodyRange != null && containsRange(bodyRange, selectionRange))
    }

    private fun findSlotArgument(element: PsiElement): SlotArgumentCandidate? {
        val valueArgument = element.getStrictParentOfType<KtValueArgument>()
        val lambdaFromValueArgument = valueArgument?.getArgumentExpression() as? KtLambdaExpression
        if (valueArgument != null && lambdaFromValueArgument != null) {
            return SlotArgumentCandidate(
                slotName = valueArgument.getArgumentName()?.asName?.identifier ?: return null,
                lambdaExpression = lambdaFromValueArgument,
                parentCall = valueArgument.getStrictParentOfType<KtCallExpression>(),
            )
        }

        val lambdaArgument = element.getStrictParentOfType<KtLambdaArgument>() ?: return null
        val parentCall = lambdaArgument.getStrictParentOfType<KtCallExpression>() ?: return null
        return SlotArgumentCandidate(
            slotName = resolveTrailingLambdaSlotName(parentCall),
            lambdaExpression = lambdaArgument.getLambdaExpression() ?: return null,
            parentCall = parentCall,
        )
    }

    private fun collectSlotArguments(call: KtCallExpression): List<SlotArgumentCandidate> {
        val result = mutableListOf<SlotArgumentCandidate>()
        call.valueArguments.forEach { argument ->
            val lambdaExpression = argument.getArgumentExpression() as? KtLambdaExpression ?: return@forEach
            val slotName = argument.getArgumentName()?.asName?.identifier ?: return@forEach
            result += SlotArgumentCandidate(
                slotName = slotName,
                lambdaExpression = lambdaExpression,
                parentCall = call,
            )
        }
        call.lambdaArguments.forEach { lambdaArgument ->
            result += SlotArgumentCandidate(
                slotName = resolveTrailingLambdaSlotName(call),
                lambdaExpression = lambdaArgument.getLambdaExpression() ?: return@forEach,
                parentCall = call,
            )
        }
        return result
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

    private fun buildTarget(
        function: KtNamedFunction,
        call: KtCallExpression,
        slot: SlotArgumentCandidate,
        usedNames: MutableSet<String>,
    ): SlotSpiTarget? {
        val lambdaBody = slot.lambdaExpression.bodyExpression ?: return null
        if (lambdaBody.statements.isEmpty()) {
            return null
        }
        val callName = call.calleeExpression?.text?.takeIf(String::isNotBlank) ?: "ComposableSlot"
        val interfaceName = allocateTypeName(
            usedNames = usedNames,
            preferred = "${function.name.orEmpty().capitalizeAscii()}${callName.capitalizeAscii()}${slot.slotName.capitalizeAscii()}Spi",
        )
        val implementationName = allocateTypeName(
            usedNames = usedNames,
            preferred = "Default${interfaceName}",
        )
        return SlotSpiTarget(
            slotName = slot.slotName,
            interfaceName = interfaceName,
            implementationName = implementationName,
            lambdaExpression = slot.lambdaExpression,
            receiverTypeText = resolveReceiverTypeText(call, slot.slotName),
            capturedParameters = collectCapturedParameters(function, slot.lambdaExpression),
        )
    }

    private fun collectCapturedParameters(
        function: KtNamedFunction,
        lambdaExpression: KtLambdaExpression,
    ): List<SlotSpiCapturedParameter> {
        val lambdaRange = lambdaExpression.textRange
        val result = linkedMapOf<String, SlotSpiCapturedParameter>()
        lambdaExpression.collectDescendantsOfType<KtNameReferenceExpression>()
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
                        !containsRange(lambdaRange, resolved.textRange) -> {
                        resolveCapturedParameter(reference, resolved.name, resolved.typeReference?.text)
                    }

                    resolved is KtDestructuringDeclarationEntry &&
                        resolved.getStrictParentOfType<KtNamedFunction>() == function &&
                        !containsRange(lambdaRange, resolved.textRange) -> {
                        resolveCapturedParameter(reference, resolved.name, null)
                    }

                    else -> null
                } ?: return@forEach
                result.putIfAbsent(parameter.name, parameter)
            }
        return result.values.toList()
    }

    private fun resolveCapturedParameter(
        reference: KtNameReferenceExpression,
        name: String?,
        declaredTypeText: String?,
    ): SlotSpiCapturedParameter? {
        val safeName = name?.takeIf(String::isNotBlank) ?: return null
        val typeText = resolveTypeText(reference, declaredTypeText)
        return SlotSpiCapturedParameter(
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

    private fun resolveReceiverTypeText(call: KtCallExpression, slotName: String): String? {
        resolveReceiverTypeFromSource(call, slotName)?.let { return it }
        val resolvedCall = runCatching { call.resolveToCall() }.getOrNull() ?: return null
        val parameter = resolvedCall.resultingDescriptor.valueParameters.firstOrNull { it.name.asString() == slotName } ?: return null
        if (parameter.type.isBuiltinFunctionalType) {
            val receiverType = parameter.type.getReceiverTypeFromFunctionType()
            if (receiverType != null) {
                return DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(receiverType)
            }
        }
        val typeText = DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(parameter.type)
        return parseReceiverTypeText(typeText)
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

    private fun String.capitalizeAscii(): String {
        if (isBlank()) {
            return "Slot"
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

private data class SlotArgumentCandidate(
    val slotName: String,
    val lambdaExpression: KtLambdaExpression,
    val parentCall: KtCallExpression?,
)
