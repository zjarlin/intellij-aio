package site.addzero.smart.intentions.kotlin.entityfieldmerge

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComboBox
import javax.swing.JComponent
import org.jetbrains.kotlin.psi.KtClass

internal class EntityFieldTargetSelectionDialog(
    project: Project,
    classes: List<KtClass>,
    defaultTarget: KtClass?,
) : DialogWrapper(project) {
    private data class TargetItem(
        val klass: KtClass,
        val label: String,
    ) {
        override fun toString(): String {
            return label
        }
    }

    private val items = classes.map { klass ->
        val path = klass.containingKtFile.virtualFile?.path.orEmpty()
        val fileName = path.substringAfterLast('/').ifBlank { klass.containingKtFile.name }
        TargetItem(
            klass = klass,
            label = "${klass.name.orEmpty()} ($fileName)",
        )
    }

    private val targetComboBox = JComboBox(items.toTypedArray()).apply {
        val defaultIndex = items.indexOfFirst { item -> item.klass == defaultTarget }
        if (defaultIndex >= 0) {
            selectedIndex = defaultIndex
        }
    }

    init {
        title = "选择目标实体类"
        setOKButtonText("继续比较字段")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("目标类:"), targetComboBox, 1, false)
            .panel
    }

    fun selectedTarget(): KtClass {
        return (targetComboBox.selectedItem as TargetItem).klass
    }
}
