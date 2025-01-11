package com.addzero.addl.action.enumgen

import com.addzero.addl.action.dictgen.DictItemInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.addzero.addl.util.JlStrUtil.toPascalCase

fun generateEnum(project: Project, fieldName: String, items: List<DictItemInfo>) {
    val enumName = "Enum${fieldName.toPascalCase()}"
    val enumContent = buildString {
        appendLine("enum class $enumName(val code: String, val description: String) {")
        items.forEach { item ->
            val enumConstant = item.itemCode.uppercase()
            appendLine("    $enumConstant(\"${item.itemCode}\", \"${item.itemDescription}\"),")
        }
        appendLine()
        appendLine("    companion object {")
        appendLine("        fun fromCode(code: String): $enumName? = values().find { it.code == code }")
        appendLine("    }")
        appendLine("}")
    }

    // 创建枚举文件
    val psiFileFactory = PsiFileFactory.getInstance(project)
    val enumFile = psiFileFactory.createFileFromText("$enumName.kt", enumContent)

    // 保存到项目中
    val baseDir = project.baseDir
    val psiDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(baseDir)
    psiDirectory.add(enumFile)
}