package com.addzero.addl.intention

import cn.hutool.core.util.ClassUtil.getPackagePath
import cn.hutool.core.util.StrUtil
import com.addzero.addl.action.anycodegen.AbsGen
import com.addzero.addl.settings.MyPluginSettingsService
import com.addzero.addl.util.JlStrUtil.toValidVariableName
import com.addzero.addl.util.ShowContentUtil
import com.addzero.addl.util.fieldinfo.PsiUtil
import com.addzero.addl.util.fieldinfo.PsiUtil.psiCtx
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * 根据字段注释生成枚举类的 Intention Action
 */
class GenEnumByFieldCommentIntention : PsiElementBaseIntentionAction(), IntentionAction {

    private val settings = MyPluginSettingsService.getInstance().state

    // 获取配置的分隔符，如果为空则使用默认值 "-"
    private val separator: String
        get() = settings.enumSeparator.takeIf { it.isNotBlank() } ?: "-"

    override fun getText() = "GenEnumByFieldComment"
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // 检查是否在字段或属性上，并且有注释
        val field = when {
            PsiTreeUtil.getParentOfType(element, PsiField::class.java) != null ->
                PsiTreeUtil.getParentOfType(element, PsiField::class.java)
            PsiTreeUtil.getParentOfType(element, KtProperty::class.java) != null ->
                PsiTreeUtil.getParentOfType(element, KtProperty::class.java)
            else -> null
        }

        return field != null && getComment(field) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        // 获取字段或属性
        val field = when {
            PsiTreeUtil.getParentOfType(element, PsiField::class.java) != null ->
                PsiTreeUtil.getParentOfType(element, PsiField::class.java)
            PsiTreeUtil.getParentOfType(element, KtProperty::class.java) != null ->
                PsiTreeUtil.getParentOfType(element, KtProperty::class.java)
            else -> return
        } ?: return

        // 获取注释文本
        val comment = getComment(field) ?: return

        // 解析注释中的枚举值
        val enumValues = parseEnumValues(comment)
            .filter { value ->
                // 过滤掉 code 或 name 为空的值
                value.code.isNotBlank() && value.name.isNotBlank()
            }
            .distinctBy { it.code } // 确保 code 唯一
            .distinctBy { it.name } // 确保 name 唯一

        // 如果没有有效的枚举值，则返回
        if (enumValues.isEmpty()) return

        val (editor, psiClass, ktClass, psiFile, virtualFile, classPath) = psiCtx(project)
        val packagePath = PsiUtil. getPackagePath(psiFile)
        val qualifiedClassName = PsiUtil. getQualifiedClassName(psiFile!!)
        // 获取目标包名

        // 生成枚举类名
        val enumName = generateEnumName(field)

        // 生成枚举类代码
        val enumCode = generateEnumCode(packagePath!!, enumName, enumValues)


