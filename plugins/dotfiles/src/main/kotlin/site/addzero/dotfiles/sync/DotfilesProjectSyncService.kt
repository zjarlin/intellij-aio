package site.addzero.dotfiles.sync

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import site.addzero.dotfiles.manifest.DotfilesPaths
import site.addzero.dotfiles.manifest.EntryMode
import site.addzero.dotfiles.manifest.EntryScope
import site.addzero.dotfiles.manifest.ManifestEntry
import site.addzero.dotfiles.manifest.ManifestRepository
import site.addzero.dotfiles.sync.DotfilesSyncStateService.EntryState
import site.addzero.dotfiles.sync.DotfilesSyncStateService.FileSnapshotState
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class DotfilesProjectSyncService(
    private val project: Project,
) {
    private val stateService = DotfilesSyncStateService.getInstance()
    private val manifestRepo = ManifestRepository()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    private val started = AtomicBoolean(false)

    private var lastAppliedUserAt: Long = 0L
    private var lastAppliedProjectAt: Long = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        refreshFromManifest()
        applyUserManifest(force = true)
        applyProjectManifest(force = true)
        startPolling()
        registerVfsListener()
    }

    fun syncNow() {
        syncFromLocal()
        applyUserManifest(force = true)
        applyProjectManifest(force = true)
    }

    fun reloadFromManifest() {
        refreshFromManifest()
        syncNow()
    }

    fun replaceUserEntries(entries: List<EntryState>) {
        val current = manifestRepo.loadUserManifest()
        val next = current.copy(entries = entries.map { it.toManifestEntry() })
        manifestRepo.saveUserManifest(next)
        refreshFromManifest()
        syncNow()
    }

    fun replaceProjectEntries(entries: List<EntryState>) {
        val state = stateService.state
        val projectState = state.projectManifests.getOrPut(project.name) {
            DotfilesSyncStateService.ManifestState()
        }
        projectState.entries = entries.toMutableList()
        projectState.updatedAt = System.currentTimeMillis()
        syncNow()
    }

    fun removeEntry(entry: EntryState) {
        if (entry.id == "dotfiles-manifest") return
        if (entry.mode == EntryMode.USER.name) {
            val current = manifestRepo.loadUserManifest()
            val next = current.copy(entries = current.entries.filterNot { it.id == entry.id })
            manifestRepo.saveUserManifest(next)
            refreshFromManifest()
        } else {
            val state = stateService.state
            val projectState = state.projectManifests[project.name] ?: return
            projectState.entries = projectState.entries.filterNot { it.id == entry.id }.toMutableList()
            projectState.updatedAt = System.currentTimeMillis()
        }
        syncNow()
    }

    fun addEntry(entry: ManifestEntry, toUserManifest: Boolean) {
        if (toUserManifest) {
            val current = manifestRepo.loadUserManifest()
            if (current.entries.any { it.id == entry.id }) return
            val next = current.copy(entries = current.entries + entry)
            manifestRepo.saveUserManifest(next)
            refreshFromManifest()
        } else {
            val state = stateService.state
            val projectState = state.projectManifests.getOrPut(project.name) {
                DotfilesSyncStateService.ManifestState()
            }
            if (projectState.entries.any { it.id == entry.id }) return
            projectState.entries.add(entry.toEntryState())
            projectState.updatedAt = System.currentTimeMillis()
        }
        applyUserManifest(force = true)
        applyProjectManifest(force = true)
    }

    fun refreshFromManifest() {
        val manifest = manifestRepo.loadUserManifest()
        val state = stateService.state
        val existing = state.userManifest.entries.associateBy { it.id }
        val merged = mutableListOf<EntryState>()
        val withManifest = manifest.entries + implicitManifestEntry()
        withManifest.forEach { entry ->
            val current = existing[entry.id]
            merged.add(entry.toEntryState(current?.files ?: mutableListOf()))
        }
        state.userManifest.entries = merged.toMutableList()
        state.userManifest.updatedAt = System.currentTimeMillis()
        ensureManifestFileBackedUp()
    }

    private fun ensureManifestFileBackedUp() {
        val path = DotfilesPaths.userManifestPath()
        if (!Files.exists(path)) return
        val state = stateService.state
        val entry = state.userManifest.entries.firstOrNull { it.id == "dotfiles-manifest" } ?: return
        entry.files = snapshotFile(path, basePath = DotfilesPaths.userHome()).toMutableList()
    }

    private fun applyUserManifest(force: Boolean) {
        val userState = stateService.state.userManifest
        if (!force && userState.updatedAt <= lastAppliedUserAt) return
        userState.entries.forEach { applyEntry(it) }
        lastAppliedUserAt = userState.updatedAt
        notifyUser("User dotfiles loaded", "User-scoped dotfiles restored.")
    }

    private fun applyProjectManifest(force: Boolean) {
        val projectState = stateService.state.projectManifests[project.name] ?: return
        if (!force && projectState.updatedAt <= lastAppliedProjectAt) return
        projectState.entries.forEach { applyEntry(it) }
        lastAppliedProjectAt = projectState.updatedAt
        notifyUser("Project dotfiles loaded", "Project-scoped dotfiles restored.")
    }

    private fun startPolling() {
        alarm.addRequest(object : Runnable {
            override fun run() {
                applyUserManifest(force = false)
                applyProjectManifest(force = false)
                alarm.addRequest(this, 10_000)
            }
        }, 10_000)
    }

    private fun registerVfsListener() {
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                if (events.isEmpty()) return
                alarm.cancelAllRequests()
                alarm.addRequest({ syncFromLocal() }, 500)
            }
        })
    }

    private fun syncFromLocal() {
        val state = stateService.state
        state.userManifest.entries.forEach { entry ->
            entry.files = snapshotEntry(entry).toMutableList()
        }
        state.userManifest.updatedAt = System.currentTimeMillis()

        state.projectManifests[project.name]?.let { projectState ->
            projectState.entries.forEach { entry ->
                entry.files = snapshotEntry(entry).toMutableList()
            }
            projectState.updatedAt = System.currentTimeMillis()
        }
    }

    private fun applyEntry(entry: EntryState) {
        val basePath = resolveBasePath(entry) ?: return
        if (entry.excludeFromGit && entry.scope == EntryScope.PROJECT_ROOT.name) {
            writeGitExclude(entry.path)
        }
        entry.files.forEach { snapshot ->
            val target = basePath.resolve(snapshot.relativePath)
            Files.createDirectories(target.parent)
            Files.write(target, Base64.getDecoder().decode(snapshot.base64))
        }
    }

    private fun snapshotEntry(entry: EntryState): List<FileSnapshotState> {
        val basePath = resolveBasePath(entry) ?: return emptyList()
        val target = basePath.resolve(entry.path)
        if (!Files.exists(target)) return emptyList()
        return if (Files.isDirectory(target)) {
            snapshotDirectory(target, basePath)
        } else {
            snapshotFile(target, basePath)
        }
    }

    private fun snapshotDirectory(dir: Path, basePath: Path): List<FileSnapshotState> {
        val result = mutableListOf<FileSnapshotState>()
        Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { path ->
                if (shouldSkipIgnored(path)) return@forEach
                result.addAll(snapshotFile(path, basePath))
            }
        }
        return result
    }

    private fun snapshotFile(path: Path, basePath: Path): List<FileSnapshotState> {
        if (shouldSkipIgnored(path)) return emptyList()
        val bytes = Files.readAllBytes(path)
        val relative = basePath.relativize(path).toString().replace('\\', '/')
        return listOf(
            FileSnapshotState(
                relativePath = relative,
                base64 = Base64.getEncoder().encodeToString(bytes),
                isBinary = isBinary(bytes),
                modifiedAt = Files.getLastModifiedTime(path).toMillis(),
            )
        )
    }

    private fun shouldSkipIgnored(path: Path): Boolean {
        val entry = findEntryForPath(path) ?: return false
        if (entry.includeIgnored) return false
        if (entry.scope != EntryScope.PROJECT_ROOT.name) return false
        val vf = VirtualFileManager.getInstance().findFileByNioPath(path) ?: return false
        return FileStatusManager.getInstance(project).getStatus(vf) == FileStatus.IGNORED
    }

    private fun findEntryForPath(path: Path): EntryState? {
        val state = stateService.state
        val entries = state.userManifest.entries + (state.projectManifests[project.name]?.entries ?: emptyList())
        return entries.firstOrNull { entry ->
            val base = resolveBasePath(entry) ?: return@firstOrNull false
            val target = base.resolve(entry.path)
            path.startsWith(target)
        }
    }

    private fun resolveBasePath(entry: EntryState): Path? = when (entry.scope) {
        EntryScope.USER_HOME.name -> DotfilesPaths.userHome()
        EntryScope.PROJECT_ROOT.name -> project.basePath?.let { Paths.get(it) }
        else -> null
    }

    private fun writeGitExclude(relativePath: String) {
        val root = project.basePath?.let { Paths.get(it) } ?: return
        val exclude = root.resolve(".git").resolve("info").resolve("exclude")
        if (!Files.exists(exclude)) return
        val content = Files.readAllLines(exclude).toMutableList()
        val normalized = relativePath.trimEnd('/').replace('\\', '/')
        if (content.any { it.trim() == normalized }) return
        content.add(normalized)
        Files.write(exclude, content)
    }

    private fun notifyUser(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Dotfiles Notifications")
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun implicitManifestEntry(): ManifestEntry {
        return ManifestEntry(
            id = "dotfiles-manifest",
            path = "${DotfilesPaths.dirName}/${DotfilesPaths.manifestFileName}",
            scope = EntryScope.USER_HOME,
            mode = EntryMode.USER,
            includeIgnored = true,
            excludeFromGit = true,
        )
    }

    private fun ManifestEntry.toEntryState(files: MutableList<FileSnapshotState> = mutableListOf()): EntryState {
        return EntryState(
            id = id,
            path = path,
            scope = scope.name,
            mode = mode.name,
            includeIgnored = includeIgnored,
            excludeFromGit = excludeFromGit,
            files = files,
        )
    }

    private fun EntryState.toEntryState(files: MutableList<FileSnapshotState>): EntryState {
        this.files = files
        return this
    }

    private fun EntryState.toManifestEntry(): ManifestEntry {
        val scopeValue = runCatching { EntryScope.valueOf(scope) }.getOrNull() ?: EntryScope.PROJECT_ROOT
        val modeValue = runCatching { EntryMode.valueOf(mode) }.getOrNull() ?: EntryMode.USER
        return ManifestEntry(
            id = id,
            path = path,
            scope = scopeValue,
            mode = modeValue,
            includeIgnored = includeIgnored,
            excludeFromGit = excludeFromGit,
        )
    }

    private fun isBinary(bytes: ByteArray): Boolean = bytes.any { it.toInt() == 0 }
}
