package site.addzero.composeblocks.editor

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import site.addzero.composeblocks.model.BlockSpec
import site.addzero.composeblocks.model.ComposeBlockType
import site.addzero.composeblocks.model.ManagedComposeDocument
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.TransferHandler
import javax.swing.border.CompoundBorder
import javax.swing.border.LineBorder
import javax.swing.border.MatteBorder

internal class ComposeBlocksBuilderCanvasPanel(
    private val onSelectBlock: (String) -> Unit,
    private val onInsertIntoContainer: (String, ComposeBlockType) -> Unit,
    private val onFillSlot: (String, ComposeBlockType) -> Unit,
) : JPanel(BorderLayout()) {

    private val contentPanel = JPanel(BorderLayout())
    private var selectedBlockId: String? = null
    private var slotFillLabels: Map<String, String> = emptyMap()

    init {
        border = JBUI.Borders.empty(8)
        add(
            JBLabel("Layout Canvas").apply {
                foreground = JBColor.GRAY
                border = JBUI.Borders.emptyBottom(8)
            },
            BorderLayout.NORTH,
        )
        add(contentPanel, BorderLayout.CENTER)
        render(null, null, emptyMap())
    }

    fun render(
        document: ManagedComposeDocument?,
        selectedBlockId: String?,
        slotFillLabels: Map<String, String>,
    ) {
        this.selectedBlockId = selectedBlockId
        this.slotFillLabels = slotFillLabels
        contentPanel.removeAll()
        if (document == null) {
            contentPanel.add(
                JBLabel("Managed Compose metadata is missing.").apply {
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.empty(12)
                },
                BorderLayout.NORTH,
            )
        } else {
            contentPanel.add(buildBlockComponent(document.root), BorderLayout.CENTER)
        }
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun buildBlockComponent(block: BlockSpec): JComponent {
        return when (block.type) {
            ComposeBlockType.SLOT -> createSlotComponent(block)
            ComposeBlockType.COLUMN,
            ComposeBlockType.ROW,
            ComposeBlockType.BOX,
            -> createContainerComponent(block)

            else -> createLeafComponent(block)
        }
    }

    private fun createContainerComponent(block: BlockSpec): JComponent {
        val panel = JPanel(BorderLayout(0, 8)).apply {
            isOpaque = true
            background = blockBackground(block.type, if (block.id == selectedBlockId) 52 else 26)
            border = CompoundBorder(
                CompoundBorder(
                    MatteBorder(0, 4, 0, 0, accentColor(block.type)),
                    LineBorder(if (block.id == selectedBlockId) accentColor(block.type) else JBColor.border(), 1, true),
                ),
                JBUI.Borders.empty(10, 12, 10, 12),
            )
        }

        val title = JBLabel(block.displayTitle).apply {
            font = font.deriveFont(java.awt.Font.BOLD.toFloat())
        }
        val meta = JBLabel("${block.type.displayName} · ${block.children.size} children").apply {
            foreground = JBColor.GRAY
            border = JBUI.Borders.emptyTop(4)
        }

        panel.add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(title, BorderLayout.NORTH)
                add(meta, BorderLayout.SOUTH)
            },
            BorderLayout.NORTH,
        )

        val childHost = when (block.type) {
            ComposeBlockType.ROW -> JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS) }
            ComposeBlockType.BOX -> createBoxHost(block)
            else -> JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        }.apply {
            isOpaque = false
        }

        if (block.type == ComposeBlockType.BOX && childHost is FractionalBoxPanel) {
            childHost.renderChildren(block.children.map(::buildBlockComponent), block.children)
        } else {
            block.children.forEachIndexed { index, child ->
                val childComponent = buildBlockComponent(child).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                    alignmentY = Component.TOP_ALIGNMENT
                }
                childHost.add(childComponent)
                if (index != block.children.lastIndex) {
                    if (block.type == ComposeBlockType.ROW) {
                        childHost.add(Box.createHorizontalStrut(10))
                    } else {
                        childHost.add(Box.createVerticalStrut(10))
                    }
                }
            }
            if (block.children.isEmpty()) {
                childHost.add(
                    JBLabel("Drop components here").apply {
                        foreground = JBColor.GRAY
                        border = JBUI.Borders.empty(8, 0)
                    }
                )
            }
        }

        installSelectionHandler(block.id, panel, title, meta)
        installContainerDropHandler(panel, block.id)
        panel.add(childHost, BorderLayout.CENTER)
        return panel
    }

    private fun createBoxHost(block: BlockSpec): JComponent {
        val hasPositionedChildren = block.children.any { child ->
            child.propValue("xFraction")?.isNotBlank() == true ||
                child.propValue("yFraction")?.isNotBlank() == true ||
                child.propValue("widthFraction")?.isNotBlank() == true ||
                child.propValue("heightFraction")?.isNotBlank() == true
        }
        return if (hasPositionedChildren) {
            FractionalBoxPanel()
        } else {
            JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        }
    }

    private fun createSlotComponent(block: BlockSpec): JComponent {
        val fillLabel = slotFillLabels[block.id]
        val panel = JPanel(BorderLayout(0, 6)).apply {
            isOpaque = true
            background = blockBackground(block.type, if (block.id == selectedBlockId) 50 else 20)
            border = CompoundBorder(
                CompoundBorder(
                    MatteBorder(0, 4, 0, 0, accentColor(block.type)),
                    LineBorder(if (block.id == selectedBlockId) accentColor(block.type) else JBColor.border(), 1, true),
                ),
                JBUI.Borders.empty(10, 12),
            )
            preferredSize = Dimension(180, 96)
        }
        val slotName = block.propValue("slotName").orEmpty().ifBlank { block.displayTitle }
        val title = JBLabel(slotName).apply {
            font = font.deriveFont(java.awt.Font.BOLD.toFloat())
        }
        val metaText = if (fillLabel.isNullOrBlank()) {
            "Named slot · drag a component here"
        } else {
            "Named slot · default content: $fillLabel"
        }
        val meta = JBLabel(metaText).apply {
            foreground = JBColor.GRAY
        }
        panel.add(title, BorderLayout.NORTH)
        panel.add(meta, BorderLayout.CENTER)
        installSelectionHandler(block.id, panel, title, meta)
        installSlotDropHandler(panel, block.id)
        return panel
    }

    private fun createLeafComponent(block: BlockSpec): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = blockBackground(block.type, if (block.id == selectedBlockId) 46 else 18)
            border = CompoundBorder(
                CompoundBorder(
                    MatteBorder(0, 4, 0, 0, accentColor(block.type)),
                    LineBorder(if (block.id == selectedBlockId) accentColor(block.type) else JBColor.border(), 1, true),
                ),
                JBUI.Borders.empty(10, 12),
            )
            preferredSize = Dimension(160, 72)
        }
        val title = JBLabel(block.displayTitle).apply {
            font = font.deriveFont(java.awt.Font.BOLD.toFloat())
        }
        val meta = JBLabel(block.type.displayName).apply {
            foreground = JBColor.GRAY
            border = JBUI.Borders.emptyTop(4)
        }
        panel.add(title, BorderLayout.NORTH)
        panel.add(meta, BorderLayout.SOUTH)
        installSelectionHandler(block.id, panel, title, meta)
        return panel
    }

    private fun installSelectionHandler(
        blockId: String,
        vararg components: JComponent,
    ) {
        val listener = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                onSelectBlock(blockId)
            }

            override fun mouseEntered(event: MouseEvent) {
                event.component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }
        }
        components.forEach { component ->
            component.addMouseListener(listener)
        }
    }

    private fun installContainerDropHandler(
        component: JComponent,
        containerId: String,
    ) {
        component.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor)
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) {
                    return false
                }
                val rawValue = support.transferable.getTransferData(DataFlavor.stringFlavor) as? String ?: return false
                val blockType = runCatching { ComposeBlockType.valueOf(rawValue) }.getOrNull() ?: return false
                onInsertIntoContainer(containerId, blockType)
                return true
            }
        }
    }

    private fun installSlotDropHandler(
        component: JComponent,
        slotBlockId: String,
    ) {
        component.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor)
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) {
                    return false
                }
                val rawValue = support.transferable.getTransferData(DataFlavor.stringFlavor) as? String ?: return false
                val blockType = runCatching { ComposeBlockType.valueOf(rawValue) }.getOrNull() ?: return false
                onFillSlot(slotBlockId, blockType)
                return true
            }
        }
    }

    private fun accentColor(type: ComposeBlockType): Color {
        return when (type) {
            ComposeBlockType.COLUMN -> JBColor(Color(41, 138, 112), Color(102, 196, 166))
            ComposeBlockType.ROW -> JBColor(Color(56, 120, 196), Color(118, 175, 242))
            ComposeBlockType.BOX -> JBColor(Color(185, 132, 56), Color(232, 183, 108))
            ComposeBlockType.SLOT -> JBColor(Color(164, 104, 192), Color(203, 146, 228))
            ComposeBlockType.TEXT,
            ComposeBlockType.BUTTON,
            ComposeBlockType.SPACER,
            ComposeBlockType.IMAGE,
            ComposeBlockType.TEXT_FIELD,
            -> JBColor(Color(124, 129, 138), Color(171, 176, 184))
        }
    }

    private fun blockBackground(
        type: ComposeBlockType,
        alpha: Int,
    ): Color {
        val base = accentColor(type)
        return Color(base.red, base.green, base.blue, alpha)
    }

    private inner class FractionalBoxPanel : JPanel(null) {
        private var blockChildren: List<BlockSpec> = emptyList()
        private var childComponents: List<JComponent> = emptyList()

        init {
            minimumSize = Dimension(360, 280)
            preferredSize = Dimension(420, 320)
            border = BorderFactory.createDashedBorder(JBColor.border())
        }

        fun renderChildren(
            components: List<JComponent>,
            blocks: List<BlockSpec>,
        ) {
            removeAll()
            childComponents = components
            blockChildren = blocks
            components.forEach(::add)
        }

        override fun doLayout() {
            val widthValue = width.coerceAtLeast(1)
            val heightValue = height.coerceAtLeast(1)
            childComponents.zip(blockChildren).forEach { (component, block) ->
                val x = ((block.propValue("xFraction")?.removeSuffix("f")?.toFloatOrNull() ?: 0f) * widthValue).toInt()
                val y = ((block.propValue("yFraction")?.removeSuffix("f")?.toFloatOrNull() ?: 0f) * heightValue).toInt()
                val childWidth = ((block.propValue("widthFraction")?.removeSuffix("f")?.toFloatOrNull() ?: 1f) * widthValue).toInt()
                val childHeight = ((block.propValue("heightFraction")?.removeSuffix("f")?.toFloatOrNull() ?: 1f) * heightValue).toInt()
                component.setBounds(
                    x.coerceAtLeast(0),
                    y.coerceAtLeast(0),
                    childWidth.coerceAtLeast(120),
                    childHeight.coerceAtLeast(72),
                )
            }
        }
    }
}
