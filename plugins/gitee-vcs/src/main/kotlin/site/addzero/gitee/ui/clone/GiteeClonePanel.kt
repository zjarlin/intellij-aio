package site.addzero.gitee.ui.clone

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import site.addzero.gitee.api.GiteeApiClient
import site.addzero.gitee.api.GiteeApiException
import site.addzero.gitee.api.model.Repo
import site.addzero.gitee.settings.GiteeConfigurable
import site.addzero.gitee.settings.GiteeSettings
import java.awt.BorderLayout
import java.io.File
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Shared clone UI for the action dialog and welcome-screen clone tab.
 */
class GiteeClonePanel(
    private val project: Project?,
    private val onStateChanged: () -> Unit = {}
) {
    private enum class UiMode {
        NEED_ACCOUNT,
        LOADING,
        READY,
        ERROR
    }

    private val settings = GiteeSettings.getInstance()
    private val repoListModel = DefaultListModel<Repo>()
    private val repoList = JBList(repoListModel)
    private val searchField = SearchTextField()
    private val directoryField = TextFieldWithBrowseButton()
    private val accountLabel = JBLabel("Account: -")
    private val statusLabel = JBLabel("Loading repositories...")
    private val reloadButton = JButton("Reload")
    private val accountActionButton = JButton("Set Gitee Account")
    private val panel = JPanel(BorderLayout(JBUI.scale(0), JBUI.scale(8)))

    private var allRepos: List<Repo> = emptyList()
    private var loading = false
    private var repositoriesLoaded = false

    init {
        repoList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        repoList.cellRenderer = object : ColoredListCellRenderer<Repo>() {
            override fun customizeCellRenderer(
                list: JList<out Repo>,
                value: Repo?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value == null) {
                    return
                }

                append(value.fullName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append(if (value.isPrivate) "  private" else "  public", SimpleTextAttributes.GRAYED_ATTRIBUTES)

                val description = value.description?.takeIf { it.isNotBlank() }
                if (description != null) {
                    append("  $description", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }
        repoList.emptyText.text = "No repositories loaded"
        repoList.addListSelectionListener {
            onStateChanged()
        }

        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                applyFilter()
            }

            override fun removeUpdate(e: DocumentEvent) {
                applyFilter()
            }

            override fun changedUpdate(e: DocumentEvent) {
                applyFilter()
            }
        })

        directoryField.addBrowseFolderListener(
            "Clone To",
            "Select the parent directory for the cloned repository",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        directoryField.text = File(System.getProperty("user.home")).absolutePath
        directoryField.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                onStateChanged()
            }

            override fun removeUpdate(e: DocumentEvent) {
                onStateChanged()
            }

            override fun changedUpdate(e: DocumentEvent) {
                onStateChanged()
            }
        })

        reloadButton.addActionListener {
            loadRepositories(force = true)
        }
        accountActionButton.addActionListener {
            openSettingsAndRefresh()
        }

        val toolbar = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(searchField, BorderLayout.CENTER)
            add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                add(reloadButton, BorderLayout.WEST)
                add(accountActionButton, BorderLayout.EAST)
            }, BorderLayout.EAST)
        }

        val listPanel = JBScrollPane(repoList).apply {
            preferredSize = JBUI.size(520, 320)
        }

        val topPanel = FormBuilder.createFormBuilder()
            .addComponent(accountLabel)
            .addVerticalGap(JBUI.scale(4))
            .addComponent(statusLabel)
            .addVerticalGap(JBUI.scale(8))
            .addComponent(toolbar)
            .addVerticalGap(JBUI.scale(8))
            .panel

        panel.border = JBUI.Borders.empty(8)
        panel.add(topPanel, BorderLayout.NORTH)
        panel.add(listPanel, BorderLayout.CENTER)
        panel.add(
            FormBuilder.createFormBuilder()
                .addLabeledComponent("Parent Directory:", directoryField)
                .panel,
            BorderLayout.SOUTH
        )

        updateUiState(
            UiMode.NEED_ACCOUNT,
            "Set your Gitee account name first. Git username/password will be requested by Git during clone when needed."
        )
    }

    fun getComponent(): JComponent = panel

    fun getPreferredFocusedComponent(): JComponent {
        return when {
            !settings.hasCloneAccountConfigured() -> accountActionButton
            repoListModel.isEmpty -> searchField
            else -> repoList
        }
    }

    fun loadRepositoriesIfNeeded() {
        if (!repositoriesLoaded && !loading) {
            loadRepositories(force = false)
        }
    }

    fun isCloneEnabled(): Boolean {
        return settings.hasCloneAccountConfigured() &&
            !loading &&
            repoList.selectedValue != null &&
            directoryField.text.trim().isNotEmpty()
    }

    fun validateAll(): List<ValidationInfo> {
        val validations = mutableListOf<ValidationInfo>()

        if (!settings.hasCloneAccountConfigured()) {
            validations += ValidationInfo("Please set your Gitee account first.", accountActionButton)
        }

        if (directoryField.text.trim().isEmpty()) {
            validations += ValidationInfo("Please choose a parent directory.", directoryField)
        }

        if (repoList.selectedValue == null) {
            validations += ValidationInfo("Please choose a repository to clone.", repoList)
        }

        val selectedRepo = repoList.selectedValue
        if (selectedRepo != null && directoryField.text.trim().isNotEmpty()) {
            val targetDir = File(directoryField.text.trim(), selectedRepo.name)
            if (targetDir.exists()) {
                validations += ValidationInfo("Target directory '${selectedRepo.name}' already exists.", directoryField)
            }
        }

        return validations
    }

    fun doClone(listener: CheckoutProvider.Listener?) {
        val repo = repoList.selectedValue ?: return
        val parentDir = File(directoryField.text.trim())
        val targetDir = File(parentDir, repo.name)

        cloneRepository(repo, targetDir, listener)
    }

    private fun loadRepositories(force: Boolean) {
        if (loading) {
            return
        }
        if (!force && repositoriesLoaded) {
            return
        }

        if (!settings.hasCloneAccountConfigured()) {
            repositoriesLoaded = false
            allRepos = emptyList()
            repoListModel.clear()
            accountLabel.text = "Account: not configured"
            repoList.emptyText.text = "Set Gitee account first"
            updateUiState(
                UiMode.NEED_ACCOUNT,
                "Set your Gitee account name first. Git username/password will be requested by Git during clone when needed."
            )
            onStateChanged()
            return
        }

        loading = true
        repoList.emptyText.text = "Loading repositories..."
        updateUiState(UiMode.LOADING, "Loading repositories from Gitee...")
        onStateChanged()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val accountName = settings.username.trim()
                val apiClient = GiteeApiClient()
                val repos = loadAllPublicRepositories(apiClient, accountName)

                ApplicationManager.getApplication().invokeLater {
                    repositoriesLoaded = true
                    loading = false
                    allRepos = repos
                    accountLabel.text = "Account: $accountName"
                    updateUiState(
                        UiMode.READY,
                        "Loaded ${repos.size} public repositories. Git will ask for credentials during clone when needed."
                    )
                    applyFilter()
                    onStateChanged()
                }
            } catch (e: GiteeApiException) {
                ApplicationManager.getApplication().invokeLater {
                    repositoriesLoaded = false
                    loading = false
                    allRepos = emptyList()
                    repoListModel.clear()
                    accountLabel.text = "Account: ${settings.username.ifBlank { "-" }}"
                    repoList.emptyText.text = "Failed to load repositories"
                    updateUiState(UiMode.ERROR, e.message ?: "Failed to load repositories")
                    onStateChanged()
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    repositoriesLoaded = false
                    loading = false
                    allRepos = emptyList()
                    repoListModel.clear()
                    accountLabel.text = "Account: ${settings.username.ifBlank { "-" }}"
                    repoList.emptyText.text = "Failed to load repositories"
                    updateUiState(UiMode.ERROR, "Failed to load repositories: ${e.message}")
                    onStateChanged()
                }
            }
        }
    }

    private fun loadAllPublicRepositories(apiClient: GiteeApiClient, username: String): List<Repo> {
        val repos = mutableListOf<Repo>()
        var page = 1

        while (true) {
            val pageRepos = apiClient.getPublicRepos(username = username, page = page, perPage = 100)
            if (pageRepos.isEmpty()) {
                break
            }

            repos += pageRepos
            if (pageRepos.size < 100) {
                break
            }

            page += 1
        }

        return repos.sortedBy { it.fullName.lowercase() }
    }

    private fun applyFilter() {
        val currentSelection = repoList.selectedValue?.fullName
        val keyword = searchField.text.trim().lowercase()

        repoListModel.clear()
        allRepos
            .asSequence()
            .filter { repo ->
                if (keyword.isBlank()) {
                    true
                } else {
                    repo.fullName.lowercase().contains(keyword) ||
                        repo.name.lowercase().contains(keyword) ||
                        (repo.description?.lowercase()?.contains(keyword) == true)
                }
            }
            .forEach(repoListModel::addElement)

        val selectedIndex = (0 until repoListModel.size())
            .firstOrNull { repoListModel.getElementAt(it).fullName == currentSelection }
            ?: if (repoListModel.isEmpty) -1 else 0
        if (selectedIndex >= 0) {
            repoList.selectedIndex = selectedIndex
        }

        if (repoListModel.isEmpty) {
            repoList.emptyText.text = if (allRepos.isEmpty()) "No repositories found" else "No repositories match the current filter"
        }

        onStateChanged()
    }

    private fun openSettingsAndRefresh() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, GiteeConfigurable::class.java)
        repositoriesLoaded = false
        loadRepositories(force = true)
    }

    private fun updateUiState(mode: UiMode, message: String) {
        statusLabel.text = message

        when (mode) {
            UiMode.NEED_ACCOUNT -> {
                accountActionButton.text = "Set Gitee Account"
                reloadButton.isEnabled = false
                searchField.isEnabled = false
                repoList.isEnabled = false
            }

            UiMode.LOADING -> {
                accountActionButton.text = "Edit Account"
                reloadButton.isEnabled = false
                searchField.isEnabled = false
                repoList.isEnabled = false
            }

            UiMode.READY -> {
                accountActionButton.text = "Edit Account"
                reloadButton.isEnabled = true
                searchField.isEnabled = true
                repoList.isEnabled = true
            }

            UiMode.ERROR -> {
                accountActionButton.text = "Edit Account"
                reloadButton.isEnabled = true
                searchField.isEnabled = false
                repoList.isEnabled = false
            }
        }
    }

    private fun cloneRepository(repo: Repo, targetDir: File, listener: CheckoutProvider.Listener?) {
        val parentProject = project
        val fullUrl = repo.cloneUrl.ifBlank { repo.htmlUrl }
        val parentDir = targetDir.parentFile
        if (parentDir == null) {
            notify(parentProject, "Invalid clone target directory", NotificationType.ERROR)
            return
        }

        val parentDirVf = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .refreshAndFindFileByIoFile(parentDir)
        if (parentDirVf == null) {
            notify(parentProject, "Could not access clone target directory: ${parentDir.absolutePath}", NotificationType.ERROR)
            return
        }

        val effectiveProject = parentProject ?: ProjectManager.getInstance().defaultProject
        val effectiveListener = listener ?: createOpenProjectListener(effectiveProject)

        GitCheckoutProvider.clone(
            effectiveProject,
            Git.getInstance(),
            effectiveListener,
            parentDirVf,
            fullUrl,
            repo.name,
            parentDir.absolutePath
        )
    }

    private fun createOpenProjectListener(project: Project): CheckoutProvider.Listener {
        return object : CheckoutProvider.Listener {
            override fun directoryCheckedOut(directory: File, vcs: com.intellij.openapi.vcs.VcsKey) {
                val openProject = Messages.showYesNoDialog(
                    project,
                    "Clone completed. Open project '${directory.name}'?",
                    "Clone Complete",
                    Messages.getQuestionIcon()
                ) == Messages.YES

                if (openProject) {
                    ApplicationManager.getApplication().invokeLater {
                        ProjectManager.getInstance().loadAndOpenProject(directory.absolutePath)
                    }
                }
            }

            override fun checkoutCompleted() {
            }
        }
    }

    private fun notify(project: Project?, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GiteeNotifications")
            .createNotification(message, type)
            .notify(project)
    }
}
