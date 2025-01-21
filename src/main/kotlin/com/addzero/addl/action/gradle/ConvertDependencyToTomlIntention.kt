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
            DialogUtil.showErrorMsg("依赖格式无效，应为：group:artifact:version")
            return
        }

        // 生成 TOML 格式
        val libraryKey = artifact.replace("-", "").replace(".", "")
        val tomlFormat = """
            [libraries]
            $libraryKey = { group = "$group", name = "$artifact", version.ref = "$artifact" }
            
            [versions]
            $artifact = "$version"
        """.trimIndent()

        // 显示转换结果并询问是否写入文件
        val tomlFile = project.basePath?.let { File(it, "gradle/libs.versions.toml") }
        if (tomlFile == null || !tomlFile.exists()) {
            DialogUtil.showInfoMsg("""
                转换结果：
                $tomlFormat
                
                请手动创建 gradle/libs.versions.toml 文件
            """.trimIndent())
            return
        }

        // 替换 Gradle 文件中的依赖
        stringLiteral.replace(createTomlSnippet(project, "libs.$libraryKey"))
        
        // 提示用户手动更新 TOML 文件
        DialogUtil.showInfoMsg("""
            已替换为版本目录引用，请手动更新 gradle/libs.versions.toml：
            
            $tomlFormat
        """.trimIndent())
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
