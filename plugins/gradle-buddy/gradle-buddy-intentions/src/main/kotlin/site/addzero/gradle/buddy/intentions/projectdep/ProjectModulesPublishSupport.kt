package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.util.GradleConstants
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.migration.PublishCommandEntry
import site.addzero.gradle.buddy.migration.PublishCommandQueueService
import java.awt.datatransfer.StringSelection
import java.io.File

object ProjectModulesPublishSupport {

    data class PublishPlan(
        val repoRoot: File,
        val selectedModules: List<ProjectModuleResolver.ModuleInfo>,
        val orderedModules: List<ProjectModuleResolver.ModuleInfo>,
        val skippedExpandedModules: List<ProjectModuleResolver.ModuleInfo>,
        val taskNames: List<String>,
        val command: String,
        val queueEntries: List<PublishCommandEntry>
    )

    sealed interface PlanResult {
        data class Success(val plan: PublishPlan) : PlanResult

        data class Failure(
            val title: String,
            val content: String,
            val type: NotificationType
        ) : PlanResult
    }

    private data class SelectedModule(
        val module: ProjectModuleResolver.ModuleInfo,
        val expanded: Boolean
    )

    private sealed interface SelectionResult {
        data class Success(
            val rootDir: VirtualFile,
            val selectedModules: List<SelectedModule>,
            val allModules: List<ProjectModuleResolver.ModuleInfo>
        ) : SelectionResult

        data object None : SelectionResult

        data object MixedRoots : SelectionResult
    }

    fun isAvailable(project: Project, selectedFiles: Array<VirtualFile>): Boolean {
        return resolveSelection(project, selectedFiles.toList()) is SelectionResult.Success
    }

    fun buildPlan(project: Project, selectedFiles: Array<VirtualFile>): PlanResult {
        return when (val selection = resolveSelection(project, selectedFiles.toList())) {
            SelectionResult.None -> {
                PlanResult.Failure(
                    title = GradleBuddyBundle.message("action.publish.modules.with.dependencies.none.title"),
                    content = GradleBuddyBundle.message("action.publish.modules.with.dependencies.none.content"),
                    type = NotificationType.WARNING
                )
            }

            SelectionResult.MixedRoots -> {
                PlanResult.Failure(
                    title = GradleBuddyBundle.message("action.publish.modules.with.dependencies.mixed.roots.title"),
                    content = GradleBuddyBundle.message("action.publish.modules.with.dependencies.mixed.roots.content"),
                    type = NotificationType.WARNING
                )
            }

            is SelectionResult.Success -> {
                val skippedExpanded = selection.selectedModules
                    .filter { it.expanded && isLikelyNonPublishModule(it.module) }
                    .map { it.module }

                val selectedModules = selection.selectedModules
                    .filterNot { it.expanded && isLikelyNonPublishModule(it.module) }
                    .map { it.module }

                if (selectedModules.isEmpty()) {
                    return PlanResult.Failure(
                        title = GradleBuddyBundle.message("action.publish.modules.with.dependencies.no.publishable.title"),
                        content = GradleBuddyBundle.message(
                            "action.publish.modules.with.dependencies.no.publishable.content",
                            summarizeModules(skippedExpanded)
                        ),
                        type = NotificationType.WARNING
                    )
                }

                val modulesByPath = selection.allModules.associateBy { it.path }
                val modulesByAccessor = selection.allModules.associateBy {
                    it.typeSafeAccessor.removePrefix("projects.")
                }
                val dependencyCache = mutableMapOf<String, Set<String>>()
                val visiting = linkedSetOf<String>()
                val visited = linkedSetOf<String>()
                val orderedPaths = mutableListOf<String>()

                fun visit(modulePath: String) {
                    if (modulePath in visited || modulePath in visiting) {
                        return
                    }

                    val module = modulesByPath[modulePath] ?: return
                    visiting += modulePath
                    dependenciesOf(module, modulesByPath, modulesByAccessor, dependencyCache).forEach(::visit)
                    visiting -= modulePath
                    visited += modulePath
                    orderedPaths += modulePath
                }

                selectedModules.forEach { visit(it.path) }

                val orderedModules = orderedPaths.mapNotNull(modulesByPath::get)
                val taskNames = orderedModules.map { "${it.path}:publishToMavenCentral" }
                val repoRoot = File(selection.rootDir.path)
                val queueEntries = orderedModules.map { module ->
                    val taskName = "${module.path}:publishToMavenCentral"
                    PublishCommandEntry(
                        moduleName = module.path.substringAfterLast(':'),
                        modulePath = module.path,
                        rootPath = repoRoot.absolutePath,
                        command = "./gradlew $taskName"
                    )
                }

                PlanResult.Success(
                    PublishPlan(
                        repoRoot = repoRoot,
                        selectedModules = selectedModules,
                        orderedModules = orderedModules,
                        skippedExpandedModules = skippedExpanded,
                        taskNames = taskNames,
                        command = "./gradlew ${taskNames.joinToString(separator = " ")}",
                        queueEntries = queueEntries
                    )
                )
            }
        }
    }

