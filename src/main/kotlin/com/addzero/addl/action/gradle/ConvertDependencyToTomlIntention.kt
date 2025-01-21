package com.addzero.addl.action.gradle

import com.addzero.addl.util.DialogUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString
import java.io.File

/**
 * Intention action to convert Gradle string dependencies to libs.versions.toml format
 */
class ConvertDependencyToTomlIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText() = "Convert to libs.versions.toml format"
    override fun getFamilyName() = "Gradle dependency conversion"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // Only support build.gradle.kts files
        return element.containingFile?.name == "build.gradle.kts" && 
               PsiTreeUtil.getParentOfType(element, GrString::class.java) != null
    }

override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val stringLiteral = PsiTreeUtil.getParentOfType(element, GrString::class.java) ?: return

    val dependencyString = stringLiteral.text.removeSurrounding("\"")
    val (group, artifact, version) = parseDependencyString(dependencyString) ?: run {
        DialogUtil.showErrorMsg("Invalid dependency format")
        return
    }

    val tomlFile = project.basePath?.let { File(it, "gradle/libs.versions.toml") }
    if (tomlFile == null || !tomlFile.exists()) {
        DialogUtil.showErrorMsg("libs.versions.toml not found")
        return
    }

    // Parse existing TOMTL content
    val tomlParser = Toml.parse(tomlFile)
    val libraries = tomlParser.getTable("libraries") ?: mutableMapOf<String, Map<String, String>>()
    val versions = tomlParser.getTable("versions") ?: mutableMapOf<String, String>()

    // Update or insert the new dependency
    val libraryKey = artifact.replace("-", "").replace(".", "")
    libraries[libraryKey] = mapOf(
        "group" to group,
        "name" to artifact,
        "version.ref" to artifact
    )
    versions[artifact] = version

    // Write updated TOML content back to the file
    val updatedTomlContent = buildString {
        append("[libraries]\n")
        libraries.forEach { (key, value) ->
            append("$key = { group = \"${value["group"]}\", name = \"${value["name"]}\", version.ref = \"${value["version.ref"]}\" }\n")
        }
        append("\n[versions]\n")
        versions.forEach { (key, value) ->
            append("$key = \"$value\"\n")
        }
    }
    tomlFile.writeText(updatedTomlContent)

    // Replace Gradle file dependency
    stringLiteral.replace(createTomlSnippet(project, "libs.$libraryKey"))
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
