package site.addzero.composebuddy.designer.ui

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposeDesignerCustomComponent
import site.addzero.composebuddy.designer.model.ComposeDesignerPaletteCatalog
import site.addzero.composebuddy.designer.model.ComposePaletteEntry
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import site.addzero.composebuddy.settings.ComposeBuddySettingsService
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComboBox
import javax.swing.DefaultListModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JToggleButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.TransferHandler

class ComposeDesignerPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {
    private val functionNameField = JBTextField(ComposeBuddyBundle.message("designer.preview.function"))
    private val paletteFunctionNameField = JBTextField(ComposeBuddyBundle.message("designer.preview.function"))
    private var targetFile: VirtualFile? = null
    private var syncingFromCanvas = false
    private var syncingFromEditor = false
    private val paletteModel = DefaultListModel<ComposePaletteEntry>()
    private var lastPaletteSignature = ""

    private val canvas = ComposeDesignerCanvas { nodes ->
        if (syncingFromEditor) {
            return@ComposeDesignerCanvas
        }
        syncCanvasToFile(nodes)
    }

    init {
        val palette = createPalette()
        refreshPaletteEntries()
        val canvasScrollPane = JBScrollPane(canvas).apply {
            border = JBUI.Borders.empty()
        }
        val paletteScrollPane = JBScrollPane(palette).apply {
            border = JBUI.Borders.empty()
        }
        val leftPanel = panel {
            row {
                label(ComposeBuddyBundle.message("designer.palette.title"))
                    .bold()
            }
            row {
                button(ComposeBuddyBundle.message("settings.designer.custom.components.add")) {
                    showAddCustomComponentDialog()
                }
            }
            row(ComposeBuddyBundle.message("designer.preview.function.label")) {
                cell(paletteFunctionNameField)
                    .resizableColumn()
            }
            row {
                cell(paletteScrollPane)
                    .resizableColumn()
            }
            row {
                button(ComposeBuddyBundle.message("designer.preview.clear")) {
                    canvas.clear()
                }
            }
        }.apply {
            border = JBUI.Borders.empty(8)
            preferredSize = JBUI.size(220, 0)
            minimumSize = JBUI.size(200, 0)
        }
        syncFunctionNameFields()
        functionNameField.addActionListener {
            rebindGeneratedFile()
        }
        functionNameField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(event: FocusEvent) {
                rebindGeneratedFile()
            }
        })
        paletteFunctionNameField.addActionListener {
            functionNameField.text = paletteFunctionNameField.text
            rebindGeneratedFile()
        }
        paletteFunctionNameField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(event: FocusEvent) {
                functionNameField.text = paletteFunctionNameField.text
                rebindGeneratedFile()
            }
        })

        val canvasPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(createToolbar(), BorderLayout.NORTH)
            add(canvasScrollPane, BorderLayout.CENTER)
        }

        add(leftPanel, BorderLayout.WEST)
        add(canvasPanel, BorderLayout.CENTER)

        initializeTargetFile()
    }

    private fun createToolbar(): JComponent {
        return JPanel(HorizontalLayout(JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(0, 0, 8, 0)
            add(JLabel(ComposeBuddyBundle.message("designer.toolbar.function")))
            functionNameField.columns = 20
            add(functionNameField)
            add(JToggleButton(ComposeBuddyBundle.message("designer.toolbar.select.container")).apply {
                addActionListener {
                    canvas.setContainerSelectionMode(isSelected)
                }
            })
            add(javax.swing.JButton(ComposeBuddyBundle.message("designer.toolbar.arrange")).apply {
                addActionListener {
                    canvas.arrangeLayout()
                }
            })
            add(javax.swing.JButton(ComposeBuddyBundle.message("designer.toolbar.unwrap.box")).apply {
                addActionListener {
                    canvas.unwrapSelectedContainer()
                }
            })
            add(JLabel(ComposeBuddyBundle.message("designer.toolbar.hint")))
        }
    }

    private fun createPalette(): JBList<ComposePaletteEntry> {
        return JBList(paletteModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            dragEnabled = true
            transferHandler = object : TransferHandler() {
                override fun getSourceActions(c: JComponent): Int = COPY

                override fun createTransferable(c: JComponent): StringSelection? {
                    val value = selectedValue ?: return null
                    return StringSelection(value.transferId())
                }
            }
            cellRenderer = DefaultListCellRenderer().also { renderer ->
                renderer.horizontalAlignment = JLabel.LEFT
            }.let { base ->
                object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: javax.swing.JList<*>,
                        value: Any?,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean,
                    ): java.awt.Component {
                        val label = base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                        label.text = (value as? ComposePaletteEntry)?.displayName.orEmpty()
                        label.border = JBUI.Borders.empty(8)
                        return label
                    }
                }
            }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(event: MouseEvent) {
                    refreshPaletteEntries()
                }
            })
        }
    }

    private fun refreshPaletteEntries() {
        val signature = ComposeDesignerPaletteCatalog.paletteEntries()
            .joinToString("|") { "${it.id}:${it.displayName}" }
        if (signature == lastPaletteSignature) {
            return
        }
        val selectedId = (0 until paletteModel.size())
            .map { paletteModel.get(it) }
            .firstOrNull()
            ?.id
        paletteModel.removeAllElements()
        ComposeDesignerPaletteCatalog.paletteEntries().forEach { paletteModel.addElement(it) }
        lastPaletteSignature = signature
        if (selectedId != null) {
            val index = (0 until paletteModel.size()).firstOrNull { paletteModel.get(it).id == selectedId }
            if (index != null) {
                // 保留当前选择，避免刷新时拖拽前状态丢失
            }
        }
    }

    private fun showAddCustomComponentDialog() {
        val dialog = AddCustomComponentDialog(project)
        if (!dialog.showAndGet()) {
            return
        }
        val newComponent = dialog.component()
        val settings = ComposeBuddySettingsService.getInstance().state
        val existingComponents = ComposeDesignerPaletteCatalog.parseCustomComponents(settings.designerCustomComponentsDsl)
        val merged = existingComponents + newComponent
        val validation = ComposeDesignerPaletteCatalog.validateCustomComponents(
            ComposeDesignerPaletteCatalog.serializeCustomComponents(merged),
        )
        if (validation.errors.isNotEmpty()) {
            throw ConfigurationException(validation.errors.joinToString("\n"))
        }
        settings.designerCustomComponentsDsl = ComposeDesignerPaletteCatalog.serializeCustomComponents(merged)
        refreshPaletteEntries()
    }

    private fun syncCanvasToFile(nodes: List<ComposeCanvasNode>) {
        val file = targetFile ?: ComposeDesignerWritebackSupport.ensureSiblingKotlinFile(project, currentFunctionName())
            ?: return
        targetFile = file
        val generated = ComposeDesignerCodeGenerator.generate(nodes, currentFunctionName())
        val packageName = ComposeDesignerWritebackSupport.packageNameForTarget(project, file)
        syncingFromCanvas = true
        try {
            ComposeDesignerWritebackSupport.writeToSpecificFile(project, file, packageName, generated)
        } finally {
            syncingFromCanvas = false
        }
    }

    private fun syncEditorToCanvas(text: String) {
        syncingFromEditor = true
        try {
            ComposeDesignerCodeParser.parse(project, text, currentFunctionName())?.let { parsed ->
                canvas.replaceFromCode(parsed)
            }
        } finally {
            syncingFromEditor = false
        }
    }

    private fun initializeTargetFile() {
        val file = targetFile ?: ComposeDesignerWritebackSupport.ensureSiblingKotlinFile(project, currentFunctionName())
            ?: return
        targetFile = file
        ComposeDesignerWritebackSupport.openInEditor(project, file)
        bindCanvasFromFile(file)
    }

    private fun rebindGeneratedFile() {
        syncFunctionNameFields()
        targetFile = ComposeDesignerWritebackSupport.ensureSiblingKotlinFile(project, currentFunctionName())
        initializeTargetFile()
    }

    private fun syncFunctionNameFields() {
        val normalized = functionNameField.text.ifBlank { paletteFunctionNameField.text }
        functionNameField.text = normalized
        paletteFunctionNameField.text = normalized
    }

    private fun bindCanvasFromFile(file: VirtualFile) {
        val document = ComposeDesignerWritebackSupport.documentFor(file) ?: return
        val text = document.text
        if (text.isBlank()) {
            return
        }
        val parsed = ComposeDesignerCodeParser.parse(project, text, currentFunctionName())
        if (parsed != null) {
            syncingFromEditor = true
            try {
                canvas.replaceFromCode(parsed)
            } finally {
                syncingFromEditor = false
            }
            return
        }
    }

    private fun currentFunctionName(): String {
        return functionNameField.text
            .trim()
            .ifBlank { ComposeBuddyBundle.message("designer.preview.function") }
            .replace(Regex("[^A-Za-z0-9_]"), "")
            .ifBlank { ComposeBuddyBundle.message("designer.preview.function") }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    override fun dispose() {
        // 无需额外释放资源
    }

    private class AddCustomComponentDialog(project: Project) : DialogWrapper(project) {
        private val nameField = JBTextField()
        private val functionField = JBTextField()
        private val importsField = JBTextField()
        private val layoutCombo = JComboBox(arrayOf("", "box", "row", "column"))
        private val widthField = JBTextField("180")
        private val heightField = JBTextField("56")
        private val templateArea = JBTextArea(
            "CustomComposable(\n    modifier = {modifier},\n)",
        ).apply {
            lineWrap = false
            rows = 8
        }

        init {
            title = ComposeBuddyBundle.message("settings.designer.custom.components.add")
            init()
        }

        fun component(): ComposeDesignerCustomComponent {
            return ComposeDesignerCustomComponent(
                displayName = nameField.text.trim(),
                functionName = functionField.text.trim(),
                imports = importsField.text.split(",").map { it.trim() }.filter { it.isNotBlank() },
                template = templateArea.text.trim(),
                width = widthField.text.toIntOrNull() ?: 180,
                height = heightField.text.toIntOrNull() ?: 56,
                layoutKind = when ((layoutCombo.selectedItem as? String).orEmpty()) {
                    "box" -> ComposePaletteItem.BOX
                    "row" -> ComposePaletteItem.ROW
                    "column" -> ComposePaletteItem.COLUMN
                    else -> null
                },
            )
        }

        override fun createCenterPanel(): JComponent {
            return panel {
                row(ComposeBuddyBundle.message("settings.designer.custom.components.name")) {
                    cell(nameField).resizableColumn()
                }
                row(ComposeBuddyBundle.message("settings.designer.custom.components.function")) {
                    cell(functionField).resizableColumn()
                }
                row(ComposeBuddyBundle.message("settings.designer.custom.components.imports")) {
                    cell(importsField).resizableColumn()
                }
                row(ComposeBuddyBundle.message("settings.designer.custom.components.layout")) {
                    cell(layoutCombo).resizableColumn()
                }
                row(ComposeBuddyBundle.message("settings.designer.custom.components.width")) {
                    cell(widthField).resizableColumn()
                }
                row(ComposeBuddyBundle.message("settings.designer.custom.components.height")) {
                    cell(heightField).resizableColumn()
                }
                row(ComposeBuddyBundle.message("settings.designer.custom.components.template")) {
                    cell(JBScrollPane(templateArea)).resizableColumn()
                }
            }
        }

        override fun doValidate(): ValidationInfo? {
            val candidate = component()
            val validation = ComposeDesignerPaletteCatalog.validateCustomComponents(
                ComposeDesignerPaletteCatalog.serializeCustomComponents(listOf(candidate)),
            )
            return validation.errors.firstOrNull()?.let { ValidationInfo(it) }
        }
    }
}
