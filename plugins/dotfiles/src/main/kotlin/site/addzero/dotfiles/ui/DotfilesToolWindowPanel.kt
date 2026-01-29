package site.addzero.dotfiles.ui

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import site.addzero.dotfiles.manifest.EntryMode
import site.addzero.dotfiles.manifest.EntryScope
import site.addzero.dotfiles.manifest.ManifestCodec
import site.addzero.dotfiles.manifest.ManifestEntry
import site.addzero.dotfiles.manifest.ManifestRepository
import site.addzero.dotfiles.sync.DotfilesProjectSyncService
import site.addzero.dotfiles.sync.DotfilesSyncStateService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.DefaultCellEditor
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTable

class DotfilesToolWindowPanel(
    private val project: Project,
) : JPanel(BorderLayout()) {

    private val manifestRepository = ManifestRepository()
    private val manifestCodec = ManifestCodec()
    private val syncService = project.getService(DotfilesProjectSyncService::class.java)
    private val stateService = DotfilesSyncStateService.getInstance()

    private val tableModel = ManifestTableModel()
    private val entryTable = JTable(tableModel)
    private val jsonArea = JBTextArea()

    init {
        configureTable()
        add(buildToolbar(), BorderLayout.NORTH)
        add(buildContent(), BorderLayout.CENTER)
        syncService.start()
        reloadList()
    }

    private fun buildToolbar(): JPanel {
        val addUserButton = JButton("Add User Entry")
        val addProjectButton = JButton("Add Project Entry")
        val removeButton = JButton("Remove")
        val reloadButton = JButton("Reload")
        val syncButton = JButton("Sync Now")
        val applyTableButton = JButton("Apply Table")
        val applyJsonButton = JButton("Apply JSON")
        val copyJsonButton = JButton("Copy JSON")

        addUserButton.addActionListener { addEntry(EntryMode.USER) }
        addProjectButton.addActionListener { addEntry(EntryMode.PROJECT) }
        removeButton.addActionListener { removeSelectedEntry() }
        reloadButton.addActionListener { reloadList() }
        syncButton.addActionListener { syncService.syncNow() }
        applyTableButton.addActionListener { applyTable() }
        applyJsonButton.addActionListener { applyJson() }
        copyJsonButton.addActionListener { jsonArea.copy() }

        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(addUserButton)
            add(addProjectButton)
            add(removeButton)
            add(reloadButton)
            add(syncButton)
            add(applyTableButton)
            add(applyJsonButton)
            add(copyJsonButton)
        }
    }

    private fun buildContent(): JBSplitter {
        jsonArea.tabSize = 2
        val tableScroll = JBScrollPane(entryTable)
        val jsonScroll = JBScrollPane(jsonArea)
        return JBSplitter(true, 0.6f).apply {
            firstComponent = tableScroll
            secondComponent = jsonScroll
        }
    }

    private fun configureTable() {
        entryTable.setShowGrid(true)
        val scopeEditor = JComboBox(arrayOf("PROJECT_ROOT", "USER_HOME"))
        val modeEditor = JComboBox(arrayOf("USER", "PROJECT"))
        entryTable.columnModel.getColumn(2).cellEditor = DefaultCellEditor(scopeEditor)
        entryTable.columnModel.getColumn(3).cellEditor = DefaultCellEditor(modeEditor)
    }

    private fun reloadList() {
        val userEntries = stateService.state.userManifest.entries
        val projectEntries = stateService.state.projectManifests[project.name]?.entries ?: emptyList()
        tableModel.setRows(userEntries + projectEntries)
        val userManifest = manifestRepository.loadUserManifest()
        jsonArea.text = manifestCodec.encode(userManifest)
    }

    private fun addEntry(mode: EntryMode) {
        val scope = EntryScope.PROJECT_ROOT
        val base = scopeBase(scope) ?: return
        val root = VfsUtil.findFile(base, true) ?: return
        val descriptor = FileChooserDescriptor(true, true, false, false, false, false)
            .withRoots(root)
        val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return
        val chosenPath = Paths.get(chosen.path)
        if (!chosenPath.startsWith(base)) {
            Messages.showErrorDialog(project, "Please choose a path under the current project.", "Dotfiles")
            return
        }
        val relative = base.relativize(chosenPath).toString().replace('\\', '/')
        val id = relative.replace('/', '_')

        val isIgnored = FileStatusManager.getInstance(project).getStatus(chosen) == FileStatus.IGNORED
        val includeIgnored = if (isIgnored) {
            val choice = Messages.showYesNoDialog(
                project,
                "This path is ignored by VCS. Back it up anyway?",
                "Ignored File",
                null
            )
            choice == Messages.YES
        } else {
            false
        }

        val entry = ManifestEntry(
            id = id,
            path = relative,
            scope = scope,
            mode = mode,
            includeIgnored = includeIgnored,
            excludeFromGit = true,
        )
        syncService.addEntry(entry, toUserManifest = mode == EntryMode.USER)
        reloadList()
    }

    private fun scopeBase(scope: EntryScope): Path? = when (scope) {
        EntryScope.USER_HOME -> Paths.get(System.getProperty("user.home"))
        EntryScope.PROJECT_ROOT -> project.basePath?.let { Paths.get(it) }
    }

    private fun removeSelectedEntry() {
        val row = entryTable.selectedRow
        if (row < 0) return
        val entry = tableModel.getRows()[row]
        if (entry.id == "dotfiles-manifest") {
            Messages.showWarningDialog(project, "The manifest entry cannot be removed.", "Dotfiles")
            return
        }
        val choice = Messages.showYesNoDialog(
            project,
            "Remove ${entry.id} from dotfiles backup?",
            "Remove Entry",
            null
        )
        if (choice != Messages.YES) return
        syncService.removeEntry(entry)
        reloadList()
    }

    private fun applyJson() {
        val parsed = manifestCodec.decodeOrNull(jsonArea.text)
        if (parsed == null) {
            Messages.showErrorDialog(project, "Invalid JSON manifest.", "Dotfiles")
            return
        }
        manifestRepository.saveUserManifest(parsed)
        syncService.reloadFromManifest()
        reloadList()
    }

    private fun applyTable() {
        val rows = tableModel.getRows()
        val userEntries = rows.filter { it.mode == EntryMode.USER.name }
        val projectEntries = rows.filter { it.mode == EntryMode.PROJECT.name }
        syncService.replaceUserEntries(userEntries)
        syncService.replaceProjectEntries(projectEntries)
        reloadList()
    }
}