    fun handlePlanResult(project: Project, result: PlanResult) {
        when (result) {
            is PlanResult.Failure -> {
                notify(project, result.title, result.content, result.type)
            }

            is PlanResult.Success -> {
                execute(project, result.plan)
            }
        }
    }

    private fun execute(project: Project, plan: PublishPlan) {
        val taskSettings = ExternalSystemTaskExecutionSettings().apply {
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            externalProjectPath = plan.repoRoot.absolutePath
            taskNames = plan.taskNames
        }

        try {
            ExternalSystemUtil.runTask(
                taskSettings,
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GradleConstants.SYSTEM_ID
            )
            showStartedNotification(project, plan)
        } catch (t: Throwable) {
            notify(
                project = project,
                title = GradleBuddyBundle.message("action.publish.modules.with.dependencies.failed.title"),
                content = GradleBuddyBundle.message(
                    "action.publish.modules.with.dependencies.failed.content",
                    plan.repoRoot.absolutePath,
                    plan.command,
                    t.message ?: t.javaClass.simpleName
                ),
                type = NotificationType.ERROR
            )
        }
    }

    private fun showStartedNotification(project: Project, plan: PublishPlan) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(
                GradleBuddyBundle.message("action.publish.modules.with.dependencies.started.title"),
                GradleBuddyBundle.message(
                    "action.publish.modules.with.dependencies.started.content",
                    plan.repoRoot.absolutePath,
                    summarizeModules(plan.selectedModules),
                    plan.orderedModules.size,
                    skippedSummary(plan.skippedExpandedModules),
                    plan.command
                ),
                NotificationType.INFORMATION
            )

