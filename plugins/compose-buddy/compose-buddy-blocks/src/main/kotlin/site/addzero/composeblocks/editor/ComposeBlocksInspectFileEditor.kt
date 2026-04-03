package site.addzero.composeblocks.editor

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
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
import site.addzero.composeblocks.model.ComposeEditableContainerKind
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
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingConstants
import javax.swing.border.CompoundBorder
import javax.swing.border.LineBorder
import javax.swing.border.MatteBorder

class ComposeBlocksInspectFileEditor(
    project: Project,
    file: VirtualFile,
) : ComposeBlocksFileEditorBase(project, file) {

    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val decorationController = ComposeBlockDecorationController(document)
    private val mutationService = ComposeBlockMutationService(project, sourceFile, document)
    private val embeddedEditor: EditorEx = (EditorFactory.getInstance().createEditor(
        document,
        project,
        sourceFile,
        false,
    ) as EditorEx).also { editor ->
        editor.settings.isFoldingOutlineShown = false
        editor.settings.additionalColumnsCount = 0
        editor.settings.additionalLinesCount = 0
        editor.settings.isWhitespacesShown = false
        editor.component.border = BorderFactory.createEmptyBorder()
    }

    private val blockCanvasPanel = JPanel()
    private val statusLabel = JBLabel("Loading Compose blocks...")
    private val hintLabel = JBLabel("Split block browser and live source editor.")

    private var visibleRoots: List<ComposeBlockNode> = emptyList()
    private var selectedNode: ComposeBlockNode? = null
    private var editingCommentNodeId: String? = null
    private var pendingInlineCommentFocusNodeId: String? = null
    private var progressiveExpansionEnabled = true
    private var updatingSelectionFromBlocks = false

    init {
        Disposer.register(this, decorationController)
        Disposer.register(this) {
            refreshAlarm.cancelAllRequests()
        }

        blockCanvasPanel.layout = BoxLayout(blockCanvasPanel, BoxLayout.Y_AXIS)

        val canvasScrollPane = JBScrollPane(blockCanvasPanel).apply {
            minimumSize = Dimension(560, 340)
            preferredSize = Dimension(960, 720)
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBar.unitIncrement = 24
            border = BorderFactory.createEmptyBorder()
        }

        val sourcePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineLeft(JBColor.border()),
                JBUI.Borders.empty(8),
            )
            add(
                JBLabel("Live Compose Source").apply {
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.emptyBottom(6)
                },
                BorderLayout.NORTH,
            )
            add(embeddedEditor.component, BorderLayout.CENTER)
        }

        rootPanel.layout = BorderLayout()
        rootPanel.add(buildToolbar(), BorderLayout.NORTH)
        rootPanel.add(
            Splitter(false, 0.46f).apply {
                firstComponent = canvasScrollPane
                secondComponent = sourcePanel
            },
            BorderLayout.CENTER,
        )

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

        refreshModel()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return embeddedEditor.contentComponent
    }

    fun setProgressiveExpansionEnabled(enabled: Boolean) {
        if (progressiveExpansionEnabled == enabled) {
            return
        }
        progressiveExpansionEnabled = enabled
        applyCurrentEditorPresentation(scrollToCaret = false)
    }

    override fun dispose() {
        decorationController.clear()
        EditorFactory.getInstance().releaseEditor(embeddedEditor)
    }

    private fun buildToolbar(): JComponent {
        hintLabel.foreground = JBColor.GRAY

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(4, 8),
            )
            add(hintLabel, BorderLayout.WEST)
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

        visibleRoots = ComposeBlockTreeBuilder.build(ktFile, hideShells = true)
        val allNodes = flattenNodes(visibleRoots)
        if (editingCommentNodeId != null && allNodes.none { it.id == editingCommentNodeId }) {
            editingCommentNodeId = null
        }
        if (pendingInlineCommentFocusNodeId != null && allNodes.none { it.id == pendingInlineCommentFocusNodeId }) {
            pendingInlineCommentFocusNodeId = null
        }

        selectedNode = findBestSelection(embeddedEditor.caretModel.offset)
        renderCanvas()
        applyCurrentEditorPresentation(scrollToCaret = false)
    }

    private fun renderCanvas() {
        blockCanvasPanel.removeAll()
        if (visibleRoots.isEmpty()) {
            renderEmptyState("No @Composable functions were found in this file.")
            return
        }

        visibleRoots.forEachIndexed { index, root ->
            val blockComponent = createBlockComponent(root, parent = null)
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

    private fun createBlockComponent(
        node: ComposeBlockNode,
        parent: ComposeBlockNode?,
    ): JPanel {
        val isSelected = selectedNode?.id == node.id
        val directParent = selectedParentNode()
        val isDirectParent = directParent?.id == node.id
        val accentColor = kindColor(node.kind)

        val titleLabel = JBLabel(buildHeaderText(node)).apply {
            font = JBFont.label().deriveFont(Font.BOLD, 12f)
            foreground = JBColor(Color(232, 236, 242), Color(232, 236, 242))
            horizontalAlignment = SwingConstants.LEFT
            border = JBUI.Borders.empty(0, 0, 0, 8)
        }

        val remarkComponent = createCommentComponent(node)

        val summaryPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(titleLabel, BorderLayout.NORTH)
            remarkComponent?.let {
                add(it, BorderLayout.SOUTH)
            }
            minimumSize = Dimension(96, 18)
            preferredSize = Dimension(220, if (remarkComponent == null) 18 else 44)
        }

        val actionPanel = createActionPanel(node, parent)
        val headerPanel = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(summaryPanel, BorderLayout.CENTER)
            if (actionPanel.componentCount > 0) {
                add(actionPanel, BorderLayout.EAST)
            }
            border = JBUI.Borders.emptyBottom(8)
        }

        val headerContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(headerPanel)
        }

        val blockPanel = JPanel(BorderLayout(0, 10)).apply {
            isOpaque = true
            background = when {
                isSelected -> blockBackground(node.kind, 54)
                isDirectParent -> blockBackground(node.kind, 24)
                else -> JBColor.PanelBackground
            }
            border = CompoundBorder(
                CompoundBorder(
                    MatteBorder(0, 4, 0, 0, accentColor),
                    LineBorder(if (isSelected) accentColor else JBColor.border(), 1, true),
                ),
                JBUI.Borders.empty(10, 12, 10, 12),
            )
            alignmentX = Component.LEFT_ALIGNMENT
            add(headerContainer, BorderLayout.NORTH)
        }

        if (node.children.isNotEmpty()) {
            blockPanel.add(createChildrenPanel(node), BorderLayout.CENTER)
        }

        if (node.kind == ComposeBlockKind.LEAF) {
            blockPanel.preferredSize = Dimension(220, 92)
        }

        installSelectionHandler(
            node,
            *listOfNotNull(blockPanel, headerPanel, titleLabel, remarkComponent).toTypedArray(),
        )
        return blockPanel
    }

    private fun createActionPanel(
        node: ComposeBlockNode,
        parent: ComposeBlockNode?,
    ): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
            isOpaque = false
        }

        if (selectedNode?.id != node.id) {
            return panel
        }

        if (node.kind != ComposeBlockKind.ROOT) {
            panel.add(
                createMiniButton(
                    label = "Wrap",
                    toolTip = "Wrap this block in a layout container",
                ) { button ->
                    showWrapMenu(button, node)
                }
            )
        }

        if (node.editableContainerKind != null && node.children.size <= 1) {
            panel.add(
                createMiniButton(
                    label = "Unwrap",
                    toolTip = "Remove this layout container",
                ) {
                    val nextOffset = mutationService.unwrapNode(node)
                    applyMutationResult(nextOffset)
                }
            )
        }

        return panel
    }

    private fun createChildrenPanel(node: ComposeBlockNode): JPanel {
        val panel = JPanel()
        panel.isOpaque = false
        panel.border = JBUI.Borders.emptyTop(2)

        when (node.axis) {
            ComposeBlockAxis.HORIZONTAL -> {
                panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
                populateContainerAxis(panel, node, horizontal = true)
            }

            ComposeBlockAxis.VERTICAL,
            ComposeBlockAxis.STACK,
            -> {
                panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
                populateContainerAxis(panel, node, horizontal = false)
            }
        }

        return panel
    }

    private fun populateContainerAxis(
        panel: JPanel,
        node: ComposeBlockNode,
        horizontal: Boolean,
    ) {
        node.children.forEachIndexed { index, child ->
            val childComponent = createBlockComponent(child, node).apply {
                if (horizontal) {
                    alignmentY = Component.TOP_ALIGNMENT
                } else {
                    alignmentX = Component.LEFT_ALIGNMENT
                }
            }
            panel.add(childComponent)
            if (index != node.children.lastIndex) {
                addGap(panel, horizontal)
            }
        }
    }

    private fun addGap(
        panel: JPanel,
        horizontal: Boolean,
    ) {
        if (horizontal) {
            panel.add(Box.createHorizontalStrut(10))
        } else {
            panel.add(Box.createVerticalStrut(10))
        }
    }

    private fun createCommentComponent(node: ComposeBlockNode): JComponent? {
        if (editingCommentNodeId != node.id) {
            return null
        }

        var committed = false
        fun commitComment(textField: JBTextField) {
            if (committed) {
                return
            }
            committed = true
            val nextOffset = mutationService.updateDocComment(node, textField.text)
            applyMutationResult(nextOffset)
        }

        val textField = JBTextField(node.commentText.orEmpty()).apply {
            toolTipText = "Doc comment for this block. Leave empty to remove it."
            emptyText.text = "Add a doc comment for this block"
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
                commitComment(this)
            }
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(event: FocusEvent) {
                    commitComment(this@apply)
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

    private fun createMiniButton(
        label: String,
        toolTip: String,
        onClick: (JButton) -> Unit,
    ): JButton {
        return JButton(label).apply {
            isFocusable = false
            toolTipText = toolTip
            margin = JBUI.insets(2, 8)
            addActionListener {
                onClick(this)
            }
        }
    }

    private fun installSelectionHandler(
        node: ComposeBlockNode,
        vararg components: JComponent,
    ) {
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

    private fun applySelection(
        node: ComposeBlockNode,
        focusInlineComment: Boolean = false,
    ) {
        selectedNode = node
        if (focusInlineComment) {
            editingCommentNodeId = node.id
            pendingInlineCommentFocusNodeId = node.id
        } else {
            editingCommentNodeId = null
        }
        renderCanvas()
        updatingSelectionFromBlocks = true
        try {
            applyCurrentEditorPresentation(
                scrollToCaret = true,
                preferredCaretOffset = node.navigationOffset,
            )
        } finally {
            updatingSelectionFromBlocks = false
        }
        if (!focusInlineComment) {
            embeddedEditor.contentComponent.requestFocusInWindow()
        }
    }

    private fun clearFocus() {
        decorationController.clear()
        embeddedEditor.foldingModel.runBatchFoldingOperation {
            embeddedEditor.foldingModel.allFoldRegions.forEach { region ->
                embeddedEditor.foldingModel.removeFoldRegion(region)
            }
        }
    }

    private fun applyCurrentEditorPresentation(
        scrollToCaret: Boolean,
        preferredCaretOffset: Int? = null,
    ) {
        val node = selectedNode
        if (node == null) {
            clearFocus()
            return
        }

        val parentNode = selectedParentNode()
        val currentCaretOffset = preferredCaretOffset
            ?: embeddedEditor.caretModel.offset.takeIf { node.focusRange.contains(it) }
            ?: node.navigationOffset
        val targetOffset = if (progressiveExpansionEnabled) {
            val focusWindow = parentNode?.focusRange ?: node.focusRange
            val clampedOffset = currentCaretOffset.coerceIn(focusWindow.startOffset, focusWindow.endOffset)
            focusRange(focusWindow, clampedOffset, scrollToCaret)
            clampedOffset
        } else {
            revealWholeDocument(currentCaretOffset, scrollToCaret)
            currentCaretOffset.coerceIn(0, document.textLength)
        }
        val selectedPath = findNodePath(node.id).orEmpty()
        decorationController.apply(
            editor = embeddedEditor,
            selectedNode = node,
            parentNode = parentNode,
            selectedPath = selectedPath,
            caretOffset = targetOffset,
        )
    }

    private fun focusRange(
        range: TextRange,
        navigationOffset: Int,
        scrollToCaret: Boolean,
    ) {
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
        if (scrollToCaret) {
            embeddedEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }

    private fun revealWholeDocument(
        navigationOffset: Int,
        scrollToCaret: Boolean,
    ) {
        embeddedEditor.foldingModel.runBatchFoldingOperation {
            embeddedEditor.foldingModel.allFoldRegions.forEach { region ->
                embeddedEditor.foldingModel.removeFoldRegion(region)
            }
        }

        embeddedEditor.caretModel.moveToOffset(navigationOffset.coerceIn(0, document.textLength))
        if (scrollToCaret) {
            embeddedEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }

    private fun syncSelectionFromCaret() {
        if (updatingSelectionFromBlocks || visibleRoots.isEmpty()) {
            return
        }

        val node = selectedNode ?: return
        decorationController.apply(
            editor = embeddedEditor,
            selectedNode = node,
            parentNode = selectedParentNode(),
            selectedPath = findNodePath(node.id).orEmpty(),
            caretOffset = embeddedEditor.caretModel.offset,
        )
    }

    private fun findBestSelection(caretOffset: Int): ComposeBlockNode? {
        val allNodes = flattenNodes(visibleRoots)
        val byCaret = allNodes
            .filter { it.focusRange.contains(caretOffset) }
            .minByOrNull { it.renderRange.length }
        if (byCaret != null) {
            return byCaret
        }

        val previousId = selectedNode?.id
        if (previousId != null) {
            val previousById = allNodes.firstOrNull { it.id == previousId }
            if (previousById != null) {
                return previousById
            }
        }

        return allNodes.firstOrNull()
    }

    private fun flattenNodes(nodes: List<ComposeBlockNode>): List<ComposeBlockNode> {
        return nodes.flatMap { node ->
            listOf(node) + flattenNodes(node.children)
        }
    }

    private fun findNodePath(
        nodeId: String,
        nodes: List<ComposeBlockNode> = visibleRoots,
        trail: List<ComposeBlockNode> = emptyList(),
    ): List<ComposeBlockNode>? {
        nodes.forEach { node ->
            val nextTrail = trail + node
            if (node.id == nodeId) {
                return nextTrail
            }
            val childResult = findNodePath(nodeId, node.children, nextTrail)
            if (childResult != null) {
                return childResult
            }
        }
        return null
    }

    private fun selectedParentNode(): ComposeBlockNode? {
        val nodeId = selectedNode?.id ?: return null
        val path = findNodePath(nodeId) ?: return null
        return path.dropLast(1).lastOrNull()
    }

    private fun showWrapMenu(
        invoker: Component,
        node: ComposeBlockNode,
    ) {
        val menu = JPopupMenu()
        ComposeEditableContainerKind.values().forEach { containerKind ->
            val label = containerKind.name.lowercase().replaceFirstChar { character ->
                character.titlecase()
            }
            menu.add(
                JMenuItem("Wrap in $label").apply {
                    addActionListener {
                        val nextOffset = mutationService.wrapNode(node, containerKind)
                        applyMutationResult(nextOffset)
                    }
                }
            )
        }
        menu.show(invoker, 0, invoker.height)
    }

    private fun applyMutationResult(nextOffset: Int?) {
        editingCommentNodeId = null
        pendingInlineCommentFocusNodeId = null
        if (nextOffset != null) {
            updatingSelectionFromBlocks = true
            try {
                embeddedEditor.caretModel.moveToOffset(nextOffset.coerceIn(0, document.textLength))
                embeddedEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            } finally {
                updatingSelectionFromBlocks = false
            }
        }
        refreshAlarm.cancelAllRequests()
        refreshModel()
    }

    private fun renderCommentText(commentText: String?): String {
        val text = commentText?.trim().orEmpty()
        return "<html>${StringUtil.escapeXmlEntities(text)}</html>"
    }

    private fun buildHeaderText(node: ComposeBlockNode): String {
        val commentText = node.commentText?.trim().orEmpty()
        if (commentText.isBlank()) {
            return node.name
        }
        return "${node.name} · $commentText"
    }

    private fun kindColor(kind: ComposeBlockKind): Color {
        return when (kind) {
            ComposeBlockKind.ROOT -> JBColor(Color(45, 125, 210), Color(111, 176, 255))
            ComposeBlockKind.CONTAINER -> JBColor(Color(31, 138, 112), Color(86, 196, 166))
            ComposeBlockKind.LEAF -> JBColor(Color(125, 130, 138), Color(164, 170, 178))
            ComposeBlockKind.SHELL -> JBColor(Color(180, 120, 24), Color(245, 190, 92))
        }
    }

    private fun blockBackground(
        kind: ComposeBlockKind,
        alpha: Int,
    ): Color {
        val base = kindColor(kind)
        return Color(base.red, base.green, base.blue, alpha)
    }

    private fun createSnapshotKtFile(): KtFile? {
        return PsiFileFactory.getInstance(project)
            .createFileFromText(
                sourceFile.name,
                KotlinFileType.INSTANCE,
                document.text,
            ) as? KtFile
    }

}
