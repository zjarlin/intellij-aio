package site.addzero.composeblocks.model

import com.intellij.openapi.util.TextRange

enum class ComposeBlockKind {
    ROOT,
    CONTAINER,
    LEAF,
    SHELL,
}

enum class ComposeBlockAxis {
    VERTICAL,
    HORIZONTAL,
    STACK,
}

enum class ComposeEditableContainerKind {
    ROW,
    COLUMN,
    BOX,
}

data class ComposeBlockNode(
    val id: String,
    val name: String,
    val kind: ComposeBlockKind,
    val axis: ComposeBlockAxis,
    val focusRange: TextRange,
    val renderRange: TextRange,
    val contentRange: TextRange?,
    val navigationOffset: Int,
    val commentText: String?,
    val commentRange: TextRange?,
    val editableContainerKind: ComposeEditableContainerKind?,
    val isLowCodeEditable: Boolean,
    val argumentListRange: TextRange?,
    val argumentInsertOffset: Int?,
    val children: List<ComposeBlockNode>,
) {
    val displayTitle: String
        get() = commentText?.takeIf { it.isNotBlank() } ?: name
}
