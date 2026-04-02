package site.addzero.composebuddy.designer.model

import java.awt.Rectangle
import java.util.UUID

enum class ComposePaletteItem {
    TEXT,
    BUTTON,
    IMAGE,
    BOX,
    ROW,
    COLUMN,
    SPACER,
}

data class ComposeCanvasNode(
    val id: String = UUID.randomUUID().toString(),
    val kind: ComposePaletteItem,
    var bounds: Rectangle,
    var parentId: String? = null,
)

data class ComposeGeneratedCode(
    val imports: Set<String>,
    val functionText: String,
    val previewText: String,
)
