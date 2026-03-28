package site.addzero.openprojecteverywhere.search

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Processor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import org.jetbrains.plugins.github.authentication.AuthorizationType
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.GHLoginSource
import site.addzero.openprojecteverywhere.OpenProjectEverywhereBundle
import site.addzero.openprojecteverywhere.service.OpenProjectEverywhereSearchService
import site.addzero.openprojecteverywhere.settings.OpenProjectEverywhereConfigurable
import site.addzero.openprojecteverywhere.settings.OpenProjectEverywhereSettings
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class OpenProjectEverywhereContributor(
    private val project: Project?
) : SearchEverywhereContributor<SearchItem> {

    private val searchService = OpenProjectEverywhereSearchService.getInstance()
    private val settings: OpenProjectEverywhereSettings
        get() = OpenProjectEverywhereSettings.getInstance()
    @Volatile
    private var selectedScope: SearchScope = SearchScope.OWN
    private val scopeActions = CopyOnWriteArrayList<AnAction>()

    override fun getSearchProviderId(): String = "OpenProjectEverywhere"

    override fun getGroupName(): String = OpenProjectEverywhereBundle.message("search.group.name")

    override fun getSortWeight(): Int = 280

    override fun showInFindResults(): Boolean = false

    override fun processSelectedItem(selected: SearchItem, modifiers: Int, searchText: String): Boolean {
        return when (selected) {
            is SearchItem.Hint -> {
                when (selected.action) {
                    HintAction.OPEN_SETTINGS ->
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, OpenProjectEverywhereConfigurable::class.java)

                    HintAction.OPEN_GITHUB_SETTINGS ->
                        openGithubSettings()

                    HintAction.NONE -> Unit
                }
                true
            }

            is SearchItem.Project -> {
                openProject(selected)
                true
            }
        }
    }

    override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in SearchItem>) {
        val scope = selectedScope
        val items = searchService.search(pattern, progressIndicator, scope, project)
        for (item in items) {
            if (progressIndicator.isCanceled) {
                return
            }
            if (!consumer.process(item)) {
                return
            }
        }
    }

    override fun getElementsRenderer(): ListCellRenderer<in SearchItem> {
        return OpenProjectEverywhereRenderer()
    }

    override fun getDataForItem(element: SearchItem, dataId: String): Any? = null

    override fun isShownInSeparateTab(): Boolean = true

    override fun isMultiSelectionSupported(): Boolean = true

    override fun getActions(onChanged: Runnable): List<AnAction> {
        if (scopeActions.isEmpty()) {
            scopeActions += availableScopes().map { scope ->
                ScopeToggleAction(scope, onChanged)
            }
        }
        return scopeActions
    }

    private fun availableScopes(): List<SearchScope> {
        return buildList {
            add(SearchScope.OWN)
            if (settings.githubEnabled) add(SearchScope.GITHUB_PUBLIC)
            if (settings.gitlabEnabled) add(SearchScope.GITLAB_PUBLIC)
            if (settings.giteeEnabled) add(SearchScope.GITEE_PUBLIC)
            if (settings.customEnabled) add(SearchScope.CUSTOM_PUBLIC)
        }
    }

    private fun openProject(item: SearchItem.Project) {
        val localPath = resolveOpenableLocalPath(item)
        if (localPath != null) {
            runCatching {
                ProjectUtil.openOrImport(localPath, project, true)
            }.onFailure {
                notify(OpenProjectEverywhereBundle.message("notification.open.failed", it.message ?: "unknown"), NotificationType.ERROR)
            }
            return
        }

        val rootPath = settings.localProjectsRoot
        val cloneRoot = File(rootPath)
        if (rootPath.isBlank() || (!cloneRoot.exists() && !cloneRoot.mkdirs())) {
            notify(OpenProjectEverywhereBundle.message("notification.clone.invalidRoot"), NotificationType.WARNING)
            ShowSettingsUtil.getInstance().showSettingsDialog(project, OpenProjectEverywhereConfigurable::class.java)
            return
        }

        val cloneUrl = item.cloneUrl
        if (cloneUrl.isNullOrBlank()) {
            notify(OpenProjectEverywhereBundle.message("notification.clone.urlMissing"), NotificationType.ERROR)
            return
        }

        val targetParent = item.cloneParentRelativePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(cloneRoot, it) }
            ?: cloneRoot
        if (!targetParent.exists() && !targetParent.mkdirs()) {
            notify(OpenProjectEverywhereBundle.message("notification.clone.invalidRoot"), NotificationType.ERROR)
            return
        }

        val targetDir = File(targetParent, item.directoryName)
        if (targetDir.exists()) {
            if (isSameRemoteRepository(targetDir, item)) {
                notify(OpenProjectEverywhereBundle.message("notification.clone.targetExists.sameRepo"), NotificationType.INFORMATION)
                openExistingProject(targetDir)
                return
            }
            showCloneConflictDialog(item, cloneUrl, targetParent, targetDir)
            return
        }

        cloneIntoDirectory(item, cloneUrl, targetParent, item.directoryName)
    }

    private fun resolveOpenableLocalPath(item: SearchItem.Project): String? {
        val localPath = item.localPath?.takeIf { File(it).exists() } ?: return null
        if (item.kind == SearchResultKind.LOCAL) {
            return localPath
        }
        return localPath.takeIf { isSameRemoteRepository(File(it), item) }
    }

    private fun openExistingProject(targetDir: File) {
        runCatching {
            ProjectUtil.openOrImport(targetDir.absolutePath, project, true)
        }.onFailure {
            notify(OpenProjectEverywhereBundle.message("notification.open.failed", it.message ?: "unknown"), NotificationType.ERROR)
        }
    }

    private fun showCloneConflictDialog(
        item: SearchItem.Project,
        cloneUrl: String,
        targetParent: File,
        targetDir: File
    ) {
        val remoteLabel = item.cloneUrl ?: item.webUrl ?: item.title
        val canOpenExisting = targetDir.isDirectory
        val options = if (canOpenExisting) {
            arrayOf(
                OpenProjectEverywhereBundle.message("dialog.clone.conflict.openExisting"),
                OpenProjectEverywhereBundle.message("dialog.clone.conflict.chooseNew"),
                Messages.getCancelButton()
            )
        } else {
            arrayOf(
                OpenProjectEverywhereBundle.message("dialog.clone.conflict.chooseNew"),
                Messages.getCancelButton()
            )
        }
        val choice = Messages.showDialog(
            project,
            OpenProjectEverywhereBundle.message(
                "dialog.clone.conflict.message",
                targetDir.absolutePath,
                item.title,
                remoteLabel
            ),
            OpenProjectEverywhereBundle.message("dialog.clone.conflict.title"),
            options,
            if (canOpenExisting) 1 else 0,
            Messages.getWarningIcon()
        )
        if (canOpenExisting && choice == 0) {
            openExistingProject(targetDir)
            return
        }
        val chooseNewIndex = if (canOpenExisting) 1 else 0
        if (choice == chooseNewIndex) {
            promptAndCloneToNewDirectory(item, cloneUrl, targetParent)
        }
    }

    private fun promptAndCloneToNewDirectory(
        item: SearchItem.Project,
        cloneUrl: String,
        targetParent: File
    ) {
        val suggestedName = suggestDirectoryName(item, targetParent)
        val directoryName = Messages.showInputDialog(
            project,
            OpenProjectEverywhereBundle.message("dialog.clone.rename.message", targetParent.absolutePath),
            OpenProjectEverywhereBundle.message("dialog.clone.rename.title"),
            Messages.getQuestionIcon(),
            suggestedName,
            object : InputValidatorEx {
                override fun checkInput(inputString: String?): Boolean {
                    return validationError(inputString) == null
                }

                override fun canClose(inputString: String?): Boolean {
                    return validationError(inputString) == null
                }

                override fun getErrorText(inputString: String?): String? {
                    return validationError(inputString)
                }

                private fun validationError(inputString: String?): String? {
                    val candidate = inputString?.trim().orEmpty()
                    if (candidate.isBlank()) {
                        return OpenProjectEverywhereBundle.message("dialog.clone.rename.error.blank")
                    }
                    if (candidate == "." || candidate == "..") {
                        return OpenProjectEverywhereBundle.message("dialog.clone.rename.error.reserved")
                    }
                    if (INVALID_DIRECTORY_NAME_CHARS.containsMatchIn(candidate)) {
                        return OpenProjectEverywhereBundle.message("dialog.clone.rename.error.invalid")
                    }
                    if (File(targetParent, candidate).exists()) {
                        return OpenProjectEverywhereBundle.message("dialog.clone.rename.error.exists")
                    }
                    return null
                }
            }
        )?.trim()
            ?: return

        cloneIntoDirectory(item, cloneUrl, targetParent, directoryName)
    }

    private fun suggestDirectoryName(item: SearchItem.Project, targetParent: File): String {
        val qualifier = sanitizeDirectoryName(item.titleQualifier.orEmpty())
            .takeIf { it.isNotBlank() && !it.equals(item.directoryName, ignoreCase = true) }
        val candidates = buildList {
            if (qualifier != null) {
                add("${item.directoryName}-$qualifier")
            }
            add("${item.directoryName}-copy")
        }
        candidates.firstOrNull { candidate ->
            !File(targetParent, candidate).exists()
        }?.let { return it }

        var index = 2
        while (File(targetParent, "${item.directoryName}-$index").exists()) {
            index += 1
        }
        return "${item.directoryName}-$index"
    }

    private fun sanitizeDirectoryName(value: String): String {
        return value
            .replace('/', '-')
            .replace(INVALID_DIRECTORY_NAME_CHARS, "_")
            .trim()
    }

    private fun isSameRemoteRepository(directory: File, item: SearchItem.Project): Boolean {
        val existingRemote = readOriginRemoteUrl(directory) ?: return false
        val existingKey = normalizeRemoteUrl(existingRemote) ?: return false
        val candidateKeys = buildSet {
            normalizeRemoteUrl(item.cloneUrl)?.let(::add)
            normalizeRemoteUrl(item.webUrl)?.let(::add)
        }
        return existingKey in candidateKeys
    }

    private fun readOriginRemoteUrl(directory: File): String? {
        if (!directory.isDirectory || !File(directory, ".git").exists()) {
            return null
        }
        val directoryVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory) ?: return null
        val effectiveProject = project ?: ProjectManager.getInstance().defaultProject
        val handler = GitLineHandler(effectiveProject, directoryVf, GitCommand.CONFIG)
        handler.addParameters("--get", "remote.origin.url")
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) {
            return null
        }
        return result.output
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeRemoteUrl(url: String?): String? {
        val trimmed = url
            ?.trim()
            ?.removeSuffix("/")
            ?.removeSuffix(".git")
            ?: return null
        if (trimmed.isBlank()) {
            return null
        }
        if ("://" !in trimmed && '@' in trimmed && ':' in trimmed) {
            val authority = trimmed.substringBefore(':')
            val path = trimmed.substringAfter(':').trim('/')
            val host = authority.substringAfter('@', authority)
            return "$host/$path".lowercase()
        }
        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri?.host != null) {
            val authority = buildString {
                append(uri.host.lowercase())
                if (uri.port > 0) {
                    append(":")
                    append(uri.port)
                }
            }
            val path = uri.path.orEmpty().trim('/').removeSuffix(".git")
            if (path.isNotBlank()) {
                return "$authority/$path".lowercase()
            }
        }
        return trimmed
            .removePrefix("ssh://")
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("git://")
            .trim('/')
            .lowercase()
    }

    private fun cloneIntoDirectory(
        item: SearchItem.Project,
        cloneUrl: String,
        targetParent: File,
        directoryName: String
    ) {
        val targetDir = File(targetParent, directoryName)
        val parentDirVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetParent)
        if (parentDirVf == null) {
            notify(OpenProjectEverywhereBundle.message("notification.clone.invalidRoot"), NotificationType.ERROR)
            return
        }

        notify(
            OpenProjectEverywhereBundle.message("notification.clone.start", item.title, targetDir.absolutePath),
            NotificationType.INFORMATION
        )

        val effectiveProject = project ?: ProjectManager.getInstance().defaultProject
        val listener = object : CheckoutProvider.Listener {
            override fun directoryCheckedOut(directory: File, vcs: com.intellij.openapi.vcs.VcsKey) {
                ProjectUtil.openOrImport(directory.absolutePath, project, true)
            }

            override fun checkoutCompleted() {
            }
        }

        runCatching {
            GitCheckoutProvider.clone(
                effectiveProject,
                Git.getInstance(),
                listener,
                parentDirVf,
                cloneUrl,
                directoryName,
                targetParent.absolutePath
            )
        }.onFailure {
            notify(OpenProjectEverywhereBundle.message("notification.clone.failed", it.message ?: "unknown"), NotificationType.ERROR)
        }
    }

    private fun notify(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("OpenProjectEverywhereNotifications")
            .createNotification(content, type)
            .notify(project)
    }

    private fun openGithubSettings() {
        settings.setGithubSecret("")
        val effectiveProject = project
            ?: ProjectManager.getInstance().openProjects.firstOrNull()
            ?: ProjectManager.getInstance().defaultProject
        GHAccountsUtil.requestNewAccount(
            GithubServerPath.DEFAULT_SERVER,
            null,
            effectiveProject,
            null,
            AuthorizationType.OAUTH,
            GHLoginSource.SETTINGS
        )
        searchService.invalidateCaches()
    }

    companion object {
        private val INVALID_DIRECTORY_NAME_CHARS = Regex("""[\\/:*?"<>|]""")
    }

    private inner class ScopeToggleAction(
        private val scope: SearchScope,
        private val onChanged: Runnable
    ) : com.intellij.openapi.actionSystem.ToggleAction(scope.actionName(settings)), DumbAware {

        override fun isSelected(e: AnActionEvent): Boolean = selectedScope == scope

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (!state || selectedScope == scope) {
                return
            }
            selectedScope = scope
            templatePresentation.text = scope.actionName(settings)
            ApplicationManager.getApplication().invokeLater {
                if (selectedScope == scope) {
                    onChanged.run()
                }
            }
        }

        override fun update(e: AnActionEvent) {
            super.update(e)
            e.presentation.text = scope.actionName(settings)
        }
    }
}

