package site.addzero.composebuddy.features.selectedregionspi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

class SelectedRegionSpiRefactor(
    private val project: Project,
) {
    fun apply(analysis: SelectedRegionSpiAnalysisResult) {
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.extract.selected.region.spi")) {
            replaceSelectedStatements(factory, analysis)
            insertDeclarations(factory, analysis.file, analysis.function, analysis)
        }
    }

    private fun replaceSelectedStatements(
        factory: KtPsiFactory,
        analysis: SelectedRegionSpiAnalysisResult,
    ) {
        val firstStatement = analysis.selectedStatements.first()
        val replacementExpression = factory.createExpression(buildReplacementExpression(analysis))
        firstStatement.replace(replacementExpression)
        analysis.selectedStatements.drop(1).forEach { statement ->
            statement.delete()
        }
    }

    private fun insertDeclarations(
        factory: KtPsiFactory,
        file: KtFile,
        anchorDeclaration: KtDeclaration,
        analysis: SelectedRegionSpiAnalysisResult,
    ) {
        var anchor: com.intellij.psi.PsiElement = anchorDeclaration
        val interfaceDeclaration = factory.createDeclaration<KtDeclaration>(buildInterfaceText(analysis))
        val implementationDeclaration = factory.createDeclaration<KtDeclaration>(buildImplementationText(analysis))
        anchor = file.addAfter(factory.createWhiteSpace("\n\n"), anchor)
        anchor = file.addAfter(interfaceDeclaration, anchor)
        anchor = file.addAfter(factory.createWhiteSpace("\n\n"), anchor)
        anchor = file.addAfter(implementationDeclaration, anchor)
        val nextSibling = anchor.nextSibling
        if (nextSibling !is PsiWhiteSpace) {
            file.addAfter(factory.createWhiteSpace("\n"), anchor)
        }
    }

    private fun buildReplacementExpression(analysis: SelectedRegionSpiAnalysisResult): String {
        val renderArguments = buildList {
            if (analysis.receiverTypeText != null) {
                add("this")
            }
            analysis.capturedParameters.forEach { parameter ->
                add("${parameter.name} = ${parameter.name}")
            }
        }.joinToString(", ")
        val renderCall = if (renderArguments.isBlank()) {
            "org.koin.compose.koinInject<${analysis.interfaceName}>().Render()"
        } else {
            "org.koin.compose.koinInject<${analysis.interfaceName}>().Render($renderArguments)"
        }
        return renderCall
    }

    private fun buildInterfaceText(analysis: SelectedRegionSpiAnalysisResult): String {
        val renderSignature = buildRenderSignature(analysis, overrideKeyword = false)
        return buildString {
            appendLine("interface ${analysis.interfaceName} {")
            appendLine("    @androidx.compose.runtime.Composable")
            appendLine("    $renderSignature")
            append("}")
        }
    }

    private fun buildImplementationText(analysis: SelectedRegionSpiAnalysisResult): String {
        val renderSignature = buildRenderSignature(analysis, overrideKeyword = true)
        val bodyText = if (analysis.receiverTypeText != null) {
            buildString {
                appendLine("        with(scope) {")
                append(indentBlock(analysis.selectedStatements.joinToString("\n") { statement -> statement.text }, 3))
                appendLine()
                append("        }")
            }
        } else {
            indentBlock(analysis.selectedStatements.joinToString("\n") { statement -> statement.text }, 2)
        }
        return buildString {
            appendLine("@org.koin.core.annotation.Single")
            appendLine("class ${analysis.implementationName} : ${analysis.interfaceName} {")
            appendLine("    @androidx.compose.runtime.Composable")
            appendLine("    $renderSignature {")
            appendLine(bodyText)
            appendLine("    }")
            append("}")
        }
    }

    private fun buildRenderSignature(
        analysis: SelectedRegionSpiAnalysisResult,
        overrideKeyword: Boolean,
    ): String {
        val parameters = buildList {
            if (analysis.receiverTypeText != null) {
                add("scope: ${analysis.receiverTypeText}")
            }
            analysis.capturedParameters.forEach { parameter ->
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
