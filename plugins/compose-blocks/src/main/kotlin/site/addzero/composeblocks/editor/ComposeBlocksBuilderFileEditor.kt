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
import site.addzero.composeblocks.model.PropSpec
import site.addzero.composeblocks.model.RawCodeBlockSpec
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.TransferHandler

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
    private val layoutCanvas = ComposeBlocksBuilderCanvasPanel(
        onSelectBlock = { blockId ->
            selectedBlockId = blockId
            renderInspector()
            renderCanvas()
        },
        onInsertIntoContainer = { containerId, blockType ->
            insertIntoContainer(containerId, blockType)
        },
        onFillSlot = { slotId, blockType ->
            fillSlotWithTemplate(slotId, blockType)
        },
    )
    private val sketchWorkspace = ComposeLayoutSketchWorkspace()

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

    override fun getPreferredFocusedComponent(): JComponent = layoutCanvas

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
                        current.copy(
                            root = BlockSpecTreeOps.deleteBlock(current.root, selected.id),
                            rawCodeBlocks = current.rawCodeBlocks.filterNot { raw -> raw.id == selected.id },
                        )
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
            add(actionButton("Unwrap") { unwrapSelected() })
            add(actionButton("Move Up") { moveSelected(-1) })
            add(actionButton("Move Down") { moveSelected(1) })
            add(actionButton("Apply Sketch") { applySketchLayout() })
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(4, 8),
            )
            add(actionsPanel, BorderLayout.WEST)
            add(
                JBLabel("Builder Mode uses a component palette, visual canvas, and sketch canvas for named-slot layout work.").apply {
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
            add(JBLabel("Component Palette"), BorderLayout.NORTH)
            add(JBScrollPane(buildPaletteList()), BorderLayout.CENTER)
        }

        val workspaceTabs = JTabbedPane().apply {
            addTab("Layout Canvas", JBScrollPane(layoutCanvas))
            addTab("Sketch Canvas", buildSketchTab())
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

        val inspectorAndPreview = Splitter(true, 0.46f).apply {
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
            secondComponent = Splitter(false, 0.56f).apply {
                firstComponent = workspaceTabs
                secondComponent = inspectorAndPreview
            }
        }
    }

    private fun buildPaletteList(): JComponent {
        return JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            ComposeBlockType.entries
                .filterNot { type -> type == ComposeBlockType.SLOT }
                .forEach { type ->
                    add(createPaletteItem(type))
                    add(javax.swing.Box.createVerticalStrut(8))
                }
        }
    }

    private fun createPaletteItem(type: ComposeBlockType): JComponent {
        val item = JLabel(type.displayName).apply {
            isOpaque = true
            background = JBColor(Color(245, 247, 250), Color(60, 64, 72))
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(8, 10),
            )
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            transferHandler = object : TransferHandler() {
                override fun createTransferable(component: JComponent): StringSelection {
                    return StringSelection(type.name)
                }

                override fun getSourceActions(component: JComponent): Int = TransferHandler.COPY
            }
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    transferHandler.exportAsDrag(this@apply, event, TransferHandler.COPY)
                }

                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 1) {
                        insertPaletteBlock(type)
                    }
                }
            })
        }
        return item
    }

    private fun buildSketchTab(): JComponent {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(sketchWorkspace, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 8)).apply {
                    isOpaque = false
                    add(
                        JButton("Apply Sketch to Layout").apply {
                            addActionListener {
                                applySketchLayout()
                            }
                        }
                    )
                    add(
                        JButton("Clear Sketch").apply {
                            addActionListener {
                                sketchWorkspace.clearAllRegions()
                            }
                        }
                    )
                },
                BorderLayout.SOUTH,
            )
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
            inspectorPanel.removeAll()
            inspectorPanel.add(
                JBLabel("This file no longer contains Compose Blocks managed metadata. Open it in Inspect Mode or recreate the managed header.").apply {
                    foreground = JBColor.GRAY
                },
                BorderLayout.NORTH,
            )
            inspectorPanel.revalidate()
            inspectorPanel.repaint()
            layoutCanvas.render(null, null, emptyMap())
            statusLabel.text = "Managed metadata missing"
            return
        }

        updateNextIdSeed(parsed)
        val selectedId = pendingSelectionId?.takeIf { BlockSpecTreeOps.findBlock(parsed.root, it) != null }
            ?: selectedBlockId?.takeIf { BlockSpecTreeOps.findBlock(parsed.root, it) != null }
            ?: parsed.root.id
        pendingSelectionId = null
        selectedBlockId = selectedId
        renderCanvas()
        renderInspector()
        applyPreviewFolding(document)
        statusLabel.text = "${parsed.kind.presentableName} builder · ${countBlocks(parsed.root)} blocks · ${parsed.composableName}"
    }

    private fun renderCanvas() {
        layoutCanvas.render(
            document = managedDocument,
            selectedBlockId = selectedBlockId,
            slotFillLabels = managedDocument?.rawCodeBlocks
                ?.associate { raw -> raw.id to raw.note.ifBlank { "Custom" } }
                .orEmpty(),
        )
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

    private fun renderInspector() {
        inspectorPanel.removeAll()
        val currentDocument = managedDocument
        val block = selectedBlock()
        if (currentDocument == null || block == null) {
            inspectorPanel.add(
                JBLabel("Select a block on the canvas to edit its note and supported properties.").apply {
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

                val currentFill = currentDocument.rawCodeBlocks.firstOrNull { raw -> raw.id == block.id }
                add(
                    formRow(
                        "Default Slot Fill",
                        JPanel(BorderLayout(8, 0)).apply {
                            isOpaque = false
                            add(
                                JBLabel(currentFill?.note ?: "Empty").apply {
                                    foreground = if (currentFill == null) {
                                        JBColor.GRAY
                                    } else {
                                        JBColor.namedColor(
                                            "Label.foreground",
                                            JBColor(Color(28, 28, 28), Color(226, 226, 226)),
                                        )
                                    }
                                },
                                BorderLayout.CENTER,
                            )
                            add(
                                JButton("Clear").apply {
                                    isEnabled = currentFill != null
                                    addActionListener {
                                        clearSlotFill(block.id)
                                    }
                                },
                                BorderLayout.EAST,
                            )
                        },
                    )
                )
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

            if (block.type in setOf(ComposeBlockType.COLUMN, ComposeBlockType.ROW, ComposeBlockType.BOX, ComposeBlockType.SLOT)) {
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

    private fun insertPaletteBlock(type: ComposeBlockType) {
        val selected = selectedBlock()
        if (selected?.type == ComposeBlockType.SLOT) {
            fillSlotWithTemplate(selected.id, type)
            return
        }
        val newBlock = createDefaultBlock(type)
        applyMutation("Insert ${type.displayName}", newBlock.id) { current ->
            current.copy(root = BlockSpecTreeOps.addNearSelection(current.root, selectedBlockId, newBlock))
        }
    }

    private fun insertIntoContainer(
        containerId: String,
        type: ComposeBlockType,
    ) {
        val newBlock = createDefaultBlock(type)
        applyMutation("Insert ${type.displayName}", newBlock.id) { current ->
            current.copy(
                root = BlockSpecTreeOps.updateBlock(current.root, containerId) { container ->
                    if (container.type.supportsChildren) {
                        container.withChildren(container.children + newBlock)
                    } else {
                        container
                    }
                },
            )
        }
    }

    private fun fillSlotWithTemplate(
        slotId: String,
        type: ComposeBlockType,
    ) {
        applyMutation("Fill Named Slot", slotId) { current ->
            current.copy(
                rawCodeBlocks = current.rawCodeBlocks
                    .filterNot { raw -> raw.id == slotId }
                    .plus(
                        RawCodeBlockSpec(
                            id = slotId,
                            note = type.displayName,
                            source = defaultSlotTemplateSource(type),
                        )
                    ),
            )
        }
    }

    private fun clearSlotFill(slotId: String) {
        applyMutation("Clear Slot Fill", slotId) { current ->
            current.copy(rawCodeBlocks = current.rawCodeBlocks.filterNot { raw -> raw.id == slotId })
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

    private fun unwrapSelected() {
        val selected = selectedBlock() ?: return
        if (!selected.type.supportsChildren || selected.children.size > 1) {
            return
        }
        if (selected.id == managedDocument?.root?.id && selected.children.isEmpty()) {
            return
        }
        val nextSelectionId = selected.children.singleOrNull()?.id ?: selectedParentIdOrRoot()
        applyMutation("Unwrap Compose Block", nextSelectionId) { current ->
            current.copy(root = BlockSpecTreeOps.unwrapBlock(current.root, selected.id))
        }
    }

    private fun applySketchLayout() {
        val current = managedDocument ?: return
        val sketchRoot = ComposeLayoutSketchTreeBuilder.buildRoot(
            rootId = current.root.id,
            rootNote = current.composableName,
            regions = sketchWorkspace.regions(),
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
                    PropSpec("fillMaxWidth", "true"),
                    PropSpec("padding", "16.dp"),
                )

                ComposeBlockType.ROW -> listOf(
                    PropSpec("fillMaxWidth", "true"),
                    PropSpec("padding", "8.dp"),
                    PropSpec("arrangement", "spacedBy(8.dp)"),
                )

                ComposeBlockType.BOX -> listOf(
                    PropSpec("fillMaxWidth", "true"),
                    PropSpec("padding", "8.dp"),
                )

                ComposeBlockType.SLOT -> listOf(
                    PropSpec("slotName", "slot"),
                    PropSpec("fillMaxWidth", "true"),
                )

                ComposeBlockType.TEXT -> listOf(
                    PropSpec("text", "Text"),
                )

                ComposeBlockType.BUTTON -> listOf(
                    PropSpec("text", "Button"),
                )

                ComposeBlockType.SPACER -> listOf(
                    PropSpec("size", "16.dp"),
                )

                ComposeBlockType.IMAGE -> listOf(
                    PropSpec("text", "Image"),
                    PropSpec("size", "64.dp"),
                )

                ComposeBlockType.TEXT_FIELD -> listOf(
                    PropSpec("text", "Label"),
                    PropSpec("fillMaxWidth", "true"),
                )
            },
        )
    }

    private fun defaultSlotTemplateSource(type: ComposeBlockType): String {
        return when (type) {
            ComposeBlockType.COLUMN -> """
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Text("Column slot")
                }
            """.trimIndent()

            ComposeBlockType.ROW -> """
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Row slot")
                }
            """.trimIndent()

            ComposeBlockType.BOX -> """
                Box(
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Text("Box slot")
                }
            """.trimIndent()

            ComposeBlockType.TEXT -> """Text("Text")"""
            ComposeBlockType.BUTTON -> """
                Button(onClick = { }) {
                    Text("Button")
                }
            """.trimIndent()

            ComposeBlockType.SPACER -> """Spacer(modifier = Modifier.size(16.dp))"""
            ComposeBlockType.IMAGE -> """
                Image(
                    painter = TODO("Provide painter"),
                    contentDescription = "Image",
                    modifier = Modifier.size(64.dp),
                )
            """.trimIndent()

            ComposeBlockType.TEXT_FIELD -> """
                TextField(
                    value = "",
                    onValueChange = { },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                )
            """.trimIndent()

            ComposeBlockType.SLOT -> ""
        }
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

    private fun collectBlocks(block: BlockSpec): List<BlockSpec> {
        return listOf(block) + block.children.flatMap(::collectBlocks)
    }

    private fun countBlocks(block: BlockSpec): Int = collectBlocks(block).size
}
