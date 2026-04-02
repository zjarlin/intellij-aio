package site.addzero.composeblocks.parser

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import site.addzero.composeblocks.model.ComposeBlockAxis
import site.addzero.composeblocks.model.ComposeBlockKind
import site.addzero.composeblocks.model.ComposeBlockNode
import site.addzero.composeblocks.model.ComposeEditableContainerKind

object ComposeBlockTreeBuilder {

    private val verticalLayoutNames = setOf(
        "Column",
        "LazyColumn",
        "LazyVerticalGrid",
        "FlowColumn",
        "DropdownMenu",
        "AlertDialog",
    )

    private val horizontalLayoutNames = setOf(
        "Row",
        "LazyRow",
        "LazyHorizontalGrid",
        "FlowRow",
        "TabRow",
        "ScrollableTabRow",
        "NavigationBar",
        "NavigationRail",
    )

    private val stackLayoutNames = setOf(
        "Box",
        "Scaffold",
        "BottomSheetScaffold",
        "Surface",
        "Card",
        "ElevatedCard",
        "OutlinedCard",
        "AnimatedVisibility",
        "AnimatedContent",
        "Crossfade",
        "ModalBottomSheet",
        "ConstraintLayout",
    )

    private val dslContainerNames = setOf(
        "item",
        "items",
        "itemsIndexed",
        "stickyHeader",
    )

    private val leafNames = setOf(
        "Text",
        "BasicText",
        "BasicTextField",
        "TextField",
        "OutlinedTextField",
        "Button",
        "OutlinedButton",
        "TextButton",
        "IconButton",
        "Icon",
        "Image",
        "Spacer",
        "Divider",
        "HorizontalDivider",
        "VerticalDivider",
        "Checkbox",
        "Switch",
        "RadioButton",
        "Slider",
        "LinearProgressIndicator",
        "CircularProgressIndicator",
    )

    private val shellHints = listOf(
        "Shell",
        "Screen",
        "Page",
        "Route",
        "Host",
        "Wrapper",
        "Container",
        "Scaffold",
    )

    fun build(ktFile: KtFile, hideShells: Boolean): List<ComposeBlockNode> {
        val roots = PsiTreeUtil.findChildrenOfType(ktFile, KtNamedFunction::class.java)
            .asSequence()
            .filter { it.isComposable() }
            .mapNotNull(::buildFunctionNode)
            .toList()

        if (!hideShells) {
            return roots
        }

        return roots.map(::collapseShellNodes)
    }

    private fun buildFunctionNode(function: KtNamedFunction): ComposeBlockNode? {
        val bodyExpression = function.bodyExpression ?: return null
        val functionName = function.name ?: "Composable"
        val comment = findLeadingBlockComment(function)
        return ComposeBlockNode(
            id = buildNodeId(functionName, function.textRange.startOffset, function.textRange.endOffset),
            name = functionName,
            kind = ComposeBlockKind.ROOT,
            axis = ComposeBlockAxis.VERTICAL,
            focusRange = mergeRanges(comment?.range, function.textRange),
            renderRange = function.textRange,
            contentRange = bodyExpression.textRange,
            navigationOffset = comment?.range?.startOffset ?: bodyExpression.textRange.startOffset,
            commentText = comment?.text,
            commentRange = comment?.range,
            editableContainerKind = null,
            isLowCodeEditable = false,
            argumentListRange = null,
            argumentInsertOffset = null,
            children = collectNodes(bodyExpression),
        )
    }

    private fun collectNodes(expression: KtExpression?): List<ComposeBlockNode> {
        if (expression == null) {
            return emptyList()
        }

        return when (expression) {
            is KtBlockExpression -> {
                expression.statements.flatMap(::collectNodes)
            }

            is KtCallExpression -> {
                collectFromCall(expression)
            }

            is KtIfExpression -> {
                collectNodes(expression.then) + collectNodes(expression.`else`)
            }

            is KtWhenExpression -> {
                expression.entries.flatMap { collectNodes(it.expression) }
            }

            is KtLambdaExpression -> {
                collectNodes(expression.bodyExpression)
            }

            is KtQualifiedExpression -> {
                collectNodes(expression.selectorExpression)
            }

            else -> {
                expression.children
                    .filterIsInstance<KtExpression>()
                    .flatMap(::collectNodes)
            }
        }
    }

