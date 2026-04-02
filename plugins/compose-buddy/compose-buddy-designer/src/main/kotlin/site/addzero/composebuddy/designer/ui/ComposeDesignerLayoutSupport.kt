package site.addzero.composebuddy.designer.ui

import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import java.awt.Point
import java.awt.Rectangle

object ComposeDesignerLayoutSupport {
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
}
