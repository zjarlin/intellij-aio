package site.addzero.composeblocks.model

enum class ManagedComposeKind(
    val suffix: String,
    val presentableName: String,
) {
    SCREEN("Screen", "Screen"),
    DIALOG("Dialog", "Dialog"),
    PAGE("Page", "Page"),
}

enum class ComposeBlockType(
    val displayName: String,
    val supportsChildren: Boolean,
) {
    COLUMN("Column", true),
    ROW("Row", true),
    BOX("Box", true),
    SLOT("Slot", false),
    TEXT("Text", false),
    BUTTON("Button", false),
    SPACER("Spacer", false),
    IMAGE("Image", false),
    TEXT_FIELD("TextField", false),
}

data class PropSpec(
    val name: String,
    val value: String,
)

data class SlotSpec(
    val name: String,
    val blocks: List<BlockSpec> = emptyList(),
)

data class BlockSpec(
    val id: String,
    val type: ComposeBlockType,
    val note: String = "",
    val props: List<PropSpec> = emptyList(),
    val slots: List<SlotSpec> = if (type.supportsChildren) listOf(SlotSpec(DEFAULT_SLOT_NAME)) else emptyList(),
) {
    val displayTitle: String
        get() = note.takeIf { it.isNotBlank() } ?: type.displayName

    val children: List<BlockSpec>
        get() = slots.firstOrNull { it.name == DEFAULT_SLOT_NAME }?.blocks.orEmpty()

    fun propValue(name: String): String? = props.firstOrNull { it.name == name }?.value

    fun withChildren(children: List<BlockSpec>): BlockSpec {
        if (!type.supportsChildren) {
            return this
        }
        val otherSlots = slots.filterNot { it.name == DEFAULT_SLOT_NAME }
        return copy(slots = listOf(SlotSpec(DEFAULT_SLOT_NAME, children)) + otherSlots)
    }

    fun upsertProp(name: String, value: String?): BlockSpec {
        val normalized = value?.trim().orEmpty()
        val remaining = props.filterNot { it.name == name }
        return if (normalized.isBlank()) {
            copy(props = remaining)
        } else {
            copy(props = remaining + PropSpec(name, normalized))
        }
    }

    fun withNote(value: String): BlockSpec = copy(note = value.trim())

    companion object {
        const val DEFAULT_SLOT_NAME = "children"

        fun create(
            id: String,
            type: ComposeBlockType,
            note: String = "",
            props: List<PropSpec> = emptyList(),
            children: List<BlockSpec> = emptyList(),
        ): BlockSpec {
            val slots = if (type.supportsChildren) {
                listOf(SlotSpec(DEFAULT_SLOT_NAME, children))
            } else {
                emptyList()
            }
            return BlockSpec(
                id = id,
                type = type,
                note = note,
                props = props,
                slots = slots,
            )
        }
    }
}

data class RawCodeBlockSpec(
    val id: String,
    val note: String = "",
    val source: String,
)

data class ManagedComposeDocument(
    val version: Int = CURRENT_VERSION,
    val kind: ManagedComposeKind,
    val packageName: String,
    val composableName: String,
    val root: BlockSpec,
    val rawCodeBlocks: List<RawCodeBlockSpec> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1

        fun create(
            kind: ManagedComposeKind,
            packageName: String,
            baseName: String,
            idFactory: () -> String,
        ): ManagedComposeDocument {
            val normalizedBaseName = baseName
                .trim()
                .ifBlank { kind.suffix }
                .replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }

            val functionName = if (normalizedBaseName.endsWith(kind.suffix)) {
                normalizedBaseName
            } else {
                normalizedBaseName + kind.suffix
            }

            val root = BlockSpec.create(
                id = idFactory(),
                type = ComposeBlockType.COLUMN,
                note = functionName,
                props = listOf(
                    PropSpec("fillMaxWidth", "true"),
                    PropSpec("padding", "16.dp"),
                ),
            )

            return ManagedComposeDocument(
                kind = kind,
                packageName = packageName,
                composableName = functionName,
                root = root,
            )
        }
    }
}
