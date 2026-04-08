package site.addzero.composebuddy.features.slotspi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class SlotSpiRefactor(
    private val project: Project,
) {
    fun apply(analysis: SlotSpiAnalysisResult) {
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.extract.slot.spi")) {
            replaceSlotLambdas(factory, analysis.targets)
            insertDeclarations(factory, analysis.file, analysis.function, analysis.targets)
        }
    }

    private fun replaceSlotLambdas(
        factory: KtPsiFactory,
        targets: List<SlotSpiTarget>,
    ) {
        targets.sortedByDescending { target -> target.lambdaExpression.textRange.startOffset }
            .forEach { target ->
                val replacementText = buildReplacementLambda(target)
                target.lambdaExpression.replace(factory.createExpression(replacementText))
            }
    }

    private fun insertDeclarations(
        factory: KtPsiFactory,
        file: KtFile,
        anchorDeclaration: KtDeclaration,
        targets: List<SlotSpiTarget>,
    ) {
        var anchor: com.intellij.psi.PsiElement = anchorDeclaration
        targets.forEach { target ->
            val interfaceDeclaration = factory.createDeclaration<KtDeclaration>(buildInterfaceText(target))
            val implementationDeclaration = factory.createDeclaration<KtDeclaration>(buildImplementationText(target))
            anchor = file.addAfter(factory.createWhiteSpace("\n\n"), anchor)
            anchor = file.addAfter(interfaceDeclaration, anchor)
            anchor = file.addAfter(factory.createWhiteSpace("\n\n"), anchor)
            anchor = file.addAfter(implementationDeclaration, anchor)
            val nextSibling = anchor.nextSibling
            if (nextSibling !is PsiWhiteSpace) {
                anchor = file.addAfter(factory.createWhiteSpace("\n"), anchor)
            }
        }
    }

    private fun buildReplacementLambda(target: SlotSpiTarget): String {
        val renderArguments = buildList {
            if (target.receiverTypeText != null) {
                add("this")
            }
            target.capturedParameters.forEach { parameter ->
                add("${parameter.name} = ${parameter.name}")
            }
        }.joinToString(", ")
        val renderCall = if (renderArguments.isBlank()) {
            "org.koin.compose.koinInject<${target.interfaceName}>().Render()"
        } else {
            "org.koin.compose.koinInject<${target.interfaceName}>().Render($renderArguments)"
        }
        return "{ $renderCall }"
    }

    private fun buildInterfaceText(target: SlotSpiTarget): String {
        val renderSignature = buildRenderSignature(target, overrideKeyword = false)
        return buildString {
            appendLine("interface ${target.interfaceName} {")
            appendLine("    @androidx.compose.runtime.Composable")
            appendLine("    $renderSignature")
            append("}")
        }
    }

    private fun buildImplementationText(target: SlotSpiTarget): String {
        val renderSignature = buildRenderSignature(target, overrideKeyword = true)
        val bodyText = if (target.receiverTypeText != null) {
            buildString {
                appendLine("        with(scope) {")
                append(indentBlock(target.lambdaExpression.bodyExpression?.text.orEmpty(), 3))
                appendLine()
                append("        }")
            }
        } else {
            indentBlock(target.lambdaExpression.bodyExpression?.text.orEmpty(), 2)
        }
        return buildString {
            appendLine("@org.koin.core.annotation.Single")
            appendLine("class ${target.implementationName} : ${target.interfaceName} {")
            appendLine("    @androidx.compose.runtime.Composable")
            appendLine("    $renderSignature {")
            appendLine(bodyText)
            appendLine("    }")
            append("}")
        }
    }

    private fun buildRenderSignature(target: SlotSpiTarget, overrideKeyword: Boolean): String {
        val parameters = buildList {
            if (target.receiverTypeText != null) {
                add("scope: ${target.receiverTypeText}")
            }
            target.capturedParameters.forEach { parameter ->
                add("${parameter.name}: ${parameter.typeText}")
            }
        }
        val prefix = if (overrideKeyword) "override fun" else "fun"
        return if (parameters.isEmpty()) {
            "$prefix Render()"
        } else {
            buildString {
                appendLine("${prefix} Render(")
                parameters.forEach { parameter ->
                    appendLine("        $parameter,")
                }
                append("    )")
            }.trimEnd()
        }
    }

    private fun indentBlock(text: String, level: Int): String {
        val indent = "    ".repeat(level)
        return text.lines().joinToString("\n") { line ->
            if (line.isBlank()) {
                indent.trimEnd()
            } else {
                indent + line
            }
        }
    }
}
