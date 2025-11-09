package site.addzero.addl.action.dictgen.intention

import cn.hutool.core.util.StrUtil
import site.addzero.addl.action.dictgen.DictInfo
import site.addzero.addl.action.dictgen.DictItemInfo
import site.addzero.addl.action.dictgen.DictTemplateUtil
import site.addzero.addl.settings.MyPluginSettingsService
import site.addzero.addl.util.DialogUtil
import site.addzero.addl.util.JlStrUtil.toValidVariableName
import site.addzero.util.psi.PsiUtil.getFilePathPair
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtProperty

/**
 * 根据字段注释生成枚举类的 Intention Action
 */
class GenEnumByFieldCommentIntention : PsiElementBaseIntentionAction(), IntentionAction {

    private val settings = MyPluginSettingsService.getInstance().state


    override fun getText() = "GenEnumByFieldComment"
    override fun getFamilyName() = text

    override fun startInWriteAction(): Boolean = false

    /**
     * 递归查找父元素中的字段或属性
     */
    private fun findFieldOrProperty(element: PsiElement): PsiElement? {
        if (element is PsiField || element is KtProperty) {
            return element
        }

        var parent = element.parent
        while (parent != null) {
            if (parent is PsiField || parent is KtProperty) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // 检查是否在字段或属性上，并且有注释
        val field = findFieldOrProperty(element)
        val b = field != null && getComment(field) != null
        return b
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        // 获取字段或属性，并判断源文件类型
        val field = when {
            PsiTreeUtil.getParentOfType(element, PsiField::class.java) != null -> PsiTreeUtil.getParentOfType(
                element,
                PsiField::class.java
            )

            PsiTreeUtil.getParentOfType(element, KtProperty::class.java) != null -> PsiTreeUtil.getParentOfType(
                element,
                KtProperty::class.java
            )

            else -> return
        } ?: return

        // 根据字段类型判断是否为Kotlin文件
        val isKotlin = field is KtProperty

        // 获取注释文本
        val comment = getComment(field) ?: return

        // 解析注释中的枚举值
        val enumValues = parseEnumValues(comment).filter { value ->
                // 过滤掉 code 或 name 为空的值
//                value.code.isNotBlank() &&
                 value.name.isNotBlank()
            }.distinctBy { it.code } // 确保 code 唯一
            .distinctBy { it.name } // 确保 name 唯一

        // 如果没有有效的枚举值，则返回
        if (enumValues.isEmpty()) {
            DialogUtil.showWarningMsg("未检测到枚举注释")
            return
        }

        // 获取文件路径
        val filePath = (field as PsiElement).getFilePathPair()

        // 生成枚举类名
        val enumName = generateEnumName(field)

        // 构造 DictInfo 和 DictItemInfo 列表
        val dictInfo = DictInfo(
            id = "", // ID可以为空
            code = enumName,
            description = comment,
        )

        // 将 EnumValue 转换为 DictItemInfo
        val dictItemInfos = enumValues.map { enumValue ->
            DictItemInfo(
                dictId = "", // 外键ID可以为空
                itemCode = enumValue.code, itemDescription = enumValue.name.replace("_", " ").lowercase()
            )
        }

        // 构造 dictData map
        val dictData = mapOf(dictInfo to dictItemInfos)

        // 生成枚举类，传递isKotlin参数
        DictTemplateUtil.generateEnumsByMeta(project, dictData, filePath, isKotlin)
    }

    /**
     * 获取字段或属性的注释
     */
    private fun getComment(element: PsiElement): String? {
        return when (element) {
            is PsiField -> {
                element.docComment?.text ?: element.navigationElement.prevSibling?.text?.takeIf { it.startsWith("//") }
            }

            is KtProperty -> {
                element.docComment?.text ?: element.navigationElement.prevSibling?.text?.takeIf { it.startsWith("//") }
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
        val cleanComment = comment.replace(Regex("/\\*\\*|\\*/"), "") // 移除文档注释标记
            .replace(Regex("^\\s*\\*\\s*", RegexOption.MULTILINE), "") // 移除每行开头的星号
            .replace(Regex("^//\\s*"), "") // 移除行注释标记
            .trim()

        // 支持的分隔符模式
        val separatorPatterns = listOf(
            Regex("(null|[a-zA-Z0-9]+)[-]\\s*([^,，\\n]+)"),           // 短横线分隔
            Regex("(null|[a-zA-Z0-9]+)[:]\\s*([^,，\\n]+)"),           // 英文冒号分隔
            Regex("(null|[a-zA-Z0-9]+)[：]\\s*([^,，\\n]+)"),          // 中文冒号分隔
            Regex("(null|[a-zA-Z0-9]+)[=]\\s*([^,，\\n]+)"),           // 等号分隔
            Regex("(null|[a-zA-Z0-9]+)[.]\\s*([^,，\\n]+)"),           // 点号分隔
            Regex("(null|[a-zA-Z0-9]+)\\s+([^,，\\n]+)"),              // 空格分隔
            Regex("(null|[a-zA-Z0-9]+)\\s*[\\(（]([^\\)）]+)[\\)）]"), // 括号包围（支持中英文括号）
            Regex("(null|[a-zA-Z0-9]+)\\s*[\\[【]([^\\]】]+)[\\]】]")  // 方括号包围（支持中英文方括号）
        )

        // 按行分割，并清理每行
        return cleanComment.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { line ->
                // 尝试所有支持的分隔符模式
                for (pattern in separatorPatterns) {
                    val match = pattern.find(line)
                    if (match != null) {
                        val (code, name) = match.destructured
                        val trimmedName = name.trim()
                        if (trimmedName.isNotEmpty()) {
                            val trimmedCode = code.trim()
                            val finalCode = if (trimmedCode.equals("null", ignoreCase = true)) null else trimmedCode
                            return@mapNotNull EnumValue(toValidVariableName(trimmedName), finalCode)
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
        return upperFirst
    }


    /**
     * 枚举值数据类
     */
    private data class EnumValue(
        val name: String, val code: String?
    )
}
