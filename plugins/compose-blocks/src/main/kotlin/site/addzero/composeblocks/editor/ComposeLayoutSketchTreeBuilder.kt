package site.addzero.composeblocks.editor

import site.addzero.composeblocks.model.BlockSpec
import site.addzero.composeblocks.model.ComposeBlockType

internal data class LayoutSketchRegion(
    val id: String,
    val name: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height
}

internal object ComposeLayoutSketchTreeBuilder {

    private const val EPSILON = 0.02f

    fun buildRoot(
        rootId: String,
        rootNote: String,
        regions: List<LayoutSketchRegion>,
        idFactory: () -> String,
    ): BlockSpec {
        val normalizedRegions = regions
            .filter { region -> region.width > 0.03f && region.height > 0.03f }
            .sortedWith(compareBy(LayoutSketchRegion::top, LayoutSketchRegion::left))
        if (normalizedRegions.isEmpty()) {
            return BlockSpec.create(
                id = rootId,
                type = ComposeBlockType.COLUMN,
                note = rootNote,
            )
                .upsertProp("fillMaxWidth", "true")
                .upsertProp("fillMaxHeight", "true")
                .upsertProp("padding", "16.dp")
        }

        val contentRoot = buildNode(
            regions = normalizedRegions,
            bounds = regionBounds(normalizedRegions),
            idFactory = idFactory,
        )
        return contentRoot.copy(id = rootId, note = rootNote)
            .upsertProp("fillMaxWidth", "true")
            .upsertProp("fillMaxHeight", "true")
    }

    private fun buildNode(
        regions: List<LayoutSketchRegion>,
        bounds: RegionBounds,
        idFactory: () -> String,
    ): BlockSpec {
        if (regions.size == 1 && regionFillsBounds(regions.single(), bounds)) {
            return createSlotBlock(
                region = regions.single(),
                idFactory = idFactory,
            )
        }

        val columnGroups = partitionByAxis(regions, Axis.VERTICAL)
        val rowGroups = partitionByAxis(regions, Axis.HORIZONTAL)
        val useColumn = shouldUseColumn(columnGroups, rowGroups)
        val useRow = shouldUseRow(columnGroups, rowGroups)

        if (useColumn) {
            return buildContainer(
                type = ComposeBlockType.COLUMN,
                groups = columnGroups,
                bounds = bounds,
                idFactory = idFactory,
                childDecorator = { child, childBounds ->
                    child
                        .upsertProp("fillMaxWidth", "true")
                        .upsertProp("weight", formatFraction(childBounds.height / bounds.height))
                },
            )
        }

        if (useRow) {
            return buildContainer(
                type = ComposeBlockType.ROW,
                groups = rowGroups,
                bounds = bounds,
                idFactory = idFactory,
                childDecorator = { child, childBounds ->
                    child
                        .upsertProp("fillMaxHeight", "true")
                        .upsertProp("weight", formatFraction(childBounds.width / bounds.width))
                },
            )
        }

        val positionedChildren = regions.map { region ->
            createSlotBlock(
                region = region,
                idFactory = idFactory,
            )
                .upsertProp("xFraction", formatFraction((region.left - bounds.left) / bounds.width))
                .upsertProp("yFraction", formatFraction((region.top - bounds.top) / bounds.height))
                .upsertProp("widthFraction", formatFraction(region.width / bounds.width))
                .upsertProp("heightFraction", formatFraction(region.height / bounds.height))
        }
        return BlockSpec.create(
            id = idFactory(),
            type = ComposeBlockType.BOX,
            children = positionedChildren,
        )
            .upsertProp("fillMaxWidth", "true")
            .upsertProp("fillMaxHeight", "true")
    }

    private fun buildContainer(
        type: ComposeBlockType,
        groups: List<List<LayoutSketchRegion>>,
        bounds: RegionBounds,
        idFactory: () -> String,
        childDecorator: (BlockSpec, RegionBounds) -> BlockSpec,
    ): BlockSpec {
        val children = groups.map { group ->
            val childBounds = regionBounds(group)
            val childBlock = buildNode(group, childBounds, idFactory)
            childDecorator(childBlock, childBounds)
        }
        return BlockSpec.create(
            id = idFactory(),
            type = type,
            children = children,
        )
            .upsertProp("fillMaxWidth", "true")
            .upsertProp("fillMaxHeight", "true")
    }

