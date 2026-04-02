package site.addzero.composeblocks.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.composeblocks.model.ComposeBlockAxis
import site.addzero.composeblocks.model.ComposeBlockKind
import site.addzero.composeblocks.model.ComposeBlockNode
import site.addzero.composeblocks.parser.ComposeBlockTreeBuilder
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.CompoundBorder
import javax.swing.border.LineBorder
import javax.swing.border.MatteBorder

class ComposeBlocksFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor, Disposable {

    private val document: Document = requireNotNull(FileDocumentManager.getInstance().getDocument(file)) {
        "Compose Blocks requires a document-backed file"
    }

    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val embeddedEditor: EditorEx = (EditorFactory.getInstance().createEditor(
        document,
        project,
        file,
        false,
    ) as EditorEx).also { editor ->
        editor.settings.isFoldingOutlineShown = false
        editor.settings.additionalColumnsCount = 0
        editor.settings.additionalLinesCount = 0
        editor.settings.isWhitespacesShown = false
        editor.component.border = BorderFactory.createEmptyBorder()
    }

    private val rootPanel = JPanel(BorderLayout())
    private val blockCanvasPanel = JPanel()
    private val statusLabel = JBLabel("Loading Compose blocks...")
    private val hintLabel = JBLabel("Edit the source inline or double-click a block to update its doc comment.")
    private val hideShellsCheckBox = JBCheckBox("Hide shell composables", true)

    private var visibleRoots: List<ComposeBlockNode> = emptyList()
    private var selectedNode: ComposeBlockNode? = null
    private var updatingSelectionFromBlocks = false
    private var pendingInlineCommentFocusNodeId: String? = null

