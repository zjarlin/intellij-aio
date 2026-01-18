package site.addzero.maven.search

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralSearchUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class VersionSelectionDialog(
    project: Project,
    private val artifact: MavenArtifact
) : DialogWrapper(project) {

    private val versionList = JBList<String>()
    private var selectedVersion: String? = null

    init {
        title = "Select Version - ${artifact.groupId}:${artifact.artifactId}"
        init()
        loadVersions()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(400, 300)

        versionList.addListSelectionListener {
            selectedVersion = versionList.selectedValue
        }

        panel.add(JBScrollPane(versionList), BorderLayout.CENTER)
        return panel
    }

    private fun loadVersions() {
        val versions = runCatching {
            val sortedDescending = MavenCentralSearchUtil.searchAllVersions(
              artifact.groupId,artifact.artifactId,50
            ).map { it.latestVersion }.distinct().sortedDescending()
          sortedDescending
        }.getOrElse {
            listOf(artifact.latestVersion)
        }

        versionList.setListData(versions.toTypedArray())
        versionList.selectedIndex = 0
        selectedVersion = versions.firstOrNull()
    }

    fun getSelectedVersion(): String? = selectedVersion
}
