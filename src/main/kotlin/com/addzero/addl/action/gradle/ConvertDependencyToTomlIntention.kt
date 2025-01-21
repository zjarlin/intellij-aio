package com.addzero.addl.action.gradle

import com.addzero.addl.action.base.BaseAction
import com.addzero.addl.util.DialogUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString

/**
 * Intention action to convert Gradle string dependencies to libs.versions.toml format
 */
class ConvertDependencyToTomlIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText() = "Convert to libs.versions.toml format"
    override fun getFamilyName() = "Gradle dependency conversion"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // Check if we're in a Gradle file and on a string literal
        return element.containingFile?.name?.endsWith(".gradle") == true &&
                PsiTreeUtil.getParentOfType(element, GrString::class.java) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val stringLiteral = PsiTreeUtil.getParentOfType(element, GrString::class.java) ?: return
        
        val dependencyString = stringLiteral.text.removeSurrounding("\"")
        val (group, artifact, version) = parseDependencyString(dependencyString) ?: run {
            DialogUtil.showErrorMsg("Invalid dependency format")
            return
        }

        // Generate the TOML format
        val tomlFormat = """
            [libraries]
            ${artifact.replace("-", "").replace(".", "")} = { group = "$group", name = "$artifact", version.ref = "$artifact" }
            
            [versions]
            $artifact = "$version"
        """.trimIndent()

        // Replace the original string with the TOML format
        stringLiteral.replace(createTomlSnippet(project, tomlFormat))
    }

    private fun parseDependencyString(dependency: String): Triple<String, String, String>? {
        val parts = dependency.split(":")
        if (parts.size != 3) return null
        return Triple(parts[0], parts[1], parts[2])
    }

    private fun createTomlSnippet(project: Project, text: String): PsiElement {
        val factory = PsiElementFactory.getInstance(project)
        return factory.createExpressionFromText("\"\"\"\n$text\n\"\"\"", null)
    }
}