        // 将生成的 SQL 语句写入到新的文件并打开
        ShowContentUtil.openTextInEditor(
            project,
            enumCode,
            "Enum",
            ".kt"
        )

    }

    /**
     * 获取字段或属性的注释
     */
    private fun getComment(element: PsiElement): String? {
        return when (element) {
            is PsiField -> {
                element.docComment?.text
                    ?: element.navigationElement.prevSibling?.text?.takeIf { it.startsWith("//") }
            }
            is KtProperty -> {
                element.docComment?.text
                    ?: element.navigationElement.prevSibling?.text?.takeIf { it.startsWith("//") }
            }
            else -> null
        }
    }

    /**
     * 解析注释中的枚举值
     * 支持以下格式：
     * 1. 短横线：1-男, 2-女
     * 2. 冒号：1:男, 2:女
     * 3. 中文冒号：1：男, 2：女
     * 4. 等号：1=男, 2=女
     * 5. 点：1.男, 2.女
     * 6. 空格：1 男, 2 女
     * 7. 括号：1(男), 2(女)
     * 8. 中文括号：1（男）, 2（女）
     */
    private fun parseEnumValues(comment: String): List<EnumValue> {
        // 清理注释标记并分行处理
        val cleanComment = comment
            .replace(Regex("/\\*\\*|\\*/"), "") // 移除文档注释标记
            .replace(Regex("^\\s*\\*\\s*", RegexOption.MULTILINE), "") // 移除每行开头的星号
            .replace(Regex("^//\\s*"), "") // 移除行注释标记
            .trim()

        // 支持的分隔符模式
        val separatorPatterns = listOf(
            Regex("(\\d+)[-]\\s*([^,，\n]+)"),           // 短横线分隔
            Regex("(\\d+)[:]\\s*([^,，\n]+)"),           // 英文冒号分隔
            Regex("(\\d+)[：]\\s*([^,，\n]+)"),          // 中文冒号分隔
            Regex("(\\d+)[=]\\s*([^,，\n]+)"),           // 等号分隔
            Regex("(\\d+)[.]\\s*([^,，\n]+)"),           // 点号分隔
            Regex("(\\d+)\\s+([^,，\n]+)"),              // 空格分隔
            Regex("(\\d+)\\s*[\\(（]([^\\)）]+)[\\)）]"), // 括号包围（支持中英文括号）
            Regex("(\\d+)\\s*[\\[【]([^\\]】]+)[\\]】]")  // 方括号包围（支持中英文方括号）
        )

        // 按行分割，并清理每行
        return cleanComment
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                // 尝试所有支持的分隔符模式
                for (pattern in separatorPatterns) {
                    val match = pattern.find(line)
                    if (match != null) {
                        val (code, name) = match.destructured
                        val trimmedName = name.trim()
                        if (trimmedName.isNotEmpty()) {
                            return@mapNotNull EnumValue(toValidVariableName(trimmedName), code.trim())
                        }
                    }
                }
                null
            }
    }

    /**
     * 生成枚举类名
     */
    private fun generateEnumName(field: PsiElement): String {
        val baseName = when (field) {
            is PsiField -> field.name
            is KtProperty -> field.name
            else -> "Unknown"
        }
        val upperFirst = StrUtil.upperFirst(baseName)
        return "Enum$upperFirst"
    }


    /**
     * 生成枚举类代码
     */
    private fun generateEnumCode(packageName: String, enumName: String, values: List<EnumValue>): String {
        val hasCode = values.any { it.code != null }

        return buildString {
            appendLine("package $packageName;")
            appendLine()
            appendLine("/**")
            appendLine(" * 自动生成的枚举类")
            appendLine(" */")
            appendLine("public enum $enumName {")

            // 枚举值
            values.forEachIndexed { index, value ->
                append("    /**")
                appendLine()
                append("     * ${value.name.replace("_", " ").lowercase()}")
                appendLine()
                append("     */")
                appendLine()
                append("    ")
                append(value.name)
                if (hasCode) {
                    append("(")
                    append(value.code ?: index)
                    append(")")
                }
                if (index < values.size - 1) append(",")
                appendLine()
            }
            appendLine(";")

            // 如果有编码，添加code字段和相关方法
            if (hasCode) {
                appendLine()
                appendLine("    private final int code;")
                appendLine()
                appendLine("    $enumName(int code) {")
                appendLine("        this.code = code;")
                appendLine("    }")
                appendLine()
                appendLine("    public int getCode() {")
                appendLine("        return code;")
                appendLine("    }")
                appendLine()
                appendLine("    public static $enumName fromCode(int code) {")
                appendLine("        for ($enumName value : values()) {")
                appendLine("            if (value.code == code) {")
                appendLine("                return value;")
                appendLine("            }")
                appendLine("        }")
                appendLine("        throw new IllegalArgumentException(\"Unknown code: \" + code);")
                appendLine("    }")
            }

            appendLine("}")
        }
    }

    /**
     * 枚举值数据类
     */
    private data class EnumValue(
        val name: String,
        val code: String
    )
}