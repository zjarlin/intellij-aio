package site.addzero.composebuddy.settings

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.designer.model.ComposeDesignerCustomComponent
import site.addzero.composebuddy.designer.model.ComposeDesignerPaletteCatalog
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import java.awt.BorderLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ComposeBuddyConfigurable : BoundConfigurable(ComposeBuddyBundle.message("settings.display.name")) {
    private val settings = ComposeBuddySettingsService.getInstance().state
    private val designerComponentsArea = JBTextArea(settings.designerCustomComponentsDsl).apply {
        lineWrap = false
        rows = 14
    }
    private val componentListModel = DefaultListModel<ComposeDesignerCustomComponent>()
    private val componentList = JBList(componentListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
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
                    val component = value as? ComposeDesignerCustomComponent
                    label.text = component?.displayName ?: ""
                    return label
                }
            }
        }
    }
    private val nameField = JBTextField()
    private val functionField = JBTextField()
    private val importsField = JBTextField()
    private val layoutCombo = JComboBox(arrayOf("", "box", "row", "column"))
    private val widthField = JBTextField("180")
    private val heightField = JBTextField("56")
    private val templateArea = JBTextArea().apply {
        lineWrap = false
        rows = 10
    }
    private var syncingForm = false
    private var syncingDsl = false

    init {
        reloadComponentsFromDsl(settings.designerCustomComponentsDsl)
        componentList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                showSelectedComponent()
            }
        }
        componentList.selectedIndex = if (componentListModel.isEmpty) -1 else 0
        installFormListeners()
        installDslListener()
    }

    override fun createPanel() = panel {
        group(ComposeBuddyBundle.message("settings.group.analysis")) {
            row(ComposeBuddyBundle.message("settings.parameter.threshold")) {
                intTextField(1..99)
                    .bindIntText(settings::parameterThreshold)
            }
            row(ComposeBuddyBundle.message("settings.callback.threshold")) {
                intTextField(1..99)
                    .bindIntText(settings::callbackThreshold)
            }
            row(ComposeBuddyBundle.message("settings.statepair.threshold")) {
                intTextField(1..99)
                    .bindIntText(settings::statePairThreshold)
            }
        }

        group(ComposeBuddyBundle.message("settings.group.wrapper")) {
            row {
                checkBox(ComposeBuddyBundle.message("settings.wrapper.props.default"))
                    .bindSelected(settings::preferPropsWrapper)
            }
            row {
                checkBox(ComposeBuddyBundle.message("settings.wrapper.compat.default"))
                    .bindSelected(settings::keepCompatibilityByDefault)
            }
            row {
                checkBox(ComposeBuddyBundle.message("settings.wrapper.templates.default"))
                    .bindSelected(settings::addTemplateParameters)
            }
        }

        group(ComposeBuddyBundle.message("settings.group.designer")) {
            row {
                cell(createStructuredEditor())
                    .resizableColumn()
            }
            row(ComposeBuddyBundle.message("settings.designer.custom.components.advanced")) {
                cell(JBScrollPane(designerComponentsArea))
                    .resizableColumn()
                    .comment(ComposeBuddyBundle.message("settings.designer.custom.components.hint"))
            }
        }

        onApply {
            settings.designerCustomComponentsDsl = designerComponentsArea.text.trim()
        }

        onReset {
            designerComponentsArea.text = settings.designerCustomComponentsDsl
            reloadComponentsFromDsl(settings.designerCustomComponentsDsl)
            componentList.selectedIndex = if (componentListModel.isEmpty) -1 else 0
        }
    }

    override fun apply() {
        val validation = ComposeDesignerPaletteCatalog.validateCustomComponents(designerComponentsArea.text.trim())
        if (validation.errors.isNotEmpty()) {
            throw ConfigurationException(validation.errors.joinToString("\n"))
        }
        super.apply()
    }

    private fun createStructuredEditor(): JPanel {
        val decorator = ToolbarDecorator.createDecorator(componentList)
            .setAddAction {
                val component = ComposeDesignerCustomComponent(
                    displayName = ComposeBuddyBundle.message("settings.designer.custom.components.add"),
                    functionName = "CustomComposable${componentListModel.size + 1}",
                    imports = emptyList(),
                    template = "CustomComposable(\n    modifier = {modifier},\n)",
                    width = 180,
                    height = 56,
                    layoutKind = null,
                )
                componentListModel.addElement(component)
                componentList.selectedIndex = componentListModel.size - 1
                syncDslFromModel()
            }
            .setAddActionName(ComposeBuddyBundle.message("settings.designer.custom.components.add"))
            .setRemoveAction {
                val index = componentList.selectedIndex
                if (index >= 0) {
                    componentListModel.remove(index)
                    componentList.selectedIndex = if (componentListModel.isEmpty) -1 else minOf(index, componentListModel.size - 1)
                    syncDslFromModel()
                }
            }
            .setRemoveActionName(ComposeBuddyBundle.message("settings.designer.custom.components.remove"))

        val formPanel = panel {
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

        layoutCombo.renderer = DefaultListCellRenderer().also { renderer ->
            renderer.horizontalAlignment = JLabel.LEFT
        }.let { base ->
            object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: javax.swing.JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean,
                ): java.awt.Component {
                    val label = base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                    label.text = if ((value as? String).isNullOrBlank()) {
                        ComposeBuddyBundle.message("settings.designer.custom.components.none")
                    } else {
                        value as String
                    }
                    return label
                }
            }
        }

        return JPanel(BorderLayout()).apply {
            add(decorator.createPanel(), BorderLayout.WEST)
            add(formPanel, BorderLayout.CENTER)
        }
    }

    private fun installFormListeners() {
        listOf(nameField, functionField, importsField, widthField, heightField).forEach { field ->
            field.document.addDocumentListener(simpleDocumentListener { updateSelectedFromForm() })
        }
        templateArea.document.addDocumentListener(simpleDocumentListener { updateSelectedFromForm() })
        layoutCombo.addActionListener {
            updateSelectedFromForm()
        }
    }

    private fun installDslListener() {
        designerComponentsArea.document.addDocumentListener(simpleDocumentListener {
            if (syncingDsl) {
                return@simpleDocumentListener
            }
            reloadComponentsFromDsl(designerComponentsArea.text)
        })
    }

    private fun showSelectedComponent() {
        syncingForm = true
        try {
            val component = componentList.selectedValue
            nameField.text = component?.displayName.orEmpty()
            functionField.text = component?.functionName.orEmpty()
            importsField.text = component?.imports?.joinToString(",").orEmpty()
            layoutCombo.selectedItem = component?.layoutKind?.name?.lowercase().orEmpty()
            widthField.text = component?.width?.toString().orEmpty()
            heightField.text = component?.height?.toString().orEmpty()
            templateArea.text = component?.template.orEmpty()
        } finally {
            syncingForm = false
        }
    }

    private fun updateSelectedFromForm() {
        if (syncingForm) {
            return
        }
        val index = componentList.selectedIndex
        if (index < 0) {
            return
        }
        componentListModel.set(
            index,
            ComposeDesignerCustomComponent(
                displayName = nameField.text.trim(),
                functionName = functionField.text.trim(),
                imports = importsField.text.split(",").map { it.trim() }.filter { it.isNotBlank() },
                template = templateArea.text,
                width = widthField.text.toIntOrNull() ?: 180,
                height = heightField.text.toIntOrNull() ?: 56,
                layoutKind = when ((layoutCombo.selectedItem as? String).orEmpty()) {
                    "box" -> ComposePaletteItem.BOX
                    "row" -> ComposePaletteItem.ROW
                    "column" -> ComposePaletteItem.COLUMN
                    else -> null
                },
            ),
        )
        componentList.selectedIndex = index
        syncDslFromModel()
    }

    private fun syncDslFromModel() {
        syncingDsl = true
        try {
            val components = (0 until componentListModel.size()).map { componentListModel.getElementAt(it) }
            designerComponentsArea.text = ComposeDesignerPaletteCatalog.serializeCustomComponents(components)
        } finally {
            syncingDsl = false
        }
    }

    private fun reloadComponentsFromDsl(rawDsl: String) {
        val validation = ComposeDesignerPaletteCatalog.validateCustomComponents(rawDsl)
        val selectedFunction = componentList.selectedValue?.functionName
        componentListModel.removeAllElements()
        validation.components.forEach { componentListModel.addElement(it) }
        val selectedIndex = if (selectedFunction != null) {
            (0 until componentListModel.size()).firstOrNull { componentListModel.getElementAt(it).functionName == selectedFunction } ?: -1
        } else {
            -1
        }
        componentList.selectedIndex = when {
            selectedIndex >= 0 -> selectedIndex
            componentListModel.isEmpty -> -1
            else -> 0
        }
        showSelectedComponent()
    }

    private fun simpleDocumentListener(onChange: () -> Unit): DocumentListener {
        return object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onChange()
            override fun removeUpdate(e: DocumentEvent?) = onChange()
            override fun changedUpdate(e: DocumentEvent?) = onChange()
        }
    }
}
