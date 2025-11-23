package site.addzero.util.lsi_impl.impl.kt.`fun`

import site.addzero.util.lsi_impl.impl.kt.anno.getArg
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import site.addzero.util.str.cleanDocComment
import site.addzero.util.str.removeAnyQuote

/**
 * Check if function is top-level (parent is KtFile)
 */
fun KtFunction.isTopLevel(): Boolean {
    return this.parent is KtFile
}

/**
 * Check if function is static (top-level or in companion object)
 */
fun KtFunction.isStaticFunction(): Boolean {
    // Check if function is top-level (parent is KtFile)
    val isTopLevel = isTopLevel()
    // Check if function is in companion object
    val isInCompanion = hasModifier(KtTokens.COMPANION_KEYWORD)
    return isTopLevel || isInCompanion
}

fun KtFunction.getComment(): String? {
    // 尝试从注解中获取描述
    this.annotationEntries.forEach { annotation ->
        val shortName = annotation.shortName?.asString()
        val description = when (shortName) {
            "ApiModelProperty" -> {
                // 获取第一个参数（value）
                val arg = annotation.getArg()
                arg
            }
            "Schema" -> {
                val arg = annotation.getArg("description")
                arg
            }
            else -> null
        }
        if (!description.isNullOrBlank()) {
            return description.removeAnyQuote()
        }
    }

    return cleanDocComment(this.docComment?.text)
}