    private fun createSlotBlock(
        region: LayoutSketchRegion,
        idFactory: () -> String,
    ): BlockSpec {
        val normalizedName = sanitizeSlotName(region.name)
        return BlockSpec.create(
            id = idFactory(),
            type = ComposeBlockType.SLOT,
            note = region.name,
        )
            .upsertProp("slotName", normalizedName)
            .upsertProp("fillMaxWidth", "true")
            .upsertProp("fillMaxHeight", "true")
    }

    private fun partitionByAxis(
        regions: List<LayoutSketchRegion>,
        axis: Axis,
    ): List<List<LayoutSketchRegion>> {
        val sorted = when (axis) {
            Axis.VERTICAL -> regions.sortedBy(LayoutSketchRegion::top)
            Axis.HORIZONTAL -> regions.sortedBy(LayoutSketchRegion::left)
        }

        val groups = mutableListOf<MutableList<LayoutSketchRegion>>()
        sorted.forEach { region ->
            val lastGroup = groups.lastOrNull()
            if (lastGroup == null) {
                groups += mutableListOf(region)
                return@forEach
            }

            val groupBounds = regionBounds(lastGroup)
            val overlaps = when (axis) {
                Axis.VERTICAL -> region.top < groupBounds.bottom - EPSILON
                Axis.HORIZONTAL -> region.left < groupBounds.right - EPSILON
            }
            if (overlaps) {
                lastGroup += region
            } else {
                groups += mutableListOf(region)
            }
        }
        return groups
    }

    private fun shouldUseColumn(
        columnGroups: List<List<LayoutSketchRegion>>,
        rowGroups: List<List<LayoutSketchRegion>>,
    ): Boolean {
        if (columnGroups.size <= 1) {
            return false
        }
        if (rowGroups.size <= 1) {
            return true
        }
        return columnGroups.size >= rowGroups.size
    }

    private fun shouldUseRow(
        columnGroups: List<List<LayoutSketchRegion>>,
        rowGroups: List<List<LayoutSketchRegion>>,
    ): Boolean {
        if (rowGroups.size <= 1) {
            return false
        }
        if (columnGroups.size <= 1) {
            return true
        }
        return rowGroups.size > columnGroups.size
    }

    private fun regionFillsBounds(
        region: LayoutSketchRegion,
        bounds: RegionBounds,
    ): Boolean {
        return (region.left - bounds.left).absoluteValue() <= EPSILON &&
            (region.top - bounds.top).absoluteValue() <= EPSILON &&
            (region.right - bounds.right).absoluteValue() <= EPSILON &&
            (region.bottom - bounds.bottom).absoluteValue() <= EPSILON
    }

    private fun regionBounds(regions: List<LayoutSketchRegion>): RegionBounds {
        val left = regions.minOf(LayoutSketchRegion::left)
        val top = regions.minOf(LayoutSketchRegion::top)
        val right = regions.maxOf(LayoutSketchRegion::right)
        val bottom = regions.maxOf(LayoutSketchRegion::bottom)
        return RegionBounds(
            left = left,
            top = top,
            width = (right - left).coerceAtLeast(0.05f),
            height = (bottom - top).coerceAtLeast(0.05f),
        )
    }

    private fun sanitizeSlotName(value: String): String {
        val parts = value
            .trim()
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return "slot"
        }
        val first = parts.first().lowercase()
        val rest = parts.drop(1).joinToString("") { part ->
            part.lowercase().replaceFirstChar { character -> character.titlecase() }
        }
        return (first + rest).ifBlank { "slot" }
    }

    private fun formatFraction(value: Float): String {
        return "%.3ff".format(value.coerceAtLeast(0.01f))
            .replace(Regex("0+f$"), "f")
            .replace(Regex("\\.f$"), "f")
    }

    private fun Float.absoluteValue(): Float = kotlin.math.abs(this)

    private data class RegionBounds(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    ) {
        val right: Float
            get() = left + width

        val bottom: Float
            get() = top + height
    }

    private enum class Axis {
        VERTICAL,
        HORIZONTAL,
    }
}
