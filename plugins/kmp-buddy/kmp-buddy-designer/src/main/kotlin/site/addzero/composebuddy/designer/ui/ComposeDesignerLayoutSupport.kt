package site.addzero.composebuddy.designer.ui

import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.abs

object ComposeDesignerLayoutSupport {
    private const val CONTAINER_INSET = 20
    private const val CONTAINER_GAP = 16
    private const val SNAP_THRESHOLD = 10
    private const val LEAF_NUDGE_STEP = 12

    fun isContainer(kind: ComposePaletteItem): Boolean {
        return when (kind) {
            ComposePaletteItem.BOX,
            ComposePaletteItem.ROW,
            ComposePaletteItem.COLUMN,
            ComposePaletteItem.CARD,
            -> true

            else -> false
        }
    }

    fun isContainer(node: ComposeCanvasNode): Boolean {
        return isContainer(effectiveKind(node))
    }

    fun effectiveKind(node: ComposeCanvasNode): ComposePaletteItem {
        return when (node.kind) {
            ComposePaletteItem.CARD -> ComposePaletteItem.BOX
            ComposePaletteItem.CUSTOM -> node.customLayoutKind ?: ComposePaletteItem.CUSTOM
            else -> node.kind
        }
    }

    fun usesIntrinsicCanvasFootprint(kind: ComposePaletteItem): Boolean {
        return when (kind) {
            ComposePaletteItem.TEXT,
            ComposePaletteItem.BUTTON,
            ComposePaletteItem.ICON,
            ComposePaletteItem.IMAGE,
            ComposePaletteItem.DIVIDER,
            -> true

            else -> false
        }
    }

    fun assignParentForPoint(
        nodes: List<ComposeCanvasNode>,
        point: Point,
        excludingNodeId: String? = null,
    ): String? {
        return assignParentForPoint(
            nodes = nodes,
            point = point,
            excludingNodeIds = excludingNodeId?.let(::setOf).orEmpty(),
        )
    }

    fun assignParentForPoint(
        nodes: List<ComposeCanvasNode>,
        point: Point,
        excludingNodeIds: Set<String>,
    ): String? {
        return nodes
            .filter { isContainer(it) && it.id !in excludingNodeIds && it.bounds.contains(point) }
            .minByOrNull { it.bounds.width * it.bounds.height }
            ?.id
    }

    fun assignParentForBounds(
        nodes: List<ComposeCanvasNode>,
        bounds: Rectangle,
        excludingNodeId: String? = null,
    ): String? {
        return assignParentForBounds(
            nodes = nodes,
            bounds = bounds,
            excludingNodeIds = excludingNodeId?.let(::setOf).orEmpty(),
        )
    }

    fun assignParentForBounds(
        nodes: List<ComposeCanvasNode>,
        bounds: Rectangle,
        excludingNodeIds: Set<String>,
    ): String? {
        val center = Point(bounds.centerX.toInt(), bounds.centerY.toInt())
        return nodes
            .filter { isContainer(it) && it.id !in excludingNodeIds && containsBounds(it.bounds, bounds) }
            .minByOrNull { it.bounds.width * it.bounds.height }
            ?.id
            ?: assignParentForPoint(nodes, center, excludingNodeIds)
    }

