package site.addzero.gradle.buddy.intentions.projectdep

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.ui.SimpleListCellRenderer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 统计 Gradle 模块之间的反向 project 依赖，并提供跳转展示能力。
 */
object ProjectDependencyUsagesSupport {

    data class ModuleUsages(
        val module: ProjectModuleResolver.ModuleInfo,
        val usages: List<Usage>
    )

    data class Usage(
        val consumerModulePath: String,
        val targetModulePath: String,
        val file: VirtualFile,
        val offset: Int,
        val lineNumber: Int,
        val lineText: String,
        val dependencyText: String
    )

    fun findUsagesForBuildFile(project: Project, buildFile: VirtualFile): ModuleUsages? {
        val graph = dependencyGraph(project)
        val module = graph.modules.firstOrNull { it.buildFile.path == buildFile.path } ?: return null
        val usages = graph.usagesByTarget[ModuleKey(module.rootDir.path, module.path)].orEmpty()
        return ModuleUsages(module, usages)
    }

    fun presentUsages(
        project: Project,
        editor: Editor?,
        module: ProjectModuleResolver.ModuleInfo,
        usages: List<Usage>
    ) {
        when {
            usages.isEmpty() -> {
                notify(
                    project = project,
                    title = GradleBuddyBundle.message("line.marker.project.dependency.usages.none.title"),
                    content = GradleBuddyBundle.message(
                        "line.marker.project.dependency.usages.none.content",
                        module.path
                    ),
                    type = NotificationType.INFORMATION
                )
            }

            usages.size == 1 -> {
                navigate(project, usages.first())
            }

            else -> {
                val items = usages.map { usage ->
                    UsagePresentation(
                        usage = usage,
                        relativePath = toRelativePath(project, usage.file.path)
                    )
                }
                val popup = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(items)
                    .setTitle(
                        GradleBuddyBundle.message(
                            "line.marker.project.dependency.usages.popup.title",
                            module.path,
                            usages.size
                        )
                    )
                    .setRenderer(SimpleListCellRenderer.create("") { item ->
                        "${item.usage.consumerModulePath}  ${item.relativePath}:${item.usage.lineNumber}  ${item.usage.lineText}"
                    })
                    .setNamerForFiltering { item ->
                        "${item.usage.consumerModulePath} ${item.relativePath}:${item.usage.lineNumber} ${item.usage.lineText}"
                    }
                    .setItemChosenCallback { item ->
                        navigate(project, item.usage)
                    }
                    .createPopup()

                if (editor != null) {
                    popup.showInBestPositionFor(editor)
                } else {
                    popup.showInFocusCenter()
                }
            }
        }
    }

