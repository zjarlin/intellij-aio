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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
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
import javax.swing.SwingUtilities
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
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
    private val hintLabel = JBLabel("Single-pane inline editor with source-coupled highlighting.")
    private val hideShellsCheckBox = JBCheckBox("Hide shell composables", true)

    private var visibleRoots: List<ComposeBlockNode> = emptyList()
    private var selectedNode: ComposeBlockNode? = null
    private var editingCommentNodeId: String? = null
    private var pendingInlineCommentFocusNodeId: String? = null
    private var reorderState: ReorderState? = null
    private var updatingSelectionFromBlocks = false
    private val slotPresentations = linkedMapOf<SlotTarget, SlotPresentation>()

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

        rootPanel.layout = BorderLayout()
        rootPanel.add(buildToolbar(), BorderLayout.NORTH)
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
            reorderState = null
            refreshModel()
        }

        refreshModel()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return embeddedEditor.contentComponent
    }

    override fun dispose() {
        decorationController.clear()
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
        val allNodes = flattenNodes(visibleRoots)
        if (editingCommentNodeId != null && allNodes.none { it.id == editingCommentNodeId }) {
            editingCommentNodeId = null
            pendingInlineCommentFocusNodeId = null
        }
        val currentReorder = reorderState
        if (currentReorder != null) {
            val hasContainer = allNodes.any { it.id == currentReorder.containerId }
            val hasChild = allNodes.any { it.id == currentReorder.childId }
            if (!hasContainer || !hasChild) {
                reorderState = null
            }
        }

        selectedNode = findBestSelection(embeddedEditor.caretModel.offset)
        renderCanvas()
        applyCurrentEditorPresentation(scrollToCaret = false)
    }

    private fun renderCanvas() {
        detachEmbeddedEditor()
        blockCanvasPanel.removeAll()
        slotPresentations.clear()
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
        refreshSlotPresentations()
        blockCanvasPanel.revalidate()
        blockCanvasPanel.repaint()
    }

    private fun renderEmptyState(message: String) {
        detachEmbeddedEditor()
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
        val line = document.getLineNumber(node.navigationOffset.coerceIn(0, document.textLength)).plus(1)
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

        val actionPanel = createActionPanel(node, parent)
        val headerPanel = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            add(summaryPanel, BorderLayout.CENTER)
            if (actionPanel.componentCount > 0) {
                add(actionPanel, BorderLayout.EAST)
            }
        }

        val headerContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(headerPanel)
            if (editingCommentNodeId == node.id) {
                add(Box.createVerticalStrut(8))
                add(createInlineCommentEditor(node))
            }
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

        val showEditableSlots = isActiveEditableContainer(node)
        if (node.children.isNotEmpty() || showEditableSlots) {
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

    private fun createActionPanel(
        node: ComposeBlockNode,
        parent: ComposeBlockNode?,
    ): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
            isOpaque = false
        }

        val activeContainer = activeEditableContainer()
        if (activeContainer != null && parent != null && activeContainer.id == parent.id) {
            panel.add(createDragHandleButton(node, parent))
        }

        if (selectedNode?.id != node.id) {
            return panel
        }

        panel.add(
            createMiniButton(
                label = if (editingCommentNodeId == node.id) "Commenting" else "Comment",
                toolTip = "Edit the doc comment shown on this block",
            ) {
                startCommentEditing(node)
            }
        )

        if (node.kind != ComposeBlockKind.ROOT) {
            panel.add(
                createMiniButton(
                    label = "Wrap",
                    toolTip = "Wrap this block in a layout container",
                ) { button ->
                    showWrapMenu(button, node)
                }
            )

            panel.add(
                createMiniButton(
                    label = "Layout",
                    toolTip = "Edit layout arguments and modifier skeleton",
                ) {
                    showLayoutDialog(node)
                }
            )
        }

        if (node.editableContainerKind != null && node.children.size <= 1) {
            panel.add(
                createMiniButton(
                    label = if (node.children.isEmpty()) "Delete" else "Unwrap",
                    toolTip = "Delete empty containers or unwrap single-child containers",
                ) {
                    val nextOffset = mutationService.simplifyContainer(node)
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
        val showEditableSlots = isActiveEditableContainer(node)
        if (showEditableSlots) {
            panel.add(createInsertSlot(node, 0, horizontal))
            if (node.children.isNotEmpty()) {
                addGap(panel, horizontal)
            }
        }

        node.children.forEachIndexed { index, child ->
            val childComponent = createBlockComponent(child, node).apply {
                if (horizontal) {
                    alignmentY = Component.TOP_ALIGNMENT
                } else {
                    alignmentX = Component.LEFT_ALIGNMENT
                }
            }
            panel.add(childComponent)
            if (index != node.children.lastIndex || showEditableSlots) {
                addGap(panel, horizontal)
            }
            if (showEditableSlots) {
                panel.add(createInsertSlot(node, index + 1, horizontal))
                if (index != node.children.lastIndex) {
                    addGap(panel, horizontal)
                }
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

    private fun createInsertSlot(
        containerNode: ComposeBlockNode,
        slotIndex: Int,
        horizontal: Boolean,
    ): JComponent {
        val slotPanel = JPanel(BorderLayout()).apply {
            isOpaque = true
            border = CompoundBorder(
                LineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(6),
            )
            preferredSize = if (horizontal) {
                Dimension(90, 60)
            } else {
                Dimension(220, 34)
            }
        }

        val actionButton = createMiniButton(
            label = "+",
            toolTip = "Insert a new block at position ${slotIndex + 1}",
        ) { button ->
            val currentReorder = reorderState?.takeIf { it.containerId == containerNode.id }
            if (currentReorder != null) {
                val movingNode = containerNode.children.firstOrNull { it.id == currentReorder.childId } ?: return@createMiniButton
                val nextOffset = mutationService.moveChild(containerNode, movingNode, slotIndex)
                applyMutationResult(nextOffset)
            } else {
                showTemplateMenu(button, containerNode, slotIndex)
            }
        }

        slotPanel.add(
            JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
                isOpaque = false
                add(actionButton)
            },
            BorderLayout.CENTER,
        )
        val slotTarget = SlotTarget(
            containerId = containerNode.id,
            slotIndex = slotIndex,
        )
        slotPanel.putClientProperty(SLOT_TARGET_PROPERTY, slotTarget)
        actionButton.putClientProperty(SLOT_TARGET_PROPERTY, slotTarget)
        slotPresentations[slotTarget] = SlotPresentation(
            panel = slotPanel,
            button = actionButton,
            containerKind = containerNode.kind,
            slotIndex = slotIndex,
        )
        return slotPanel
    }

    private fun createInlineEditorHost(): JComponent {
        detachEmbeddedEditor()
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(8)
            preferredSize = Dimension(400, 240)
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
                val nextOffset = mutationService.updateDocComment(node, text)
                applyMutationResult(nextOffset)
            }
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(event: FocusEvent) {
                    val nextOffset = mutationService.updateDocComment(node, text)
                    applyMutationResult(nextOffset)
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

    private fun createDragHandleButton(
        node: ComposeBlockNode,
        parent: ComposeBlockNode,
    ): JButton {
        val button = JButton(moveHandleLabel(parent.axis)).apply {
            isFocusable = false
            toolTipText = "Drag to reorder inside ${parent.name}"
            margin = JBUI.insets(2, 8)
            cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        }

        val listener = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                reorderState = ReorderState(
                    containerId = parent.id,
                    childId = node.id,
                    hoveredSlotIndex = null,
                )
                refreshSlotPresentations()
            }

            override fun mouseDragged(event: MouseEvent) {
                val slotTarget = findSlotTargetFromEvent(event)
                val currentState = reorderState ?: return
                if (currentState.containerId != parent.id || currentState.childId != node.id) {
                    return
                }
                val nextState = currentState.copy(
                    hoveredSlotIndex = slotTarget
                        ?.takeIf { it.containerId == parent.id }
                        ?.slotIndex,
                )
                if (nextState != currentState) {
                    reorderState = nextState
                    refreshSlotPresentations()
                }
            }

            override fun mouseReleased(event: MouseEvent) {
                val currentState = reorderState
                if (currentState == null || currentState.containerId != parent.id || currentState.childId != node.id) {
                    return
                }

                val slotTarget = findSlotTargetFromEvent(event)
                if (slotTarget != null && slotTarget.containerId == parent.id) {
                    val movingNode = parent.children.firstOrNull { it.id == node.id }
                    if (movingNode != null) {
                        val nextOffset = mutationService.moveChild(parent, movingNode, slotTarget.slotIndex)
                        applyMutationResult(nextOffset)
                        return
                    }
                }

                reorderState = null
                refreshSlotPresentations()
            }
        }

        button.addMouseListener(listener)
        button.addMouseMotionListener(listener)
        return button
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
        if (selectedNode?.id != node.id) {
            reorderState = null
        }
        selectedNode = node
        if (focusInlineComment) {
            editingCommentNodeId = node.id
            pendingInlineCommentFocusNodeId = node.id
        } else if (editingCommentNodeId != node.id) {
            editingCommentNodeId = null
            pendingInlineCommentFocusNodeId = null
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
        val focusWindow = parentNode?.focusRange ?: node.focusRange
        val currentCaretOffset = preferredCaretOffset
            ?: embeddedEditor.caretModel.offset.takeIf { node.focusRange.contains(it) }
            ?: node.navigationOffset
        val targetOffset = currentCaretOffset.coerceIn(focusWindow.startOffset, focusWindow.endOffset)
        focusRange(focusWindow, targetOffset, scrollToCaret)
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

    private fun syncSelectionFromCaret() {
        if (updatingSelectionFromBlocks || visibleRoots.isEmpty()) {
            return
        }

        val nextSelection = findBestSelection(embeddedEditor.caretModel.offset) ?: return
        if (nextSelection.id == selectedNode?.id) {
            decorationController.apply(
                editor = embeddedEditor,
                selectedNode = selectedNode,
                parentNode = selectedParentNode(),
                selectedPath = selectedNode?.id?.let(::findNodePath).orEmpty(),
                caretOffset = embeddedEditor.caretModel.offset,
            )
            return
        }

        selectedNode = nextSelection
        if (editingCommentNodeId != nextSelection.id) {
            editingCommentNodeId = null
            pendingInlineCommentFocusNodeId = null
        }
        renderCanvas()
        applyCurrentEditorPresentation(scrollToCaret = true)
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

    private fun activeEditableContainer(): ComposeBlockNode? {
        val selected = selectedNode ?: return null
        if (selected.isLowCodeEditable) {
            return selected
        }
        val parentNode = selectedParentNode()
        return parentNode?.takeIf { it.isLowCodeEditable }
    }

    private fun isActiveEditableContainer(node: ComposeBlockNode): Boolean {
        return activeEditableContainer()?.id == node.id
    }

    private fun showTemplateMenu(
        invoker: Component,
        containerNode: ComposeBlockNode,
        slotIndex: Int,
    ) {
        val menu = JPopupMenu()
        ComposeBlockTemplate.values().forEach { template ->
            menu.add(
                JMenuItem(template.label).apply {
                    addActionListener {
                        val nextOffset = mutationService.insertTemplate(containerNode, slotIndex, template)
                        applyMutationResult(nextOffset)
                    }
                }
            )
        }
        menu.show(invoker, 0, invoker.height)
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

    private fun showLayoutDialog(node: ComposeBlockNode) {
        val dialog = ComposeBlockLayoutDialog(mutationService.readLayoutProperties(node))
        if (!dialog.showAndGet()) {
            return
        }
        val nextOffset = mutationService.updateLayoutProperties(node, dialog.properties())
        applyMutationResult(nextOffset)
    }

    private fun startCommentEditing(node: ComposeBlockNode) {
        editingCommentNodeId = node.id
        pendingInlineCommentFocusNodeId = node.id
        renderCanvas()
    }

    private fun applyMutationResult(nextOffset: Int?) {
        reorderState = null
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

    private fun refreshSlotPresentations() {
        val currentReorder = reorderState
        slotPresentations.forEach { (slotTarget, presentation) ->
            val draggingInContainer = currentReorder?.containerId == slotTarget.containerId
            val isHoveredTarget = draggingInContainer && currentReorder.hoveredSlotIndex == slotTarget.slotIndex
            presentation.panel.background = when {
                isHoveredTarget -> blockBackground(presentation.containerKind, 40)
                draggingInContainer -> JBColor(Color(242, 245, 250), Color(63, 67, 76))
                else -> JBColor(Color(248, 249, 252), Color(58, 62, 70))
            }
            presentation.panel.border = CompoundBorder(
                LineBorder(
                    when {
                        isHoveredTarget -> kindColor(presentation.containerKind)
                        draggingInContainer -> blockBackground(presentation.containerKind, 120)
                        else -> JBColor.border()
                    },
                    1,
                    true,
                ),
                JBUI.Borders.empty(6),
            )
            presentation.button.text = if (isHoveredTarget) "Drop Here" else "+"
            presentation.button.toolTipText = if (draggingInContainer) {
                "Release to move the block to position ${presentation.slotIndex + 1}"
            } else {
                "Insert a new block at position ${presentation.slotIndex + 1}"
            }
        }
        blockCanvasPanel.repaint()
    }

    private fun findSlotTargetFromEvent(event: MouseEvent): SlotTarget? {
        val canvasPoint = SwingUtilities.convertPoint(event.component, event.point, blockCanvasPanel)
        val deepest = SwingUtilities.getDeepestComponentAt(blockCanvasPanel, canvasPoint.x, canvasPoint.y)
        return findSlotTarget(deepest)
    }

    private fun findSlotTarget(component: Component?): SlotTarget? {
        var current: Component? = component
        while (current != null) {
            if (current is JComponent) {
                val slotTarget = current.getClientProperty(SLOT_TARGET_PROPERTY) as? SlotTarget
                if (slotTarget != null) {
                    return slotTarget
                }
            }
            current = current.parent
        }
        return null
    }

    private fun buildMetaText(
        node: ComposeBlockNode,
        line: Int,
        childCountText: String,
    ): String {
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
        if (node.isLowCodeEditable) {
            parts += "Low-code"
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

    private fun blockBackground(
        kind: ComposeBlockKind,
        alpha: Int,
    ): Color {
        val base = kindColor(kind)
        return Color(base.red, base.green, base.blue, alpha)
    }

    private fun moveHandleLabel(axis: ComposeBlockAxis): String {
        return when (axis) {
            ComposeBlockAxis.HORIZONTAL -> "⇆"
            ComposeBlockAxis.VERTICAL -> "⇅"
            ComposeBlockAxis.STACK -> "⛶"
        }
    }

    private fun detachEmbeddedEditor() {
        val parent = embeddedEditor.component.parent as? JComponent ?: return
        parent.remove(embeddedEditor.component)
        parent.revalidate()
        parent.repaint()
    }

    private fun createSnapshotKtFile(): KtFile? {
        return PsiFileFactory.getInstance(project)
            .createFileFromText(
                sourceFile.name,
                KotlinFileType.INSTANCE,
                document.text,
            ) as? KtFile
    }

    private data class ReorderState(
        val containerId: String,
        val childId: String,
        val hoveredSlotIndex: Int?,
    )

    private data class SlotTarget(
        val containerId: String,
        val slotIndex: Int,
    )

    private data class SlotPresentation(
        val panel: JPanel,
        val button: JButton,
        val containerKind: ComposeBlockKind,
        val slotIndex: Int,
    )

    private companion object {
        const val SLOT_TARGET_PROPERTY = "compose.blocks.slot.target"
    }
}
