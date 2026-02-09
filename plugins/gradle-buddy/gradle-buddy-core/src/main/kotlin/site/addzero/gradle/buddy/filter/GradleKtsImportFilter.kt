package site.addzero.gradle.buddy.filter

import com.intellij.codeInsight.ImportFilter
import com.intellij.psi.PsiFile

/**
 * 过滤 .gradle.kts 文件中的自动导入候选。
 *
 * Gradle Kotlin DSL 在构建时会生成带哈希码的 accessor 类，包名形如：
 * - gradle.kotlin.dsl.accessors._2971d23da8e99aaeb4234eeb6c3d106c.ksp
 * - gradle.kotlin.dsl.plugins._a3b4c5d6e7f8...
 *
 * 这些类被 IDE 的自动导入机制捞到后会产生无意义的 import 语句。
 * 本 filter 在 .gradle.kts 文件中拦截所有包含哈希段的导入候选。
 *
 * 判断规则：包名中出现 `_` 后跟 20 位以上十六进制字符的片段，
 * 视为 Gradle 生成的哈希产物，拒绝自动导入。
 */
class GradleKtsImportFilter : ImportFilter() {

    companion object {
        /**
         * 匹配包名中的哈希段：下划线后跟 20+ 位十六进制字符
         * 例如: _2971d23da8e99aaeb4234eeb6c3d106c
         */
        private val HASH_SEGMENT_PATTERN = Regex("""_[0-9a-f]{20,}""")
    }

    override fun shouldUseFullyQualifiedName(targetFile: PsiFile, classQualifiedName: String): Boolean {
        if (!targetFile.name.endsWith(".gradle.kts")) return false
        return HASH_SEGMENT_PATTERN.containsMatchIn(classQualifiedName)
    }
}
