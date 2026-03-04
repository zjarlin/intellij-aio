package site.addzero.cloudfile.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.PopupHandler
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import site.addzero.cloudfile.cache.OfflineCacheManager
import site.addzero.cloudfile.settings.CloudFileSettings
import site.addzero.cloudfile.settings.ProjectHostingSettings
import site.addzero.cloudfile.share.ShareLinkManager
import site.addzero.cloudfile.storage.StorageService
import site.addzero.cloudfile.storage.StorageServiceFactory
import site.addzero.cloudfile.sync.CloudFileSyncService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.tree.*

/**
 * Browser panel for viewing and managing hosted files in cloud storage
 */
class HostedFilesBrowserPanel(private val project: Project) : SimpleToolWindowPanel(false, true) {

    private val settings = CloudFileSettings.getInstance()
    private val projectSettings = ProjectHostingSettings.getInstance(project)
    private val syncService = CloudFileSyncService.getInstance(project)
    private val cacheManager = OfflineCacheManager.getInstance(project)
    private val shareManager = ShareLinkManager.getInstance(project)

    private lateinit var tree: Tree
    private lateinit var treeModel: DefaultTreeModel
    private lateinit var namespaceCombo: ComboBox<String>
    private lateinit var statusLabel: JLabel
    private var storageService: StorageService? = null

    init {
        createUI()
        refreshData()
    }

    private fun createUI() {
        // Toolbar panel
        val toolbarPanel = JPanel(BorderLayout())

        // Namespace selector
        val namespacePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        namespacePanel.add(JLabel("Namespace:"))
        namespaceCombo = ComboBox()
        namespaceCombo.addItem("Global")
        namespaceCombo.addItem(project.name)
        namespaceCombo.addActionListener { refreshData() }
        namespacePanel.add(namespaceCombo)

        // Refresh button
        val refreshBtn = JButton("Refresh", AllIcons.Actions.Refresh)
        refreshBtn.addActionListener { refreshData() }
        namespacePanel.add(refreshBtn)

        // Status label
        statusLabel = JLabel()
        namespacePanel.add(Box.createHorizontalStrut(20))
        namespacePanel.add(statusLabel)

        toolbarPanel.add(namespacePanel, BorderLayout.WEST)

        // Action toolbar
        val actionGroup = DefaultActionGroup()
        actionGroup.add(DownloadSelectedAction())
        actionGroup.add(DeleteSelectedAction())
        actionGroup.addSeparator()
        actionGroup.add(ShareConfigurationAction())
        actionGroup.add(ImportShareAction())
        actionGroup.addSeparator()
        actionGroup.add(ViewCacheAction())

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("CloudFileBrowser", actionGroup, true)
        actionToolbar.targetComponent = this
        toolbarPanel.add(actionToolbar.component, BorderLayout.EAST)

        setToolbar(toolbarPanel)

        // Tree view
        val rootNode = DefaultMutableTreeNode("Cloud Storage")
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel)
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION

