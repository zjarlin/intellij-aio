package site.addzero.dioxus.buddy.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DioxusBuddyGeneratedExcludePolicy : DirectoryIndexExcludePolicy {
    private val projectBasePath: String?

    constructor() {
        projectBasePath = null
    }

    constructor(project: Project) {
        projectBasePath = project.basePath
    }

    override fun getExcludeUrlsForProject(): Array<String> {
        return DioxusBuddyGeneratedExcludePaths.collectProjectExcludeUrls(projectBasePath)
            .toTypedArray()
    }

    override fun getExcludeRootsForModule(rootModel: ModuleRootModel): Array<VirtualFilePointer> {
        return DioxusBuddyGeneratedExcludePaths.collectModuleExcludeUrls(rootModel.contentRootUrls)
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

internal object DioxusBuddyGeneratedExcludePaths {
    private const val GENERATED_DIRECTORY = ".dioxus-buddy"
    private const val CARGO_MANIFEST = "Cargo.toml"
    private const val MAX_SCAN_DEPTH = 8

    fun collectProjectExcludeUrls(projectBasePath: String?): List<String> {
        return projectBasePath
            ?.takeIf { it.isNotBlank() }
            ?.let { collectExcludeUrls(Paths.get(it)) }
            .orEmpty()
    }

    fun collectModuleExcludeUrls(contentRootUrls: Array<String>): List<String> {
        return contentRootUrls.asSequence()
            .map { rootUrl -> rootUrl.trimEnd('/') }
            .filter { rootUrl -> rootUrl.isNotBlank() }
            .flatMap { rootUrl ->
                val rootPath = runCatching { Paths.get(VfsUtilCore.urlToPath(rootUrl)) }.getOrNull()
                if (rootPath != null) {
                    collectExcludeUrls(rootPath).asSequence()
                } else {
                    sequenceOf("$rootUrl/$GENERATED_DIRECTORY")
                }
            }
            .distinct()
            .toList()
    }

    private fun collectExcludeUrls(rootPath: Path): List<String> {
        val cargoRoots = mutableSetOf(rootPath.toAbsolutePath().normalize())
        if (!Files.isDirectory(rootPath)) {
            return cargoRoots
                .map { cargoRoot -> VfsUtilCore.pathToUrl(cargoRoot.resolve(GENERATED_DIRECTORY).toString()) }
                .distinct()
        }

        runCatching {
            Files.find(
                rootPath,
                MAX_SCAN_DEPTH,
                { path, attributes ->
                    attributes.isRegularFile && path.fileName.toString() == CARGO_MANIFEST
                },
            ).use { manifests ->
                manifests.forEach { manifestPath ->
                    manifestPath.parent?.toAbsolutePath()?.normalize()?.let(cargoRoots::add)
                }
            }
        }

        return cargoRoots
            .map { cargoRoot -> VfsUtilCore.pathToUrl(cargoRoot.resolve(GENERATED_DIRECTORY).toString()) }
            .distinct()
    }
}

private object NoOpVirtualFilePointerListener : VirtualFilePointerListener
