package site.addzero.composebuddy.designer.ui

import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposeGeneratedCode
import site.addzero.composebuddy.designer.model.ComposeDesignerPaletteCatalog
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import java.awt.Rectangle

object ComposeDesignerCodeGenerator {
    private val baseImports = linkedSetOf(
        "androidx.compose.runtime.Composable",
    )

    private val layoutImports = linkedSetOf(
        "androidx.compose.foundation.Image",
        "androidx.compose.foundation.background",
        "androidx.compose.foundation.layout.*",
        "androidx.compose.foundation.shape.RoundedCornerShape",
        "androidx.compose.material3.Button",
        "androidx.compose.material3.Text",
        "androidx.compose.ui.draw.clip",
        "androidx.compose.ui.graphics.Color",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.res.painterResource",
        "androidx.compose.ui.unit.dp",
    )

    fun generate(nodes: List<ComposeCanvasNode>, functionName: String): ComposeGeneratedCode {
        if (nodes.isEmpty()) {
            return ComposeGeneratedCode(
                imports = baseImports,
                functionText = listOf(
                    "@Composable",
                    "fun $functionName() {",
                    "}",
                ).joinToString("\n"),
            )
        }

        val imports = linkedSetOf<String>().apply {
            addAll(baseImports)
            addAll(layoutImports)
            nodes.forEach { node ->
                ComposeDesignerPaletteCatalog.resolveCustomByFunction(node.customFunctionName)?.imports?.let(::addAll)
            }
        }
        val bodyLines = buildList {
            add("@Composable")
            add("fun $functionName() {")
            val rootChildren = nodes.filter { it.parentId == null }.sortedBy { it.bounds.y * 10_000 + it.bounds.x }
            rootChildren.forEach { node ->
                addAll(renderNode(node, nodes, parent = null, indent = "    "))
            }
            add("}")
        }

        return ComposeGeneratedCode(
            imports = imports,
            functionText = bodyLines.joinToString("\n"),
        )
    }

    private fun renderNode(
        node: ComposeCanvasNode,
        allNodes: List<ComposeCanvasNode>,
        parent: ComposeCanvasNode?,
        indent: String,
    ): List<String> {
        val effectiveKind = ComposeDesignerLayoutSupport.effectiveKind(node)
        val children = allNodes.filter { it.parentId == node.id }.sortedWith(layoutComparator(effectiveKind))
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
            ComposePaletteItem.CUSTOM -> renderCustomNode(node, children, allNodes, parent, indent)
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
        val lines = mutableListOf("$indent$callName(")
        lines += "$indent    modifier = ${modifierFor(node, parent)}"
        lines += "$indent        .clip(RoundedCornerShape(22.dp))"
        lines += "$indent        .background(Color(0xFFFFFFFF))"
        lines += "$indent        .padding(20.dp),"
        when (node.kind) {
            ComposePaletteItem.ROW -> lines += "$indent    horizontalArrangement = Arrangement.spacedBy(16.dp),"
            ComposePaletteItem.COLUMN -> lines += "$indent    verticalArrangement = Arrangement.spacedBy(16.dp),"
            else -> Unit
        }
        lines += "$indent) {"
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

    private fun renderCustomNode(
        node: ComposeCanvasNode,
        children: List<ComposeCanvasNode>,
        allNodes: List<ComposeCanvasNode>,
        parent: ComposeCanvasNode?,
        indent: String,
    ): List<String> {
        val modifier = modifierFor(node, parent)
        val custom = ComposeDesignerPaletteCatalog.resolveCustomByFunction(node.customFunctionName)
        val childrenText = children.joinToString("\n") { child ->
            renderNode(child, allNodes, node, "").joinToString("\n")
        }
        val template = custom?.template
            ?.replace("{modifier}", modifier)
            ?.replace("{children}", childrenText.prependIndent("    ").trimEnd())
            ?: "${node.customFunctionName ?: "UnknownComposable"}(modifier = $modifier)"
        return template.lines().map { "$indent$it" }
    }

    private fun modifierFor(node: ComposeCanvasNode, parent: ComposeCanvasNode?): String {
        val relative = relativeBounds(node.bounds, parent?.bounds)
        val parentKind = parent?.let(ComposeDesignerLayoutSupport::effectiveKind)
        return when {
            parent == null || parentKind == ComposePaletteItem.BOX -> "Modifier.offset(${relative.x}.dp, ${relative.y}.dp).size(${relative.width}.dp, ${relative.height}.dp)"
            parentKind == ComposePaletteItem.COLUMN && relative.width >= parent.bounds.width - 48 -> "Modifier.fillMaxWidth().height(${relative.height}.dp)"
            parentKind == ComposePaletteItem.ROW && relative.height >= parent.bounds.height - 48 -> "Modifier.width(${relative.width}.dp).fillMaxHeight()"
            else -> "Modifier.size(${relative.width}.dp, ${relative.height}.dp)"
        }
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
