package site.addzero.gradle.buddy.intentions.select

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class VersionSelectionDialog(
    project: Project,
    titleText: String,
    versions: List<String>,
    currentVersion: String?
) : DialogWrapper(project) {

    private val versionList = JBList<String>()
    private var selectedVersion: String? = null

    init {
        title = titleText
        versionList.setListData(versions.toTypedArray())
        val initialIndex = currentVersion?.let { versions.indexOf(it) } ?: -1
        versionList.selectedIndex = if (initialIndex >= 0) initialIndex else 0
        selectedVersion = versionList.selectedValue
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(420, 320)

        versionList.addListSelectionListener {
            selectedVersion = versionList.selectedValue
        }

        panel.add(JBScrollPane(versionList), BorderLayout.CENTER)
        return panel
    }

    fun getSelectedVersion(): String? = selectedVersion
}
