package site.addzero.composebuddy.features.slotspi

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import site.addzero.composebuddy.ComposeBuddyBundle
import javax.swing.JCheckBox
import javax.swing.JComponent

class SlotSpiTargetSelectionDialog(
    project: Project,
    private val targets: List<SlotSpiTarget>,
) : DialogWrapper(project) {
    private val checkBoxes = targets.associateWith { target ->
        JCheckBox(buildTargetLabel(target), true)
    }

    init {
        title = ComposeBuddyBundle.message("dialog.slot.spi.select.title")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            label(ComposeBuddyBundle.message("dialog.slot.spi.select.message"))
        }
        targets.forEach { target ->
            row {
                cell(checkBoxes.getValue(target))
                    .align(AlignX.FILL)
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (selectedTargets().isEmpty()) {
            return ValidationInfo(ComposeBuddyBundle.message("dialog.slot.spi.select.validation"))
        }
        return null
    }

    fun selectedTargets(): List<SlotSpiTarget> {
        return targets.filter { target -> checkBoxes.getValue(target).isSelected }
    }

    private fun buildTargetLabel(target: SlotSpiTarget): String {
        val details = buildList {
            if (target.receiverTypeText != null) {
                add("receiver: ${target.receiverTypeText.substringAfterLast('.')}")
            }
            if (target.capturedParameters.isNotEmpty()) {
                add("captures: ${target.capturedParameters.joinToString(", ") { parameter -> parameter.name }}")
            }
        }
        if (details.isEmpty()) {
            return target.slotName
        }
        return "${target.slotName}  (${details.joinToString(" | ")})"
    }
}