    fun assignParentForDrop(
        nodes: List<ComposeCanvasNode>,
        bounds: Rectangle,
        pointer: Point?,
        excludingNodeIds: Set<String>,
    ): String? {
        val pointerParent = pointer?.let { assignParentForPoint(nodes, it, excludingNodeIds) }
        if (pointerParent != null) {
            return pointerParent
        }
        return assignParentForBounds(nodes, bounds, excludingNodeIds)
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
            .sortedWith(layoutComparator(effectiveKind(parent)))
        val base = Rectangle(requestedBounds)
        val adjusted = when (effectiveKind(parent)) {
            ComposePaletteItem.ROW -> {
                val nextX = siblings.lastOrNull()?.let { it.bounds.x + it.bounds.width + CONTAINER_GAP } ?: parent.bounds.x + CONTAINER_INSET
                val nextY = parent.bounds.y + CONTAINER_INSET
                Rectangle(nextX, nextY, base.width, base.height)
            }

            ComposePaletteItem.COLUMN -> {
                val nextX = parent.bounds.x + CONTAINER_INSET
                val nextY = siblings.lastOrNull()?.let { it.bounds.y + it.bounds.height + CONTAINER_GAP } ?: parent.bounds.y + CONTAINER_INSET
                Rectangle(nextX, nextY, base.width, base.height)
            }

            ComposePaletteItem.BOX -> {
                val minX = parent.bounds.x + CONTAINER_INSET
                val maxX = (parent.bounds.x + parent.bounds.width - base.width - CONTAINER_INSET).coerceAtLeast(minX)
                val minY = parent.bounds.y + CONTAINER_INSET
                val maxY = (parent.bounds.y + parent.bounds.height - base.height - CONTAINER_INSET).coerceAtLeast(minY)
                Rectangle(
                    base.x.coerceIn(minX, maxX),
                    base.y.coerceIn(minY, maxY),
                    base.width,
                    base.height,
                )
            }

            else -> base
        }
        val kept = keepInsideParent(adjusted, parent, kind)
        return if (effectiveKind(parent) == ComposePaletteItem.BOX) {
            avoidSiblingOverlap(nodes, parent, kept, excludingNodeId)
        } else {
            kept
        }
    }

    fun keepInsideParent(
        bounds: Rectangle,
        parent: ComposeCanvasNode,
        kind: ComposePaletteItem,
    ): Rectangle {
        val width = if (kind == ComposePaletteItem.COLUMN || kind == ComposePaletteItem.ROW || kind == ComposePaletteItem.BOX) {
            bounds.width.coerceAtLeast(180)
        } else {
            bounds.width
        }
        val height = bounds.height.coerceAtLeast(24)
        val minX = parent.bounds.x + CONTAINER_INSET
        val minY = parent.bounds.y + CONTAINER_INSET
        val maxX = (parent.bounds.x + parent.bounds.width - width - CONTAINER_INSET).coerceAtLeast(minX)
        val maxY = (parent.bounds.y + parent.bounds.height - height - CONTAINER_INSET).coerceAtLeast(minY)
        return Rectangle(
            bounds.x.coerceIn(minX, maxX),
            bounds.y.coerceIn(minY, maxY),
            width,
            height,
        )
    }

    fun moveSubtree(nodes: List<ComposeCanvasNode>, nodeId: String, dx: Int, dy: Int) {
        val descendants = descendantsOf(nodes, nodeId)
        descendants.forEach { node ->
            node.bounds = Rectangle(node.bounds.x + dx, node.bounds.y + dy, node.bounds.width, node.bounds.height)
        }
    }

    fun avoidSiblingOverlap(
        nodes: List<ComposeCanvasNode>,
        parent: ComposeCanvasNode?,
        requestedBounds: Rectangle,
        excludingNodeId: String? = null,
    ): Rectangle {
        val siblings = nodes.filter { it.parentId == parent?.id && it.id != excludingNodeId }
        if (siblings.none { it.bounds.intersects(requestedBounds) }) {
            return requestedBounds
        }
        var candidate = Rectangle(requestedBounds)
        val minX = parent?.bounds?.x?.plus(CONTAINER_INSET) ?: 24
        val minY = parent?.bounds?.y?.plus(CONTAINER_INSET) ?: 24
        val maxX = parent?.bounds?.let { (it.x + it.width - candidate.width - CONTAINER_INSET).coerceAtLeast(minX) }
        val maxY = parent?.bounds?.let { (it.y + it.height - candidate.height - CONTAINER_INSET).coerceAtLeast(minY) }
        repeat(600) {
            if (siblings.none { it.bounds.intersects(candidate) }) {
                return candidate
            }
            candidate = Rectangle(candidate.x + LEAF_NUDGE_STEP, candidate.y, candidate.width, candidate.height)
            if (maxX != null && candidate.x > maxX) {
                candidate.x = minX
                candidate.y += LEAF_NUDGE_STEP
            }
            if (maxY != null && candidate.y > maxY) {
                candidate.y = minY
            }
        }
        return candidate
    }

