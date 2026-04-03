package site.addzero.composeblocks.editor

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class ComposeBlockLayoutDialog(
    initialProperties: ComposeBlockLayoutProperties,
) : DialogWrapper(true) {

    private val arrangementField = JBTextField(initialProperties.arrangement)
    private val alignmentField = JBTextField(initialProperties.alignment)
    private val contentAlignmentField = JBTextField(initialProperties.contentAlignment)
    private val horizontalArrangementField = JBTextField(initialProperties.horizontalArrangement)
    private val verticalAlignmentField = JBTextField(initialProperties.verticalAlignment)
    private val paddingField = JBTextField(initialProperties.padding)
    private val weightField = JBTextField(initialProperties.weight)
    private val fillMaxWidthCheckBox = JBCheckBox("fillMaxWidth()", initialProperties.fillMaxWidth)
    private val fillMaxHeightCheckBox = JBCheckBox("fillMaxHeight()", initialProperties.fillMaxHeight)

    init {
        title = "Edit Compose Layout"
        init()
    }

    fun properties(): ComposeBlockLayoutProperties {
        return ComposeBlockLayoutProperties(
            arrangement = arrangementField.text.orEmpty(),
            alignment = alignmentField.text.orEmpty(),
            contentAlignment = contentAlignmentField.text.orEmpty(),
            horizontalArrangement = horizontalArrangementField.text.orEmpty(),
            verticalAlignment = verticalAlignmentField.text.orEmpty(),
            padding = paddingField.text.orEmpty(),
            weight = weightField.text.orEmpty(),
            fillMaxWidth = fillMaxWidthCheckBox.isSelected,
            fillMaxHeight = fillMaxHeightCheckBox.isSelected,
        )
    }

    override fun createCenterPanel(): JComponent {
        val formPanel = JPanel(GridLayout(0, 2, 10, 8)).apply {
            add(JBLabel("arrangement"))
            add(arrangementField)
            add(JBLabel("alignment"))
            add(alignmentField)
            add(JBLabel("contentAlignment"))
            add(contentAlignmentField)
            add(JBLabel("horizontalArrangement"))
            add(horizontalArrangementField)
            add(JBLabel("verticalAlignment"))
            add(verticalAlignmentField)
            add(JBLabel("padding(...)"))
            add(paddingField)
            add(JBLabel("weight(...)"))
            add(weightField)
        }

        val modifierPanel = JPanel(GridLayout(0, 1, 0, 6)).apply {
            add(fillMaxWidthCheckBox)
            add(fillMaxHeightCheckBox)
        }

        return JPanel(BorderLayout(0, 10)).apply {
            border = JBUI.Borders.empty(4, 8)
            add(formPanel, BorderLayout.CENTER)
            add(modifierPanel, BorderLayout.SOUTH)
        }
    }
}