class OpenProjectEverywhereContributorFactory : SearchEverywhereContributorFactory<SearchItem> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<SearchItem> {
        return OpenProjectEverywhereContributor(initEvent.project)
    }
}

private class OpenProjectEverywhereRenderer : ListCellRenderer<SearchItem> {

    override fun getListCellRendererComponent(
        list: JList<out SearchItem>,
        value: SearchItem?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val panel = JPanel(BorderLayout(JBUI.scale(8), 0))
        panel.border = JBUI.Borders.empty(4, 8)
        panel.background = if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus) else UIUtil.getListBackground()

        val iconLabel = JLabel()
        val textPanel = JPanel()
        textPanel.layout = javax.swing.BoxLayout(textPanel, javax.swing.BoxLayout.Y_AXIS)
        textPanel.isOpaque = false

        when (value) {
            is SearchItem.Project -> {
                iconLabel.icon = projectIcon(value.kind)

                val title = SimpleColoredComponent()
                title.append(value.title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                title.append("  [${value.categoryLabel}]", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)

                val subtitle = SimpleColoredComponent()
                subtitle.append(value.subtitle, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                value.description?.takeIf { it.isNotBlank() }?.let {
                    subtitle.append("  $it", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }

                textPanel.add(title)
                textPanel.add(subtitle)
            }

            is SearchItem.Hint -> {
                iconLabel.icon = if (value.isError) com.intellij.icons.AllIcons.General.Warning else com.intellij.icons.AllIcons.General.Settings

                val title = SimpleColoredComponent()
                val attributes = if (value.isError) {
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED)
                } else {
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                }
                title.append(value.title, attributes)

                val subtitle = SimpleColoredComponent()
                value.description?.takeIf { it.isNotBlank() }?.let {
                    subtitle.append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                textPanel.add(title)
                textPanel.add(subtitle)
            }

            null -> Unit
        }

        panel.add(iconLabel, BorderLayout.WEST)
        panel.add(textPanel, BorderLayout.CENTER)
        return panel
    }

    private fun projectIcon(kind: SearchResultKind): Icon {
        return when (kind) {
            SearchResultKind.LOCAL -> com.intellij.icons.AllIcons.Nodes.Folder
            SearchResultKind.GITHUB,
            SearchResultKind.GITLAB,
            SearchResultKind.GITEE,
            SearchResultKind.CUSTOM -> com.intellij.icons.AllIcons.Actions.CheckOut
        }
    }
}