    private fun collectFromCall(call: KtCallExpression): List<ComposeBlockNode> {
        val calleeName = call.calleeExpression?.text ?: return collectLambdaChildren(call)
        val children = collectLambdaChildren(call)
        val kind = classifyBlockKind(calleeName, children.isNotEmpty()) ?: return children
        val comment = findLeadingBlockComment(call)
        val editableContainerKind = classifyEditableContainerKind(calleeName)

        return listOf(
            ComposeBlockNode(
                id = buildNodeId(calleeName, call.textRange.startOffset, call.textRange.endOffset),
                name = calleeName,
                kind = kind,
                axis = classifyAxis(calleeName, kind),
                focusRange = mergeRanges(comment?.range, call.textRange),
                renderRange = call.textRange,
                contentRange = findContentRange(call),
                navigationOffset = comment?.range?.startOffset ?: findNavigationOffset(call),
                commentText = comment?.text,
                commentRange = comment?.range,
                editableContainerKind = editableContainerKind,
                isLowCodeEditable = isLowCodeEditableCall(call, editableContainerKind, children),
                argumentListRange = call.valueArgumentList?.textRange,
                argumentInsertOffset = findArgumentInsertOffset(call),
                children = children,
            )
        )
    }

    private fun collectLambdaChildren(call: KtCallExpression): List<ComposeBlockNode> {
        val trailingLambdaBodies = call.lambdaArguments
            .mapNotNull { it.getLambdaExpression()?.bodyExpression }

        val valueLambdaBodies = call.valueArguments
            .mapNotNull { it.getArgumentExpression() as? KtLambdaExpression }
            .mapNotNull { it.bodyExpression }

        return (trailingLambdaBodies + valueLambdaBodies)
            .distinctBy { it.textRange.startOffset to it.textRange.endOffset }
            .flatMap(::collectNodes)
    }

    private fun classifyBlockKind(name: String, hasChildren: Boolean): ComposeBlockKind? {
        if (hasChildren) {
            if (name in verticalLayoutNames || name in horizontalLayoutNames || name in stackLayoutNames || name in dslContainerNames) {
                return ComposeBlockKind.CONTAINER
            }

            if (shellHints.any { hint -> name.contains(hint, ignoreCase = true) }) {
                return ComposeBlockKind.SHELL
            }

            if (name.firstOrNull()?.isUpperCase() == true) {
                return ComposeBlockKind.SHELL
            }

            return ComposeBlockKind.CONTAINER
        }

        if (name in leafNames) {
            return ComposeBlockKind.LEAF
        }

        if (name.firstOrNull()?.isUpperCase() == true) {
            return ComposeBlockKind.LEAF
        }

        return null
    }

    private fun classifyAxis(name: String, kind: ComposeBlockKind): ComposeBlockAxis {
        if (kind == ComposeBlockKind.ROOT) {
            return ComposeBlockAxis.VERTICAL
        }

        if (name in horizontalLayoutNames) {
            return ComposeBlockAxis.HORIZONTAL
        }

        if (name in stackLayoutNames) {
            return ComposeBlockAxis.STACK
        }

        return ComposeBlockAxis.VERTICAL
    }

    private fun classifyEditableContainerKind(name: String): ComposeEditableContainerKind? {
        return when (name) {
            "Row" -> ComposeEditableContainerKind.ROW
            "Column" -> ComposeEditableContainerKind.COLUMN
            "Box" -> ComposeEditableContainerKind.BOX
            else -> null
        }
    }

    private fun KtNamedFunction.isComposable(): Boolean {
        return annotationEntries.any { annotation ->
            annotation.shortName?.asString() == "Composable"
        }
    }

    private fun collapseShellNodes(node: ComposeBlockNode): ComposeBlockNode {
        val collapsedChildren = collapseShellChildren(node.children)
        return node.copy(
            children = collapsedChildren.nodes,
            isLowCodeEditable = node.isLowCodeEditable && !collapsedChildren.flattenedShell,
        )
    }

