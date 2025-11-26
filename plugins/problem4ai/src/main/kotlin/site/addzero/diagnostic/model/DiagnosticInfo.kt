package site.addzero.diagnostic.model

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

enum class DiagnosticSeverity {
    ERROR, WARNING
}

data class DiagnosticItem(
    val file: VirtualFile,
    val psiFile: PsiFile?,
    val lineNumber: Int,
    val message: String,
    val severity: DiagnosticSeverity
) {
    fun toAiPrompt(): String = buildString {
        append("文件: ${file.name}\n")
        append("行号: $lineNumber\n")
        append("问题: $message\n")
        append("请帮我修复这个${if (severity == DiagnosticSeverity.ERROR) "错误" else "警告"}")
    }
}

data class FileDiagnostics(
    val file: VirtualFile,
    val psiFile: PsiFile?,
    val items: List<DiagnosticItem>
) {
    val hasErrors: Boolean get() = items.any { it.severity == DiagnosticSeverity.ERROR }
    val hasWarnings: Boolean get() = items.any { it.severity == DiagnosticSeverity.WARNING }
    
    fun toAiPrompt(): String = buildString {
        appendLine("=== 文件: ${file.name} ===")
        items.forEachIndexed { index, item ->
            appendLine("问题${index + 1}:")
            appendLine("  行号: ${item.lineNumber}")
            appendLine("  类型: ${if (item.severity == DiagnosticSeverity.ERROR) "错误" else "警告"}")
            appendLine("  内容: ${item.message}")
        }
        appendLine("请帮我修复以上问题。")
    }
}