        notification.addAction(object : AnAction(
            GradleBuddyBundle.message("action.publish.modules.with.dependencies.copy.command")
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                CopyPasteManager.getInstance().setContents(StringSelection(plan.command))
                notify(
                    project = project,
                    title = GradleBuddyBundle.message("action.publish.modules.with.dependencies.command.copied.title"),
                    content = GradleBuddyBundle.message(
                        "action.publish.modules.with.dependencies.command.copied.content",
                        plan.command
                    ),
                    type = NotificationType.INFORMATION
                )
            }
        })

        notification.addAction(object : AnAction(
            GradleBuddyBundle.message("action.publish.modules.with.dependencies.enqueue.commands")
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val added = project.getService(PublishCommandQueueService::class.java)
                    .addAll(plan.queueEntries)
                notify(
                    project = project,
                    title = GradleBuddyBundle.message("action.publish.modules.with.dependencies.command.enqueued.title"),
                    content = GradleBuddyBundle.message(
                        "action.publish.modules.with.dependencies.command.enqueued.content",
                        added
                    ),
                    type = NotificationType.INFORMATION
                )
            }
        })

        notification.notify(project)
    }

    private fun resolveSelection(project: Project, selectedFiles: List<VirtualFile>): SelectionResult {
        if (selectedFiles.isEmpty()) {
            return SelectionResult.None
        }

        val allModules = ProjectModuleResolver.scanModules(project)
        if (allModules.isEmpty()) {
            return SelectionResult.None
        }

        val collected = linkedMapOf<String, SelectedModule>()
        val roots = linkedSetOf<String>()

        selectedFiles.forEach { file ->
            val rootDir = ProjectModuleResolver.findOwningRoot(project, file) ?: return@forEach
            val sameRootModules = allModules.filter { it.rootDir.path == rootDir.path }
            if (sameRootModules.isEmpty()) {
                return@forEach
            }

            val resolvedModules = resolveModulesForSelection(file, sameRootModules)
            if (resolvedModules.isEmpty()) {
                return@forEach
            }

            roots += rootDir.path
            resolvedModules.forEach { selected ->
                val existing = collected[selected.module.path]
                if (existing == null || existing.expanded && !selected.expanded) {
                    collected[selected.module.path] = selected
                }
            }
        }

        if (collected.isEmpty()) {
            return SelectionResult.None
        }
        if (roots.size != 1) {
            return SelectionResult.MixedRoots
        }

        val rootPath = roots.first()
        val rootDir = allModules.firstOrNull { it.rootDir.path == rootPath }?.rootDir ?: return SelectionResult.None
        val sameRootModules = allModules.filter { it.rootDir.path == rootPath }
        return SelectionResult.Success(
            rootDir = rootDir,
            selectedModules = collected.values.toList(),
            allModules = sameRootModules
        )
    }

    private fun resolveModulesForSelection(
        file: VirtualFile,
        modules: List<ProjectModuleResolver.ModuleInfo>
    ): List<SelectedModule> {
        if (modules.isEmpty()) {
            return emptyList()
        }

        val exactMatch = when {
            file.isDirectory -> modules.firstOrNull { it.buildFile.parent.path == file.path }
            else -> modules.firstOrNull { it.buildFile.path == file.path }
        }
        if (exactMatch != null) {
            return listOf(SelectedModule(exactMatch, expanded = false))
        }

        val containingModule = modules
            .filter { isPathInside(it.buildFile.parent.path, file.path) }
            .maxByOrNull { it.buildFile.parent.path.length }
        if (containingModule != null) {
            return listOf(SelectedModule(containingModule, expanded = false))
        }

        if (!file.isDirectory) {
            return emptyList()
        }

        return modules
            .filter { isPathInside(file.path, it.buildFile.parent.path) }
            .sortedBy { it.path }
            .map { SelectedModule(it, expanded = true) }
    }

    private fun dependenciesOf(
        module: ProjectModuleResolver.ModuleInfo,
        modulesByPath: Map<String, ProjectModuleResolver.ModuleInfo>,
        modulesByAccessor: Map<String, ProjectModuleResolver.ModuleInfo>,
        cache: MutableMap<String, Set<String>>
    ): Set<String> {
        return cache.getOrPut(module.path) {
            val content = runCatching { VfsUtilCore.loadText(module.buildFile) }.getOrDefault("")
            val result = linkedSetOf<String>()

            PROJECT_DEPENDENCY_PATTERNS.forEach { pattern ->
                pattern.findAll(content).forEach { match ->
                    val configName = match.groupValues[1]
                    if (!isPublishRelevantConfiguration(configName)) {
                        return@forEach
                    }

                    val modulePath = match.groupValues.drop(2).firstOrNull { it.isNotBlank() } ?: return@forEach
                    if (modulePath in modulesByPath) {
                        result += modulePath
                    }
                }
            }

            TYPESAFE_PROJECT_PATTERN.findAll(content).forEach { match ->
                val configName = match.groupValues[1]
                if (!isPublishRelevantConfiguration(configName)) {
                    return@forEach
                }

                val accessor = match.groupValues[2].removePrefix("projects.")
                val moduleInfo = modulesByAccessor[accessor]
                if (moduleInfo != null) {
                    result += moduleInfo.path
                }
            }

            result
        }
    }

    private fun isPublishRelevantConfiguration(name: String): Boolean {
        val normalized = name.trim().lowercase()
        if (normalized.isBlank()) {
            return false
        }
        if ("test" in normalized || "fixture" in normalized) {
            return false
        }

        return normalized == "api" ||
            normalized == "implementation" ||
            normalized == "compileonly" ||
            normalized == "runtimeonly" ||
            normalized == "ksp" ||
            normalized == "kapt" ||
            normalized == "annotationprocessor" ||
            normalized.endsWith("api") ||
            normalized.endsWith("implementation") ||
            normalized.endsWith("compileonly") ||
            normalized.endsWith("runtimeonly") ||
            normalized.endsWith("ksp") ||
            normalized.endsWith("kapt") ||
            normalized.endsWith("annotationprocessor")
    }

    private fun isLikelyNonPublishModule(module: ProjectModuleResolver.ModuleInfo): Boolean {
        val name = module.path.substringAfterLast(':').lowercase()
        return NON_PUBLISH_MODULE_MARKERS.any { marker ->
            name == marker || name.contains("-$marker") || name.contains("${marker}-")
        }
    }

    private fun summarizeModules(modules: List<ProjectModuleResolver.ModuleInfo>): String {
        if (modules.isEmpty()) {
            return GradleBuddyBundle.message("action.publish.modules.with.dependencies.none.value")
        }

        val names = modules.map { it.path }.distinct()
        val preview = names.take(MODULE_SUMMARY_LIMIT).joinToString(separator = ", ")
        return if (names.size <= MODULE_SUMMARY_LIMIT) {
            preview
        } else {
            "$preview, +${names.size - MODULE_SUMMARY_LIMIT}"
        }
    }

    private fun skippedSummary(modules: List<ProjectModuleResolver.ModuleInfo>): String {
        return if (modules.isEmpty()) {
            GradleBuddyBundle.message("action.publish.modules.with.dependencies.none.value")
        } else {
            summarizeModules(modules)
        }
    }

    private fun isPathInside(parentPath: String, childPath: String): Boolean {
        val normalizedParent = parentPath.trimEnd('/')
        val normalizedChild = childPath.trimEnd('/')
        return normalizedChild == normalizedParent || normalizedChild.startsWith("$normalizedParent/")
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, type)
            .notify(project)
    }

    private val PROJECT_DEPENDENCY_PATTERNS = listOf(
        Regex(
            """([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*project\s*\(\s*["'](:[^"']+)["']\s*\)\s*\)"""
        ),
        Regex(
            """([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*project\s*\(\s*path\s*=\s*["'](:[^"']+)["'][^)]*\)\s*\)"""
        ),
        Regex(
            """([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*project\s*\(\s*mapOf\s*\((?s:.*?)["']path["']\s*to\s*["'](:[^"']+)["'](?s:.*?)\)\s*\)\s*\)"""
        ),
        Regex(
            """([A-Za-z_][A-Za-z0-9_]*)\s+project\s*\(\s*["'](:[^"']+)["']\s*\)"""
        )
    )

    private val TYPESAFE_PROJECT_PATTERN = Regex(
        """([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*(projects\.[A-Za-z0-9_.]+)\s*\)"""
    )

    private val NON_PUBLISH_MODULE_MARKERS = setOf(
        "smoke",
        "test",
        "tests",
        "sample",
        "samples",
        "example",
        "examples",
        "demo",
        "benchmark",
        "benchmarks"
    )

    private const val MODULE_SUMMARY_LIMIT = 5
}