    fun relayoutAutoContainer(
        nodes: List<ComposeCanvasNode>,
        containerId: String?,
    ) {
        val container = nodes.firstOrNull { it.id == containerId } ?: return
        if (!isContainer(container)) {
            return
        }

        val children = nodes
            .filter { it.parentId == container.id }
            .sortedWith(layoutComparator(effectiveKind(container)))

        when (effectiveKind(container)) {
            ComposePaletteItem.ROW -> {
                var cursorX = container.bounds.x + CONTAINER_INSET
                children.forEach { child ->
                    child.bounds = Rectangle(
                        cursorX,
                        container.bounds.y + CONTAINER_INSET,
                        child.bounds.width,
                        child.bounds.height,
                    )
                    cursorX = child.bounds.x + child.bounds.width + CONTAINER_GAP
                }
                growContainerToFitChildren(nodes, container, children, ComposePaletteItem.ROW)
            }

            ComposePaletteItem.COLUMN -> {
                var cursorY = container.bounds.y + CONTAINER_INSET
                children.forEach { child ->
                    child.bounds = Rectangle(
                        container.bounds.x + CONTAINER_INSET,
                        cursorY,
                        child.bounds.width,
                        child.bounds.height,
                    )
                    cursorY = child.bounds.y + child.bounds.height + CONTAINER_GAP
                }
                growContainerToFitChildren(nodes, container, children, ComposePaletteItem.COLUMN)
            }

            ComposePaletteItem.BOX -> growContainerToFitChildren(nodes, container, children, ComposePaletteItem.BOX)
            else -> Unit
        }
    }

    fun relayoutAutoContainersFrom(
        nodes: List<ComposeCanvasNode>,
        startParentId: String?,
    ) {
        var currentParentId = startParentId
        while (currentParentId != null) {
            relayoutAutoContainer(nodes, currentParentId)
            currentParentId = nodes.firstOrNull { it.id == currentParentId }?.parentId
        }
    }

    fun normalizeFreeLayout(
        nodes: MutableList<ComposeCanvasNode>,
        rootNodeId: String,
    ) {
        val targets = nodes
            .filter { it.id == rootNodeId || effectiveKind(it) == ComposePaletteItem.BOX }
            .sortedByDescending { depthOf(nodes, it.id) }
            .map { it.id }
        targets.forEach { parentId ->
            normalizeBoxChildren(nodes, parentId, rootNodeId)
        }
    }

