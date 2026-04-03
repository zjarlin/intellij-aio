package site.addzero.composebuddy.designer.ui

import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposeDesignerPaletteCatalog
import site.addzero.composebuddy.designer.model.ComposePaletteEntry
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
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.TransferHandler

class ComposeDesignerCanvas(
    private val onChanged: (List<ComposeCanvasNode>) -> Unit,
) : JComponent(), Disposable {
    private enum class ResizeAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    private val nodes = mutableListOf<ComposeCanvasNode>()
    private val rootNodeId = "designer-root-box"
    private var selectedNodeId: String? = null
    private var selectionRect: Rectangle? = null
    private var operation: CanvasOperation = CanvasOperation.IDLE
    private var dragOrigin: Point? = null
    private var lastPointer: Point? = null
    private var dragNodeOriginBounds: Rectangle? = null
    private var resizeAnchor: ResizeAnchor? = null
    private var snapGuides = ComposeDesignerLayoutSupport.SnapGuides()
    private var dropPreview: ComposeDesignerLayoutSupport.DropPreview? = null

    init {
        preferredSize = Dimension(900, 700)
        border = JBUI.Borders.empty(12)
        background = JBColor.PanelBackground
        isOpaque = true
        transferHandler = CanvasTransferHandler()
        initializeRootCanvas()

        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                val hitNode = hitNodeAt(event.point)
                selectedNodeId = hitNode?.id ?: rootNodeId
                requestFocusInWindow()
                dragOrigin = event.point
                lastPointer = event.point
                dragNodeOriginBounds = hitNode?.bounds?.let(::Rectangle)
                resizeAnchor = hitNode?.let { resizeAnchorAt(it, event.point) }
                operation = when {
                    hitNode != null && resizeAnchor != null -> CanvasOperation.RESIZE
                    hitNode != null && hitNode.id != rootNodeId -> CanvasOperation.MOVE
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
                    CanvasOperation.MOVE -> {
                        moveSelection(event.point)
                        updateDropPreview(event.point)
                    }
                    CanvasOperation.RESIZE -> resizeSelection(event.point)
                    CanvasOperation.IDLE -> Unit
                }
                lastPointer = event.point
                repaint()
            }

            override fun mouseMoved(event: MouseEvent) {
                val selected = selectedNode()
                cursor = selected
                    ?.let { resizeAnchorAt(it, event.point) }
                    ?.let { anchorCursor(it) }
                    ?: Cursor.getDefaultCursor()
            }

            override fun mouseReleased(event: MouseEvent) {
                when (operation) {
                    CanvasOperation.DRAW_SELECTION -> createContainerFromSelection()
                    CanvasOperation.MOVE -> reparentSelection()
                    CanvasOperation.RESIZE -> relayoutSelectedParent()
                    CanvasOperation.IDLE,
                    -> Unit
                }
                operation = CanvasOperation.IDLE
                dragOrigin = null
                lastPointer = null
                dragNodeOriginBounds = null
                resizeAnchor = null
                selectionRect = null
                snapGuides = ComposeDesignerLayoutSupport.SnapGuides()
                dropPreview = null
                fireChanged()
                repaint()
            }
        }

        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)
        isFocusable = true
        val deleteAction = object : AbstractAction() {
            override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                deleteSelected()
            }
        }
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "compose.designer.delete")
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "compose.designer.delete")
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "compose.designer.delete")
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "compose.designer.delete")
        actionMap.put("compose.designer.delete", deleteAction)
    }

    fun clear() {
        nodes.clear()
        initializeRootCanvas()
        fireChanged()
        repaint()
    }

    fun snapshot(): List<ComposeCanvasNode> {
        return nodes
            .filterNot { it.id == rootNodeId }
            .map {
                it.copy(
                    bounds = Rectangle(it.bounds),
                    parentId = it.parentId?.takeIf { parentId -> parentId != rootNodeId },
                )
            }
    }

    fun replaceFromCode(parsedNodes: List<ComposeCanvasNode>) {
        nodes.clear()
        initializeRootCanvas()
        nodes += parsedNodes.map { it.copy(bounds = Rectangle(it.bounds), parentId = it.parentId ?: rootNodeId) }
        selectedNodeId = rootNodeId
        fireChanged()
        repaint()
    }

    fun deleteSelected() {
        val selectedId = selectedNodeId ?: return
        if (selectedId == rootNodeId) {
            return
        }
        val toDelete = ComposeDesignerLayoutSupport.descendantsOf(nodes, selectedId).map { it.id }.toSet()
        val parentId = nodes.firstOrNull { it.id == selectedId }?.parentId
        nodes.removeAll { it.id in toDelete }
        selectedNodeId = parentId ?: rootNodeId
        ComposeDesignerLayoutSupport.relayoutAutoContainersFrom(nodes, parentId)
        fireChanged()
        repaint()
    }

    fun addNode(entry: ComposePaletteEntry, point: Point) {
        val kind = entry.kind
        val parentId = ComposeDesignerLayoutSupport.assignParentForPoint(nodes, point)
        val parent = nodes.firstOrNull { it.id == parentId }
        val bounds = ComposeDesignerLayoutSupport.childBoundsForParent(
            nodes = nodes,
            parent = parent,
            requestedBounds = defaultBoundsFor(entry, point),
            kind = kind,
        )
        nodes += ComposeCanvasNode(
            kind = kind,
            bounds = bounds,
            parentId = parentId,
            customName = entry.customComponent?.displayName,
            customFunctionName = entry.customComponent?.functionName,
            customLayoutKind = entry.customComponent?.layoutKind,
        )
        selectedNodeId = nodes.lastOrNull()?.id
        ComposeDesignerLayoutSupport.relayoutAutoContainersFrom(nodes, parentId)
        fireChanged()
        repaint()
    }

    private fun selectedNode(): ComposeCanvasNode? = nodes.firstOrNull { it.id == selectedNodeId }

    private fun hitNodeAt(point: Point): ComposeCanvasNode? {
        return nodes
            .filter { it.bounds.contains(point) }
            .sortedWith(
                compareByDescending<ComposeCanvasNode> { depthOf(it) }
                    .thenBy { it.bounds.width * it.bounds.height }
                    .thenByDescending { nodes.indexOf(it) },
            )
            .firstOrNull()
    }

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
        val start = dragOrigin ?: return
        val originBounds = dragNodeOriginBounds ?: return
        val proposed = Rectangle(
            originBounds.x + (point.x - start.x),
            originBounds.y + (point.y - start.y),
            originBounds.width,
            originBounds.height,
        )
        val (snappedBounds, guides) = ComposeDesignerLayoutSupport.snapBounds(nodes, selected, proposed)
        val dx = snappedBounds.x - selected.bounds.x
        val dy = snappedBounds.y - selected.bounds.y
        snapGuides = guides
        ComposeDesignerLayoutSupport.moveSubtree(nodes, selected.id, dx, dy)
    }

    private fun updateDropPreview(point: Point) {
        val selected = selectedNode() ?: return
        dropPreview = ComposeDesignerLayoutSupport.dropPreview(nodes, selected, point)
    }

    private fun resizeSelection(point: Point) {
        val selected = selectedNode() ?: return
        val originBounds = dragNodeOriginBounds ?: return
        val anchor = resizeAnchor ?: ResizeAnchor.BOTTOM_RIGHT
        val minWidth = 48
        val minHeight = 24
        val resized = when (anchor) {
            ResizeAnchor.TOP_LEFT -> {
                val maxX = originBounds.x + originBounds.width - minWidth
                val maxY = originBounds.y + originBounds.height - minHeight
                val nextX = point.x.coerceAtMost(maxX)
                val nextY = point.y.coerceAtMost(maxY)
                Rectangle(
                    nextX,
                    nextY,
                    (originBounds.x + originBounds.width - nextX).coerceAtLeast(minWidth),
                    (originBounds.y + originBounds.height - nextY).coerceAtLeast(minHeight),
                )
            }

            ResizeAnchor.TOP_RIGHT -> {
                val maxY = originBounds.y + originBounds.height - minHeight
                val nextY = point.y.coerceAtMost(maxY)
                Rectangle(
                    originBounds.x,
                    nextY,
                    (point.x - originBounds.x).coerceAtLeast(minWidth),
                    (originBounds.y + originBounds.height - nextY).coerceAtLeast(minHeight),
                )
            }

            ResizeAnchor.BOTTOM_LEFT -> {
                val maxX = originBounds.x + originBounds.width - minWidth
                val nextX = point.x.coerceAtMost(maxX)
                Rectangle(
                    nextX,
                    originBounds.y,
                    (originBounds.x + originBounds.width - nextX).coerceAtLeast(minWidth),
                    (point.y - originBounds.y).coerceAtLeast(minHeight),
                )
            }

            ResizeAnchor.BOTTOM_RIGHT -> Rectangle(
                originBounds.x,
                originBounds.y,
                (point.x - originBounds.x).coerceAtLeast(minWidth),
                (point.y - originBounds.y).coerceAtLeast(minHeight),
            )
        }
        val parent = nodes.firstOrNull { it.id == selected.parentId }
        selected.bounds = if (parent != null) {
            ComposeDesignerLayoutSupport.keepInsideParent(resized, parent, selected.kind)
        } else {
            resized
        }
    }

    private fun createContainerFromSelection() {
        val rect = selectionRect ?: return
        if (rect.width <= 20 || rect.height <= 20) return
        val enclosed = ComposeDesignerLayoutSupport.collectChildrenInRect(nodes, rect)
            .filterNot { ComposeDesignerLayoutSupport.isContainer(it.kind) && it.bounds == rect }
        val kind = ComposeDesignerLayoutSupport.inferContainerKind(enclosed)
        val parentId = ComposeDesignerLayoutSupport.assignParentForBounds(nodes, rect)
        val parent = nodes.firstOrNull { it.id == parentId }
        val container = ComposeCanvasNode(
            kind = kind,
            bounds = ComposeDesignerLayoutSupport.childBoundsForParent(
                nodes = nodes,
                parent = parent,
                requestedBounds = rect,
                kind = kind,
            ),
            parentId = parentId,
        )
        nodes += container
        enclosed.forEach { child ->
            if (child.id != container.id && child.parentId == parentId) {
                child.parentId = container.id
            }
        }
        selectedNodeId = container.id
        ComposeDesignerLayoutSupport.relayoutAutoContainersFrom(nodes, container.id)
        ComposeDesignerLayoutSupport.relayoutAutoContainersFrom(nodes, parentId)
    }

    private fun reparentSelection() {
        val selected = selectedNode() ?: return
        val previousParentId = selected.parentId
        val preview = dropPreview ?: ComposeDesignerLayoutSupport.dropPreview(nodes, selected, lastPointer)
        val excludedIds = ComposeDesignerLayoutSupport.descendantsOf(nodes, selected.id).map { it.id }.toSet()
        val parentId = preview.parentId ?: ComposeDesignerLayoutSupport.assignParentForDrop(
            nodes = nodes,
            bounds = selected.bounds,
            pointer = lastPointer,
            excludingNodeIds = excludedIds,
        )
        selected.parentId = parentId
        val parent = nodes.firstOrNull { it.id == parentId }
        if (parent != null) {
            selected.bounds = preview.previewBounds ?: ComposeDesignerLayoutSupport.childBoundsForParent(
                nodes = nodes,
                parent = parent,
                requestedBounds = selected.bounds,
                kind = selected.kind,
                excludingNodeId = selected.id,
            )
        }
        ComposeDesignerLayoutSupport.relayoutAutoContainersFrom(nodes, previousParentId)
        ComposeDesignerLayoutSupport.relayoutAutoContainersFrom(nodes, parentId)
    }

    private fun relayoutSelectedParent() {
        val selected = selectedNode() ?: return
        ComposeDesignerLayoutSupport.relayoutAutoContainersFrom(nodes, selected.parentId)
    }

    private fun defaultBoundsFor(entry: ComposePaletteEntry, point: Point): Rectangle {
        val width = when (entry.kind) {
            ComposePaletteItem.TEXT -> 160
            ComposePaletteItem.BUTTON -> 180
            ComposePaletteItem.IMAGE -> 160
            ComposePaletteItem.BOX -> 220
            ComposePaletteItem.ROW -> 220
            ComposePaletteItem.COLUMN -> 220
            ComposePaletteItem.SPACER -> 100
            ComposePaletteItem.CUSTOM -> entry.customComponent?.width ?: 180
        }
        val height = when (entry.kind) {
            ComposePaletteItem.TEXT -> 48
            ComposePaletteItem.BUTTON -> 56
            ComposePaletteItem.IMAGE -> 120
            ComposePaletteItem.BOX -> 140
            ComposePaletteItem.ROW -> 120
            ComposePaletteItem.COLUMN -> 120
            ComposePaletteItem.SPACER -> 24
            ComposePaletteItem.CUSTOM -> entry.customComponent?.height ?: 56
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
            val isDropTarget = node.id == dropPreview?.parentId && node.id != selectedNodeId
            val fillColor = when {
                isSelected -> JBColor(Color(0x3D, 0x7A, 0xCC), Color(0x65, 0xA9, 0xFF))
                isDropTarget -> JBColor(Color(0xC9, 0xE2, 0xFF), Color(0x3B, 0x4A, 0x5E))
                ComposeDesignerLayoutSupport.isContainer(node.kind) -> JBColor(Color(0xE8, 0xF0, 0xF6), Color(0x2F, 0x36, 0x40))
                else -> JBColor(Color(0xD6, 0xE4, 0xF0), Color(0x35, 0x3B, 0x45))
            }
            g2.color = fillColor
            g2.fillRoundRect(node.bounds.x, node.bounds.y, node.bounds.width, node.bounds.height, 22, 22)
            g2.color = when {
                isSelected -> JBColor(Color(0x1B, 0x4E, 0x8C), Color(0x91, 0xC7, 0xFF))
                isDropTarget -> JBColor(Color(0x2D, 0x6E, 0xC4), Color(0x9E, 0xC7, 0xFF))
                else -> JBColor.border()
            }
            g2.stroke = BasicStroke(if (isSelected || isDropTarget) 2.5f else 1.5f)
            g2.drawRoundRect(node.bounds.x, node.bounds.y, node.bounds.width, node.bounds.height, 22, 22)
            g2.drawString(nodeLabel(node), node.bounds.x + 12, node.bounds.y + 24)
            g2.drawString("${ComposeBuddyBundle.message("designer.canvas.auto")} • 20dp padding • 22dp radius", node.bounds.x + 12, node.bounds.y + 44)

            if (isSelected && node.id != rootNodeId) {
                resizeHandles(node).values.forEach { handle ->
                    g2.fillRect(handle.x, handle.y, handle.width, handle.height)
                }
            }
        }

        drawSnapGuides(g2)
        drawDropPreview(g2)

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

    private fun resizeAnchorAt(node: ComposeCanvasNode, point: Point): ResizeAnchor? {
        return resizeHandles(node).entries.firstOrNull { it.value.contains(point) }?.key
    }

    private fun resizeHandles(node: ComposeCanvasNode): Map<ResizeAnchor, Rectangle> {
        return mapOf(
            ResizeAnchor.TOP_LEFT to Rectangle(node.bounds.x, node.bounds.y, 10, 10),
            ResizeAnchor.TOP_RIGHT to Rectangle(node.bounds.x + node.bounds.width - 10, node.bounds.y, 10, 10),
            ResizeAnchor.BOTTOM_LEFT to Rectangle(node.bounds.x, node.bounds.y + node.bounds.height - 10, 10, 10),
            ResizeAnchor.BOTTOM_RIGHT to Rectangle(node.bounds.x + node.bounds.width - 10, node.bounds.y + node.bounds.height - 10, 10, 10),
        )
    }

    private fun anchorCursor(anchor: ResizeAnchor): Cursor {
        val cursorType = when (anchor) {
            ResizeAnchor.TOP_LEFT,
            ResizeAnchor.BOTTOM_RIGHT,
            -> Cursor.NW_RESIZE_CURSOR

            ResizeAnchor.TOP_RIGHT,
            ResizeAnchor.BOTTOM_LEFT,
            -> Cursor.NE_RESIZE_CURSOR
        }
        return Cursor.getPredefinedCursor(cursorType)
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

    private fun nodeLabel(node: ComposeCanvasNode): String {
        if (node.id == rootNodeId) {
            return ComposeBuddyBundle.message("designer.canvas.root")
        }
        return when (node.kind) {
            ComposePaletteItem.TEXT -> ComposeBuddyBundle.message("designer.node.text")
            ComposePaletteItem.BUTTON -> ComposeBuddyBundle.message("designer.node.button")
            ComposePaletteItem.IMAGE -> ComposeBuddyBundle.message("designer.node.image")
            ComposePaletteItem.BOX -> ComposeBuddyBundle.message("designer.node.box")
            ComposePaletteItem.ROW -> ComposeBuddyBundle.message("designer.node.row")
            ComposePaletteItem.COLUMN -> ComposeBuddyBundle.message("designer.node.column")
            ComposePaletteItem.SPACER -> ComposeBuddyBundle.message("designer.node.spacer")
            ComposePaletteItem.CUSTOM -> node.customName ?: node.customFunctionName ?: ComposeBuddyBundle.message("designer.node.custom")
        }
    }

    private fun drawSnapGuides(g2: Graphics2D) {
        if (snapGuides.vertical.isEmpty() && snapGuides.horizontal.isEmpty()) {
            return
        }
        g2.color = JBColor(Color(0x4D, 0x8B, 0xD6), Color(0x7B, 0xC3, 0xFF))
        g2.stroke = BasicStroke(1.25f)
        snapGuides.vertical.forEach { x ->
            g2.drawLine(x, 0, x, height)
        }
        snapGuides.horizontal.forEach { y ->
            g2.drawLine(0, y, width, y)
        }
    }

    private fun drawDropPreview(g2: Graphics2D) {
        val preview = dropPreview ?: return
        preview.previewBounds?.let { bounds ->
            val fill = JBColor(Color(0xF9, 0x8B, 0x1F, 54), Color(0xFF, 0xB8, 0x57, 68))
            val stroke = JBColor(Color(0xF9, 0x8B, 0x1F), Color(0xFF, 0xB8, 0x57))
            val previousStroke = g2.stroke
            g2.color = fill
            g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 18, 18)
            g2.color = stroke
            g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(8f, 6f), 0f)
            g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 18, 18)
            g2.stroke = previousStroke
        }
        preview.line?.let { line ->
            g2.color = JBColor(Color(0xF9, 0x8B, 0x1F), Color(0xFF, 0xB8, 0x57))
            g2.stroke = BasicStroke(3f)
            g2.drawLine(line.first.x, line.first.y, line.second.x, line.second.y)
        }
    }

    private fun initializeRootCanvas() {
        nodes += ComposeCanvasNode(
            id = rootNodeId,
            kind = ComposePaletteItem.BOX,
            bounds = Rectangle(28, 28, 820, 560),
            parentId = null,
        )
        selectedNodeId = rootNodeId
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
            val entry = ComposeDesignerPaletteCatalog.resolveTransferId(value) ?: return false
            addNode(entry, support.dropLocation.dropPoint)
            return true
        }

        override fun getSourceActions(c: JComponent): Int = DnDConstants.ACTION_COPY

        override fun createTransferable(c: JComponent): StringSelection? = null
    }
}
