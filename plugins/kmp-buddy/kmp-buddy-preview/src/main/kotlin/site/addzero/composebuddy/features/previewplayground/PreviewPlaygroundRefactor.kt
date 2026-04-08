package site.addzero.composebuddy.features.previewplayground

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.preview.support.PreviewNamingSupport
import site.addzero.composebuddy.preview.support.PreviewWriteSupport
import site.addzero.composebuddy.support.ComposePreviewSupport

class PreviewPlaygroundRefactor(
    private val project: Project,
) {
    fun apply(analysis: PreviewPlaygroundAnalysisResult) {
        val function = analysis.function
        val functionName = function.name ?: return
        val previewTitle = "Quick preview: $functionName"
        val previewName = PreviewNamingSupport.uniqueFunctionName(function, "${functionName}QuickPreview")
        val invocationText = ComposePreviewSupport.renderCallExpression(
            function = function,
            includeDefaulted = false,
            indent = "            ",
        )
        val declarationText = buildString {
            appendLine("@androidx.compose.ui.tooling.preview.Preview(")
            appendLine("    name = \"$previewTitle\",")
            appendLine("    showBackground = true,")
            appendLine("    widthDp = 420,")
            appendLine(")")
            appendLine("@androidx.compose.runtime.Composable")
            appendLine("private fun $previewName() {")
            appendLine("    androidx.compose.foundation.layout.Column {")
            appendLine("        androidx.compose.foundation.text.BasicText(text = \"$previewTitle\")")
            appendLine(invocationText.prependIndent("        "))
            appendLine("    }")
            appendLine("}")
        }
        val factory = KtPsiFactory(project)
        PreviewWriteSupport.run(project, ComposeBuddyBundle.message("command.generate.quick.preview.playground")) {
            function.parent.addBefore(factory.createWhiteSpace("\n\n"), function)
            function.parent.addBefore(factory.createDeclaration(declarationText.trim()), function)
        }
    }
}
