package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener

class SmartGeneratedArtifactExcludePolicy : DirectoryIndexExcludePolicy {
    private val project: Project?
    private val projectBasePath: String?

    constructor() {
        project = null
        projectBasePath = null
    }

    constructor(project: Project) {
        this.project = project
        projectBasePath = project.basePath
    }

    override fun getExcludeUrlsForProject(): Array<String> {
        return SmartGeneratedArtifactExcludePaths.collectProjectExcludeUrls(
            projectBasePath,
            sourceOnlySearchEnabled(project),
        )
            .toTypedArray()
    }

    override fun getExcludeRootsForModule(rootModel: ModuleRootModel): Array<VirtualFilePointer> {
        val moduleProject = rootModel.module.project
        return SmartGeneratedArtifactExcludePaths.collectModuleExcludeUrls(
            moduleProject.basePath,
            rootModel.contentRootUrls,
            sourceOnlySearchEnabled(moduleProject),
        )
            .map { url ->
                VirtualFilePointerManager.getInstance().createDirectoryPointer(
                    url,
                    true,
                    moduleProject,
                    NoOpVirtualFilePointerListener,
                )
            }
            .toTypedArray()
    }

    private fun sourceOnlySearchEnabled(project: Project?): Boolean {
        return project?.service<SourceOnlySearchProjectService>()?.isFilterGeneratedCodeEnabled()
            ?: SourceOnlySearchProjectService.DEFAULT_FILTER_GENERATED_CODE
    }
}

private object NoOpVirtualFilePointerListener : VirtualFilePointerListener

internal object SmartGeneratedArtifactExcludePaths {
    private val alwaysExcludedRelativeDirectoryPaths = listOf(
        ".gradle",
        ".kotlin",
        ".gradle-user-home",
        "build/tmp",
    )

    private val sourceOnlyRelativeDirectoryPaths = listOf(
        "build",
        "out",
        "target",
        "generated",
        "src/generated",
        "src/main/generated",
        "src/test/generated",
    )

    fun collectModuleExcludeUrls(
        projectBasePath: String?,
        contentRootUrls: Array<String>,
        sourceOnlySearchEnabled: Boolean = false,
    ): List<String> {
        val builtInExcludeUrls = contentRootUrls.asSequence()
            .map { rootUrl -> rootUrl.trimEnd('/') }
            .filter { rootUrl -> rootUrl.isNotBlank() }
            .flatMap { rootUrl ->
                relativeDirectoryPaths(sourceOnlySearchEnabled).asSequence().map { relativePath ->
                    "$rootUrl/$relativePath"
                }
            }
            .distinct()
            .toList()
        val gitignoreExcludeUrls = GitignoreSearchExclusion.collectDirectoryExcludeUrls(projectBasePath, contentRootUrls)
        return pruneNestedUrls(builtInExcludeUrls + gitignoreExcludeUrls)
    }

    fun collectProjectExcludeUrls(
        projectBasePath: String?,
        sourceOnlySearchEnabled: Boolean = false,
    ): List<String> {
        val builtInExcludeUrls = projectBasePath
            ?.let { basePath ->
                val rootUrl = java.nio.file.Paths.get(basePath).normalize()
                    .toUri()
                    .toASCIIString()
                    .trimEnd('/')
                relativeDirectoryPaths(sourceOnlySearchEnabled).map { relativePath ->
                    "$rootUrl/$relativePath"
                }
            }
            .orEmpty()
        val gitignoreExcludeUrls = GitignoreSearchExclusion.collectProjectExcludeUrls(projectBasePath)
        return pruneNestedUrls(builtInExcludeUrls + gitignoreExcludeUrls)
    }

    private fun relativeDirectoryPaths(sourceOnlySearchEnabled: Boolean): List<String> {
        if (!sourceOnlySearchEnabled) {
            return alwaysExcludedRelativeDirectoryPaths
        }
        return alwaysExcludedRelativeDirectoryPaths + sourceOnlyRelativeDirectoryPaths
    }

    private fun pruneNestedUrls(urls: List<String>): List<String> {
        val distinctUrls = urls.distinct()
        return distinctUrls.filterNot { candidateUrl ->
            distinctUrls.any { parentUrl ->
                parentUrl != candidateUrl && candidateUrl.startsWith("${parentUrl.trimEnd('/')}/")
            }
        }
    }
}
