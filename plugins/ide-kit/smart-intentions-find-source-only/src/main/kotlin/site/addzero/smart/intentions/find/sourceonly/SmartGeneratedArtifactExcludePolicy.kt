package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener

class SmartGeneratedArtifactExcludePolicy : DirectoryIndexExcludePolicy {
    override fun getExcludeRootsForModule(rootModel: ModuleRootModel): Array<VirtualFilePointer> {
        return SmartGeneratedArtifactExcludePaths.collectModuleExcludeUrls(rootModel.contentRootUrls)
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
    private val excludedDirectoryNames = listOf(
        ".gradle",
        ".kotlin",
    )

    fun collectModuleExcludeUrls(contentRootUrls: Array<String>): List<String> {
        return contentRootUrls.asSequence()
            .map { rootUrl -> rootUrl.trimEnd('/') }
            .filter { rootUrl -> rootUrl.isNotBlank() }
            .flatMap { rootUrl ->
                excludedDirectoryNames.asSequence().map { directoryName ->
                    "$rootUrl/$directoryName"
                }
            }
            .distinct()
            .toList()
    }
}