    private fun collapseShellChildren(nodes: List<ComposeBlockNode>): CollapsedChildren {
        var flattenedShell = false
        val collapsedNodes = nodes.flatMap { childNode ->
            val collapsedNode = collapseShellNodes(childNode)
            if (collapsedNode.kind == ComposeBlockKind.SHELL) {
                flattenedShell = true
                collapsedNode.children
            } else {
                listOf(collapsedNode)
            }
        }
        return CollapsedChildren(
            nodes = collapsedNodes,
            flattenedShell = flattenedShell,
        )
    }

    private fun buildNodeId(name: String, startOffset: Int, endOffset: Int): String {
        return "$name:$startOffset:$endOffset"
    }

    private fun findNavigationOffset(call: KtCallExpression): Int {
        val textRange = call.textRange
        val text = call.text
        val offsetInCall = text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: 0
        return textRange.startOffset + offsetInCall
    }

    private fun findContentRange(call: KtCallExpression): TextRange? {
        val body = findPrimaryLambdaBody(call) ?: return null
        val start = body.lBrace?.textRange?.endOffset ?: body.textRange.startOffset
        val end = body.rBrace?.textRange?.startOffset ?: body.textRange.endOffset
        return TextRange(start, end)
    }

    private fun findPrimaryLambdaBody(call: KtCallExpression): KtBlockExpression? {
        return call.lambdaArguments.firstOrNull()
            ?.getLambdaExpression()
            ?.bodyExpression
            ?: call.valueArguments
                .mapNotNull { it.getArgumentExpression() as? KtLambdaExpression }
                .firstOrNull()
                ?.bodyExpression
    }

    private fun findArgumentInsertOffset(call: KtCallExpression): Int? {
        val valueArgumentList = call.valueArgumentList
        if (valueArgumentList != null) {
            return valueArgumentList.textRange.endOffset - 1
        }

        return call.calleeExpression?.textRange?.endOffset
    }

    private fun isLowCodeEditableCall(
        call: KtCallExpression,
        editableContainerKind: ComposeEditableContainerKind?,
        children: List<ComposeBlockNode>,
    ): Boolean {
        if (editableContainerKind == null) {
            return false
        }

        val body = findPrimaryLambdaBody(call) ?: return false
        val statements = body.statements
        if (statements.isEmpty()) {
            return true
        }

        val directCalls = statements.mapNotNull(::unwrapDirectCall)
        if (directCalls.size != statements.size) {
            return false
        }

        if (directCalls.any { directCall ->
                val calleeName = directCall.calleeExpression?.text
                calleeName == null || calleeName in dslContainerNames
            }
        ) {
            return false
        }

        if (directCalls.size != children.size) {
            return false
        }

        return directCalls.zip(children).all { (directCall, childNode) ->
            directCall.textRange.startOffset == childNode.renderRange.startOffset
        }
    }

    private fun unwrapDirectCall(expression: KtExpression): KtCallExpression? {
        return when (expression) {
            is KtCallExpression -> expression
            is KtQualifiedExpression -> expression.selectorExpression as? KtCallExpression
            else -> null
        }
    }

    private fun findLeadingBlockComment(element: PsiElement): BlockCommentInfo? {
        var current = element.prevSibling
        while (current != null) {
            when (current) {
                is PsiWhiteSpace -> {
                    current = current.prevSibling
                }

                is PsiComment -> {
                    if (!current.text.startsWith("/*")) {
                        return null
                    }
                    return BlockCommentInfo(
                        text = normalizeBlockComment(current.text),
                        range = current.textRange,
                    )
                }

                else -> {
                    return null
                }
            }
        }
        return null
    }

    private fun normalizeBlockComment(commentText: String): String {
        return commentText
            .removePrefix("/*")
            .removeSuffix("*/")
            .lineSequence()
            .map { it.trim().removePrefix("*").trim() }
            .joinToString(" ")
            .trim()
    }

    private fun mergeRanges(commentRange: TextRange?, bodyRange: TextRange): TextRange {
        if (commentRange == null) {
            return bodyRange
        }

        return TextRange(
            minOf(commentRange.startOffset, bodyRange.startOffset),
            maxOf(commentRange.endOffset, bodyRange.endOffset),
        )
    }

    private data class BlockCommentInfo(
        val text: String,
        val range: TextRange,
    )

    private data class CollapsedChildren(
        val nodes: List<ComposeBlockNode>,
        val flattenedShell: Boolean,
    )
}
