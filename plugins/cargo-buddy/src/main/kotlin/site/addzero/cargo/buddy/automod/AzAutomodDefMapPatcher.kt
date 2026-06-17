package site.addzero.cargo.buddy.automod

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.PsiManager
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.crate.crateGraph
import org.rust.lang.core.macros.MacroCallBody
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.resolve2.CollectorContext
import org.rust.lang.core.resolve2.CrateDefMap
import org.rust.lang.core.resolve2.DefCollector
import org.rust.lang.core.resolve2.DefCollectorSharedContext
import org.rust.lang.core.resolve2.FileInclusionType
import org.rust.lang.core.resolve2.ItemPointer
import org.rust.lang.core.resolve2.MacroCallInfo
import org.rust.lang.core.resolve2.ModCollectorContext
import org.rust.lang.core.resolve2.ModData
import org.rust.lang.core.resolve2.ModPath
import org.rust.lang.core.resolve2.PerNs
import org.rust.lang.core.resolve2.VisItem
import org.rust.lang.core.resolve2.Visibility
import org.rust.lang.core.resolve2.collectScope
import org.rust.lang.core.resolve2.defMapService
import org.rust.lang.core.resolve2.forceRebuildDefMapForAllCrates
import org.rust.lang.core.resolve2.getAllDefMaps
import org.rust.lang.core.resolve2.util.DefMapTaskExecutor
import org.rust.lang.core.psi.rustPsiManager
import site.addzero.cargo.buddy.model.CargoCrateResolver
import java.nio.file.Path
import java.nio.file.Paths

object AzAutomodDefMapPatcher {
    private val LOG = Logger.getInstance(AzAutomodDefMapPatcher::class.java)

    suspend fun rebuildAndPatch(project: Project) {
        try {
            project.forceRebuildDefMapForAllCrates()
            patchProject(project)
        } catch (e: Throwable) {
            LOG.warn("Unable to patch Rust def maps for az-automod", e)
        }
    }

