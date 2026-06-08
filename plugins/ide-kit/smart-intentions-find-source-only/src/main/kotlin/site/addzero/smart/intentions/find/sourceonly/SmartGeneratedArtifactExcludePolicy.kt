package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener

class SmartGeneratedArtifactExcludePolicy : DirectoryIndexExcludePolicy {
    private val projectBasePath: String?

    constructor() {
        projectBasePath = null
    }

    constructor(project: Project) {
        projectBasePath = project.basePath
    }

    override fun getExcludeUrlsForProject(): Array<String> {
        return SmartGeneratedArtifactExcludePaths.collectProjectExcludeUrls(projectBasePath)
            .toTypedArray()
    }

    override fun getExcludeRootsForModule(rootModel: ModuleRootModel): Array<VirtualFilePointer> {
        return SmartGeneratedArtifactExcludePaths.collectModuleExcludeUrls(
            rootModel.module.project.basePath,
            rootModel.contentRootUrls,
        )
            .map { url ->
                VirtualFilePointerManager.getInstance().createDirectoryPointer(
                    url,
                    true,
                    rootModel.module.project,
                    NoOpVirtualFilePointerListener,
                )
            }
            .toTypedArray()
    }
}

private object NoOpVirtualFilePointerListener : VirtualFilePointerListener

internal object SmartGeneratedArtifactExcludePaths {
    private val excludedRelativeDirectoryPaths = listOf(
        ".gradle",
        ".kotlin",
        ".gradle-user-home",
        "build/tmp",
    )

    fun collectModuleExcludeUrls(projectBasePath: String?, contentRootUrls: Array<String>): List<String> {
        val builtInExcludeUrls = contentRootUrls.asSequence()
            .map { rootUrl -> rootUrl.trimEnd('/') }
            .filter { rootUrl -> rootUrl.isNotBlank() }
            .flatMap { rootUrl ->
                excludedRelativeDirectoryPaths.asSequence().map { relativePath ->
                    "$rootUrl/$relativePath"
                }
            }
            .distinct()
            .toList()
        val gitignoreExcludeUrls = GitignoreSearchExclusion.collectDirectoryExcludeUrls(projectBasePath, contentRootUrls)
        return (builtInExcludeUrls + gitignoreExcludeUrls).distinct()
    }

    fun collectProjectExcludeUrls(projectBasePath: String?): List<String> {
        return GitignoreSearchExclusion.collectProjectExcludeUrls(projectBasePath)
    }
}
