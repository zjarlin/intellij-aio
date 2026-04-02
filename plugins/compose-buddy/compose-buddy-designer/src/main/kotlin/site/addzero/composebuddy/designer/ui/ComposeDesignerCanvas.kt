package site.addzero.composebuddy.designer.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.dnd.DnDConstants
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.TransferHandler

class ComposeDesignerCanvas(
    private val onChanged: (List<ComposeCanvasNode>) -> Unit,
) : JComponent(), Disposable {
    private val nodes = mutableListOf<ComposeCanvasNode>()
    private var selectedNodeId: String? = null
    private var selectionRect: Rectangle? = null
    private var operation: CanvasOperation = CanvasOperation.IDLE
    private var dragOrigin: Point? = null
    private var lastPointer: Point? = null

    init {
        preferredSize = Dimension(900, 700)
        border = JBUI.Borders.empty(12)
        background = JBColor.PanelBackground
        isOpaque = true
        transferHandler = CanvasTransferHandler()

        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                val hitNode = nodes.lastOrNull { it.bounds.contains(event.point) }
                selectedNodeId = hitNode?.id
                dragOrigin = event.point
                lastPointer = event.point
                operation = when {
                    hitNode != null && resizeHandle(hitNode).contains(event.point) -> CanvasOperation.RESIZE
                    hitNode != null -> CanvasOperation.MOVE
                    else -> {
                        selectionRect = Rectangle(event.point)
                        CanvasOperation.DRAW_SELECTION
                    }
                }
                repaint()
            }

            override fun mouseDragged(event: MouseEvent) {
                when (operation) {
                    CanvasOperation.DRAW_SELECTION -> updateSelectionRect(event.point)
                    CanvasOperation.MOVE -> moveSelection(event.point)
                    CanvasOperation.RESIZE -> resizeSelection(event.point)
                    CanvasOperation.IDLE -> Unit
                }
                lastPointer = event.point
                repaint()
            }

            override fun mouseMoved(event: MouseEvent) {
                val selected = selectedNode()
                cursor = if (selected != null && resizeHandle(selected).contains(event.point)) {
                    Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }

            override fun mouseReleased(event: MouseEvent) {
                when (operation) {
                    CanvasOperation.DRAW_SELECTION -> createContainerFromSelection()
                    CanvasOperation.MOVE -> reparentSelection()
                    CanvasOperation.RESIZE,
                    CanvasOperation.IDLE,
                    -> Unit
                }
                operation = CanvasOperation.IDLE
                dragOrigin = null
                lastPointer = null
                selectionRect = null
                fireChanged()
                repaint()
            }
        }

        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)
    }

    fun clear() {
        nodes.clear()
        selectedNodeId = null
        fireChanged()
        repaint()
    }

    fun addNode(kind: ComposePaletteItem, point: Point) {
        val bounds = defaultBoundsFor(kind, point)
        val parentId = ComposeDesignerLayoutSupport.assignParentForPoint(nodes, point)
        nodes += ComposeCanvasNode(kind = kind, bounds = bounds, parentId = parentId)
        selectedNodeId = nodes.lastOrNull()?.id
        fireChanged()
        repaint()
    }

    private fun selectedNode(): ComposeCanvasNode? = nodes.firstOrNull { it.id == selectedNodeId }

    private fun updateSelectionRect(point: Point) {
        val start = dragOrigin ?: return
        selectionRect = Rectangle(
            minOf(start.x, point.x),
            minOf(start.y, point.y),
            kotlin.math.abs(point.x - start.x),
            kotlin.math.abs(point.y - start.y),
        )
    }

    private fun moveSelection(point: Point) {
        val selected = selectedNode() ?: return
        val previous = lastPointer ?: return
        val dx = point.x - previous.x
        val dy = point.y - previous.y
        ComposeDesignerLayoutSupport.moveSubtree(nodes, selected.id, dx, dy)
    }

    private fun resizeSelection(point: Point) {
        val selected = selectedNode() ?: return
        val minWidth = 48
        val minHeight = 24
        selected.bounds = Rectangle(
            selected.bounds.x,
            selected.bounds.y,
            (point.x - selected.bounds.x).coerceAtLeast(minWidth),
            (point.y - selected.bounds.y).coerceAtLeast(minHeight),
        )
    }

    private fun createContainerFromSelection() {
        val rect = selectionRect ?: return
        if (rect.width <= 20 || rect.height <= 20) return
        val enclosed = ComposeDesignerLayoutSupport.collectChildrenInRect(nodes, rect)
            .filterNot { ComposeDesignerLayoutSupport.isContainer(it.kind) && it.bounds == rect }
        val kind = ComposeDesignerLayoutSupport.inferContainerKind(enclosed)
        val parentId = ComposeDesignerLayoutSupport.assignParentForBounds(nodes, rect)
        val container = ComposeCanvasNode(kind = kind, bounds = rect, parentId = parentId)
        nodes += container
        enclosed.forEach { child ->
            if (child.id != container.id && child.parentId == parentId) {
                child.parentId = container.id
            }
        }
        selectedNodeId = container.id
    }

    private fun reparentSelection() {
        val selected = selectedNode() ?: return
        if (selected.kind == ComposePaletteItem.ROW || selected.kind == ComposePaletteItem.COLUMN || selected.kind == ComposePaletteItem.BOX) {
            return
        }
        selected.parentId = ComposeDesignerLayoutSupport.assignParentForBounds(nodes, selected.bounds, excludingNodeId = selected.id)
    }

    private fun defaultBoundsFor(kind: ComposePaletteItem, point: Point): Rectangle {
        val width = when (kind) {
            ComposePaletteItem.TEXT -> 160
            ComposePaletteItem.BUTTON -> 180
            ComposePaletteItem.IMAGE -> 160
            ComposePaletteItem.BOX -> 220
            ComposePaletteItem.ROW -> 220
            ComposePaletteItem.COLUMN -> 220
            ComposePaletteItem.SPACER -> 100
        }
        val height = when (kind) {
            ComposePaletteItem.TEXT -> 48
            ComposePaletteItem.BUTTON -> 56
            ComposePaletteItem.IMAGE -> 120
            ComposePaletteItem.BOX -> 140
            ComposePaletteItem.ROW -> 120
            ComposePaletteItem.COLUMN -> 120
            ComposePaletteItem.SPACER -> 24
        }
        return Rectangle(point.x, point.y, width, height)
    }

    private fun fireChanged() {
        onChanged(nodes.map { it.copy(bounds = Rectangle(it.bounds), parentId = it.parentId) })
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g2 = graphics as Graphics2D
        g2.color = background
        g2.fillRect(0, 0, width, height)
        drawGrid(g2)

        if (nodes.isEmpty()) {
            g2.color = JBColor.GRAY
            g2.drawString(ComposeBuddyBundle.message("designer.canvas.empty"), 24, 32)
        }

        nodes.sortedBy { depthOf(it) }.forEach { node ->
            val isSelected = node.id == selectedNodeId
            val fillColor = when {
                isSelected -> JBColor(Color(0x3D, 0x7A, 0xCC), Color(0x65, 0xA9, 0xFF))
                ComposeDesignerLayoutSupport.isContainer(node.kind) -> JBColor(Color(0xE8, 0xF0, 0xF6), Color(0x2F, 0x36, 0x40))
                else -> JBColor(Color(0xD6, 0xE4, 0xF0), Color(0x35, 0x3B, 0x45))
            }
            g2.color = fillColor
            g2.fillRoundRect(node.bounds.x, node.bounds.y, node.bounds.width, node.bounds.height, 16, 16)
            g2.color = if (isSelected) JBColor(Color(0x1B, 0x4E, 0x8C), Color(0x91, 0xC7, 0xFF)) else JBColor.border()
            g2.stroke = BasicStroke(if (isSelected) 2.5f else 1.5f)
            g2.drawRoundRect(node.bounds.x, node.bounds.y, node.bounds.width, node.bounds.height, 16, 16)
            g2.drawString(nodeLabel(node.kind), node.bounds.x + 12, node.bounds.y + 24)
            g2.drawString("Modifier.offset(...).size(...)", node.bounds.x + 12, node.bounds.y + 44)

            if (isSelected) {
                val handle = resizeHandle(node)
                g2.fillRect(handle.x, handle.y, handle.width, handle.height)
            }
        }

        selectionRect?.let { rect ->
            g2.color = JBColor(Color(0x4D, 0x8B, 0xD6, 80), Color(0x7B, 0xA7, 0xE8, 80))
            g2.fill(rect)
            g2.color = JBColor(Color(0x2D, 0x6E, 0xC4), Color(0x9E, 0xC7, 0xFF))
            g2.stroke = BasicStroke(2f)
            g2.draw(rect)
            g2.drawString(ComposeBuddyBundle.message("designer.canvas.selection"), rect.x + 8, rect.y + 18)
        }
    }

    private fun depthOf(node: ComposeCanvasNode): Int {
        var depth = 0
        var currentParent = node.parentId
        while (currentParent != null) {
            depth++
            currentParent = nodes.firstOrNull { it.id == currentParent }?.parentId
        }
        return depth
    }

    private fun resizeHandle(node: ComposeCanvasNode): Rectangle {
        return Rectangle(node.bounds.x + node.bounds.width - 10, node.bounds.y + node.bounds.height - 10, 10, 10)
    }

    private fun drawGrid(g2: Graphics2D) {
        g2.color = JBColor(Color(0xF0, 0xF4, 0xF8), Color(0x2B, 0x2F, 0x36))
        for (x in 0 until width step 24) {
            g2.drawLine(x, 0, x, height)
        }
        for (y in 0 until height step 24) {
            g2.drawLine(0, y, width, y)
        }
    }

    private fun nodeLabel(kind: ComposePaletteItem): String {
        return when (kind) {
            ComposePaletteItem.TEXT -> ComposeBuddyBundle.message("designer.node.text")
            ComposePaletteItem.BUTTON -> ComposeBuddyBundle.message("designer.node.button")
            ComposePaletteItem.IMAGE -> ComposeBuddyBundle.message("designer.node.image")
            ComposePaletteItem.BOX -> ComposeBuddyBundle.message("designer.node.box")
            ComposePaletteItem.ROW -> ComposeBuddyBundle.message("designer.node.row")
            ComposePaletteItem.COLUMN -> ComposeBuddyBundle.message("designer.node.column")
            ComposePaletteItem.SPACER -> ComposeBuddyBundle.message("designer.node.spacer")
        }
    }

    override fun dispose() = Unit

    private enum class CanvasOperation {
        IDLE,
        DRAW_SELECTION,
        MOVE,
        RESIZE,
    }

    inner class CanvasTransferHandler : TransferHandler() {
        override fun canImport(support: TransferSupport): Boolean {
            return support.isDataFlavorSupported(DataFlavor.stringFlavor)
        }

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false
            val value = support.transferable.getTransferData(DataFlavor.stringFlavor) as? String ?: return false
            val kind = runCatching { ComposePaletteItem.valueOf(value) }.getOrNull() ?: return false
            addNode(kind, support.dropLocation.dropPoint)
            return true
        }

        override fun getSourceActions(c: JComponent): Int = DnDConstants.ACTION_COPY

        override fun createTransferable(c: JComponent): StringSelection? = null
    }
}
