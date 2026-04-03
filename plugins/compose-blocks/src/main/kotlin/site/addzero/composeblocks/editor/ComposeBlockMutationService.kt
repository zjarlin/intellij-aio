package site.addzero.composeblocks.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import site.addzero.composeblocks.model.ComposeBlockKind
import site.addzero.composeblocks.model.ComposeBlockNode
import site.addzero.composeblocks.model.ComposeEditableContainerKind

internal enum class ComposeBlockTemplate(
    val label: String,
    val source: String,
) {
    TEXT(
        label = "Text",
        source = """Text("Text")""",
    ),
    BUTTON(
        label = "Button",
        source = """
            Button(onClick = { }) {
                Text("Button")
            }
        """.trimIndent(),
    ),
    SPACER(
        label = "Spacer",
        source = """Spacer(modifier = Modifier)""",
    ),
    BOX(
        label = "Box",
        source = """
            Box {
            }
        """.trimIndent(),
    ),
    ROW(
        label = "Row",
        source = """
            Row {
            }
        """.trimIndent(),
    ),
    COLUMN(
        label = "Column",
        source = """
            Column {
            }
        """.trimIndent(),
    ),
}

internal data class ComposeBlockLayoutProperties(
    val arrangement: String = "",
    val alignment: String = "",
    val contentAlignment: String = "",
    val horizontalArrangement: String = "",
    val verticalAlignment: String = "",
    val padding: String = "",
    val weight: String = "",
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
)

