package site.addzero.composebuddy.designer.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.designer.model.ComposeCanvasNode
import site.addzero.composebuddy.designer.model.ComposeDesignerPaletteCatalog
import site.addzero.composebuddy.designer.model.ComposePaletteEntry
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.DefaultListCellRenderer
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
    private var targetFile = ComposeDesignerWritebackSupport.findSiblingKotlinFile(project, currentFunctionName())
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
        val file = targetFile ?: return
        bindCanvasFromFile(file)
    }

    private fun rebindGeneratedFile() {
        syncFunctionNameFields()
        targetFile = ComposeDesignerWritebackSupport.findSiblingKotlinFile(project, currentFunctionName())
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
}
