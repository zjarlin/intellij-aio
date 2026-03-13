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
import git4idea.GitVcs
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
        NEED_LOGIN,
        LOADING,
        READY,
        AUTH_ERROR,
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
    private val accountActionButton = JButton("Login to Gitee")
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
            UiMode.NEED_LOGIN,
            "Not logged in. Click 'Login to Gitee' to configure your account and access token."
        )
    }

    fun getComponent(): JComponent = panel

    fun getPreferredFocusedComponent(): JComponent {
        return when {
            !settings.isConfigured() -> accountActionButton
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
        return settings.isConfigured() &&
            !loading &&
            repoList.selectedValue != null &&
            directoryField.text.trim().isNotEmpty()
    }

    fun validateAll(): List<ValidationInfo> {
        val validations = mutableListOf<ValidationInfo>()

        if (!settings.isConfigured()) {
            validations += ValidationInfo("Please log in to Gitee first.", accountActionButton)
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

        if (!settings.isConfigured()) {
            repositoriesLoaded = false
            allRepos = emptyList()
            repoListModel.clear()
            accountLabel.text = "Account: not configured"
            repoList.emptyText.text = "Configure access token first"
            updateUiState(
                UiMode.NEED_LOGIN,
                "Not logged in. Click 'Login to Gitee' to configure your account and access token."
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
                val apiClient = GiteeApiClient(settings.accessToken)
                val user = apiClient.getUser()
                val accountName = settings.username.ifBlank { user.login }
                val repos = loadAllRepositories(apiClient)

                if (settings.username.isBlank() && user.login.isNotBlank()) {
                    settings.username = user.login
                }

                ApplicationManager.getApplication().invokeLater {
                    repositoriesLoaded = true
                    loading = false
                    allRepos = repos
                    accountLabel.text = "Account: $accountName"
                    updateUiState(UiMode.READY, "Loaded ${repos.size} repositories")
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
                    if (e.statusCode == 401 || e.statusCode == 403) {
                        updateUiState(
                            UiMode.AUTH_ERROR,
                            "Authentication failed. Click 'Login to Gitee' to update your account or access token."
                        )
                    } else {
                        updateUiState(UiMode.ERROR, e.message ?: "Failed to load repositories")
                    }
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

    private fun loadAllRepositories(apiClient: GiteeApiClient): List<Repo> {
        val repos = mutableListOf<Repo>()
        var page = 1

        while (true) {
            val pageRepos = apiClient.getRepos(page = page, perPage = 100)
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
            UiMode.NEED_LOGIN -> {
                accountActionButton.text = "Login to Gitee"
                reloadButton.isEnabled = false
                searchField.isEnabled = false
                repoList.isEnabled = false
            }

            UiMode.LOADING -> {
                accountActionButton.text = "Manage Account"
                reloadButton.isEnabled = false
                searchField.isEnabled = false
                repoList.isEnabled = false
            }

            UiMode.READY -> {
                accountActionButton.text = "Manage Account"
                reloadButton.isEnabled = true
                searchField.isEnabled = true
                repoList.isEnabled = true
            }

            UiMode.AUTH_ERROR -> {
                accountActionButton.text = "Login to Gitee"
                reloadButton.isEnabled = true
                searchField.isEnabled = false
                repoList.isEnabled = false
            }

            UiMode.ERROR -> {
                accountActionButton.text = "Manage Account"
                reloadButton.isEnabled = true
                searchField.isEnabled = false
                repoList.isEnabled = false
            }
        }
    }

    private fun cloneRepository(repo: Repo, targetDir: File, listener: CheckoutProvider.Listener?) {
        val parentProject = project
        val fullUrl = repo.cloneUrl.ifBlank { repo.htmlUrl }

        com.intellij.openapi.progress.ProgressManager.getInstance().run(
            object : com.intellij.openapi.progress.Task.Backgroundable(parentProject, "Cloning from Gitee...", true) {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    try {
                        indicator.text = "Cloning ${repo.fullName}..."

                        val process = ProcessBuilder("git", "clone", fullUrl, targetDir.absolutePath)
                            .start()

                        val exitCode = process.waitFor()
                        val errorOutput = process.errorStream.bufferedReader().readText().trim()

                        if (exitCode != 0) {
                            notify(
                                parentProject,
                                if (errorOutput.isNotBlank()) errorOutput else "Failed to clone repository (exit code: $exitCode)",
                                NotificationType.ERROR
                            )
                            return
                        }

                        com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath(targetDir.absolutePath)

                        notify(
                            parentProject,
                            "Successfully cloned to ${targetDir.absolutePath}",
                            NotificationType.INFORMATION
                        )

                        ApplicationManager.getApplication().invokeLater {
                            if (listener != null) {
                                listener.directoryCheckedOut(targetDir, GitVcs.getKey())
                                listener.checkoutCompleted()
                            } else {
                                promptToOpenProject(parentProject, targetDir, repo.name)
                            }
                        }
                    } catch (e: Exception) {
                        notify(parentProject, "Failed to clone: ${e.message}", NotificationType.ERROR)
                    }
                }
            }
        )
    }

    private fun promptToOpenProject(project: Project?, targetDir: File, repoName: String) {
        val openProject = Messages.showYesNoDialog(
            project,
            "Clone completed. Open project '$repoName'?",
            "Clone Complete",
            Messages.getQuestionIcon()
        ) == Messages.YES

        if (openProject) {
            ApplicationManager.getApplication().invokeLater {
                ProjectManager.getInstance().loadAndOpenProject(targetDir.absolutePath)
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