    private fun dependencyGraph(project: Project): DependencyGraph {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(
                buildDependencyGraph(project),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
    }

    private fun buildDependencyGraph(project: Project): DependencyGraph {
        val modules = ProjectModuleResolver.scanModules(project)
        val modulesByKey = modules.associateBy { ModuleKey(it.rootDir.path, it.path) }
        val modulesByRoot = modules.groupBy { it.rootDir.path }
        val usagesByTarget = linkedMapOf<ModuleKey, MutableList<Usage>>()
        val psiManager = PsiManager.getInstance(project)

        for (consumerModule in modules) {
            val psiFile = psiManager.findFile(consumerModule.buildFile) as? KtFile ?: continue
            val sameRootModules = modulesByRoot[consumerModule.rootDir.path].orEmpty()
            val modulesByAccessor = sameRootModules.associateBy { it.typeSafeAccessor }
            val usages = collectUsages(psiFile, consumerModule, modulesByAccessor)

            for (usage in usages) {
                val targetKey = ModuleKey(consumerModule.rootDir.path, usage.targetModulePath)
                val selfKey = ModuleKey(consumerModule.rootDir.path, consumerModule.path)
                if (targetKey == selfKey || targetKey !in modulesByKey) {
                    continue
                }
                usagesByTarget.getOrPut(targetKey) { mutableListOf() } += usage
            }
        }

        val sortedUsages = usagesByTarget.mapValues { (_, usages) ->
            usages.sortedWith(
                compareBy<Usage>({ it.consumerModulePath }, { it.lineNumber }, { it.offset })
            )
        }

        return DependencyGraph(
            modules = modules,
            usagesByTarget = sortedUsages
        )
    }

    private fun collectUsages(
        file: KtFile,
        consumerModule: ProjectModuleResolver.ModuleInfo,
        modulesByAccessor: Map<String, ProjectModuleResolver.ModuleInfo>
    ): List<Usage> {
        val result = mutableListOf<Usage>()
        val seen = linkedSetOf<UsageIdentity>()

        file.collectDescendantsOfType<KtCallExpression>()
            .mapNotNull { callExpression ->
                resolveDependencyCall(callExpression, modulesByAccessor)
            }
            .forEach { target ->
                appendUsage(file, consumerModule, target, seen, result)
            }

        collectTextFallbackTargets(file.text, modulesByAccessor)
            .forEach { target ->
                appendUsage(file, consumerModule, target, seen, result)
            }

        return result
    }

    private fun appendUsage(
        file: KtFile,
        consumerModule: ProjectModuleResolver.ModuleInfo,
        target: DependencyTarget,
        seen: MutableSet<UsageIdentity>,
        result: MutableList<Usage>
    ) {
        val identity = UsageIdentity(target.modulePath, target.offset)
        if (!seen.add(identity)) {
            return
        }

        val virtualFile = file.virtualFile ?: return
        result += createUsage(
            content = file.text,
            file = virtualFile,
            consumerModulePath = consumerModule.path,
            target = target
        )
    }

    private fun resolveDependencyCall(
        dependencyCall: KtCallExpression,
        modulesByAccessor: Map<String, ProjectModuleResolver.ModuleInfo>
    ): DependencyTarget? {
        if (dependencyCall.valueArguments.size != 1 || !isInsideDependenciesBlock(dependencyCall)) {
            return null
        }

        val argumentExpression = dependencyCall.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
        return when (argumentExpression) {
            is KtCallExpression -> resolveProjectCall(dependencyCall, argumentExpression)
            is KtDotQualifiedExpression -> resolveProjectAccessor(dependencyCall, argumentExpression, modulesByAccessor)
            else -> null
        }
    }

    private fun resolveProjectCall(
        dependencyCall: KtCallExpression,
        projectCall: KtCallExpression
    ): DependencyTarget? {
        if (projectCall.calleeExpression?.text != "project") {
            return null
        }

        val stringExpression = projectCall.valueArguments.singleOrNull()?.getArgumentExpression()
            as? KtStringTemplateExpression
            ?: return null
        val modulePath = extractLiteralString(stringExpression)
            ?.trim()
            ?.takeIf { it.startsWith(":") && it != ":" }
            ?: return null

        return DependencyTarget(
            modulePath = modulePath,
            offset = dependencyCall.textRange.startOffset,
            dependencyText = projectCall.text
        )
    }

    private fun resolveProjectAccessor(
        dependencyCall: KtCallExpression,
        expression: KtDotQualifiedExpression,
        modulesByAccessor: Map<String, ProjectModuleResolver.ModuleInfo>
    ): DependencyTarget? {
        val topExpression = findTopDotExpression(expression)
        val accessor = topExpression.text.trim()
        if (!PROJECTS_ACCESSOR_REGEX.matches(accessor)) {
            return null
        }
        val module = modulesByAccessor[accessor] ?: return null

        return DependencyTarget(
            modulePath = module.path,
            offset = dependencyCall.textRange.startOffset,
            dependencyText = accessor
        )
    }

    private fun collectTextFallbackTargets(
        text: String,
        modulesByAccessor: Map<String, ProjectModuleResolver.ModuleInfo>
    ): List<DependencyTarget> {
        val result = mutableListOf<DependencyTarget>()

        for (match in PROJECT_CALL_DEPENDENCY_REGEX.findAll(text)) {
            val callName = match.groupValues[1]
            if (!looksLikeDependencyCallName(callName)) {
                continue
            }
            val modulePath = match.groupValues[2].takeIf { it.startsWith(":") && it != ":" } ?: continue
            result += DependencyTarget(
                modulePath = modulePath,
                offset = match.range.first,
                dependencyText = """project("$modulePath")"""
            )
        }

        for (match in PROJECT_ACCESSOR_DEPENDENCY_REGEX.findAll(text)) {
            val callName = match.groupValues[1]
            if (!looksLikeDependencyCallName(callName)) {
                continue
            }
            val accessor = match.groupValues[2]
            val module = modulesByAccessor[accessor] ?: continue
            result += DependencyTarget(
                modulePath = module.path,
                offset = match.range.first,
                dependencyText = accessor
            )
        }

        return result
    }

    private fun createUsage(
        content: String,
        file: VirtualFile,
        consumerModulePath: String,
        target: DependencyTarget
    ): Usage {
        val safeOffset = target.offset.coerceIn(0, content.length)
        val lineStart = content.lastIndexOf('\n', startIndex = (safeOffset - 1).coerceAtLeast(0))
            .let { if (it == -1) 0 else it + 1 }
        val lineEnd = content.indexOf('\n', startIndex = safeOffset)
            .let { if (it == -1) content.length else it }
        val lineNumber = content.take(safeOffset).count { it == '\n' } + 1

        return Usage(
            consumerModulePath = consumerModulePath,
            targetModulePath = target.modulePath,
            file = file,
            offset = safeOffset,
            lineNumber = lineNumber,
            lineText = content.substring(lineStart, lineEnd).trim(),
            dependencyText = target.dependencyText
        )
    }

    private fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) {
            return null
        }
        return expression.entries.joinToString(separator = "") { it.text }
    }

    private fun findTopDotExpression(expression: KtDotQualifiedExpression): KtDotQualifiedExpression {
        var current = expression
        while (current.parent is KtDotQualifiedExpression) {
            current = current.parent as KtDotQualifiedExpression
        }
        return current
    }

    private fun isInsideDependenciesBlock(element: com.intellij.psi.PsiElement): Boolean {
        var current: com.intellij.psi.PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression && current.calleeExpression?.text == "dependencies") {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun looksLikeDependencyCallName(callName: String): Boolean {
        if (callName in DEPENDENCY_CONFIGURATIONS) {
            return true
        }

        return callName.startsWith("ksp") ||
            callName.startsWith("kapt") ||
            callName.endsWith("Implementation") ||
            callName.endsWith("Api") ||
            callName.endsWith("CompileOnly") ||
            callName.endsWith("RuntimeOnly") ||
            callName.endsWith("Processor") ||
            callName.endsWith("Ksp") ||
            callName.endsWith("Kapt")
    }

    private fun navigate(project: Project, usage: Usage) {
        OpenFileDescriptor(project, usage.file, usage.offset).navigate(true)
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("GradleBuddy")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun toRelativePath(project: Project, path: String): String {
        val basePath = project.basePath?.replace('\\', '/')?.trimEnd('/') ?: return path
        val normalizedPath = path.replace('\\', '/')
        return normalizedPath.removePrefix("$basePath/").ifBlank { normalizedPath }
    }

    private data class DependencyGraph(
        val modules: List<ProjectModuleResolver.ModuleInfo>,
        val usagesByTarget: Map<ModuleKey, List<Usage>>
    )

    private data class ModuleKey(
        val rootPath: String,
        val modulePath: String
    )

    private data class DependencyTarget(
        val modulePath: String,
        val offset: Int,
        val dependencyText: String
    )

    private data class UsageIdentity(
        val modulePath: String,
        val offset: Int
    )

    private data class UsagePresentation(
        val usage: Usage,
        val relativePath: String
    )

    private val PROJECTS_ACCESSOR_REGEX = Regex("""^projects\.[A-Za-z0-9_.]+$""")

    private val PROJECT_CALL_DEPENDENCY_REGEX = Regex(
        """([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*project\s*\(\s*"(:[^"]+)"\s*\)\s*\)"""
    )

    private val PROJECT_ACCESSOR_DEPENDENCY_REGEX = Regex(
        """([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*(projects\.[A-Za-z0-9_.]+)\s*\)"""
    )

    private val DEPENDENCY_CONFIGURATIONS = setOf(
        "implementation", "api", "compileOnly", "runtimeOnly",
        "testImplementation", "testApi", "testCompileOnly", "testRuntimeOnly",
        "androidTestImplementation", "androidTestApi", "androidTestCompileOnly", "androidTestRuntimeOnly",
        "debugImplementation", "releaseImplementation",
        "kapt", "ksp", "annotationProcessor", "lintChecks",
        "testFixturesImplementation", "testFixturesApi", "testFixturesCompileOnly", "testFixturesRuntimeOnly"
    )
}
