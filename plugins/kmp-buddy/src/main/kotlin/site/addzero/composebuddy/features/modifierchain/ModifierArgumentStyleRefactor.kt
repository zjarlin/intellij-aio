package site.addzero.composebuddy.features.modifierchain

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.support.ComposeNamingSupport
import site.addzero.composebuddy.support.ComposeRefactorWriteSupport

internal class ModifierArgumentStyleRefactor(
    private val project: Project,
) {
    fun apply(analysis: ModifierArgumentStyleAnalysisResult) {
        val factory = KtPsiFactory(project)
        ComposeRefactorWriteSupport.run(project, ComposeBuddyBundle.message("command.extract.modifier.argument.styleable")) {
            val file = analysis.function.containingKtFile
            val functionName = ComposeNamingSupport.uniqueFunctionName(
                analysis.function,
                "${analysis.function.name?.replaceFirstChar { char -> char.lowercaseChar() } ?: "modifier"}Style",
            )
            val declaration = renderDeclaration(functionName, analysis.calls)
            file.add(factory.createWhiteSpace("\n\n"))
            file.add(factory.createDeclaration(declaration))
            analysis.argument.replace(factory.createArgument("modifier = ${analysis.rootModifierText}.$functionName()"))
            file.ensureImport(factory, STYLEABLE_FQ_NAME)
            file.ensureImport(factory, EXPERIMENTAL_STYLE_API_FQ_NAME)
            file.ensureImport(factory, COMPOSABLE_FQ_NAME)
            file.ensureImport(factory, MODIFIER_FQ_NAME)
        }
    }

    private fun renderDeclaration(functionName: String, calls: List<String>): String {
        val rendering = ModifierStyleableRenderSupport.rewriteComposableReads(calls)
        return buildString {
            append("@OptIn(ExperimentalFoundationStyleApi::class)\n")
            append("@Composable\n")
            append("private fun Modifier.$functionName(): Modifier {\n")
            rendering.declarations.forEach { declaration ->
                append("    ")
                append(declaration)
                append("\n")
            }
            append("    return styleable {\n")
            rendering.calls.forEach { call ->
                append(indentCall(call))
                append("\n")
            }
            append("    }\n")
            append("}")
        }
    }

    private fun indentCall(call: String): String {
        return call.lineSequence()
            .joinToString(separator = "\n") { line -> "        $line" }
    }

    private fun KtFile.ensureImport(
        factory: KtPsiFactory,
        importText: String,
    ) {
        if (importDirectives.any { directive -> directive.importPath?.pathStr == importText }) {
            return
        }
        val importDirective = factory.createImportDirective(
            ImportPath(FqName(importText), false),
        )
        val importList = importList
        if (importList != null) {
            val anchor = importList.imports.lastOrNull()
            if (anchor != null) {
                importList.addAfter(importDirective, anchor)
            } else {
                importList.add(importDirective)
            }
            return
        }

        val anchor: PsiElement = packageDirective ?: firstChild
        addAfter(factory.createNewLine(), anchor)
        addAfter(importDirective, anchor)
    }

    private companion object {
        const val STYLEABLE_FQ_NAME = "androidx.compose.foundation.style.styleable"
        const val EXPERIMENTAL_STYLE_API_FQ_NAME = "androidx.compose.foundation.style.ExperimentalFoundationStyleApi"
        const val COMPOSABLE_FQ_NAME = "androidx.compose.runtime.Composable"
        const val MODIFIER_FQ_NAME = "androidx.compose.ui.Modifier"
    }
}
