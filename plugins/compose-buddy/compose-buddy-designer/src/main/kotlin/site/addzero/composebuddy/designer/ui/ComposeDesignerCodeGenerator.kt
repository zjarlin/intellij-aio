package site.addzero.composebuddy.designer.ui

import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposeGeneratedCode
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import java.awt.Rectangle

object ComposeDesignerCodeGenerator {
    private val defaultImports = linkedSetOf(
        "androidx.compose.foundation.Image",
        "androidx.compose.foundation.layout.*",
        "androidx.compose.material.Button",
        "androidx.compose.material.Text",
        "androidx.compose.runtime.Composable",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.res.painterResource",
        "androidx.compose.ui.unit.dp",
    )

    fun generate(nodes: List<ComposeCanvasNode>, functionName: String): ComposeGeneratedCode {
        val bodyLines = buildList {
            add("@Composable")
            add("fun $functionName() {")
            add("    Box(modifier = Modifier.fillMaxSize()) {")
            val rootChildren = nodes.filter { it.parentId == null }.sortedBy { it.bounds.y * 10_000 + it.bounds.x }
            if (rootChildren.isEmpty()) {
                add("        // ${ComposeBuddyBundle.message("designer.canvas.empty")}")
            } else {
                rootChildren.forEach { node ->
                    addAll(renderNode(node, nodes, parent = null, indent = "        "))
                }
            }
            add("    }")
            add("}")
        }

        val preview = buildString {
            defaultImports.forEach { appendLine("import $it") }
            appendLine()
            append(bodyLines.joinToString("\n"))
        }.trimEnd()

        return ComposeGeneratedCode(
            imports = defaultImports,
            functionText = bodyLines.joinToString("\n"),
            previewText = preview,
        )
    }

    private fun renderNode(
        node: ComposeCanvasNode,
        allNodes: List<ComposeCanvasNode>,
        parent: ComposeCanvasNode?,
        indent: String,
    ): List<String> {
        val children = allNodes.filter { it.parentId == node.id }.sortedWith(layoutComparator(node.kind))
        return when (node.kind) {
            ComposePaletteItem.BOX,
            ComposePaletteItem.ROW,
            ComposePaletteItem.COLUMN,
            -> renderContainer(node, children, allNodes, parent, indent)

            ComposePaletteItem.TEXT -> listOf("$indent${textCall(node, parent)}")
            ComposePaletteItem.BUTTON -> listOf(
                "$indent${buttonStart(node, parent)}",
                "$indent    Text(\"${ComposeBuddyBundle.message("designer.node.button")}\")",
                "$indent}",
            )
            ComposePaletteItem.IMAGE -> listOf("$indent${imageCall(node, parent)}")
            ComposePaletteItem.SPACER -> listOf("$indent${spacerCall(node, parent)}")
        }
    }

    private fun renderContainer(
        node: ComposeCanvasNode,
        children: List<ComposeCanvasNode>,
        allNodes: List<ComposeCanvasNode>,
        parent: ComposeCanvasNode?,
        indent: String,
    ): List<String> {
        val callName = when (node.kind) {
            ComposePaletteItem.BOX -> "Box"
            ComposePaletteItem.ROW -> "Row"
            ComposePaletteItem.COLUMN -> "Column"
            else -> error("Not a container")
        }
        val lines = mutableListOf("$indent$callName(modifier = ${modifierFor(node, parent, isContainer = true)}) {")
        if (children.isEmpty()) {
            lines += "$indent}"
            return lines
        }

        children.forEachIndexed { index, child ->
            if (node.kind == ComposePaletteItem.ROW && index > 0) {
                val previous = children[index - 1]
                val gap = (child.bounds.x - (previous.bounds.x + previous.bounds.width)).coerceAtLeast(0)
                if (gap > 0) {
                    lines += "$indent    Spacer(modifier = Modifier.width(${gap}.dp))"
                }
            }
            if (node.kind == ComposePaletteItem.COLUMN && index > 0) {
                val previous = children[index - 1]
                val gap = (child.bounds.y - (previous.bounds.y + previous.bounds.height)).coerceAtLeast(0)
                if (gap > 0) {
                    lines += "$indent    Spacer(modifier = Modifier.height(${gap}.dp))"
                }
            }
            lines += renderNode(child, allNodes, node, "$indent    ")
        }
        lines += "$indent}"
        return lines
    }

    private fun layoutComparator(kind: ComposePaletteItem): Comparator<ComposeCanvasNode> {
        return when (kind) {
            ComposePaletteItem.ROW -> compareBy<ComposeCanvasNode> { it.bounds.x }.thenBy { it.bounds.y }
            ComposePaletteItem.COLUMN -> compareBy<ComposeCanvasNode> { it.bounds.y }.thenBy { it.bounds.x }
            else -> compareBy<ComposeCanvasNode> { it.bounds.y }.thenBy { it.bounds.x }
        }
    }

    private fun textCall(node: ComposeCanvasNode, parent: ComposeCanvasNode?): String {
        return "Text(text = \"${ComposeBuddyBundle.message("designer.node.text")}\", modifier = ${modifierFor(node, parent)})"
    }

    private fun buttonStart(node: ComposeCanvasNode, parent: ComposeCanvasNode?): String {
        return "Button(onClick = { }, modifier = ${modifierFor(node, parent)}) {"
    }

    private fun imageCall(node: ComposeCanvasNode, parent: ComposeCanvasNode?): String {
        return "Image(painter = painterResource(\"placeholder.png\"), contentDescription = null, modifier = ${modifierFor(node, parent)})"
    }

    private fun spacerCall(node: ComposeCanvasNode, parent: ComposeCanvasNode?): String {
        return "Spacer(modifier = ${modifierFor(node, parent)})"
    }

    private fun modifierFor(node: ComposeCanvasNode, parent: ComposeCanvasNode?, isContainer: Boolean = false): String {
        val relative = relativeBounds(node.bounds, parent?.bounds)
        val base = when {
            parent == null || parent.kind == ComposePaletteItem.BOX -> {
                val size = if (isContainer) "Modifier.offset(${relative.x}.dp, ${relative.y}.dp).size(${relative.width}.dp, ${relative.height}.dp)" else "Modifier.offset(${relative.x}.dp, ${relative.y}.dp).size(${relative.width}.dp, ${relative.height}.dp)"
                size
            }
            else -> "Modifier.size(${relative.width}.dp, ${relative.height}.dp)"
        }
        return base
    }

    private fun relativeBounds(bounds: Rectangle, parentBounds: Rectangle?): Rectangle {
        if (parentBounds == null) return bounds
        return Rectangle(
            bounds.x - parentBounds.x,
            bounds.y - parentBounds.y,
            bounds.width,
            bounds.height,
        )
    }
}
