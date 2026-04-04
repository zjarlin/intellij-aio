package site.addzero.composebuddy.designer.ui

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposeDesignerPaletteCatalog
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import java.awt.Rectangle

object ComposeDesignerCodeParser {
    fun parse(
        project: Project,
        text: String,
        functionName: String,
    ): List<ComposeCanvasNode>? {
        val psiFile = PsiFileFactory.getInstance(project)
            .createFileFromText("ComposeDesignerGenerated.kt", KotlinFileType.INSTANCE, text) as? KtFile
            ?: return null
        val function = psiFile.declarations
            .filterIsInstance<KtNamedFunction>()
            .firstOrNull { it.name == functionName }
            ?: psiFile.declarations.filterIsInstance<KtNamedFunction>().firstOrNull()
            ?: return null
        val rootCalls = function.bodyBlockExpression
            ?.statements
            ?.filterIsInstance<KtCallExpression>()
            .orEmpty()
        if (rootCalls.isEmpty()) {
            return emptyList()
        }

        val nodes = mutableListOf<ComposeCanvasNode>()
        rootCalls.forEach { rootCall ->
            val node = buildNode(rootCall, null) ?: return@forEach
            nodes += node
            collectNodes(rootCall, node.id, nodes)
        }
        return nodes
    }

    private fun collectNodes(
        parentCall: KtCallExpression,
        parentId: String?,
        nodes: MutableList<ComposeCanvasNode>,
    ) {
        childCalls(parentCall).forEach { child ->
            val node = buildNode(child, parentId) ?: return@forEach
            nodes += node
            collectNodes(child, node.id, nodes)
        }
    }

    private fun childCalls(call: KtCallExpression): List<KtCallExpression> {
        return call.lambdaArguments
            .flatMap { lambda ->
                lambda.getLambdaExpression()
                    ?.bodyExpression
                    ?.statements
                    ?.mapNotNull { statement -> statement as? KtCallExpression }
                    .orEmpty()
            }
    }

    private fun buildNode(
        call: KtCallExpression,
        parentId: String?,
    ): ComposeCanvasNode? {
        val calleeName = call.calleeExpression?.text ?: return null
        val custom = ComposeDesignerPaletteCatalog.resolveCustomByFunction(calleeName)
        val kind = mapKind(calleeName, custom != null) ?: return null
        return ComposeCanvasNode(
            id = "parsed-${call.textRange.startOffset}-${call.textRange.endOffset}",
            kind = kind,
            bounds = boundsFor(call, kind),
            parentId = parentId,
            customName = custom?.displayName,
            customFunctionName = custom?.functionName,
            customLayoutKind = custom?.layoutKind,
        )
    }

    private fun mapKind(name: String, isCustom: Boolean): ComposePaletteItem? {
        if (isCustom) {
            return ComposePaletteItem.CUSTOM
        }
        return when (name) {
            "Box" -> ComposePaletteItem.BOX
            "Row" -> ComposePaletteItem.ROW
            "Column" -> ComposePaletteItem.COLUMN
            "Text" -> ComposePaletteItem.TEXT
            "Button" -> ComposePaletteItem.BUTTON
            "Image" -> ComposePaletteItem.IMAGE
            "Spacer" -> ComposePaletteItem.SPACER
            else -> null
        }
    }

    private fun boundsFor(
        call: KtCallExpression,
        kind: ComposePaletteItem,
    ): Rectangle {
        val text = call.text
        val custom = ComposeDesignerPaletteCatalog.resolveCustomByFunction(call.calleeExpression?.text)
        val offsetMatch = Regex("""offset\((\d+)\.dp,\s*(\d+)\.dp\)""").find(text)
        val sizeMatch = Regex("""size\((\d+)\.dp,\s*(\d+)\.dp\)""").find(text)
        val widthMatch = Regex("""width\((\d+)\.dp\)""").find(text)
        val heightMatch = Regex("""height\((\d+)\.dp\)""").find(text)
        return Rectangle(
            offsetMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 48,
            offsetMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 48,
            sizeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: widthMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: custom?.width
                ?: defaultWidth(kind),
            sizeMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
                ?: heightMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: custom?.height
                ?: defaultHeight(kind),
        )
    }

    private fun defaultWidth(kind: ComposePaletteItem): Int {
        return when (kind) {
            ComposePaletteItem.TEXT -> 160
            ComposePaletteItem.BUTTON -> 180
            ComposePaletteItem.IMAGE -> 160
            ComposePaletteItem.BOX,
            ComposePaletteItem.ROW,
            ComposePaletteItem.COLUMN,
            -> 220
            ComposePaletteItem.SPACER -> 100
            ComposePaletteItem.CUSTOM -> 180
        }
    }

    private fun defaultHeight(kind: ComposePaletteItem): Int {
        return when (kind) {
            ComposePaletteItem.TEXT -> 48
            ComposePaletteItem.BUTTON -> 56
            ComposePaletteItem.IMAGE -> 120
            ComposePaletteItem.BOX,
            ComposePaletteItem.ROW,
            ComposePaletteItem.COLUMN,
            -> 120
            ComposePaletteItem.SPACER -> 24
            ComposePaletteItem.CUSTOM -> 56
        }
    }
}