    init {
        Disposer.register(this) {
            refreshAlarm.cancelAllRequests()
        }

        blockCanvasPanel.layout = BoxLayout(blockCanvasPanel, BoxLayout.Y_AXIS)

        val toolbarPanel = buildToolbar()
        val canvasScrollPane = JBScrollPane(blockCanvasPanel).apply {
            minimumSize = Dimension(500, 320)
            preferredSize = Dimension(900, 640)
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBar.unitIncrement = 24
            border = BorderFactory.createEmptyBorder()
        }

        rootPanel.add(toolbarPanel, BorderLayout.NORTH)
        rootPanel.add(canvasScrollPane, BorderLayout.CENTER)

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                scheduleRefresh()
            }
        }, this)

        embeddedEditor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                syncSelectionFromCaret()
            }
        }, this)

        hideShellsCheckBox.addActionListener {
            refreshModel()
        }

        refreshModel()
    }

    override fun getComponent(): JComponent {
        return rootPanel
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return embeddedEditor.contentComponent
    }

    override fun getName(): String {
        return "Compose Blocks"
    }

    override fun getFile(): VirtualFile {
        return file
    }

    override fun setState(state: FileEditorState) {
    }

    override fun isModified(): Boolean {
        return FileDocumentManager.getInstance().isFileModified(file)
    }

    override fun isValid(): Boolean {
        return file.isValid
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun getCurrentLocation(): FileEditorLocation? {
        return null
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(embeddedEditor)
    }

    private fun buildToolbar(): JComponent {
        val refreshButton = JButton("Refresh").apply {
            addActionListener {
                refreshModel()
            }
        }

        val leftControls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6)).apply {
            isOpaque = false
            add(refreshButton)
            add(hideShellsCheckBox)
        }

        hintLabel.foreground = JBColor.GRAY

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(4, 8),
            )
            add(leftControls, BorderLayout.WEST)
            add(hintLabel, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.EAST)
        }
    }

    private fun scheduleRefresh() {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest(
            { refreshModel() },
            180,
        )
    }

    private fun refreshModel() {
        val ktFile = createSnapshotKtFile()
        if (ktFile == null) {
            renderEmptyState("Compose Blocks only works on Kotlin PSI files.")
            clearFocus()
            return
        }

        visibleRoots = ComposeBlockTreeBuilder.build(ktFile, hideShellsCheckBox.isSelected)
        selectedNode = findBestSelection(embeddedEditor.caretModel.offset)
        renderCanvas()

        val currentSelection = selectedNode
        if (currentSelection != null) {
            focusRange(currentSelection.focusRange, currentSelection.navigationOffset)
        } else {
            clearFocus()
        }
    }

    private fun renderCanvas() {
        blockCanvasPanel.removeAll()
        if (visibleRoots.isEmpty()) {
            renderEmptyState("No @Composable functions were found in this file.")
            return
        }

        visibleRoots.forEachIndexed { index, root ->
            val blockComponent = createBlockComponent(root)
            blockComponent.alignmentX = Component.LEFT_ALIGNMENT
            blockCanvasPanel.add(blockComponent)
            if (index != visibleRoots.lastIndex) {
                blockCanvasPanel.add(Box.createVerticalStrut(12))
            }
        }

        val totalBlocks = flattenNodes(visibleRoots).size
        statusLabel.text = "Showing $totalBlocks blocks in ${visibleRoots.size} composable functions"
        blockCanvasPanel.revalidate()
        blockCanvasPanel.repaint()
    }

    private fun renderEmptyState(message: String) {
        blockCanvasPanel.removeAll()
        blockCanvasPanel.add(
            JBLabel(message).apply {
                border = JBUI.Borders.empty(16)
                alignmentX = Component.LEFT_ALIGNMENT
            }
        )
        statusLabel.text = message
        blockCanvasPanel.revalidate()
        blockCanvasPanel.repaint()
    }

    private fun createBlockComponent(node: ComposeBlockNode): JPanel {
        val isSelected = selectedNode?.id == node.id
        val accentColor = kindColor(node.kind)
        val line = document.getLineNumber(node.navigationOffset).plus(1)
        val childCountText = if (node.children.isEmpty()) "leaf" else "${node.children.size} children"

        val titleLabel = JBLabel(node.displayTitle).apply {
            font = font.deriveFont(Font.BOLD.toFloat())
        }

        val metaLabel = JBLabel(buildMetaText(node, line, childCountText)).apply {
            foreground = JBColor.GRAY
            border = JBUI.Borders.emptyTop(4)
        }

        val summaryPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.NORTH)
            add(metaLabel, BorderLayout.SOUTH)
        }

        val headerPanel = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(summaryPanel, BorderLayout.WEST)
            if (isSelected) {
                add(createInlineCommentEditor(node), BorderLayout.CENTER)
            }
        }

        val blockPanel = JPanel(BorderLayout(0, 10)).apply {
            isOpaque = true
            background = if (isSelected) {
                JBColor(Color(235, 244, 255), Color(58, 68, 88))
            } else {
                JBColor.PanelBackground
            }
            border = CompoundBorder(
                CompoundBorder(
                    MatteBorder(0, 4, 0, 0, accentColor),
                    LineBorder(if (isSelected) accentColor else JBColor.border(), 1, true),
                ),
                JBUI.Borders.empty(10, 12, 10, 12),
            )
            alignmentX = Component.LEFT_ALIGNMENT
            add(headerPanel, BorderLayout.NORTH)
        }

        if (node.children.isNotEmpty()) {
            blockPanel.add(createChildrenPanel(node), BorderLayout.CENTER)
        }

        if (isSelected) {
            blockPanel.add(createInlineEditorHost(), BorderLayout.SOUTH)
        } else if (node.kind == ComposeBlockKind.LEAF) {
            blockPanel.preferredSize = Dimension(180, 78)
        }

        installSelectionHandler(node, blockPanel, headerPanel, titleLabel, metaLabel)
        return blockPanel
    }

    private fun createChildrenPanel(node: ComposeBlockNode): JPanel {
        val panel = JPanel()
        panel.isOpaque = false
        panel.border = JBUI.Borders.emptyTop(2)

        when (node.axis) {
            ComposeBlockAxis.HORIZONTAL -> {
                panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
                node.children.forEachIndexed { index, child ->
                    val childComponent = createBlockComponent(child).apply {
                        alignmentY = Component.TOP_ALIGNMENT
                    }
                    panel.add(childComponent)
                    if (index != node.children.lastIndex) {
                        panel.add(Box.createHorizontalStrut(10))
                    }
                }
            }

            ComposeBlockAxis.VERTICAL,
            ComposeBlockAxis.STACK -> {
                panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
                node.children.forEachIndexed { index, child ->
                    val childComponent = createBlockComponent(child).apply {
                        alignmentX = Component.LEFT_ALIGNMENT
                    }
                    panel.add(childComponent)
                    if (index != node.children.lastIndex) {
                        panel.add(Box.createVerticalStrut(10))
                    }
                }
            }
        }

        return panel
    }

    private fun createInlineEditorHost(): JComponent {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(8)
            preferredSize = Dimension(400, 220)
            add(
                JBLabel("Live Compose source").apply {
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.emptyBottom(6)
                },
                BorderLayout.NORTH,
            )
            add(embeddedEditor.component, BorderLayout.CENTER)
        }
    }

    private fun createInlineCommentEditor(node: ComposeBlockNode): JComponent {
        val textField = JBTextField(node.commentText.orEmpty()).apply {
            toolTipText = "Block note. Leave empty to remove it from this block."
            emptyText.text = "Add a note for this block"
            foreground = JBColor(Color(255, 141, 43), Color(255, 181, 112))
            background = JBColor(
                Color(248, 250, 255),
                Color(66, 75, 96),
            )
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(4, 8),
            )
            addActionListener {
                commitInlineComment(node, text)
            }
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(event: FocusEvent) {
                    commitInlineComment(node, text)
                }
            })
        }

        if (pendingInlineCommentFocusNodeId == node.id) {
            pendingInlineCommentFocusNodeId = null
            javax.swing.SwingUtilities.invokeLater {
                textField.requestFocusInWindow()
                textField.selectAll()
            }
        }

        return textField
    }

    private fun installSelectionHandler(node: ComposeBlockNode, vararg components: JComponent) {
        val listener = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount >= 2) {
                    applySelection(node, focusInlineComment = true)
                    return
                }
                applySelection(node)
            }

            override fun mouseEntered(event: MouseEvent) {
                event.component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }
        }

        components.forEach { component ->
            component.addMouseListener(listener)
        }
    }

    private fun applySelection(node: ComposeBlockNode, focusInlineComment: Boolean = false) {
        pendingInlineCommentFocusNodeId = node.id.takeIf { focusInlineComment }
        selectedNode = node
        renderCanvas()
        updatingSelectionFromBlocks = true
        try {
            focusRange(node.focusRange, node.navigationOffset)
        } finally {
            updatingSelectionFromBlocks = false
        }
        if (!focusInlineComment) {
            embeddedEditor.contentComponent.requestFocusInWindow()
        }
    }

    private fun applyBlockComment(node: ComposeBlockNode, rawCommentText: String) {
        val normalizedComment = normalizeComment(rawCommentText)
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return

        WriteCommandAction.runWriteCommandAction(project, "Update Compose Block Comment", null, Runnable {
            when {
                node.commentRange != null && normalizedComment.isBlank() -> {
                    val deleteRange = expandCommentRemovalRange(node.commentRange)
                    document.deleteString(deleteRange.startOffset, deleteRange.endOffset)
                }

                node.commentRange != null -> {
                    document.replaceString(
                        node.commentRange.startOffset,
                        node.commentRange.endOffset,
                        formatDocComment(normalizedComment),
                    )
                }

                normalizedComment.isNotBlank() -> {
                    val insertOffset = document.getLineStartOffset(document.getLineNumber(node.focusRange.startOffset))
                    document.insertString(insertOffset, buildInsertedComment(insertOffset, normalizedComment))
                }
            }
        }, psiFile)
    }

    private fun clearFocus() {
        embeddedEditor.foldingModel.runBatchFoldingOperation {
            embeddedEditor.foldingModel.allFoldRegions.forEach { region ->
                embeddedEditor.foldingModel.removeFoldRegion(region)
            }
        }
    }

    private fun focusRange(range: TextRange, navigationOffset: Int) {
        embeddedEditor.foldingModel.runBatchFoldingOperation {
            embeddedEditor.foldingModel.allFoldRegions.forEach { region ->
                embeddedEditor.foldingModel.removeFoldRegion(region)
            }

            if (range.startOffset > 0) {
                embeddedEditor.foldingModel.addFoldRegion(0, range.startOffset, "…")
            }
            if (range.endOffset < document.textLength) {
                embeddedEditor.foldingModel.addFoldRegion(range.endOffset, document.textLength, "…")
            }
            embeddedEditor.foldingModel.allFoldRegions.forEach { it.isExpanded = false }
        }

        embeddedEditor.caretModel.moveToOffset(navigationOffset.coerceIn(range.startOffset, range.endOffset))
        embeddedEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    private fun commitInlineComment(node: ComposeBlockNode, rawCommentText: String) {
        val normalizedComment = normalizeComment(rawCommentText)
        if (normalizedComment == node.commentText.orEmpty()) {
            return
        }
        applyBlockComment(node, rawCommentText)
    }

    private fun syncSelectionFromCaret() {
        if (updatingSelectionFromBlocks || visibleRoots.isEmpty()) {
            return
        }

        val nextSelection = findBestSelection(embeddedEditor.caretModel.offset) ?: return
        if (nextSelection.id == selectedNode?.id) {
            return
        }

        selectedNode = nextSelection
        renderCanvas()
        embeddedEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    }

    private fun findBestSelection(caretOffset: Int): ComposeBlockNode? {
        val allNodes = flattenNodes(visibleRoots)
        val previousById = selectedNode?.id?.let { selectedId ->
            allNodes.firstOrNull { it.id == selectedId }
        }
        if (previousById != null) {
            return previousById
        }

        val byCaret = allNodes
            .filter { it.focusRange.contains(caretOffset) }
            .minByOrNull { it.focusRange.length }
        if (byCaret != null) {
            return byCaret
        }

        val previousOffset = selectedNode?.navigationOffset
        if (previousOffset != null) {
            return allNodes
                .filter { it.focusRange.contains(previousOffset) }
                .minByOrNull { it.focusRange.length }
        }

        return allNodes.firstOrNull()
    }

    private fun flattenNodes(nodes: List<ComposeBlockNode>): List<ComposeBlockNode> {
        return nodes.flatMap { node ->
            listOf(node) + flattenNodes(node.children)
        }
    }

    private fun buildMetaText(node: ComposeBlockNode, line: Int, childCountText: String): String {
        val axisText = when (node.axis) {
            ComposeBlockAxis.VERTICAL -> "Vertical"
            ComposeBlockAxis.HORIZONTAL -> "Horizontal"
            ComposeBlockAxis.STACK -> "Stack"
        }

        val parts = mutableListOf<String>()
        parts += node.name
        parts += kindLabel(node.kind)
        if (node.children.isNotEmpty()) {
            parts += axisText
        }
        parts += "line $line"
        parts += childCountText
        return parts.joinToString(" · ")
    }

    private fun kindLabel(kind: ComposeBlockKind): String {
        return when (kind) {
            ComposeBlockKind.ROOT -> "Function"
            ComposeBlockKind.CONTAINER -> "Container"
            ComposeBlockKind.LEAF -> "Leaf"
            ComposeBlockKind.SHELL -> "Shell"
        }
    }

    private fun kindColor(kind: ComposeBlockKind): Color {
        return when (kind) {
            ComposeBlockKind.ROOT -> JBColor(Color(45, 125, 210), Color(111, 176, 255))
            ComposeBlockKind.CONTAINER -> JBColor(Color(31, 138, 112), Color(86, 196, 166))
            ComposeBlockKind.LEAF -> JBColor(Color(125, 130, 138), Color(164, 170, 178))
            ComposeBlockKind.SHELL -> JBColor(Color(180, 120, 24), Color(245, 190, 92))
        }
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

    private fun buildInsertedComment(insertOffset: Int, commentText: String): String {
        val lineNumber = document.getLineNumber(insertOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.charsSequence.subSequence(lineStart, lineEnd).toString()
        val indent = lineText.takeWhile { it == ' ' || it == '\t' }
        return "$indent${formatDocComment(commentText)}\n"
    }

    private fun expandCommentRemovalRange(commentRange: TextRange): TextRange {
        val lineNumber = document.getLineNumber(commentRange.startOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val beforeComment = document.charsSequence.subSequence(lineStart, commentRange.startOffset)
        val afterComment = document.charsSequence.subSequence(commentRange.endOffset, lineEnd)
        if (beforeComment.isBlank() && afterComment.isBlank()) {
            val deleteEnd = if (lineEnd < document.textLength) {
                minOf(lineEnd + 1, document.textLength)
            } else {
                lineEnd
            }
            return TextRange(lineStart, deleteEnd)
        }

        return commentRange
    }

    private fun createSnapshotKtFile(): KtFile? {
        return PsiFileFactory.getInstance(project)
            .createFileFromText(
                file.name,
                KotlinFileType.INSTANCE,
                document.text,
            ) as? KtFile
    }
}
