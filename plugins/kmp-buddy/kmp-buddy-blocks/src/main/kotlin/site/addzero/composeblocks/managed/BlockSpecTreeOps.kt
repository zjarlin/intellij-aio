package site.addzero.composeblocks.managed

import site.addzero.composeblocks.model.BlockSpec
import site.addzero.composeblocks.model.ComposeBlockType

object BlockSpecTreeOps {

    fun findBlock(root: BlockSpec, targetId: String?): BlockSpec? {
        if (targetId.isNullOrBlank()) {
            return null
        }
        if (root.id == targetId) {
            return root
        }
        root.children.forEach { child ->
            val match = findBlock(child, targetId)
            if (match != null) {
                return match
            }
        }
        return null
    }

    fun updateBlock(root: BlockSpec, targetId: String, transform: (BlockSpec) -> BlockSpec): BlockSpec {
        if (root.id == targetId) {
            return transform(root)
        }

        var changed = false
        val updatedChildren = root.children.map { child ->
            val updatedChild = updateBlock(child, targetId, transform)
            if (updatedChild !== child) {
                changed = true
            }
            updatedChild
        }
        return if (changed) root.withChildren(updatedChildren) else root
    }

    fun addNearSelection(
        root: BlockSpec,
        selectedId: String?,
        newBlock: BlockSpec,
    ): BlockSpec {
        if (selectedId.isNullOrBlank()) {
            return root.withChildren(root.children + newBlock)
        }

        val updated = addNearSelectionInternal(root, selectedId, newBlock)
        return if (updated.changed) updated.block else root.withChildren(root.children + newBlock)
    }

    fun deleteBlock(root: BlockSpec, targetId: String): BlockSpec {
        if (root.id == targetId) {
            return root
        }

        val retainedChildren = root.children.filterNot { it.id == targetId }
        if (retainedChildren.size != root.children.size) {
            return root.withChildren(retainedChildren)
        }

        var changed = false
        val updatedChildren = root.children.map { child ->
            val updatedChild = deleteBlock(child, targetId)
            if (updatedChild !== child) {
                changed = true
            }
            updatedChild
        }
        return if (changed) root.withChildren(updatedChildren) else root
    }

    fun moveBlock(
        root: BlockSpec,
        targetId: String,
        delta: Int,
    ): BlockSpec {
        if (delta == 0) {
            return root
        }

        val directIndex = root.children.indexOfFirst { it.id == targetId }
        if (directIndex >= 0) {
            val destinationIndex = (directIndex + delta).coerceIn(0, root.children.lastIndex)
            if (destinationIndex == directIndex) {
                return root
            }
            val reordered = root.children.toMutableList()
            val moved = reordered.removeAt(directIndex)
            reordered.add(destinationIndex, moved)
            return root.withChildren(reordered)
        }

        var changed = false
        val updatedChildren = root.children.map { child ->
            val updatedChild = moveBlock(child, targetId, delta)
            if (updatedChild !== child) {
                changed = true
            }
            updatedChild
        }
        return if (changed) root.withChildren(updatedChildren) else root
    }

    fun wrapBlock(
        root: BlockSpec,
        targetId: String,
        wrapperType: ComposeBlockType,
        idFactory: () -> String,
    ): BlockSpec {
        if (root.id == targetId) {
            return BlockSpec.create(
                id = idFactory(),
                type = wrapperType,
                children = listOf(root),
            )
        }

        val directIndex = root.children.indexOfFirst { it.id == targetId }
        if (directIndex >= 0) {
            val wrapper = BlockSpec.create(
                id = idFactory(),
                type = wrapperType,
                children = listOf(root.children[directIndex]),
            )
            val updatedChildren = root.children.toMutableList().apply {
                set(directIndex, wrapper)
            }
            return root.withChildren(updatedChildren)
        }

        var changed = false
        val updatedChildren = root.children.map { child ->
            val updatedChild = wrapBlock(child, targetId, wrapperType, idFactory)
            if (updatedChild !== child) {
                changed = true
            }
            updatedChild
        }
        return if (changed) root.withChildren(updatedChildren) else root
    }

    fun unwrapBlock(
        root: BlockSpec,
        targetId: String,
    ): BlockSpec {
        if (root.id == targetId) {
            return when (root.children.size) {
                0 -> root
                1 -> root.children.single()
                else -> root
            }
        }

        val directIndex = root.children.indexOfFirst { it.id == targetId }
        if (directIndex >= 0) {
            val target = root.children[directIndex]
            if (target.children.size > 1) {
                return root
            }
            val updatedChildren = root.children.toMutableList().apply {
                removeAt(directIndex)
                when (target.children.size) {
                    0 -> Unit
                    1 -> add(directIndex, target.children.single())
                }
            }
            return root.withChildren(updatedChildren)
        }

        var changed = false
        val updatedChildren = root.children.map { child ->
            val updatedChild = unwrapBlock(child, targetId)
            if (updatedChild !== child) {
                changed = true
            }
            updatedChild
        }
        return if (changed) root.withChildren(updatedChildren) else root
    }

    fun duplicateBlock(
        root: BlockSpec,
        targetId: String,
        idFactory: () -> String,
    ): BlockSpec {
        val directIndex = root.children.indexOfFirst { it.id == targetId }
        if (directIndex >= 0) {
            val duplicate = duplicateWithFreshIds(root.children[directIndex], idFactory)
            val updatedChildren = root.children.toMutableList().apply {
                add(directIndex + 1, duplicate)
            }
            return root.withChildren(updatedChildren)
        }

        var changed = false
        val updatedChildren = root.children.map { child ->
            val updatedChild = duplicateBlock(child, targetId, idFactory)
            if (updatedChild !== child) {
                changed = true
            }
            updatedChild
        }
        return if (changed) root.withChildren(updatedChildren) else root
    }

    private fun duplicateWithFreshIds(block: BlockSpec, idFactory: () -> String): BlockSpec {
        return BlockSpec.create(
            id = idFactory(),
            type = block.type,
            note = block.note,
            props = block.props,
            children = block.children.map { child -> duplicateWithFreshIds(child, idFactory) },
        )
    }

    private fun addNearSelectionInternal(
        root: BlockSpec,
        selectedId: String,
        newBlock: BlockSpec,
    ): Mutation {
        if (root.id == selectedId && root.type.supportsChildren) {
            return Mutation(root.withChildren(root.children + newBlock), true)
        }

        val directIndex = root.children.indexOfFirst { it.id == selectedId }
        if (directIndex >= 0) {
            val selected = root.children[directIndex]
            val updatedChildren = root.children.toMutableList().apply {
                if (selected.type.supportsChildren) {
                    set(directIndex, selected.withChildren(selected.children + newBlock))
                } else {
                    add(directIndex + 1, newBlock)
                }
            }
            return Mutation(root.withChildren(updatedChildren), true)
        }

        var changed = false
        val updatedChildren = root.children.map { child ->
            val updated = addNearSelectionInternal(child, selectedId, newBlock)
            if (updated.changed) {
                changed = true
            }
            updated.block
        }
        return if (changed) Mutation(root.withChildren(updatedChildren), true) else Mutation(root, false)
    }

    private data class Mutation(
        val block: BlockSpec,
        val changed: Boolean,
    )
}
