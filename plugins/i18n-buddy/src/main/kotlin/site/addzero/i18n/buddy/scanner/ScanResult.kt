package site.addzero.i18n.buddy.scanner

import com.intellij.openapi.vfs.VirtualFile

/**
 * A single hardcoded string found during scanning.
 */
data class ScanResult(
    /** The raw string content (without quotes), e.g. "你好" */
    val text: String,
    /** The file containing this string */
    val file: VirtualFile,
    /** File path relative to project root */
    val relativePath: String,
    /** Line number (0-based) */
    val line: Int,
    /** Start offset in the document */
    val startOffset: Int,
    /** End offset in the document (covers the entire string literal including quotes) */
    val endOffset: Int,
    /** Generated constant key, e.g. "NI_HAO" */
    var generatedKey: String = "",
    /** Whether the user selected this item for conversion */
    var selected: Boolean = true,
    /** Whether this string is inside a @Composable function (→ i18n wrap) or not (→ constant only) */
    val inComposable: Boolean = false,
)
