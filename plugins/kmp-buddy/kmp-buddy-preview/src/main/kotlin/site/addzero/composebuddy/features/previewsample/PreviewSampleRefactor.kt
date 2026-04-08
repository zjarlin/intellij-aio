package site.addzero.composebuddy.features.previewsample

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.preview.support.PreviewNamingSupport
import site.addzero.composebuddy.preview.support.PreviewWriteSupport
import site.addzero.composebuddy.support.ComposePreviewSupport

class PreviewSampleRefactor(
    private val project: Project,
) {
    fun apply(analysis: PreviewSampleAnalysisResult) {
        val function = analysis.function
        val factory = KtPsiFactory(project)
        PreviewWriteSupport.run(project, ComposeBuddyBundle.message("command.generate.preview.samples")) {
            val variants = listOf("Default", "Loading", "Error", "Success")
            variants.forEach { variant ->
                val previewName = PreviewNamingSupport.uniqueFunctionName(function, "${function.name}$variant" + "Preview")
                val callArguments = function.valueParameters.joinToString(", ") { parameter ->
                    val name = parameter.name ?: "value"
                    "$name = ${ComposePreviewSupport.sampleExpression(parameter, variant.lowercase())}"
                }
                val declaration = buildString {
                    appendLine("@androidx.compose.ui.tooling.preview.Preview")
                    appendLine("@androidx.compose.runtime.Composable")
                    appendLine("private fun $previewName() {")
                    appendLine("    ${function.name}($callArguments)")
                    appendLine("}")
                }
                function.parent.addBefore(factory.createWhiteSpace("\n\n"), function)
                function.parent.addBefore(factory.createDeclaration(declaration.trim()), function)
            }
        }
    }
}