    fun snapBounds(
        nodes: List<ComposeCanvasNode>,
        node: ComposeCanvasNode,
        proposedBounds: Rectangle,
    ): Pair<Rectangle, SnapGuides> {
        val parent = nodes.firstOrNull { it.id == node.parentId }
        val candidateXs = linkedSetOf(24, proposedBounds.x, proposedBounds.x + proposedBounds.width / 2, proposedBounds.x + proposedBounds.width)
        val candidateYs = linkedSetOf(24, proposedBounds.y, proposedBounds.y + proposedBounds.height / 2, proposedBounds.y + proposedBounds.height)

        if (parent != null) {
            candidateXs += parent.bounds.x + CONTAINER_INSET
            candidateXs += parent.bounds.x + parent.bounds.width / 2
            candidateXs += parent.bounds.x + parent.bounds.width - CONTAINER_INSET
            candidateYs += parent.bounds.y + CONTAINER_INSET
            candidateYs += parent.bounds.y + parent.bounds.height / 2
            candidateYs += parent.bounds.y + parent.bounds.height - CONTAINER_INSET
        }

        nodes.filter { it.id != node.id && it.parentId == node.parentId }.forEach { sibling ->
            candidateXs += sibling.bounds.x
            candidateXs += sibling.bounds.x + sibling.bounds.width / 2
            candidateXs += sibling.bounds.x + sibling.bounds.width
            candidateYs += sibling.bounds.y
            candidateYs += sibling.bounds.y + sibling.bounds.height / 2
            candidateYs += sibling.bounds.y + sibling.bounds.height
        }

        val snappedX = snapAxis(
            start = proposedBounds.x,
            center = proposedBounds.x + proposedBounds.width / 2,
            end = proposedBounds.x + proposedBounds.width,
            targets = candidateXs.toList(),
        )
        val snappedY = snapAxis(
            start = proposedBounds.y,
            center = proposedBounds.y + proposedBounds.height / 2,
            end = proposedBounds.y + proposedBounds.height,
            targets = candidateYs.toList(),
        )

        val adjusted = Rectangle(
            proposedBounds.x + snappedX.delta,
            proposedBounds.y + snappedY.delta,
            proposedBounds.width,
            proposedBounds.height,
        )
        return adjusted to SnapGuides(
            vertical = snappedX.guide?.let(::listOf).orEmpty(),
            horizontal = snappedY.guide?.let(::listOf).orEmpty(),
        )
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

    private fun depthOf(nodes: List<ComposeCanvasNode>, nodeId: String): Int {
        var depth = 0
        var currentParent = nodes.firstOrNull { it.id == nodeId }?.parentId
        while (currentParent != null) {
            depth += 1
            currentParent = nodes.firstOrNull { it.id == currentParent }?.parentId
        }
        return depth
    }

    private fun layoutComparator(kind: ComposePaletteItem): Comparator<ComposeCanvasNode> {
        return when (kind) {
            ComposePaletteItem.ROW -> compareBy<ComposeCanvasNode> { it.bounds.x }.thenBy { it.bounds.y }
            ComposePaletteItem.COLUMN -> compareBy<ComposeCanvasNode> { it.bounds.y }.thenBy { it.bounds.x }
            else -> compareBy<ComposeCanvasNode> { it.bounds.y }.thenBy { it.bounds.x }
        }
    }

    private fun normalizeBoxChildren(
        nodes: MutableList<ComposeCanvasNode>,
        parentId: String,
        rootNodeId: String,
    ) {
        val children = nodes
            .filter { it.parentId == parentId }
            .sortedWith(compareBy<ComposeCanvasNode> { it.bounds.y }.thenBy { it.bounds.x })
        if (children.size < 2) {
            return
        }
        val rowGroups = groupByRows(children)
        val parent = nodes.firstOrNull { it.id == parentId }
        val normalizedParentId = if (parentId == rootNodeId) null else parentId
        if (rowGroups.size == 1) {
            val group = rowGroups.first()
            if (shouldWrapAsRow(group)) {
                wrapChildren(nodes, normalizedParentId, ComposePaletteItem.ROW, group, parent)
            } else if (shouldWrapAsColumn(group)) {
                wrapChildren(nodes, normalizedParentId, ComposePaletteItem.COLUMN, group, parent)
            }
            return
        }
        val column = ComposeCanvasNode(
            kind = ComposePaletteItem.COLUMN,
            bounds = boundingRect(children),
            parentId = normalizedParentId,
        )
        nodes += column
        rowGroups.forEach { group ->
            if (group.size == 1) {
                group.first().parentId = column.id
            } else {
                val row = ComposeCanvasNode(
                    kind = ComposePaletteItem.ROW,
                    bounds = boundingRect(group),
                    parentId = column.id,
                )
                nodes += row
                group.forEach { child ->
                    child.parentId = row.id
                }
            }
        }
        relayoutAutoContainersFrom(nodes, column.id)
    }

    private fun wrapChildren(
        nodes: MutableList<ComposeCanvasNode>,
        parentId: String?,
        kind: ComposePaletteItem,
        children: List<ComposeCanvasNode>,
        originalParent: ComposeCanvasNode?,
    ) {
        val container = ComposeCanvasNode(
            kind = kind,
            bounds = boundingRect(children),
            parentId = parentId,
        )
        nodes += container
        children.forEach { child ->
            child.parentId = container.id
        }
        relayoutAutoContainersFrom(nodes, container.id)
        if (originalParent != null) {
            relayoutAutoContainersFrom(nodes, originalParent.id)
        }
    }

    private fun groupByRows(children: List<ComposeCanvasNode>): List<List<ComposeCanvasNode>> {
        if (children.isEmpty()) {
            return emptyList()
        }
        val threshold = maxOf(48, children.map { it.bounds.height }.average().toInt() / 2 + CONTAINER_GAP)
        val groups = mutableListOf<MutableList<ComposeCanvasNode>>()
        children.sortedBy { it.bounds.y }.forEach { child ->
            val centerY = child.bounds.y + child.bounds.height / 2
            val group = groups.firstOrNull { current ->
                val averageCenterY = current.map { it.bounds.y + it.bounds.height / 2 }.average()
                abs(centerY - averageCenterY) <= threshold
            }
            if (group == null) {
                groups += mutableListOf(child)
            } else {
                group += child
            }
        }
        return groups.map { group -> group.sortedBy { it.bounds.x } }
    }

    private fun shouldWrapAsRow(children: List<ComposeCanvasNode>): Boolean {
        if (children.size < 2) {
            return false
        }
        val xSpread = children.maxOf { it.bounds.centerX } - children.minOf { it.bounds.centerX }
        val ySpread = children.maxOf { it.bounds.centerY } - children.minOf { it.bounds.centerY }
        return xSpread >= ySpread
    }

    private fun shouldWrapAsColumn(children: List<ComposeCanvasNode>): Boolean {
        if (children.size < 2) {
            return false
        }
        val xSpread = children.maxOf { it.bounds.centerX } - children.minOf { it.bounds.centerX }
        val ySpread = children.maxOf { it.bounds.centerY } - children.minOf { it.bounds.centerY }
        return ySpread > xSpread
    }

    private fun boundingRect(nodes: List<ComposeCanvasNode>): Rectangle {
        val left = nodes.minOf { it.bounds.x }
        val top = nodes.minOf { it.bounds.y }
        val right = nodes.maxOf { it.bounds.x + it.bounds.width }
        val bottom = nodes.maxOf { it.bounds.y + it.bounds.height }
        return Rectangle(left, top, right - left, bottom - top)
    }

    private fun growContainerToFitChildren(
        nodes: List<ComposeCanvasNode>,
        container: ComposeCanvasNode,
        children: List<ComposeCanvasNode>,
        layoutKind: ComposePaletteItem,
    ) {
        if (children.isEmpty()) {
            return
        }
        val right = children.maxOf { it.bounds.x + it.bounds.width }
        val bottom = children.maxOf { it.bounds.y + it.bounds.height }
        val left = children.minOf { it.bounds.x }
        val top = children.minOf { it.bounds.y }
        val requiredWidth = when (layoutKind) {
            ComposePaletteItem.ROW,
            ComposePaletteItem.BOX,
            -> (right - container.bounds.x) + CONTAINER_INSET

            ComposePaletteItem.COLUMN -> {
                val contentWidth = children.maxOf { it.bounds.width }
                maxOf(container.bounds.width, contentWidth + CONTAINER_INSET * 2)
            }

            else -> container.bounds.width
        }
        val requiredHeight = when (layoutKind) {
            ComposePaletteItem.COLUMN,
            ComposePaletteItem.BOX,
            -> (bottom - container.bounds.y) + CONTAINER_INSET

            ComposePaletteItem.ROW -> {
                val contentHeight = children.maxOf { it.bounds.height }
                maxOf(container.bounds.height, contentHeight + CONTAINER_INSET * 2)
            }

            else -> container.bounds.height
        }
        val adjustedX = if (left < container.bounds.x + CONTAINER_INSET) {
            left - CONTAINER_INSET
        } else {
            container.bounds.x
        }
        val adjustedY = if (top < container.bounds.y + CONTAINER_INSET) {
            top - CONTAINER_INSET
        } else {
            container.bounds.y
        }
        val deltaX = container.bounds.x - adjustedX
        val deltaY = container.bounds.y - adjustedY
        val proposed = if (deltaX != 0 || deltaY != 0) {
            Rectangle(
                adjustedX,
                adjustedY,
                maxOf(container.bounds.width + deltaX, requiredWidth),
                maxOf(container.bounds.height + deltaY, requiredHeight),
            )
        } else {
            Rectangle(
                container.bounds.x,
                container.bounds.y,
                maxOf(container.bounds.width, requiredWidth),
                maxOf(container.bounds.height, requiredHeight),
            )
        }
        container.bounds = constrainContainerGrowth(nodes, container, proposed)
    }

    private fun constrainContainerGrowth(
        nodes: List<ComposeCanvasNode>,
        container: ComposeCanvasNode,
        proposed: Rectangle,
    ): Rectangle {
        val parent = nodes.firstOrNull { it.id == container.parentId } ?: return proposed
        var maxRight = parent.bounds.x + parent.bounds.width - CONTAINER_INSET
        var maxBottom = parent.bounds.y + parent.bounds.height - CONTAINER_INSET
        if (effectiveKind(parent) == ComposePaletteItem.BOX) {
            val siblings = nodes.filter { it.parentId == parent.id && it.id != container.id }
            siblings.forEach { sibling ->
                if (sibling.bounds.x >= proposed.x && rangesOverlap(proposed.y, proposed.y + proposed.height, sibling.bounds.y, sibling.bounds.y + sibling.bounds.height)) {
                    maxRight = minOf(maxRight, sibling.bounds.x - CONTAINER_GAP)
                }
                if (sibling.bounds.y >= proposed.y && rangesOverlap(proposed.x, proposed.x + proposed.width, sibling.bounds.x, sibling.bounds.x + sibling.bounds.width)) {
                    maxBottom = minOf(maxBottom, sibling.bounds.y - CONTAINER_GAP)
                }
            }
        }
        val minWidth = if (isContainer(container)) 180 else 48
        val constrainedWidth = (maxRight - proposed.x).coerceAtLeast(minWidth)
        val constrainedHeight = (maxBottom - proposed.y).coerceAtLeast(24)
        return Rectangle(
            proposed.x,
            proposed.y,
            minOf(proposed.width, constrainedWidth),
            minOf(proposed.height, constrainedHeight),
        )
    }

    private fun rangesOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean {
        return startA < endB && startB < endA
    }

    private fun snapAxis(
        start: Int,
        center: Int,
        end: Int,
        targets: List<Int>,
    ): SnapAxisResult {
        var bestDelta = 0
        var bestGuide: Int? = null
        var bestDistance = SNAP_THRESHOLD + 1
        targets.forEach { target ->
            listOf(start, center, end).forEach { anchor ->
                val delta = target - anchor
                val distance = kotlin.math.abs(delta)
                if (distance < bestDistance && distance <= SNAP_THRESHOLD) {
                    bestDistance = distance
                    bestDelta = delta
                    bestGuide = target
                }
            }
        }
        return SnapAxisResult(bestDelta, bestGuide)
    }

    data class SnapGuides(
        val vertical: List<Int> = emptyList(),
        val horizontal: List<Int> = emptyList(),
    )

    data class DropPreview(
        val parentId: String?,
        val line: Pair<Point, Point>? = null,
        val previewBounds: Rectangle? = null,
    )

    private data class SnapAxisResult(
        val delta: Int,
        val guide: Int?,
    )

    fun dropPreview(
        nodes: List<ComposeCanvasNode>,
        movingNode: ComposeCanvasNode,
        pointer: Point?,
    ): DropPreview {
        val excludedIds = descendantsOf(nodes, movingNode.id).map { it.id }.toSet()
        val parentId = assignParentForDrop(nodes, movingNode.bounds, pointer, excludedIds)
        val parent = nodes.firstOrNull { it.id == parentId } ?: return DropPreview(parentId)
        val layoutKind = effectiveKind(parent)
        if (pointer == null) {
            return DropPreview(parentId = parentId)
        }
        val siblings = nodes
            .filter { it.parentId == parent.id && it.id !in excludedIds }
            .sortedWith(layoutComparator(layoutKind))
        if (layoutKind == ComposePaletteItem.ROW) {
            val movingCenterX = movingNode.bounds.x + movingNode.bounds.width / 2
            val index = insertionIndexForRow(siblings, movingCenterX)
            val previewBounds = previewBoundsForRow(parent, siblings, movingNode, index)
            val x = insertionX(parent, siblings, index)
            return DropPreview(
                parentId = parentId,
                line = Point(x, parent.bounds.y + CONTAINER_INSET) to Point(x, parent.bounds.y + parent.bounds.height - CONTAINER_INSET),
                previewBounds = previewBounds,
            )
        }
        if (layoutKind == ComposePaletteItem.COLUMN) {
            val movingCenterY = movingNode.bounds.y + movingNode.bounds.height / 2
            val index = insertionIndexForColumn(siblings, movingCenterY)
            val previewBounds = previewBoundsForColumn(parent, siblings, movingNode, index)
            val y = insertionY(parent, siblings, index)
            return DropPreview(
                parentId = parentId,
                line = Point(parent.bounds.x + CONTAINER_INSET, y) to Point(parent.bounds.x + parent.bounds.width - CONTAINER_INSET, y),
                previewBounds = previewBounds,
            )
        }
        val previewBounds = keepInsideParent(Rectangle(movingNode.bounds), parent, movingNode.kind)
        return DropPreview(
            parentId = parentId,
            previewBounds = previewBounds,
        )
    }

    private fun insertionIndexForRow(siblings: List<ComposeCanvasNode>, pointerX: Int): Int {
        siblings.forEachIndexed { index, sibling ->
            val center = sibling.bounds.x + sibling.bounds.width / 2
            if (pointerX < center) {
                return index
            }
        }
        return siblings.size
    }

    private fun insertionIndexForColumn(siblings: List<ComposeCanvasNode>, pointerY: Int): Int {
        siblings.forEachIndexed { index, sibling ->
            val center = sibling.bounds.y + sibling.bounds.height / 2
            if (pointerY < center) {
                return index
            }
        }
        return siblings.size
    }

    private fun previewBoundsForRow(
        parent: ComposeCanvasNode,
        siblings: List<ComposeCanvasNode>,
        movingNode: ComposeCanvasNode,
        index: Int,
    ): Rectangle {
        val x = when {
            siblings.isEmpty() -> parent.bounds.x + CONTAINER_INSET
            index <= 0 -> parent.bounds.x + CONTAINER_INSET
            else -> siblings[index - 1].bounds.x + siblings[index - 1].bounds.width + CONTAINER_GAP
        }
        val y = parent.bounds.y + CONTAINER_INSET
        return keepInsideParent(
            Rectangle(x, y, movingNode.bounds.width, movingNode.bounds.height),
            parent,
            movingNode.kind,
        )
    }

    private fun previewBoundsForColumn(
        parent: ComposeCanvasNode,
        siblings: List<ComposeCanvasNode>,
        movingNode: ComposeCanvasNode,
        index: Int,
    ): Rectangle {
        val x = parent.bounds.x + CONTAINER_INSET
        val y = when {
            siblings.isEmpty() -> parent.bounds.y + CONTAINER_INSET
            index <= 0 -> parent.bounds.y + CONTAINER_INSET
            else -> siblings[index - 1].bounds.y + siblings[index - 1].bounds.height + CONTAINER_GAP
        }
        return keepInsideParent(
            Rectangle(x, y, movingNode.bounds.width, movingNode.bounds.height),
            parent,
            movingNode.kind,
        )
    }

    private fun insertionX(parent: ComposeCanvasNode, siblings: List<ComposeCanvasNode>, index: Int): Int {
        return when {
            siblings.isEmpty() -> parent.bounds.x + CONTAINER_INSET
            index <= 0 -> parent.bounds.x + CONTAINER_INSET - CONTAINER_GAP / 2
            index >= siblings.size -> siblings.last().bounds.x + siblings.last().bounds.width + CONTAINER_GAP / 2
            else -> siblings[index].bounds.x - CONTAINER_GAP / 2
        }
    }

    private fun insertionY(parent: ComposeCanvasNode, siblings: List<ComposeCanvasNode>, index: Int): Int {
        return when {
            siblings.isEmpty() -> parent.bounds.y + CONTAINER_INSET
            index <= 0 -> parent.bounds.y + CONTAINER_INSET - CONTAINER_GAP / 2
            index >= siblings.size -> siblings.last().bounds.y + siblings.last().bounds.height + CONTAINER_GAP / 2
            else -> siblings[index].bounds.y - CONTAINER_GAP / 2
        }
    }
}