internal class ComposeBlockMutationService(
    private val project: Project,
    private val file: VirtualFile,
    private val document: Document,
) {

    private val psiFactory = KtPsiFactory(project, false)

    fun updateDocComment(node: ComposeBlockNode, rawCommentText: String): Int? {
        val normalizedComment = normalizeComment(rawCommentText)
        val existingCommentRange = node.commentRange ?: findLeadingCommentClusterRange(node)
        if (shouldSkipCommentUpdate(node, normalizedComment, existingCommentRange)) {
            return node.navigationOffset
        }
        return runWriteCommand("Update Compose Block Comment") {
            when {
                existingCommentRange != null && normalizedComment.isBlank() -> {
                    val deleteRange = expandWholeLineRange(existingCommentRange)
                    document.deleteString(deleteRange.startOffset, deleteRange.endOffset)
                    deleteRange.startOffset
                }

                existingCommentRange != null -> {
                    document.replaceString(
                        existingCommentRange.startOffset,
                        existingCommentRange.endOffset,
                        formatDocComment(normalizedComment),
                    )
                    existingCommentRange.startOffset
                }

                normalizedComment.isNotBlank() -> {
                    val insertOffset = document.getLineStartOffset(document.getLineNumber(node.focusRange.startOffset))
                    document.insertString(insertOffset, buildInsertedComment(insertOffset, normalizedComment))
                    insertOffset
                }

                else -> {
                    node.navigationOffset
                }
            }
        }
    }

    private fun shouldSkipCommentUpdate(
        node: ComposeBlockNode,
        normalizedComment: String,
        existingCommentRange: TextRange?,
    ): Boolean {
        if (normalizedComment != node.commentText.orEmpty()) {
            return false
        }

        if (existingCommentRange == null) {
            return normalizedComment.isBlank()
        }

        if (normalizedComment.isBlank()) {
            return false
        }

        val existingSource = document.getText(existingCommentRange).trim()
        return existingSource == formatDocComment(normalizedComment)
    }

    private fun findLeadingCommentClusterRange(node: ComposeBlockNode): TextRange? {
        val anchorOffset = node.renderRange.startOffset.coerceIn(0, document.textLength)
        var line = document.getLineNumber(anchorOffset) - 1
        var startOffset: Int? = null
        var endOffset: Int? = null
        while (line >= 0) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val trimmed = document.charsSequence.subSequence(lineStart, lineEnd).toString().trim()
            if (trimmed.startsWith("/*")) {
                startOffset = lineStart
                if (endOffset == null) {
                    endOffset = lineEnd
                }
                line -= 1
                continue
            }
            break
        }
        if (startOffset == null || endOffset == null) {
            return null
        }
        return TextRange(startOffset, endOffset)
    }

    fun insertTemplate(
        containerNode: ComposeBlockNode,
        slotIndex: Int,
        template: ComposeBlockTemplate,
    ): Int? {
        if (!containerNode.isLowCodeEditable) {
            return null
        }

        val contentRange = containerNode.contentRange ?: return null
        val fragments = containerNode.children
            .map(::extractNodeFragment)
            .toMutableList()
        val targetIndex = slotIndex.coerceIn(0, fragments.size)
        fragments.add(targetIndex, template.source)
        val rebuiltContent = rebuildContainerContent(containerNode, fragments, targetIndex)
        return runWriteCommand("Insert Compose Block") {
            document.replaceString(contentRange.startOffset, contentRange.endOffset, rebuiltContent.text)
            rebuiltContent.anchorOffset?.let { contentRange.startOffset + it } ?: containerNode.navigationOffset
        }
    }

    fun moveChild(
        containerNode: ComposeBlockNode,
        childNode: ComposeBlockNode,
        slotIndex: Int,
    ): Int? {
        if (!containerNode.isLowCodeEditable) {
            return null
        }

        val contentRange = containerNode.contentRange ?: return null
        val childIndex = containerNode.children.indexOfFirst { it.id == childNode.id }
        if (childIndex < 0) {
            return null
        }

        val fragments = containerNode.children
            .map(::extractNodeFragment)
            .toMutableList()
        val movingFragment = fragments.removeAt(childIndex)
        val adjustedSlotIndex = if (slotIndex > childIndex) {
            slotIndex - 1
        } else {
            slotIndex
        }
        val targetIndex = adjustedSlotIndex.coerceIn(0, fragments.size)
        fragments.add(targetIndex, movingFragment)
        val rebuiltContent = rebuildContainerContent(containerNode, fragments, targetIndex)
        return runWriteCommand("Reorder Compose Block") {
            document.replaceString(contentRange.startOffset, contentRange.endOffset, rebuiltContent.text)
            rebuiltContent.anchorOffset?.let { contentRange.startOffset + it } ?: containerNode.navigationOffset
        }
    }

    fun wrapNode(
        node: ComposeBlockNode,
        wrapperKind: ComposeEditableContainerKind,
    ): Int? {
        if (node.kind == ComposeBlockKind.ROOT) {
            return null
        }

        val wrapperName = when (wrapperKind) {
            ComposeEditableContainerKind.ROW -> "Row"
            ComposeEditableContainerKind.COLUMN -> "Column"
            ComposeEditableContainerKind.BOX -> "Box"
        }
        val baseIndent = lineIndent(node.renderRange.startOffset)
        val childIndent = baseIndent + defaultIndentUnit()
        val wrappedSource = buildString {
            append(wrapperName)
            append(" {\n")
            append(indentMultiline(normalizeFragment(document.getText(node.renderRange)), childIndent))
            append('\n')
            append(baseIndent)
            append('}')
        }
        return runWriteCommand("Wrap Compose Block") {
            document.replaceString(node.renderRange.startOffset, node.renderRange.endOffset, wrappedSource)
            node.renderRange.startOffset
        }
    }

    fun simplifyContainer(node: ComposeBlockNode): Int? {
        if (node.children.size > 1) {
            return null
        }

        return runWriteCommand("Simplify Compose Container") {
            when {
                node.children.isEmpty() -> {
                    val deleteRange = expandWholeLineRange(node.focusRange)
                    document.deleteString(deleteRange.startOffset, deleteRange.endOffset)
                    deleteRange.startOffset
                }

                else -> {
                    val replacement = reindentFragment(
                        fragment = extractNodeFragment(node.children.single()),
                        indent = lineIndent(node.renderRange.startOffset),
                    )
                    document.replaceString(node.renderRange.startOffset, node.renderRange.endOffset, replacement)
                    node.renderRange.startOffset
                }
            }
        }
    }

    fun readLayoutProperties(node: ComposeBlockNode): ComposeBlockLayoutProperties {
        val call = parseCall(node) ?: return ComposeBlockLayoutProperties()
        val namedArguments = call.valueArguments.associate { argument ->
            val name = argument.getArgumentName()?.asName?.asString().orEmpty()
            name to argument.getArgumentExpression()?.text.orEmpty()
        }
        val modifierExpression = namedArguments["modifier"].orEmpty()
        return ComposeBlockLayoutProperties(
            arrangement = namedArguments["arrangement"].orEmpty(),
            alignment = namedArguments["alignment"].orEmpty(),
            contentAlignment = namedArguments["contentAlignment"].orEmpty(),
            horizontalArrangement = namedArguments["horizontalArrangement"].orEmpty(),
            verticalAlignment = namedArguments["verticalAlignment"].orEmpty(),
            padding = extractModifierArgument(modifierExpression, "padding"),
            weight = extractModifierArgument(modifierExpression, "weight"),
            fillMaxWidth = hasModifierCall(modifierExpression, "fillMaxWidth"),
            fillMaxHeight = hasModifierCall(modifierExpression, "fillMaxHeight"),
        )
    }

    fun updateLayoutProperties(
        node: ComposeBlockNode,
        properties: ComposeBlockLayoutProperties,
    ): Int? {
        val call = parseCall(node) ?: return null
        val updatedCallText = updateCallText(call, properties)
        if (updatedCallText == document.getText(node.renderRange)) {
            return node.navigationOffset
        }
        return runWriteCommand("Update Compose Layout Properties") {
            document.replaceString(node.renderRange.startOffset, node.renderRange.endOffset, updatedCallText)
            node.renderRange.startOffset
        }
    }

    private fun updateCallText(
        call: KtCallExpression,
        properties: ComposeBlockLayoutProperties,
    ): String {
        val calleeText = call.calleeExpression?.text ?: return call.text
        val typeArguments = call.typeArgumentList?.text.orEmpty()
        val trailingLambdas = call.lambdaArguments.joinToString(" ") { it.text }
        val preservedArguments = mutableListOf<String>()
        var existingModifierExpression: String? = null

        call.valueArguments.forEach { argument ->
            val argumentName = argument.getArgumentName()?.asName?.asString()
            when (argumentName) {
                "arrangement",
                "alignment",
                "contentAlignment",
                "horizontalArrangement",
                "verticalAlignment",
                -> Unit

                "modifier" -> {
                    existingModifierExpression = argument.getArgumentExpression()?.text
                }

                else -> {
                    preservedArguments += argument.text
                }
            }
        }

        val managedArguments = buildManagedArguments(properties, existingModifierExpression)
        val allArguments = preservedArguments + managedArguments

        return buildString {
            append(calleeText)
            append(typeArguments)
            if (allArguments.isNotEmpty()) {
                append('(')
                append(allArguments.joinToString(", "))
                append(')')
            }
            if (trailingLambdas.isNotBlank()) {
                append(' ')
                append(trailingLambdas)
            }
        }
    }

    private fun buildManagedArguments(
        properties: ComposeBlockLayoutProperties,
        existingModifierExpression: String?,
    ): List<String> {
        val managedArguments = mutableListOf<String>()
        if (properties.arrangement.isNotBlank()) {
            managedArguments += "arrangement = ${properties.arrangement.trim()}"
        }
        if (properties.alignment.isNotBlank()) {
            managedArguments += "alignment = ${properties.alignment.trim()}"
        }
        if (properties.contentAlignment.isNotBlank()) {
            managedArguments += "contentAlignment = ${properties.contentAlignment.trim()}"
        }
        if (properties.horizontalArrangement.isNotBlank()) {
            managedArguments += "horizontalArrangement = ${properties.horizontalArrangement.trim()}"
        }
        if (properties.verticalAlignment.isNotBlank()) {
            managedArguments += "verticalAlignment = ${properties.verticalAlignment.trim()}"
        }
        val modifierExpression = updateModifierExpression(existingModifierExpression, properties)
        if (modifierExpression != null) {
            managedArguments += "modifier = $modifierExpression"
        }
        return managedArguments
    }

    private fun updateModifierExpression(
        existingModifierExpression: String?,
        properties: ComposeBlockLayoutProperties,
    ): String? {
        var modifierBase = existingModifierExpression?.trim().orEmpty()
        modifierBase = stripModifierCall(modifierBase, "padding")
        modifierBase = stripModifierCall(modifierBase, "weight")
        modifierBase = stripModifierCall(modifierBase, "fillMaxWidth")
        modifierBase = stripModifierCall(modifierBase, "fillMaxHeight")
        modifierBase = modifierBase.trim().removeSuffix(".")
        if (modifierBase.isBlank()) {
            modifierBase = "Modifier"
        }

        val modifierCalls = mutableListOf<String>()
        if (properties.fillMaxWidth) {
            modifierCalls += "fillMaxWidth()"
        }
        if (properties.fillMaxHeight) {
            modifierCalls += "fillMaxHeight()"
        }
        if (properties.padding.isNotBlank()) {
            modifierCalls += "padding(${properties.padding.trim()})"
        }
        if (properties.weight.isNotBlank()) {
            modifierCalls += "weight(${properties.weight.trim()})"
        }

        val hasManagedModifier = modifierCalls.isNotEmpty()
        if (!hasManagedModifier && modifierBase == "Modifier" && existingModifierExpression.isNullOrBlank()) {
            return null
        }

        return buildString {
            append(modifierBase)
            modifierCalls.forEach { modifierCall ->
                append('.')
                append(modifierCall)
            }
        }
    }

    private fun parseCall(node: ComposeBlockNode): KtCallExpression? {
        val callText = document.getText(node.renderRange)
        return try {
            psiFactory.createExpression(callText) as? KtCallExpression
        } catch (_: Exception) {
            null
        }
    }

    private fun extractModifierArgument(
        modifierExpression: String,
        name: String,
    ): String {
        val regex = Regex("""\.$name\(([^)]*)\)""")
        return regex.find(modifierExpression)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun hasModifierCall(
        modifierExpression: String,
        name: String,
    ): Boolean {
        return Regex("""\.$name\(([^)]*)\)""").containsMatchIn(modifierExpression)
    }

    private fun stripModifierCall(
        modifierExpression: String,
        name: String,
    ): String {
        if (modifierExpression.isBlank()) {
            return modifierExpression
        }
        return modifierExpression.replace(Regex("""\.$name\([^)]*\)"""), "")
    }

    private fun rebuildContainerContent(
        containerNode: ComposeBlockNode,
        fragments: List<String>,
        anchorIndex: Int?,
    ): RebuiltContent {
        if (fragments.isEmpty()) {
            return RebuiltContent(
                text = "",
                anchorOffset = null,
            )
        }

        val containerIndent = lineIndent(containerNode.renderRange.startOffset)
        val childIndent = childIndent(containerNode, containerIndent)
        val normalizedFragments = fragments.map(::normalizeFragment)
        val bodyText = buildString {
            append('\n')
            append(
                normalizedFragments.joinToString("\n\n") { fragment ->
                    indentMultiline(fragment, childIndent)
                }
            )
            append('\n')
            append(containerIndent)
        }
        val anchorOffset = anchorIndex
            ?.takeIf { it in normalizedFragments.indices }
            ?.let { normalizedFragments[it] }
            ?.let { firstMeaningfulLine(it) }
            ?.let { "$childIndent$it" }
            ?.let(bodyText::indexOf)
            ?.takeIf { it >= 0 }
        return RebuiltContent(
            text = bodyText,
            anchorOffset = anchorOffset,
        )
    }

    private fun extractNodeFragment(node: ComposeBlockNode): String {
        return document.getText(node.focusRange)
    }

    private fun normalizeFragment(fragment: String): String {
        return fragment
            .trim('\n', '\r')
            .trimEnd()
            .trimIndent()
    }

    private fun reindentFragment(
        fragment: String,
        indent: String,
    ): String {
        return indentMultiline(normalizeFragment(fragment), indent)
    }

    private fun indentMultiline(
        text: String,
        indent: String,
    ): String {
        return text.lineSequence().joinToString("\n") { line ->
            if (line.isBlank()) {
                ""
            } else {
                indent + line
            }
        }
    }

    private fun firstMeaningfulLine(fragment: String): String? {
        return fragment.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
    }

    private fun childIndent(
        containerNode: ComposeBlockNode,
        containerIndent: String,
    ): String {
        val firstChild = containerNode.children.firstOrNull()
        if (firstChild != null) {
            return lineIndent(firstChild.focusRange.startOffset)
        }

        return containerIndent + defaultIndentUnit()
    }

    private fun lineIndent(offset: Int): String {
        val lineNumber = document.getLineNumber(offset.coerceIn(0, document.textLength))
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.charsSequence.subSequence(lineStart, lineEnd).toString()
        return lineText.takeWhile { it == ' ' || it == '\t' }
    }

    private fun defaultIndentUnit(): String {
        return "    "
    }

    private fun normalizeComment(commentText: String): String {
        return commentText
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" ")
            .replace("*/", "* /")
            .trim()
    }

    private fun formatDocComment(commentText: String): String {
        return "/** $commentText */"
    }

    private fun buildInsertedComment(
        insertOffset: Int,
        commentText: String,
    ): String {
        val lineNumber = document.getLineNumber(insertOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.charsSequence.subSequence(lineStart, lineEnd).toString()
        val indent = lineText.takeWhile { it == ' ' || it == '\t' }
        return "$indent${formatDocComment(commentText)}\n"
    }

    private fun expandWholeLineRange(range: TextRange): TextRange {
        val safeStart = range.startOffset.coerceIn(0, document.textLength)
        val safeEnd = range.endOffset.coerceIn(0, document.textLength)
        val startLine = document.getLineNumber(safeStart)
        val endLine = document.getLineNumber((safeEnd - 1).coerceAtLeast(safeStart))
        val lineStart = document.getLineStartOffset(startLine)
        val lineEnd = document.getLineEndOffset(endLine)
        val before = document.charsSequence.subSequence(lineStart, safeStart)
        val after = document.charsSequence.subSequence(safeEnd, lineEnd)
        if (before.isBlank() && after.isBlank()) {
            val deleteEnd = if (lineEnd < document.textLength) {
                minOf(lineEnd + 1, document.textLength)
            } else {
                lineEnd
            }
            return TextRange(lineStart, deleteEnd)
        }

        return TextRange(safeStart, safeEnd)
    }

    private fun <T> runWriteCommand(
        name: String,
        action: () -> T,
    ): T {
        return WriteCommandAction.writeCommandAction(project)
            .withName(name)
            .compute<T, RuntimeException> {
                action()
            }
    }

    private data class RebuiltContent(
        val text: String,
        val anchorOffset: Int?,
    )
}
