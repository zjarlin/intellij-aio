package site.addzero.composeblocks.managed

import site.addzero.composeblocks.model.BlockSpec
import site.addzero.composeblocks.model.ComposeBlockType
import site.addzero.composeblocks.model.ManagedComposeDocument

object ManagedComposeSourceGenerator {

    fun generate(document: ManagedComposeDocument): String {
        val slotDefaults = document.rawCodeBlocks.associateBy({ raw -> raw.id }, { raw -> raw.source.trim() })
        val slotBindings = collectSlotBindings(document.root, slotDefaults)
        return buildString {
            append(ManagedComposeDocumentCodec.renderHeader(document))
            append("\n\n")
            if (document.packageName.isNotBlank()) {
                append("package ${document.packageName}\n\n")
            }
            appendLine("import androidx.compose.foundation.Image")
            appendLine("import androidx.compose.foundation.layout.*")
            appendLine("import androidx.compose.material3.*")
            appendLine("import androidx.compose.runtime.Composable")
            appendLine("import androidx.compose.ui.Alignment")
            appendLine("import androidx.compose.ui.Modifier")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine()
            appendLine("@Composable")
            appendFunctionSignature(
                builder = this,
                composableName = document.composableName,
                slotBindings = slotBindings,
            )
            appendBlock(
                builder = this,
                block = document.root,
                indentLevel = 1,
                slotBindings = slotBindings,
            )
            appendLine("}")
        }
    }

    private fun appendFunctionSignature(
        builder: StringBuilder,
        composableName: String,
        slotBindings: List<SlotBinding>,
    ) {
        if (slotBindings.isEmpty()) {
            builder.appendLine("fun $composableName() {")
            return
        }

        builder.appendLine("fun $composableName(")
        slotBindings.forEachIndexed { index, slotBinding ->
            builder.append(indent(1))
            builder.append(slotBinding.parameterName)
            builder.append(": @Composable () -> Unit")
            val defaultSource = slotBinding.defaultSource
            if (!defaultSource.isNullOrBlank()) {
                builder.append(" = {\n")
                defaultSource.lineSequence().forEach { line ->
                    builder.append(indent(2))
                    builder.appendLine(line)
                }
                builder.append(indent(1))
                builder.append("}")
            }
            if (index != slotBindings.lastIndex) {
                builder.append(',')
            }
            builder.appendLine()
        }
        builder.appendLine(") {")
    }

    private fun appendBlock(
        builder: StringBuilder,
        block: BlockSpec,
        indentLevel: Int,
        slotBindings: List<SlotBinding>,
    ) {
        appendNote(builder, block.note, indentLevel)
        when (block.type) {
            ComposeBlockType.COLUMN -> appendContainerBlock(
                builder = builder,
                callName = "Column",
                block = block,
                indentLevel = indentLevel,
                slotBindings = slotBindings,
                extraArgs = listOfNotNull(
                    block.propValue("alignment")?.takeIf { it.isNotBlank() }?.let {
                        "horizontalAlignment = ${alignmentExpression(it)}"
                    },
                    block.propValue("arrangement")?.takeIf { it.isNotBlank() }?.let {
                        "verticalArrangement = ${arrangementExpression(it)}"
                    },
                ),
            )

            ComposeBlockType.ROW -> appendContainerBlock(
                builder = builder,
                callName = "Row",
                block = block,
                indentLevel = indentLevel,
                slotBindings = slotBindings,
                extraArgs = listOfNotNull(
                    block.propValue("alignment")?.takeIf { it.isNotBlank() }?.let {
                        "verticalAlignment = ${alignmentExpression(it)}"
                    },
                    block.propValue("arrangement")?.takeIf { it.isNotBlank() }?.let {
                        "horizontalArrangement = ${arrangementExpression(it)}"
                    },
                ),
            )

            ComposeBlockType.BOX -> appendContainerBlock(
                builder = builder,
                callName = if (block.children.any(::hasPositionedLayout)) "BoxWithConstraints" else "Box",
                block = block,
                indentLevel = indentLevel,
                slotBindings = slotBindings,
                extraArgs = listOfNotNull(
                    block.propValue("alignment")?.takeIf { it.isNotBlank() }?.let {
                        "contentAlignment = ${alignmentExpression(it)}"
                    },
                ),
            )

            ComposeBlockType.SLOT -> appendSlotBlock(
                builder = builder,
                block = block,
                indentLevel = indentLevel,
                slotBindings = slotBindings,
            )

            ComposeBlockType.TEXT -> appendLeafBlock(
                builder = builder,
                callName = "Text",
                indentLevel = indentLevel,
                args = listOfNotNull(
                    "text = ${quoted(block.propValue("text") ?: "Text")}",
                    modifierArgument(block),
                ),
            )

            ComposeBlockType.BUTTON -> appendButtonBlock(builder, block, indentLevel)
            ComposeBlockType.SPACER -> appendLeafBlock(
                builder = builder,
                callName = "Spacer",
                indentLevel = indentLevel,
                args = listOf(
                    modifierArgument(block) ?: "modifier = Modifier.size(16.dp)",
                ),
            )

            ComposeBlockType.IMAGE -> appendLeafBlock(
                builder = builder,
                callName = "Image",
                indentLevel = indentLevel,
                args = listOfNotNull(
                    "painter = TODO(\"Provide painter\")",
                    "contentDescription = ${quoted(block.propValue("text") ?: "Image")}",
                    modifierArgument(block) ?: "modifier = Modifier.size(64.dp)",
                ),
            )

            ComposeBlockType.TEXT_FIELD -> appendLeafBlock(
                builder = builder,
                callName = "TextField",
                indentLevel = indentLevel,
                args = listOfNotNull(
                    "value = \"\"",
                    "onValueChange = { }",
                    "label = { Text(${quoted(block.propValue("text") ?: "Label")}) }",
                    modifierArgument(block) ?: "modifier = Modifier.fillMaxWidth()",
                ),
            )
        }
    }