    suspend fun patchProject(project: Project) {
        val defMapsByCrate = readAction {
            project.getAllDefMaps().associateBy { it.crate }
        }
        val crates = readAction {
            project.crateGraph.topSortedCrates.mapNotNull { crate ->
                val crateId = crate.id ?: return@mapNotNull null
                crate to crateId
            }
        }
        val patched = mutableListOf<Pair<Int, CrateDefMap>>()

        for ((crate, crateId) in crates) {
            val defMap = defMapsByCrate[crateId] ?: continue
            if (patchCrate(project, crate, defMap)) {
                patched += crateId to defMap
            }
        }

        if (patched.isNotEmpty()) {
            applyPatchedDefMaps(project, patched)
            invokeOnEdt {
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
            LOG.info("Patched ${patched.size} Rust def map(s) for az-automod")
        }
    }

    private fun <T> readAction(action: () -> T): T {
        return ReadAction.compute<T, RuntimeException> { action() }
    }

    private fun collectScopeInReadAction(
        scope: RsFile,
        modData: ModData,
        collectorContext: ModCollectorContext,
        inclusionType: FileInclusionType,
    ) {
        readAction {
            collectScope(
                scope,
                modData,
                collectorContext,
                inclusionType,
                modData.macroIndex,
                null,
                ItemPointer.NULL_POINTER,
            )
        }
    }

    private fun applyPatchedDefMaps(
        project: Project,
        patched: List<Pair<Int, CrateDefMap>>,
    ) {
        val write = Runnable {
            WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
                project.rustPsiManager.incRustStructureModificationCount()
                for ((crateId, defMap) in patched) {
                    project.defMapService.setDefMap(crateId, defMap)
                }
                project.defMapService.setAllDefMapsUpToDate()
            }
        }

        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            write.run()
        } else {
            application.invokeAndWait(write)
        }
    }

    private fun invokeOnEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            action()
        } else {
            application.invokeAndWait { action() }
        }
    }

    private suspend fun patchCrate(
        project: Project,
        crate: Crate,
        defMap: CrateDefMap,
    ): Boolean {
        val rootSnapshot = readAction {
            val rootFile = crate.rootMod ?: return@readAction null
            RootSnapshot(rootFile, rootFile.text, rootFile.virtualFile)
        } ?: return false
        if (!AzAutomodExpander.hasAutomodCall(rootSnapshot.text)) return false

        val cargoCrate = readAction {
            CargoCrateResolver.resolveForFile(project, rootSnapshot.virtualFile)
        } ?: return false
        val manifestDirectory = Paths.get(cargoCrate.rootPath)
        val context = CollectorContext(
            crate,
            DefCollectorSharedContext(project, rootSnapshot.file),
        )
        val collectorContext = ModCollectorContext(defMap, context)

        collectScopeInReadAction(
            rootSnapshot.file,
            defMap.root,
            collectorContext,
            FileInclusionType.CrateRoot,
        )

        val changed = collectAutomodMacroCalls(
            project = project,
            defMap = defMap,
            collectorContext = collectorContext,
            context = context,
            manifestDirectory = manifestDirectory,
        )
        if (!changed) return false

        DefCollector(
            project,
            defMap,
            context,
            DefMapTaskExecutor.singleThread(),
        ).collect()
        return true
    }

    private fun collectAutomodMacroCalls(
        project: Project,
        defMap: CrateDefMap,
        collectorContext: ModCollectorContext,
        context: CollectorContext,
        manifestDirectory: Path,
    ): Boolean {
        var changed = false

        while (true) {
            val calls = context.macroCalls
                .filterIsInstance<MacroCallInfo>()
                .filter { it.path.contentEquals(AUTOMOD_PATH) }
            if (calls.isEmpty()) break

            context.macroCalls.removeAll(calls.toSet())
            for (call in calls) {
                val expansion = automodExpansion(call, manifestDirectory) ?: continue
                changed = collectAutomodModules(
                    project = project,
                    defMap = defMap,
                    collectorContext = collectorContext,
                    parent = call.containingMod,
                    parentDirectory = expansion.directory,
                    modules = expansion.modules,
                    visibility = visibility(defMap, call.containingMod, expansion.visibility),
                ) || changed
            }
        }

        return changed
    }

    private fun automodExpansion(
        call: MacroCallInfo,
        manifestDirectory: Path,
    ): AutomodMacroExpansion? {
        val body = (call.body as? MacroCallBody.FunctionLike)?.text ?: return null
        return try {
            AzAutomodExpander.parseMacroCall("automod::dir!($body)", manifestDirectory)
        } catch (e: AzAutomodExpansionException) {
            LOG.warn("Unable to expand az-automod macro body: $body", e)
            null
        }
    }

    private data class RootSnapshot(
        val file: RsFile,
        val text: String,
        val virtualFile: VirtualFile,
    )

    private fun collectAutomodModules(
        project: Project,
        defMap: CrateDefMap,
        collectorContext: ModCollectorContext,
        parent: ModData,
        parentDirectory: Path,
        modules: List<Module>,
        visibility: Visibility,
    ): Boolean {
        var changed = false
        modules.forEachIndexed { index, module ->
            if (module.moduleName in parent.childModules) return@forEachIndexed

            val childPath = parent.path.append(module.moduleName)
            val child = when (val kind = module.kind) {
                ModuleKind.File -> {
                    val filePath = parentDirectory.resolve("${module.sourceName}.rs").normalize()
                    val file = findRsFile(project, filePath) ?: return@forEachIndexed
                    val childModData = createFileModData(
                        parent = parent,
                        path = childPath,
                        index = index,
                        fileId = file.fileId,
                        ownedDirectory = ownedDirectoryForFile(parentDirectory, module),
                        hasPathAttribute = module.sourceName != module.moduleName,
                        crateDescription = defMap.crateDescription,
                    )
                    recordModule(parent, module.moduleName, childModData, visibility)
                    collectScopeInReadAction(
                        file.file,
                        childModData,
                        collectorContext,
                        FileInclusionType.RegularModFile,
                    )
                    childModData
                }

                is ModuleKind.Directory -> {
                    val directoryPath = parentDirectory.resolve(module.sourceName).normalize()
                    val directory = findVirtualFile(directoryPath) ?: return@forEachIndexed
                    val childModData = createInlineModData(
                        parent = parent,
                        path = childPath,
                        index = index,
                        ownedDirectory = directory,
                        hasPathAttribute = module.sourceName != module.moduleName,
                        crateDescription = defMap.crateDescription,
                    )
                    recordModule(parent, module.moduleName, childModData, visibility)
                    collectAutomodModules(
                        project = project,
                        defMap = defMap,
                        collectorContext = collectorContext,
                        parent = childModData,
                        parentDirectory = directoryPath,
                        modules = kind.items,
                        visibility = visibility,
                    )
                    childModData
                }
            }
            changed = true
        }
        return changed
    }

    private fun createFileModData(
        parent: ModData,
        path: ModPath,
        index: Int,
        fileId: Int?,
        ownedDirectory: VirtualFile?,
        hasPathAttribute: Boolean,
        crateDescription: String,
    ): ModData {
        return ModData(
            parent = parent,
            crate = parent.crate,
            path = path,
            macroIndex = parent.macroIndex.append(AUTOMOD_INDEX_BASE + index),
            isDeeplyEnabledByCfgOuter = true,
            isEnabledByCfgInner = true,
            fileId = fileId,
            fileRelativePath = "",
            ownedDirectoryId = (ownedDirectory as? VirtualFileWithId)?.id,
            hasPathAttribute = hasPathAttribute,
            hasMacroUse = false,
            crateDescription = crateDescription,
            prelude = parent.prelude,
            isTest = false,
            itemPointer = ItemPointer.NULL_POINTER,
        )
    }

    private fun createInlineModData(
        parent: ModData,
        path: ModPath,
        index: Int,
        ownedDirectory: VirtualFile,
        hasPathAttribute: Boolean,
        crateDescription: String,
    ): ModData {
        return ModData(
            parent = parent,
            crate = parent.crate,
            path = path,
            macroIndex = parent.macroIndex.append(AUTOMOD_INDEX_BASE + index),
            isDeeplyEnabledByCfgOuter = true,
            isEnabledByCfgInner = true,
            fileId = parent.fileId,
            fileRelativePath = "${parent.fileRelativePath}::${path.name}",
            ownedDirectoryId = (ownedDirectory as? VirtualFileWithId)?.id,
            hasPathAttribute = hasPathAttribute,
            hasMacroUse = false,
            crateDescription = crateDescription,
            prelude = parent.prelude,
            isTest = false,
            itemPointer = ItemPointer.NULL_POINTER,
        )
    }

    private fun recordModule(
        parent: ModData,
        name: String,
        child: ModData,
        visibility: Visibility,
    ) {
        val visItem = VisItem(child.path, visibility, isModOrEnum = true)
        child.asVisItem = visItem
        if (parent.addVisibleItem(name, PerNs.types(visItem))) {
            parent.childModules[name] = child
        }
    }

    private fun visibility(
        defMap: CrateDefMap,
        parent: ModData,
        raw: String,
    ): Visibility {
        return when (raw) {
            "pub" -> Visibility.Public
            "pub(crate)" -> defMap.root.visibilityInSelf
            "pub(super)" -> parent.parent?.visibilityInSelf ?: defMap.root.visibilityInSelf
            "" -> parent.visibilityInSelf
            else -> Visibility.Public
        }
    }

    private fun ownedDirectoryForFile(
        parentDirectory: Path,
        module: Module,
    ): VirtualFile? {
        return if (module.sourceName == module.moduleName) {
            findVirtualFile(parentDirectory.resolve(module.sourceName).normalize())
        } else {
            findVirtualFile(parentDirectory)
        }
    }

    private fun findRsFile(
        project: Project,
        path: Path,
    ): FileSnapshot? {
        return readAction {
            val virtualFile = findVirtualFile(path) ?: return@readAction null
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? RsFile
                ?: return@readAction null
            FileSnapshot(file, (virtualFile as? VirtualFileWithId)?.id)
        }
    }

    private fun findVirtualFile(path: Path): VirtualFile? {
        return readAction {
            LocalFileSystem.getInstance().findFileByNioFile(path)
        }
    }

    private data class FileSnapshot(
        val file: RsFile,
        val fileId: Int?,
    )

    private val AUTOMOD_CALL = Regex(
        """automod\s*::\s*dir!\s*\(\s*(?:(pub(?:\s*\([^)]*\))?)\s*)?"((?:\\.|[^"\\])*)"\s*\)\s*;?""",
    )
    private val AUTOMOD_PATH = arrayOf("automod", "dir")
    private const val AUTOMOD_INDEX_BASE = 1_000_000
}
