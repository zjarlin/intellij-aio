package site.addzero.composebuddy.designer.ui

import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import java.awt.Point
import java.awt.Rectangle

object ComposeDesignerLayoutSupport {
    private const val CONTAINER_INSET = 20
    private const val CONTAINER_GAP = 16

    fun isContainer(kind: ComposePaletteItem): Boolean {
        return kind == ComposePaletteItem.BOX || kind == ComposePaletteItem.ROW || kind == ComposePaletteItem.COLUMN
    }

    fun assignParentForPoint(
        nodes: List<ComposeCanvasNode>,
        point: Point,
        excludingNodeId: String? = null,
    ): String? {
        return nodes
            .filter { isContainer(it.kind) && it.id != excludingNodeId && it.bounds.contains(point) }
            .minByOrNull { it.bounds.width * it.bounds.height }
            ?.id
    }

    fun assignParentForBounds(
        nodes: List<ComposeCanvasNode>,
        bounds: Rectangle,
        excludingNodeId: String? = null,
    ): String? {
        val center = Point(bounds.centerX.toInt(), bounds.centerY.toInt())
        return nodes
            .filter { isContainer(it.kind) && it.id != excludingNodeId && containsBounds(it.bounds, bounds) }
            .minByOrNull { it.bounds.width * it.bounds.height }
            ?.id
            ?: assignParentForPoint(nodes, center, excludingNodeId)
    }

    fun inferContainerKind(children: List<ComposeCanvasNode>): ComposePaletteItem {
        if (children.size < 2) return ComposePaletteItem.BOX
        val xSpread = children.maxOf { it.bounds.centerX } - children.minOf { it.bounds.centerX }
        val ySpread = children.maxOf { it.bounds.centerY } - children.minOf { it.bounds.centerY }
        return when {
            xSpread > ySpread * 1.4 -> ComposePaletteItem.ROW
            ySpread > xSpread * 1.4 -> ComposePaletteItem.COLUMN
            else -> ComposePaletteItem.BOX
        }
    }

    fun collectChildrenInRect(nodes: List<ComposeCanvasNode>, rect: Rectangle): List<ComposeCanvasNode> {
        return nodes.filter { rect.contains(it.bounds) }
    }

    fun childBoundsForParent(
        nodes: List<ComposeCanvasNode>,
        parent: ComposeCanvasNode?,
        requestedBounds: Rectangle,
        kind: ComposePaletteItem,
        excludingNodeId: String? = null,
    ): Rectangle {
        if (parent == null) {
            return requestedBounds
        }

        val siblings = nodes
            .filter { it.parentId == parent.id && it.id != excludingNodeId }
            .sortedWith(layoutComparator(parent.kind))
        val base = Rectangle(requestedBounds)
        val adjusted = when (parent.kind) {
            ComposePaletteItem.ROW -> {
                val nextX = siblings.lastOrNull()?.let { it.bounds.x + it.bounds.width + CONTAINER_GAP } ?: parent.bounds.x + CONTAINER_INSET
                val nextY = parent.bounds.y + CONTAINER_INSET
                Rectangle(nextX, nextY, base.width, fitHeightToParent(parent, base.height))
            }

            ComposePaletteItem.COLUMN -> {
                val nextX = parent.bounds.x + CONTAINER_INSET
                val nextY = siblings.lastOrNull()?.let { it.bounds.y + it.bounds.height + CONTAINER_GAP } ?: parent.bounds.y + CONTAINER_INSET
                Rectangle(nextX, nextY, fitWidthToParent(parent, base.width), base.height)
            }

            ComposePaletteItem.BOX -> Rectangle(
                (base.x).coerceIn(parent.bounds.x + CONTAINER_INSET, parent.bounds.x + parent.bounds.width - base.width - CONTAINER_INSET),
                (base.y).coerceIn(parent.bounds.y + CONTAINER_INSET, parent.bounds.y + parent.bounds.height - base.height - CONTAINER_INSET),
                fitWidthToParent(parent, base.width),
                fitHeightToParent(parent, base.height),
            )

            else -> base
        }
        return keepInsideParent(adjusted, parent, kind)
    }

    fun keepInsideParent(
        bounds: Rectangle,
        parent: ComposeCanvasNode,
        kind: ComposePaletteItem,
    ): Rectangle {
        val maxWidth = (parent.bounds.width - CONTAINER_INSET * 2).coerceAtLeast(72)
        val maxHeight = (parent.bounds.height - CONTAINER_INSET * 2).coerceAtLeast(40)
        val width = bounds.width.coerceAtMost(maxWidth)
        val height = bounds.height.coerceAtMost(maxHeight)
        val minX = parent.bounds.x + CONTAINER_INSET
        val minY = parent.bounds.y + CONTAINER_INSET
        val maxX = (parent.bounds.x + parent.bounds.width - width - CONTAINER_INSET).coerceAtLeast(minX)
        val maxY = (parent.bounds.y + parent.bounds.height - height - CONTAINER_INSET).coerceAtLeast(minY)
        return Rectangle(
            bounds.x.coerceIn(minX, maxX),
            bounds.y.coerceIn(minY, maxY),
            if (kind == ComposePaletteItem.COLUMN || kind == ComposePaletteItem.ROW || kind == ComposePaletteItem.BOX) {
                width.coerceAtLeast(180)
            } else {
                width
            },
            height,
        )
    }

    fun moveSubtree(nodes: List<ComposeCanvasNode>, nodeId: String, dx: Int, dy: Int) {
        val descendants = descendantsOf(nodes, nodeId)
        descendants.forEach { node ->
            node.bounds = Rectangle(node.bounds.x + dx, node.bounds.y + dy, node.bounds.width, node.bounds.height)
        }
    }

    fun descendantsOf(nodes: List<ComposeCanvasNode>, nodeId: String): List<ComposeCanvasNode> {
        val direct = nodes.filter { it.id == nodeId || it.parentId == nodeId }
        val nestedIds = direct.filter { it.id != nodeId }.flatMap { descendantsOf(nodes, it.id) }
        return (direct + nestedIds).distinctBy { it.id }
    }

    private fun containsBounds(container: Rectangle, child: Rectangle): Boolean {
        return container.x <= child.x &&
            container.y <= child.y &&
            container.x + container.width >= child.x + child.width &&
            container.y + container.height >= child.y + child.height
    }

    private fun fitWidthToParent(parent: ComposeCanvasNode, width: Int): Int {
        return width.coerceAtMost((parent.bounds.width - CONTAINER_INSET * 2).coerceAtLeast(120))
    }

    private fun fitHeightToParent(parent: ComposeCanvasNode, height: Int): Int {
        return height.coerceAtMost((parent.bounds.height - CONTAINER_INSET * 2).coerceAtLeast(56))
    }

    private fun layoutComparator(kind: ComposePaletteItem): Comparator<ComposeCanvasNode> {
        return when (kind) {
            ComposePaletteItem.ROW -> compareBy<ComposeCanvasNode> { it.bounds.x }.thenBy { it.bounds.y }
            ComposePaletteItem.COLUMN -> compareBy<ComposeCanvasNode> { it.bounds.y }.thenBy { it.bounds.x }
            else -> compareBy<ComposeCanvasNode> { it.bounds.y }.thenBy { it.bounds.x }
        }
    }
}