        // Double click to download and open
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    downloadAndOpenSelected()
                }
            }
        })

        // Popup menu
        PopupHandler.installPopupMenu(tree, actionGroup, "CloudFileBrowserPopup")

        // Speed search
        TreeSpeedSearch(tree)

        setContent(JBScrollPane(tree))
    }

    fun refreshData() {
        val namespace = when (namespaceCombo.selectedItem) {
            "Global" -> null
            else -> namespaceCombo.selectedItem as? String ?: project.name
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Loading hosted files", true) {
                override fun run(indicator: ProgressIndicator) {
                    loadRemoteFiles(namespace, indicator)
                }
            }
        )
    }

    /**
     * Preview files that will be synced based on current rules
     */
    fun previewLocalRules(): List<String> {
        val rules = projectSettings.getEffectiveRules(project)
        val files = mutableListOf<String>()

        rules.forEach { rule ->
            when (rule.type) {
                CloudFileSettings.HostingRule.RuleType.FILE -> files.add("[File] ${rule.pattern}")
                CloudFileSettings.HostingRule.RuleType.DIRECTORY -> files.add("[Dir] ${rule.pattern}")
                CloudFileSettings.HostingRule.RuleType.GLOB -> files.add("[Glob] ${rule.pattern}")
            }
        }

        return files
    }

    private fun loadRemoteFiles(namespace: String?, indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        indicator.text = "Connecting to cloud storage..."

        try {
            val service = initializeStorage() ?: run {
                SwingUtilities.invokeLater {
                    updateStatus("Storage not configured", true)
                }
                return
            }

            indicator.text = "Listing files..."
            val files = service.listFiles("", namespace)

            SwingUtilities.invokeLater {
                updateTree(files, namespace)
                updateStatus("${files.size} files in ${namespace ?: "global"}")
            }
        } catch (e: Exception) {
            // Fall back to cache view
            val cachedFiles = cacheManager.getCachedFiles(namespace)
            SwingUtilities.invokeLater {
                updateTreeFromCache(cachedFiles, namespace)
                updateStatus("Offline mode - ${cachedFiles.size} cached files", true)
            }
        }
    }

    private fun updateTree(files: List<StorageService.RemoteFileInfo>, namespace: String?) {
        val root = DefaultMutableTreeNode("Cloud Storage")

        // Group files by directory
        val dirMap = mutableMapOf<String, DefaultMutableTreeNode>()
        dirMap[""] = root

        files.sortedBy { it.key }.forEach { file ->
            val pathParts = file.key.split("/")
            var currentPath = ""
            var parentNode = root

            pathParts.dropLast(1).forEach { part ->
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                val node = dirMap.getOrPut(currentPath) {
                    val newNode = DefaultMutableTreeNode(CloudFileNode.DirNode(part, currentPath))
                    parentNode.add(newNode)
                    newNode
                }
                parentNode = node
            }

            val fileName = pathParts.last()
            parentNode.add(DefaultMutableTreeNode(
                CloudFileNode.FileNode(
                    name = fileName,
                    fullPath = file.key,
                    size = file.size,
                    lastModified = file.lastModified,
                    etag = file.etag,
                    isCached = cacheManager.isCached(file.key, namespace)
                )
            ))
        }

        treeModel.setRoot(root)
        tree.expandPath(TreePath(root.path))
    }

    private fun updateTreeFromCache(cachedFiles: List<OfflineCacheManager.CachedFileInfo>, namespace: String?) {
        val root = DefaultMutableTreeNode("Cloud Storage (Cached)")

        cachedFiles.sortedBy { it.relativePath }.forEach { file ->
            val node = CloudFileNode.FileNode(
                name = File(file.relativePath).name,
                fullPath = file.relativePath,
                size = file.size,
                lastModified = file.cachedTimestamp,
                etag = file.contentHash.take(8),
                isCached = true
            )
            root.add(DefaultMutableTreeNode(node))
        }

        treeModel.setRoot(root)
    }

    private fun downloadAndOpenSelected() {
        val selectedNodes = tree.selectionPaths?.mapNotNull {
            (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? CloudFileNode.FileNode
        } ?: return

        if (selectedNodes.isEmpty()) return

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Downloading files", true) {
                override fun run(indicator: ProgressIndicator) {
                    val namespace = getCurrentNamespace()
                    val service = initializeStorage()

                    selectedNodes.forEachIndexed { index, node ->
                        indicator.fraction = (index + 1).toDouble() / selectedNodes.size
                        indicator.text = "Downloading ${node.name}..."

                        val localFile = File(project.basePath, node.fullPath)

                        // Check cache first
                        val cachedContent = cacheManager.getCachedFile(node.fullPath, namespace)
                        if (cachedContent != null && service == null) {
                            // Use cache when offline
                            localFile.parentFile?.mkdirs()
                            localFile.writeBytes(cachedContent)
                        } else if (service != null) {
                            // Download from cloud
                            service.downloadFile(node.fullPath, localFile, namespace)
                        }

                        // Open in editor
                        SwingUtilities.invokeLater {
                            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile)?.let { vFile ->
                                FileEditorManager.getInstance(project).openFile(vFile, true)
                            }
                        }
                    }
                }
            }
        )
    }

    private fun downloadSelected() {
        downloadAndOpenSelected()
    }

    private fun deleteSelected() {
        val selectedNodes = tree.selectionPaths?.mapNotNull {
            (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? CloudFileNode.FileNode
        } ?: return

        val result = JOptionPane.showConfirmDialog(
            this,
            "Delete ${selectedNodes.size} files from cloud storage?\nThis cannot be undone!",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result != JOptionPane.YES_OPTION) return

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Deleting files", true) {
                override fun run(indicator: ProgressIndicator) {
                    val namespace = getCurrentNamespace()
                    val service = initializeStorage() ?: return

                    selectedNodes.forEach { node ->
                        service.deleteFile(node.fullPath, namespace)
                    }

                    refreshData()
                }
            }
        )
    }

    private fun showShareDialog() {
        val dialog = ShareDialog(project, shareManager)
        if (dialog.showAndGet()) {
            val packageData = dialog.getSharePackage()
            val base64 = shareManager.exportToBase64(packageData)

            // Show in dialog with copy button
            val textArea = JTextArea(base64, 5, 60)
            textArea.lineWrap = true
            textArea.isEditable = false

            JOptionPane.showMessageDialog(
                this,
                JScrollPane(textArea),
                "Share Link (Copy this text)",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun showImportDialog() {
        val input = JOptionPane.showInputDialog(
            this,
            "Paste share link or Base64 string:",
            "Import Share",
            JOptionPane.QUESTION_MESSAGE
        ) ?: return

        val packageData = shareManager.importFromBase64(input)
            ?: shareManager.importFromJson(input)
            ?: run {
                JOptionPane.showMessageDialog(
                    this,
                    "Invalid share format",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
                return
            }

        val dialog = ImportDialog(project, packageData, shareManager)
        dialog.show()
    }

    private fun showCacheStatus() {
        val stats = cacheManager.getCacheStats()
        val message = """
            Cache Statistics:

            Total Files: ${stats.totalFiles}
            Total Size: %.2f MB
            Synced Files: ${stats.syncedFiles}
            Pending Uploads: ${stats.pendingUploads}

            Cache Directory:
            ${stats.cacheDirPath}
        """.trimIndent().format(stats.totalSizeMB())

        JOptionPane.showMessageDialog(
            this,
            message,
            "Cache Status",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun initializeStorage(): StorageService? {
        if (storageService != null) return storageService

        return try {
            storageService = when (settings.state.provider) {
                CloudFileSettings.StorageProvider.S3 -> {
                    val accessKey = settings.getS3AccessKey() ?: return null
                    val secretKey = settings.getS3SecretKey() ?: return null
                    StorageServiceFactory.createS3Service(
                        endpoint = settings.state.s3Endpoint,
                        region = settings.state.s3Region,
                        bucket = settings.state.s3Bucket,
                        accessKey = accessKey,
                        secretKey = secretKey
                    )
                }
                CloudFileSettings.StorageProvider.OSS -> {
                    val accessKeyId = settings.getOssAccessKeyId() ?: return null
                    val accessKeySecret = settings.getOssAccessKeySecret() ?: return null
                    StorageServiceFactory.createOssService(
                        endpoint = settings.state.ossEndpoint,
                        bucket = settings.state.ossBucket,
                        accessKeyId = accessKeyId,
                        accessKeySecret = accessKeySecret
                    )
                }
            }
            storageService
        } catch (e: Exception) {
            null
        }
    }

    private fun getCurrentNamespace(): String? {
        return when (namespaceCombo.selectedItem) {
            "Global" -> null
            else -> namespaceCombo.selectedItem as? String ?: project.name
        }
    }

    private fun updateStatus(message: String, isWarning: Boolean = false) {
        statusLabel.text = message
        statusLabel.icon = if (isWarning) AllIcons.General.Warning else AllIcons.Actions.Commit
    }

    // Tree node wrapper
    sealed class CloudFileNode {
        abstract val name: String

        data class DirNode(
            override val name: String,
            val path: String
        ) : CloudFileNode() {
            override fun toString(): String = name
        }

        data class FileNode(
            override val name: String,
            val fullPath: String,
            val size: Long,
            val lastModified: Long,
            val etag: String,
            val isCached: Boolean
        ) : CloudFileNode() {
            override fun toString(): String {
                val sizeStr = formatSize(size)
                val cacheIndicator = if (isCached) "[Cached] " else ""
                return "$cacheIndicator$name ($sizeStr)"
            }

            private fun formatSize(size: Long): String {
                return when {
                    size < 1024 -> "$size B"
                    size < 1024 * 1024 -> "${size / 1024} KB"
                    else -> "${size / (1024 * 1024)} MB"
                }
            }
        }
    }

    // Actions
    inner class DownloadSelectedAction : AnAction(
        "Download",
        "Download selected files to local project",
        AllIcons.Actions.Download
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            downloadSelected()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = tree.selectionCount > 0
        }
    }

    inner class DeleteSelectedAction : AnAction(
        "Delete from Cloud",
        "Delete selected files from cloud storage",
        AllIcons.Actions.GC
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            deleteSelected()
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = tree.selectionCount > 0
        }
    }

    inner class ShareConfigurationAction : AnAction(
        "Share...",
        "Generate share link for configuration and files",
        AllIcons.Actions.Share
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            showShareDialog()
        }
    }

    inner class ImportShareAction : AnAction(
        "Import...",
        "Import configuration from share link",
        AllIcons.Actions.Install
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            showImportDialog()
        }
    }

    inner class ViewCacheAction : AnAction(
        "Cache Status",
        "View local cache statistics",
        AllIcons.Actions.Show
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            showCacheStatus()
        }
    }
}
