package site.addzero.composebuddy.support

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import site.addzero.composebuddy.ComposeBuddyBundle
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

object SharedSourceSetTargetSelection {
    fun getOrChooseTargetSourceSet(project: Project, moduleRootPath: Path): String? {
        MoveToSharedSourceSetSupport.getRememberedSharedSourceSet(moduleRootPath)?.let { sourceSetName ->
            return sourceSetName
        }
        return chooseAndRememberTargetSourceSet(project, moduleRootPath)
    }

    fun chooseAndRememberTargetSourceSet(project: Project, moduleRootPath: Path): String? {
        val dialog = SharedSourceSetSelectionDialog(
            project,
            moduleRootPath.fileName?.toString() ?: moduleRootPath.toString(),
        )
        if (!dialog.showAndGet()) {
            return null
        }
        val selected = dialog.selectedSourceSetName
        MoveToSharedSourceSetSupport.rememberSharedSourceSet(moduleRootPath, selected)
        return selected
    }

    private class SharedSourceSetSelectionDialog(
        project: Project,
        moduleName: String,
    ) : DialogWrapper(project) {
        private val options = MoveToSharedSourceSetSupport.SUPPORTED_SHARED_SOURCE_SETS.toTypedArray()
        private val sourceSetCombo = JComboBox(options)
        private val message = ComposeBuddyBundle.message("dialog.shared.source.set.select.message", moduleName)

        val selectedSourceSetName: String
            get() = sourceSetCombo.selectedItem as String

        init {
            title = ComposeBuddyBundle.message("dialog.shared.source.set.select.title")
            init()
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(BorderLayout(0, 8)).apply {
                add(JLabel(message), BorderLayout.NORTH)
                add(sourceSetCombo, BorderLayout.CENTER)
            }
        }
    }
}