    private fun appendContainerBlock(
        builder: StringBuilder,
        callName: String,
        block: BlockSpec,
        indentLevel: Int,
        slotBindings: List<SlotBinding>,
        extraArgs: List<String>,
    ) {
        val args = listOfNotNull(modifierArgument(block)) + extraArgs
        builder.append(indent(indentLevel))
        builder.append(callName)
        if (args.isEmpty()) {
            builder.appendLine(" {")
        } else {
            builder.appendLine("(")
            args.forEach { arg ->
                builder.append(indent(indentLevel + 1))
                builder.append(arg)
                builder.appendLine(",")
            }
            builder.append(indent(indentLevel))
            builder.appendLine(") {")
        }
        if (block.children.isEmpty()) {
            builder.append(indent(indentLevel + 1))
            builder.appendLine("Text(\"Drop blocks here\")")
        } else {
            block.children.forEach { child ->
                appendBlock(builder, child, indentLevel + 1, slotBindings)
            }
        }
        builder.append(indent(indentLevel))
        builder.appendLine("}")
    }

    private fun appendSlotBlock(
        builder: StringBuilder,
        block: BlockSpec,
        indentLevel: Int,
        slotBindings: List<SlotBinding>,
    ) {
        val slotBinding = slotBindings.firstOrNull { binding -> binding.blockId == block.id } ?: return
        val modifier = modifierArgument(block)
        val contentAlignment = block.propValue("alignment")?.takeIf { it.isNotBlank() }?.let { alignmentExpression(it) }
        if (modifier == null && contentAlignment == null) {
            builder.append(indent(indentLevel))
            builder.append(slotBinding.parameterName)
            builder.appendLine("()")
            return
        }

        builder.append(indent(indentLevel))
        builder.appendLine("Box(")
        listOfNotNull(
            modifier,
            contentAlignment?.let { "contentAlignment = $it" },
        ).forEach { arg ->
            builder.append(indent(indentLevel + 1))
            builder.append(arg)
            builder.appendLine(",")
        }
        builder.append(indent(indentLevel))
        builder.appendLine(") {")
        builder.append(indent(indentLevel + 1))
        builder.append(slotBinding.parameterName)
        builder.appendLine("()")
        builder.append(indent(indentLevel))
        builder.appendLine("}")
    }

    private fun appendButtonBlock(
        builder: StringBuilder,
        block: BlockSpec,
        indentLevel: Int,
    ) {
        val args = listOfNotNull(
            "onClick = { }",
            modifierArgument(block),
        )
        builder.append(indent(indentLevel))
        builder.appendLine("Button(")
        args.forEach { arg ->
            builder.append(indent(indentLevel + 1))
            builder.append(arg)
            builder.appendLine(",")
        }
        builder.append(indent(indentLevel))
        builder.appendLine(") {")
        builder.append(indent(indentLevel + 1))
        builder.append("Text(")
        builder.append(quoted(block.propValue("text") ?: "Button"))
        builder.appendLine(")")
        builder.append(indent(indentLevel))
        builder.appendLine("}")
    }

    private fun appendLeafBlock(
        builder: StringBuilder,
        callName: String,
        indentLevel: Int,
        args: List<String>,
    ) {
        builder.append(indent(indentLevel))
        builder.append(callName)
        builder.appendLine("(")
        args.forEach { arg ->
            builder.append(indent(indentLevel + 1))
            builder.append(arg)
            builder.appendLine(",")
        }
        builder.append(indent(indentLevel))
        builder.appendLine(")")
    }

    private fun appendNote(
        builder: StringBuilder,
        note: String,
        indentLevel: Int,
    ) {
        if (note.isBlank()) {
            return
        }
        builder.append(indent(indentLevel))
        builder.append("/** ")
        builder.append(note.replace("*/", "* /"))
        builder.appendLine(" */")
    }

