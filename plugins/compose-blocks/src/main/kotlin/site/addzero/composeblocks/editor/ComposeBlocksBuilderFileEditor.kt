package site.addzero.composeblocks.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import site.addzero.composeblocks.managed.BlockSpecTreeOps
import site.addzero.composeblocks.managed.ManagedComposeDocumentCodec
import site.addzero.composeblocks.managed.ManagedComposeSourceGenerator
import site.addzero.composeblocks.model.BlockSpec
import site.addzero.composeblocks.model.ComposeBlockType
import site.addzero.composeblocks.model.ManagedComposeDocument
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ComposeBlocksBuilderFileEditor(
    project: Project,
    file: VirtualFile,
) : ComposeBlocksFileEditorBase(project, file) {

    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val previewEditor = (EditorFactory.getInstance().createEditor(
        document,
        project,
        sourceFile,
        true,
    ) as EditorEx).also { editor ->
        editor.settings.isWhitespacesShown = false
        editor.settings.additionalColumnsCount = 0
        editor.settings.additionalLinesCount = 1
        editor.component.border = JBUI.Borders.empty()
    }

    private val statusLabel = JBLabel("Loading Compose Blocks builder…")
    private val inspectorPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val treeRoot = DefaultMutableTreeNode("Compose Blocks")
    private val treeModel = DefaultTreeModel(treeRoot)
    private val canvasTree = JTree(treeModel).apply {
        isRootVisible = true
        cellRenderer = BlockTreeRenderer()
        addTreeSelectionListener(::onTreeSelectionChanged)
    }

    private var managedDocument: ManagedComposeDocument? = null
    private var selectedBlockId: String? = null
    private var pendingSelectionId: String? = null
    private var nextIdSeed = 1L

    init {
        rootPanel.layout = BorderLayout()
        rootPanel.add(buildToolbar(), BorderLayout.NORTH)
        rootPanel.add(buildContent(), BorderLayout.CENTER)

        document.addDocumentListener(
            object : com.intellij.openapi.editor.event.DocumentListener {
                override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                    scheduleRefresh()
                }
            },
            this,
        )

        refreshFromDocument()
    }

    override fun getPreferredFocusedComponent(): JComponent = canvasTree

    override fun dispose() {
        refreshAlarm.cancelAllRequests()
        EditorFactory.getInstance().releaseEditor(previewEditor)
    }

    private fun buildToolbar(): JComponent {
        val actionsPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            isOpaque = false
            add(actionButton("Delete") {
                val selected = selectedBlock()
                if (selected != null && selected.id != managedDocument?.root?.id) {
                    applyMutation("Delete Compose Block", selectedParentIdOrRoot()) { current ->
                        current.copy(root = BlockSpecTreeOps.deleteBlock(current.root, selected.id))
                    }
                }
            })
            add(actionButton("Duplicate") {
                val selected = selectedBlock() ?: return@actionButton
                if (selected.id == managedDocument?.root?.id) {
                    return@actionButton
                }
                val duplicateId = nextBlockId()
                applyMutation("Duplicate Compose Block", duplicateId) { current ->
                    current.copy(root = BlockSpecTreeOps.duplicateBlock(current.root, selected.id, seededIdFactory(duplicateId)))
                }
            })
            add(actionButton("Wrap Row") { wrapSelected(ComposeBlockType.ROW) })
            add(actionButton("Wrap Column") { wrapSelected(ComposeBlockType.COLUMN) })
            add(actionButton("Wrap Box") { wrapSelected(ComposeBlockType.BOX) })
            add(actionButton("Move Up") { moveSelected(-1) })
            add(actionButton("Move Down") { moveSelected(1) })
            add(actionButton("Sketch Layout") { sketchLayout() })
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(4, 8),
            )
            add(actionsPanel, BorderLayout.WEST)
            add(
                JBLabel("Builder Mode edits managed files only. Use Sketch Layout to draw named slots and generate lambda-based layout skeletons.").apply {
                    foreground = JBColor.GRAY
                },
                BorderLayout.CENTER,
            )
            add(statusLabel, BorderLayout.EAST)
        }
    }

    private fun buildContent(): JComponent {
        val palettePanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineRight(JBColor.border()),
                JBUI.Borders.empty(8),
            )
            add(JBLabel("Palette", SwingConstants.LEFT), BorderLayout.NORTH)
            add(
                JBScrollPane(
                    JBPanel<JBPanel<*>>().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        isOpaque = false
                        ComposeBlockType.entries
                            .filterNot { type -> type == ComposeBlockType.SLOT }
                            .forEach { type ->
                            add(
                                JButton(type.displayName).apply {
                                    alignmentX = Component.LEFT_ALIGNMENT
                                    maximumSize = java.awt.Dimension(Int.MAX_VALUE, preferredSize.height)
                                    addActionListener {
                                        insertPaletteBlock(type)
                                    }
                                }
                            )
                            add(javax.swing.Box.createVerticalStrut(6))
                        }
                    }
                ),
                BorderLayout.CENTER,
            )
        }

        val canvasPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(JBLabel("Canvas"), BorderLayout.NORTH)
            add(JBScrollPane(canvasTree), BorderLayout.CENTER)
        }

        val previewPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(
                JBLabel("Generated Source Preview").apply {
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.empty(0, 0, 6, 0)
                },
                BorderLayout.NORTH,
            )
            add(previewEditor.component, BorderLayout.CENTER)
        }

        val inspectorAndPreview = Splitter(true, 0.42f).apply {
            firstComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLineLeft(JBColor.border()),
                    JBUI.Borders.empty(8),
                )
                add(inspectorPanel, BorderLayout.CENTER)
            }
            secondComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLineTop(JBColor.border()),
                    JBUI.Borders.empty(8),
                )
                add(previewPanel, BorderLayout.CENTER)
            }
        }

        return Splitter(false, 0.18f).apply {
            firstComponent = palettePanel
            secondComponent = Splitter(false, 0.4f).apply {
                firstComponent = canvasPanel
                secondComponent = inspectorAndPreview
            }
        }
    }

    private fun actionButton(label: String, action: () -> Unit): JButton {
        return JButton(label).apply {
            addActionListener { action() }
        }
    }

    private fun scheduleRefresh() {
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ refreshFromDocument() }, 150)
    }

    private fun refreshFromDocument() {
        val parsed = ManagedComposeDocumentCodec.parseDocument(document.text)
        managedDocument = parsed
        if (parsed == null) {
            treeRoot.removeAllChildren()
            treeModel.reload()
            inspectorPanel.removeAll()
            inspectorPanel.add(
                JBLabel("This file no longer contains Compose Blocks managed metadata. Open it in Inspect Mode or recreate the managed header.").apply {
                    foreground = JBColor.GRAY
                },
                BorderLayout.NORTH,
            )
            inspectorPanel.revalidate()
            inspectorPanel.repaint()
            statusLabel.text = "Managed metadata missing"
            return
        }

        updateNextIdSeed(parsed)
        val selectedId = pendingSelectionId?.takeIf { BlockSpecTreeOps.findBlock(parsed.root, it) != null }
            ?: selectedBlockId?.takeIf { BlockSpecTreeOps.findBlock(parsed.root, it) != null }
            ?: parsed.root.id
        pendingSelectionId = null
        selectedBlockId = selectedId
        rebuildTree(parsed, selectedId)
        renderInspector()
        applyPreviewFolding(document)
        statusLabel.text = "${parsed.kind.presentableName} builder · ${countBlocks(parsed.root)} blocks · ${parsed.composableName}"
    }

    private fun updateNextIdSeed(document: ManagedComposeDocument) {
        val maxExisting = collectBlocks(document.root)
            .mapNotNull { block ->
                block.id.removePrefix("block-").toLongOrNull()
            }
            .maxOrNull()
            ?: 0L
        nextIdSeed = maxOf(nextIdSeed, maxExisting + 1)
    }

    private fun rebuildTree(document: ManagedComposeDocument, selectionId: String) {
        treeRoot.userObject = BlockTreeEntry(document.root)
        treeRoot.removeAllChildren()
        buildTreeNodes(treeRoot, document.root)
        treeModel.reload()
        expandAll(canvasTree)
        findTreePath(selectionId)?.let(canvasTree::setSelectionPath)
    }

    private fun buildTreeNodes(parentNode: DefaultMutableTreeNode, block: BlockSpec) {
        block.children.forEach { child ->
            val childNode = DefaultMutableTreeNode(BlockTreeEntry(child))
            parentNode.add(childNode)
            buildTreeNodes(childNode, child)
        }
    }

    private fun renderInspector() {
        inspectorPanel.removeAll()
        val currentDocument = managedDocument
        val block = selectedBlock()
        if (currentDocument == null || block == null) {
            inspectorPanel.add(
                JBLabel("Select a block to edit its note and supported properties.").apply {
                    foreground = JBColor.GRAY
                },
                BorderLayout.NORTH,
            )
            inspectorPanel.revalidate()
            inspectorPanel.repaint()
            return
        }

        val content = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(sectionTitle("${block.type.displayName} Inspector"))
            add(labeledField("Note", block.note, "Update Block Note") { selectedBlock, newValue ->
                selectedBlock.withNote(newValue)
            })

            if (block.type == ComposeBlockType.SLOT) {
                add(labeledField("Slot Name", block.propValue("slotName").orEmpty(), "Update Slot Name") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("slotName", newValue)
                })
            }

            if (block.type in setOf(ComposeBlockType.TEXT, ComposeBlockType.BUTTON, ComposeBlockType.IMAGE, ComposeBlockType.TEXT_FIELD)) {
                add(labeledField("Text", block.propValue("text").orEmpty(), "Update Text") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("text", newValue)
                })
            }

            if (block.type.supportsChildren || block.type in setOf(ComposeBlockType.TEXT, ComposeBlockType.BUTTON, ComposeBlockType.TEXT_FIELD)) {
                add(labeledField("Padding", block.propValue("padding").orEmpty(), "Update Padding") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("padding", newValue)
                })
            }

            if (block.type in setOf(ComposeBlockType.SPACER, ComposeBlockType.IMAGE)) {
                add(labeledField("Size", block.propValue("size").orEmpty(), "Update Size") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("size", newValue)
                })
            }

            if (block.type == ComposeBlockType.SLOT) {
                add(labeledField("Width Fraction", block.propValue("widthFraction").orEmpty(), "Update Width Fraction") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("widthFraction", newValue)
                })
                add(labeledField("Height Fraction", block.propValue("heightFraction").orEmpty(), "Update Height Fraction") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("heightFraction", newValue)
                })
                add(labeledField("X Fraction", block.propValue("xFraction").orEmpty(), "Update X Fraction") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("xFraction", newValue)
                })
                add(labeledField("Y Fraction", block.propValue("yFraction").orEmpty(), "Update Y Fraction") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("yFraction", newValue)
                })
            }

            add(labeledField("Weight", block.propValue("weight").orEmpty(), "Update Weight") { selectedBlock, newValue ->
                selectedBlock.upsertProp("weight", newValue)
            })

            add(checkBoxField("Fill Max Width", block.propValue("fillMaxWidth").equals("true", true)) { selectedBlock, isEnabled ->
                selectedBlock.upsertProp("fillMaxWidth", isEnabled.toString().takeIf { isEnabled })
            })

            add(checkBoxField("Fill Max Height", block.propValue("fillMaxHeight").equals("true", true)) { selectedBlock, isEnabled ->
                selectedBlock.upsertProp("fillMaxHeight", isEnabled.toString().takeIf { isEnabled })
            })

            if (block.type in setOf(ComposeBlockType.COLUMN, ComposeBlockType.ROW, ComposeBlockType.BOX)) {
                add(labeledField("Alignment", block.propValue("alignment").orEmpty(), "Update Alignment") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("alignment", newValue)
                })
            }

            if (block.type in setOf(ComposeBlockType.COLUMN, ComposeBlockType.ROW)) {
                add(labeledField("Arrangement", block.propValue("arrangement").orEmpty(), "Update Arrangement") { selectedBlock, newValue ->
                    selectedBlock.upsertProp("arrangement", newValue)
                })
            }

            add(javax.swing.Box.createVerticalGlue())
        }

        inspectorPanel.add(JBScrollPane(content), BorderLayout.CENTER)
        inspectorPanel.revalidate()
        inspectorPanel.repaint()
    }

    private fun labeledField(
        label: String,
        value: String,
        commandName: String,
        transform: (BlockSpec, String) -> BlockSpec,
    ): JComponent {
        val field = JBTextField(value).apply {
            addActionListener {
                updateSelectedBlock(commandName) { block -> transform(block, text) }
            }
            addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusLost(event: java.awt.event.FocusEvent) {
                    updateSelectedBlock(commandName) { block -> transform(block, text) }
                }
            })
        }
        return formRow(label, field)
    }

    private fun checkBoxField(
        label: String,
        selected: Boolean,
        transform: (BlockSpec, Boolean) -> BlockSpec,
    ): JComponent {
        val checkBox = JBCheckBox(label, selected).apply {
            addActionListener {
                updateSelectedBlock("Toggle $label") { block -> transform(block, isSelected) }
            }
        }
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 0, 8, 0)
            add(checkBox, BorderLayout.CENTER)
        }
    }

    private fun formRow(label: String, component: JComponent): JComponent {
        return JBPanel<JBPanel<*>>(BorderLayout(0, 4)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 0, 10, 0)
            add(JBLabel(label).apply { foreground = JBColor.GRAY }, BorderLayout.NORTH)
            add(component, BorderLayout.CENTER)
        }
    }

    private fun sectionTitle(text: String): JComponent {
        return JBLabel(text).apply {
            font = font.deriveFont(java.awt.Font.BOLD.toFloat())
            border = JBUI.Borders.empty(0, 0, 10, 0)
        }
    }

    private fun onTreeSelectionChanged(event: TreeSelectionEvent) {
        val node = event.path?.lastPathComponent as? DefaultMutableTreeNode ?: return
        val entry = node.userObject as? BlockTreeEntry ?: return
        selectedBlockId = entry.block.id
        renderInspector()
    }

    private fun insertPaletteBlock(type: ComposeBlockType) {
        val newBlock = createDefaultBlock(type)
        applyMutation("Insert ${type.displayName}", newBlock.id) { current ->
            current.copy(root = BlockSpecTreeOps.addNearSelection(current.root, selectedBlockId, newBlock))
        }
    }

    private fun moveSelected(delta: Int) {
        val selected = selectedBlock() ?: return
        if (selected.id == managedDocument?.root?.id) {
            return
        }
        applyMutation("Move Compose Block", selected.id) { current ->
            current.copy(root = BlockSpecTreeOps.moveBlock(current.root, selected.id, delta))
        }
    }

    private fun wrapSelected(wrapperType: ComposeBlockType) {
        val selected = selectedBlock() ?: return
        val wrapperId = nextBlockId()
        applyMutation("Wrap Compose Block", wrapperId) { current ->
            current.copy(root = BlockSpecTreeOps.wrapBlock(current.root, selected.id, wrapperType, seededIdFactory(wrapperId)))
        }
    }

    private fun sketchLayout() {
        val current = managedDocument ?: return
        val dialog = ComposeLayoutSketchDialog(project)
        if (!dialog.showAndGet()) {
            return
        }
        val sketchRoot = ComposeLayoutSketchTreeBuilder.buildRoot(
            rootId = current.root.id,
            rootNote = current.composableName,
            regions = dialog.regions(),
            idFactory = { nextBlockId() },
        )
        applyMutation("Sketch Compose Layout", sketchRoot.id) { managed ->
            managed.copy(root = sketchRoot)
        }
    }

    private fun updateSelectedBlock(
        commandName: String,
        transform: (BlockSpec) -> BlockSpec,
    ) {
        val selected = selectedBlock() ?: return
        applyMutation(commandName, selected.id) { current ->
            current.copy(root = BlockSpecTreeOps.updateBlock(current.root, selected.id, transform))
        }
    }

    private fun applyMutation(
        commandName: String,
        selectionId: String?,
        transform: (ManagedComposeDocument) -> ManagedComposeDocument,
    ) {
        val current = managedDocument ?: return
        val updated = transform(current)
        if (updated == current) {
            return
        }
        pendingSelectionId = selectionId
        writeManagedDocument(commandName, updated)
    }

    private fun writeManagedDocument(commandName: String, updated: ManagedComposeDocument) {
        val psiFile = PsiManager.getInstance(project).findFile(sourceFile) ?: return
        val generated = ManagedComposeSourceGenerator.generate(updated)
        WriteCommandAction.runWriteCommandAction(project, commandName, null, Runnable {
            document.replaceString(0, document.textLength, generated)
            FileDocumentManager.getInstance().saveDocument(document)
        }, psiFile)
    }

    private fun applyPreviewFolding(document: Document) {
        val headerRange = ManagedComposeDocumentCodec.findHeaderRange(document.text) ?: return
        previewEditor.foldingModel.runBatchFoldingOperation {
            previewEditor.foldingModel.allFoldRegions.forEach { region ->
                previewEditor.foldingModel.removeFoldRegion(region)
            }
            previewEditor.foldingModel.addFoldRegion(
                headerRange.startOffset,
                headerRange.endOffset,
                "Compose Blocks managed metadata…",
            )?.isExpanded = false
        }
    }

    private fun selectedBlock(): BlockSpec? {
        val current = managedDocument ?: return null
        return BlockSpecTreeOps.findBlock(current.root, selectedBlockId)
    }

    private fun selectedParentIdOrRoot(): String {
        return managedDocument?.root?.id.orEmpty()
    }

    private fun createDefaultBlock(type: ComposeBlockType): BlockSpec {
        return BlockSpec.create(
            id = nextBlockId(),
            type = type,
            note = type.displayName,
            props = when (type) {
                ComposeBlockType.COLUMN -> listOf(
                    site.addzero.composeblocks.model.PropSpec("fillMaxWidth", "true"),
                    site.addzero.composeblocks.model.PropSpec("padding", "16.dp"),
                )

                ComposeBlockType.ROW -> listOf(
                    site.addzero.composeblocks.model.PropSpec("fillMaxWidth", "true"),
                    site.addzero.composeblocks.model.PropSpec("padding", "8.dp"),
                    site.addzero.composeblocks.model.PropSpec("arrangement", "spacedBy(8.dp)"),
                )

                ComposeBlockType.BOX -> listOf(
                    site.addzero.composeblocks.model.PropSpec("fillMaxWidth", "true"),
                    site.addzero.composeblocks.model.PropSpec("padding", "8.dp"),
                )

                ComposeBlockType.SLOT -> listOf(
                    site.addzero.composeblocks.model.PropSpec("slotName", "slot"),
                    site.addzero.composeblocks.model.PropSpec("fillMaxWidth", "true"),
                )

                ComposeBlockType.TEXT -> listOf(
                    site.addzero.composeblocks.model.PropSpec("text", "Text"),
                )

                ComposeBlockType.BUTTON -> listOf(
                    site.addzero.composeblocks.model.PropSpec("text", "Button"),
                )

                ComposeBlockType.SPACER -> listOf(
                    site.addzero.composeblocks.model.PropSpec("size", "16.dp"),
                )

                ComposeBlockType.IMAGE -> listOf(
                    site.addzero.composeblocks.model.PropSpec("text", "Image"),
                    site.addzero.composeblocks.model.PropSpec("size", "64.dp"),
                )

                ComposeBlockType.TEXT_FIELD -> listOf(
                    site.addzero.composeblocks.model.PropSpec("text", "Label"),
                    site.addzero.composeblocks.model.PropSpec("fillMaxWidth", "true"),
                )
            },
        )
    }

    private fun nextBlockId(): String = "block-${nextIdSeed++}"

    private fun seededIdFactory(preferredId: String): () -> String {
        var first = true
        return {
            if (first) {
                first = false
                preferredId
            } else {
                nextBlockId()
            }
        }
    }

    private fun findTreePath(blockId: String): TreePath? {
        return findTreePath(TreePath(treeRoot), blockId)
    }

    private fun findTreePath(path: TreePath, blockId: String): TreePath? {
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return null
        val entry = node.userObject as? BlockTreeEntry
        if (entry?.block?.id == blockId) {
            return path
        }
        val children = node.children()
        while (children.hasMoreElements()) {
            val childNode = children.nextElement() as DefaultMutableTreeNode
            val childPath = path.pathByAddingChild(childNode)
            val match = findTreePath(childPath, blockId)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun expandAll(tree: JTree) {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row += 1
        }
    }

    private fun collectBlocks(block: BlockSpec): List<BlockSpec> {
        return listOf(block) + block.children.flatMap(::collectBlocks)
    }

    private fun countBlocks(block: BlockSpec): Int = collectBlocks(block).size

    private class BlockTreeEntry(val block: BlockSpec)

    private class BlockTreeRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): Component {
            val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            val node = value as? DefaultMutableTreeNode
            val entry = node?.userObject as? BlockTreeEntry
            text = entry?.block?.let { block ->
                "${block.displayTitle} · ${block.type.displayName}"
            } ?: text
            return component
        }
    }
}
