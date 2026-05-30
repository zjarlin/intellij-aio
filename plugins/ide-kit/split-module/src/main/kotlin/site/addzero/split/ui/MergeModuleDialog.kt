package site.addzero.split.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import site.addzero.split.services.ModuleMerger
import java.awt.Dimension
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 合并模块确认对话框
 */
class MergeModuleDialog(
    project: Project,
    selectedModules: List<VirtualFile>,
    defaultBasePackage: String,
) : DialogWrapper(project) {

    private data class ModuleItem(
        val name: String,
        val path: String,
        val packageSegment: String,
    ) {
        override fun toString(): String = name
    }

    private val moduleItems = selectedModules
        .sortedBy { it.name }
        .map { module ->
            ModuleItem(
                name = module.name,
                path = module.path,
                packageSegment = ModuleMerger.packageSegmentForModule(module.name),
            )
        }

    private val targetComboBox = JComboBox(moduleItems.toTypedArray()).apply {
        val canonicalIndex = moduleItems.indexOfFirst { it.name == "az-compose" }
        if (canonicalIndex >= 0) {
            selectedIndex = canonicalIndex
        }
        addActionListener { refreshPreview() }
    }

    private val basePackageField = JBTextField(defaultBasePackage, 36)
    private val previewArea = JBTextArea(9, 58).apply {
        isEditable = false
        lineWrap = false
        minimumSize = Dimension(520, 150)
    }

    init {
        title = "Merge Modules"
        basePackageField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                refreshPreview()
            }

            override fun removeUpdate(e: DocumentEvent) {
                refreshPreview()
            }

            override fun changedUpdate(e: DocumentEvent) {
                refreshPreview()
            }
        })
        init()
        refreshPreview()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Main module:"), targetComboBox, 1, false)
            .addLabeledComponent(JBLabel("Base package:"), basePackageField, 1, false)
            .addLabeledComponent(JBLabel("Package preview:"), JScrollPane(previewArea), 1, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun doValidate(): ValidationInfo? {
        val basePackage = getBasePackage()
        if (basePackage.isBlank()) {
            return ValidationInfo("Base package cannot be empty", basePackageField)
        }

        if (!ModuleMerger.isValidPackageName(basePackage)) {
            return ValidationInfo("Base package is not a valid Java/Kotlin package", basePackageField)
        }

        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = basePackageField

    fun getTargetModulePath(): String = selectedItem().path

    fun getBasePackage(): String = basePackageField.text.trim()

    private fun selectedItem(): ModuleItem {
        return targetComboBox.selectedItem as ModuleItem
    }

    private fun refreshPreview() {
        val target = selectedItem()
        val basePackage = getBasePackage().ifBlank { "<base-package>" }
        previewArea.text = moduleItems
            .filterNot { it.path == target.path }
            .joinToString("\n") { item ->
                "${item.name} -> $basePackage.${item.packageSegment}"
            }
    }
}
