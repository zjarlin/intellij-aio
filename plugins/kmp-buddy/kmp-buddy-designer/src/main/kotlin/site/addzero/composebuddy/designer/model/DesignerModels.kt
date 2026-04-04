package site.addzero.composebuddy.designer.model

import java.awt.Rectangle
import java.util.UUID

enum class ComposePaletteItem {
    TEXT,
    BUTTON,
    ICON,
    IMAGE,
    CARD,
    BOX,
    ROW,
    COLUMN,
    DIVIDER,
    SPACER,
    CUSTOM,
}

data class ComposeCanvasNode(
    val id: String = UUID.randomUUID().toString(),
    val kind: ComposePaletteItem,
    var bounds: Rectangle,
    var parentId: String? = null,
    var customName: String? = null,
    var customFunctionName: String? = null,
    var customLayoutKind: ComposePaletteItem? = null,
)

data class ComposeGeneratedCode(
    val imports: Set<String>,
    val functionText: String,
)
