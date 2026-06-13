package site.addzero.composebuddy.support

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object MoveComposeComponentWithDependenciesSupport {
    data class ComponentMovePlan(
        val componentFile: VirtualFile,
        val componentParentDirectory: VirtualFile,
        val dependencyFiles: List<VirtualFile>,
        val minimalUseMovePlans: List<MoveToSharedSourceSetSupport.MovePlan>,
        val commonMovePlans: List<MoveToSharedSourceSetSupport.MovePlan>,
        val couplingPoints: List<CouplingPoint>,
        val couplingReadme: CouplingReadme?,
    ) {
        val movePlans: List<MoveToSharedSourceSetSupport.MovePlan>
            get() = minimalUseMovePlans + commonMovePlans
    }

    data class CouplingPoint(
        val sharedFile: VirtualFile,
        val sharedFileFqNames: List<String>,
        val couplingFileFqNames: List<String>,
    )

    data class CouplingReadme(
        val targetDirectoryPath: Path,
        val content: String,
    )

    fun isMovableComponent(function: KtNamedFunction): Boolean {
        return ComposeFunctionSupport.isComposable(function) && function.parent is KtFile
    }

    fun buildPlan(
        project: Project,
        componentFile: KtFile,
    ): ComponentMovePlan? {
        val componentVirtualFile = componentFile.virtualFile ?: return null
        val componentParentDirectory = componentVirtualFile.parent ?: return null
        val parentDirectoryPath = Paths.get(componentParentDirectory.path).normalize()
        val parentDirectoryName = parentDirectoryPath.fileName?.toString() ?: return null
        if (parentDirectoryName in RESERVED_TARGET_DIRECTORY_NAMES) {
            return null
        }
        val dependencyFiles = collectDependencyFiles(project, componentFile)
        if (dependencyFiles.isEmpty()) {
            return null
        }
        val psiManager = PsiManager.getInstance(project)
        val dependencyPathSet = dependencyFiles.map { it.path }.toSet()
        val couplingPoints = dependencyFiles.mapNotNull { file ->
            val ktFile = psiManager.findFile(file) as? KtFile ?: return@mapNotNull null
            collectCouplingPoint(project, ktFile, dependencyPathSet, componentVirtualFile)
        }
        val commonFilePaths = couplingPoints.map { it.sharedFile.path }.toSet()
        val minimalUseDirectoryPath = parentDirectoryPath.resolve(MINIMAL_USE_DIRECTORY_NAME).normalize()
        val commonDirectoryPath = parentDirectoryPath.resolve(COMMON_DIRECTORY_NAME).normalize()
        val minimalUseMovePlans = dependencyFiles
            .filterNot { it.path in commonFilePaths }
            .mapNotNull { file -> buildDirectoryMovePlan(file, minimalUseDirectoryPath) }
        val commonMovePlans = dependencyFiles
            .filter { it.path in commonFilePaths }
            .mapNotNull { file -> buildDirectoryMovePlan(file, commonDirectoryPath) }
        if (minimalUseMovePlans.size + commonMovePlans.size != dependencyFiles.size) {
            return null
        }
        val movePlans = minimalUseMovePlans + commonMovePlans
        val conflictFreePlans = MoveToSharedSourceSetSupport.filterConflictingPlans(movePlans)
        if (conflictFreePlans.size != movePlans.size) {
            return null
        }
        val couplingReadme = buildCouplingReadme(commonDirectoryPath, couplingPoints)
        return ComponentMovePlan(
            componentFile = componentVirtualFile,
            componentParentDirectory = componentParentDirectory,
            dependencyFiles = dependencyFiles,
            minimalUseMovePlans = minimalUseMovePlans,
            commonMovePlans = commonMovePlans,
            couplingPoints = couplingPoints,
            couplingReadme = couplingReadme,
        )
    }

    fun writeCouplingReadme(
        project: Project,
        plan: ComponentMovePlan,
        commandName: String,
    ): Boolean {
        val couplingReadme = plan.couplingReadme ?: return true
        return WriteCommandAction.writeCommandAction(project)
            .withName(commandName)
            .compute<Boolean, RuntimeException> {
                val targetDirectory = plan.componentParentDirectory.findChild(COMMON_DIRECTORY_NAME)
                    ?: plan.componentParentDirectory.createChildDirectory(this, COMMON_DIRECTORY_NAME)
                val readmeFile = targetDirectory.findChild(README_FILE_NAME)
                    ?: targetDirectory.createChildData(this, README_FILE_NAME)
                val existingText = String(readmeFile.contentsToByteArray(), Charsets.UTF_8)
                val nextText = mergeReadmeText(existingText, couplingReadme.content)
                VfsUtil.saveText(readmeFile, nextText)
                true
            }
    }

    fun collectDependencyFiles(project: Project, componentFile: KtFile): List<VirtualFile> {
        val entryVirtualFile = componentFile.virtualFile ?: return emptyList()
        val samePackageFiles = collectSamePackageFiles(project, componentFile)
        val samePackagePaths = samePackageFiles.mapNotNull { it.virtualFile?.path }.toSet()
        val result = linkedMapOf(entryVirtualFile.path to componentFile)
        val queue = ArrayDeque<KtFile>()
        queue.add(componentFile)

        while (queue.isNotEmpty()) {
            val currentFile = queue.removeFirst()
            currentFile.collectReferencedSamePackageFiles(samePackagePaths).forEach { dependencyFile ->
                val dependencyVirtualFile = dependencyFile.virtualFile ?: return@forEach
                if (result.putIfAbsent(dependencyVirtualFile.path, dependencyFile) == null) {
                    queue.add(dependencyFile)
                }
            }
        }

        return result.values.mapNotNull { it.virtualFile }
    }

    private fun buildDirectoryMovePlan(
        file: VirtualFile,
        targetDirectoryPath: Path,
    ): MoveToSharedSourceSetSupport.MovePlan? {
        if (!file.isValid || file.isDirectory || !file.isInLocalFileSystem) {
            return null
        }
        val currentFilePath = Paths.get(file.path).normalize()
        val targetFilePath = targetDirectoryPath.resolve(file.name).normalize()
        if (targetFilePath == currentFilePath || Files.exists(targetFilePath)) {
            return null
        }
        return MoveToSharedSourceSetSupport.MovePlan(
            sourceFile = file,
            targetFilePath = targetFilePath,
        )
    }

    private fun collectCouplingPoint(
        project: Project,
        file: KtFile,
        dependencyPathSet: Set<String>,
        componentVirtualFile: VirtualFile,
    ): CouplingPoint? {
        val sharedVirtualFile = file.virtualFile ?: return null
        if (sharedVirtualFile.path == componentVirtualFile.path) {
            return null
        }
        val couplingFileFqNames = file.declarations
            .filterIsInstance<KtNamedDeclaration>()
            .filterNot { declaration -> declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) }
            .flatMap { declaration -> collectExternalReferenceFqNames(project, declaration, dependencyPathSet) }
            .distinct()
            .sorted()
        if (couplingFileFqNames.isEmpty()) {
            return null
        }
        val sharedPackageName = appendPackageSegment(file.packageFqName.asString(), COMMON_DIRECTORY_NAME)
        return CouplingPoint(
            sharedFile = sharedVirtualFile,
            sharedFileFqNames = file.topLevelFqNames(sharedPackageName),
            couplingFileFqNames = couplingFileFqNames,
        )
    }

    private fun collectExternalReferenceFqNames(
        project: Project,
        declaration: KtNamedDeclaration,
        dependencyPathSet: Set<String>,
    ): List<String> {
        return ReferencesSearch.search(declaration, GlobalSearchScope.projectScope(project))
            .findAll()
            .mapNotNull { reference -> reference.element.containingFile as? KtFile }
            .filter { referencedFile ->
                val referencedVirtualFile = referencedFile.virtualFile ?: return@filter false
                referencedVirtualFile.path !in dependencyPathSet
            }
            .flatMap { referencedFile -> referencedFile.topLevelFqNames() }
    }

    private fun buildCouplingReadme(
        commonDirectoryPath: Path,
        couplingPoints: List<CouplingPoint>,
    ): CouplingReadme? {
        if (couplingPoints.isEmpty()) {
            return null
        }
        val content = buildString {
            appendLine(README_START)
            appendLine("# KMP Buddy Common")
            appendLine()
            appendLine("Generated while extracting a minimal Compose dependency set.")
            appendLine()
            appendLine("## Coupling Points")
            couplingPoints.sortedBy { it.sharedFile.path }.forEach { point ->
                appendLine()
                appendLine("- Shared file: `${point.sharedFileFqNames.joinToString("`, `")}`")
                appendLine("  - Referenced outside the minimal dependency set:")
                point.couplingFileFqNames.forEach { fqName ->
                    appendLine("    - `$fqName`")
                }
            }
            appendLine(README_END)
        }.trimEnd() + "\n"
        return CouplingReadme(
            targetDirectoryPath = commonDirectoryPath,
            content = content,
        )
    }

    private fun mergeReadmeText(existingText: String, generatedBlock: String): String {
        val startIndex = existingText.indexOf(README_START)
        val endIndex = existingText.indexOf(README_END)
        if (startIndex >= 0 && endIndex >= startIndex) {
            val replaceEndIndex = endIndex + README_END.length
            return existingText.replaceRange(startIndex, replaceEndIndex, generatedBlock.trimEnd()) + "\n"
        }
        if (existingText.isBlank()) {
            return generatedBlock
        }
        return existingText.trimEnd() + "\n\n" + generatedBlock
    }

    private fun KtFile.topLevelFqNames(packageNameOverride: String? = null): List<String> {
        val packageName = packageNameOverride ?: packageFqName.asString()
        val declarationNames = declarations
            .filterIsInstance<KtNamedDeclaration>()
            .filterNot { declaration -> declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) }
            .mapNotNull { declaration -> declaration.name }
            .distinct()
            .ifEmpty { listOf(virtualFile?.nameWithoutExtension ?: name.removeSuffix(".kt")) }
        return declarationNames.map { name -> joinFqName(packageName, name) }
    }

    private fun appendPackageSegment(packageName: String, segment: String): String {
        if (packageName.isBlank()) {
            return segment
        }
        return "$packageName.$segment"
    }

    private fun joinFqName(packageName: String, name: String): String {
        if (packageName.isBlank()) {
            return name
        }
        return "$packageName.$name"
    }

    private fun collectSamePackageFiles(project: Project, componentFile: KtFile): List<KtFile> {
        val componentVirtualFile = componentFile.virtualFile ?: return listOf(componentFile)
        val packageName = componentFile.packageFqName.asString()
        val candidateDirectories = linkedMapOf<String, VirtualFile>()
        componentVirtualFile.parent?.let { candidateDirectories[it.path] = it }

        val sourceLayout = MoveToSharedSourceSetSupport.parseSourceLayout(componentVirtualFile)
        if (sourceLayout != null) {
            val packageRelativePath = packageName.replace('.', '/')
            val sourceRootPath = sourceLayout.moduleRootPath
                .resolve("src")
                .resolve(sourceLayout.sourceSetName)
                .resolve("kotlin")
            val packageDirectoryPath = if (packageRelativePath.isBlank()) {
                sourceRootPath
            } else {
                sourceRootPath.resolve(packageRelativePath)
            }
            LocalFileSystem.getInstance()
                .findFileByNioFile(packageDirectoryPath.normalize())
                ?.let { candidateDirectories[it.path] = it }
        }

        val psiManager = PsiManager.getInstance(project)
        return candidateDirectories.values
            .flatMap { directory -> directory.children.toList() }
            .filter { file -> file.extension?.equals("kt", ignoreCase = true) == true }
            .mapNotNull { file -> psiManager.findFile(file) as? KtFile }
            .filter { file -> file.packageFqName.asString() == packageName }
            .distinctBy { file -> file.virtualFile?.path }
    }

    private fun KtFile.collectReferencedSamePackageFiles(samePackagePaths: Set<String>): List<KtFile> {
        return collectDescendantsOfType<KtNameReferenceExpression>()
            .mapNotNull { reference -> reference.resolveSamePackageFile() }
            .filter { file -> file.virtualFile?.path in samePackagePaths }
            .distinctBy { file -> file.virtualFile?.path }
    }

    private fun KtNameReferenceExpression.resolveSamePackageFile(): KtFile? {
        val resolved = runCatching { mainReference.resolve() }.getOrNull() ?: return null
        val declaration = when (resolved) {
            is KtNamedDeclaration -> resolved
            else -> resolved.getStrictParentOfType<KtNamedDeclaration>()
        } ?: return null
        return declaration.containingKtFile
    }

    private const val MINIMAL_USE_DIRECTORY_NAME = "minimal_use"
    private const val COMMON_DIRECTORY_NAME = "common"
    private const val README_FILE_NAME = "README.md"
    private const val README_START = "<!-- KMP Buddy coupling points:start -->"
    private const val README_END = "<!-- KMP Buddy coupling points:end -->"
    private val RESERVED_TARGET_DIRECTORY_NAMES = setOf(MINIMAL_USE_DIRECTORY_NAME, COMMON_DIRECTORY_NAME)
}
