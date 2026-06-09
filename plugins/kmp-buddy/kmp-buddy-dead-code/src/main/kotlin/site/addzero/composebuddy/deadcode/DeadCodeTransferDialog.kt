package site.addzero.composebuddy.deadcode

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.ButtonGroup
import javax.swing.JComponent

class DeadCodeTransferDialog(project: Project) : DialogWrapper(project) {
    private val deadCodeButton = JBRadioButton(DeadCodeTransferMode.DEAD_CODE.dialogLabel, true)
    private val liveCodeButton = JBRadioButton(DeadCodeTransferMode.LIVE_CODE.dialogLabel, false)

    init {
        title = "KMP Buddy Code Transfer"
        ButtonGroup().apply {
            add(deadCodeButton)
            add(liveCodeButton)
        }
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            label("Choose how to transfer whole Kotlin files from the reachable-code analysis.")
        }
        row {
            label("This physically moves files out of the source module; restore uses the generated manifest.")
        }
        row {
            cell(deadCodeButton)
        }
        row {
            cell(JBLabel("Moves only files where every top-level declaration is unreachable."))
        }
        row {
            cell(liveCodeButton)
        }
        row {
            cell(JBLabel("Moves only files where every top-level declaration is reachable. Mixed files stay in place."))
        }
        row {
            label("Both modes preserve relative directories and original file names, and write a restore manifest.")
        }
    }

    fun selectedMode(): DeadCodeTransferMode {
        return if (liveCodeButton.isSelected) {
            DeadCodeTransferMode.LIVE_CODE
        } else {
            DeadCodeTransferMode.DEAD_CODE
        }
    }
}