    private fun modifierArgument(block: BlockSpec): String? {
        val modifierParts = mutableListOf<String>()
        block.propValue("fillMaxWidth")?.takeIf { it.equals("true", ignoreCase = true) || it.isNotBlank() }?.let { value ->
            modifierParts += if (value.equals("true", ignoreCase = true)) {
                "fillMaxWidth()"
            } else {
                "fillMaxWidth($value)"
            }
        }
        block.propValue("fillMaxHeight")?.takeIf { it.equals("true", ignoreCase = true) || it.isNotBlank() }?.let { value ->
            modifierParts += if (value.equals("true", ignoreCase = true)) {
                "fillMaxHeight()"
            } else {
                "fillMaxHeight($value)"
            }
        }
        block.propValue("padding")?.takeIf { it.isNotBlank() }?.let { value ->
            modifierParts += "padding(${dimensionExpression(value)})"
        }
        block.propValue("size")?.takeIf { it.isNotBlank() }?.let { value ->
            modifierParts += "size(${dimensionExpression(value)})"
        }
        block.propValue("width")?.takeIf { it.isNotBlank() }?.let { value ->
            modifierParts += "width(${dimensionExpression(value)})"
        }
        block.propValue("height")?.takeIf { it.isNotBlank() }?.let { value ->
            modifierParts += "height(${dimensionExpression(value)})"
        }
        block.propValue("weight")?.takeIf { it.isNotBlank() }?.let { value ->
            modifierParts += "weight(${weightExpression(value)})"
        }
        block.propValue("widthFraction")?.takeIf { it.isNotBlank() }?.let { value ->
            modifierParts += "fillMaxWidth(${fractionExpression(value)})"
        }
        block.propValue("heightFraction")?.takeIf { it.isNotBlank() }?.let { value ->
            modifierParts += "fillMaxHeight(${fractionExpression(value)})"
        }
        val xFraction = block.propValue("xFraction")?.takeIf { it.isNotBlank() }
        val yFraction = block.propValue("yFraction")?.takeIf { it.isNotBlank() }
        if (xFraction != null || yFraction != null) {
            val xExpression = xFraction?.let { "maxWidth * ${fractionExpression(it)}" } ?: "0.dp"
            val yExpression = yFraction?.let { "maxHeight * ${fractionExpression(it)}" } ?: "0.dp"
            modifierParts += "offset(x = $xExpression, y = $yExpression)"
        }

        if (modifierParts.isEmpty()) {
            return null
        }

        return "modifier = Modifier.${modifierParts.joinToString(".")}"
    }

    private fun hasPositionedLayout(block: BlockSpec): Boolean {
        return block.propValue("xFraction")?.isNotBlank() == true ||
            block.propValue("yFraction")?.isNotBlank() == true ||
            block.propValue("widthFraction")?.isNotBlank() == true ||
            block.propValue("heightFraction")?.isNotBlank() == true
    }

    private fun collectSlotBindings(
        root: BlockSpec,
        slotDefaults: Map<String, String>,
    ): List<SlotBinding> {
        val usedNames = linkedMapOf<String, Int>()
        return collectBlocks(root)
            .filter { block -> block.type == ComposeBlockType.SLOT }
            .map { slotBlock ->
                val rawName = slotBlock.propValue("slotName")
                    ?: slotBlock.note.takeIf { it.isNotBlank() }
                    ?: "slot"
                val parameterName = uniqueSlotName(
                    usedNames = usedNames,
                    baseName = sanitizeSlotName(rawName),
                )
                SlotBinding(
                    blockId = slotBlock.id,
                    parameterName = parameterName,
                    defaultSource = slotDefaults[slotBlock.id],
                )
            }
    }

    private fun uniqueSlotName(
        usedNames: MutableMap<String, Int>,
        baseName: String,
    ): String {
        val current = usedNames[baseName]
        return if (current == null) {
            usedNames[baseName] = 1
            baseName
        } else {
            val next = current + 1
            usedNames[baseName] = next
            "${baseName}${next}"
        }
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
        val candidate = (first + rest).replaceFirst(Regex("^[^A-Za-z_]"), "_$0")
        return candidate.ifBlank { "slot" }
    }

    private fun collectBlocks(block: BlockSpec): List<BlockSpec> {
        return listOf(block) + block.children.flatMap(::collectBlocks)
    }

    private fun alignmentExpression(value: String): String {
        return if (value.startsWith("Alignment.")) value else "Alignment.$value"
    }

    private fun arrangementExpression(value: String): String {
        return if (value.startsWith("Arrangement.")) value else "Arrangement.$value"
    }

    private fun dimensionExpression(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.endsWith(".dp") || trimmed.endsWith("dp") -> trimmed
            trimmed.endsWith('f') || trimmed.endsWith('%') -> trimmed
            trimmed.all { it.isDigit() } -> "${trimmed}.dp"
            else -> trimmed
        }
    }

    private fun weightExpression(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.endsWith('f')) trimmed else "${trimmed}f"
    }

    private fun fractionExpression(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.endsWith('f')) trimmed else "${trimmed}f"
    }

    private fun quoted(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    else -> append(char)
                }
            }
            append('"')
        }
    }

    private fun indent(level: Int): String = "    ".repeat(level)

    private data class SlotBinding(
        val blockId: String,
        val parameterName: String,
        val defaultSource: String?,
    )
}
