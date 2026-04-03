package site.addzero.composeblocks.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import javax.swing.JComponent

internal class ComposeLayoutSketchDialog(
    project: Project,
) : DialogWrapper(project, true) {

    private val workspace = ComposeLayoutSketchWorkspace()

    init {
        title = "Sketch Layout Slots"
        init()
    }

    fun regions(): List<LayoutSketchRegion> = workspace.regions()

    override fun createCenterPanel(): JComponent {
        workspace.preferredSize = Dimension(980, 620)
        return workspace
    }
}
