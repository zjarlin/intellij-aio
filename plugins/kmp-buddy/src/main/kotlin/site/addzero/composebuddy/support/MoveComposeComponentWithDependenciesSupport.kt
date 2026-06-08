package site.addzero.composebuddy.support

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
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
        val dependencyFiles: List<VirtualFile>,
        val movePlans: List<MoveToSharedSourceSetSupport.MovePlan>,
    )

    fun isMovableComponent(function: KtNamedFunction): Boolean {
        return ComposeFunctionSupport.isComposable(function) && function.parent is KtFile
    }

    fun buildPlan(
        project: Project,
        componentFile: KtFile,
        componentLibraryRootPath: Path,
    ): ComponentMovePlan? {
        val dependencyFiles = collectDependencyFiles(project, componentFile)
        if (dependencyFiles.isEmpty()) {
            return null
        }
        val psiManager = PsiManager.getInstance(project)
        val movePlans = dependencyFiles.mapNotNull { file ->
            val ktFile = psiManager.findFile(file) as? KtFile ?: return@mapNotNull null
            buildComponentLibraryMovePlan(
                file = file,
                packageName = ktFile.packageFqName.asString(),
                componentLibraryRootPath = componentLibraryRootPath,
            )
        }
        if (movePlans.size != dependencyFiles.size) {
            return null
        }
        val conflictFreePlans = MoveToSharedSourceSetSupport.filterConflictingPlans(movePlans)
        if (conflictFreePlans.size != movePlans.size) {
            return null
        }
        return ComponentMovePlan(
            componentFile = componentFile.virtualFile ?: return null,
            dependencyFiles = dependencyFiles,
            movePlans = conflictFreePlans,
        )
    }

    private fun buildComponentLibraryMovePlan(
        file: VirtualFile,
        packageName: String,
        componentLibraryRootPath: Path,
    ): MoveToSharedSourceSetSupport.MovePlan? {
        if (!file.isValid || file.isDirectory || !file.isInLocalFileSystem) {
            return null
        }
        val currentFilePath = Paths.get(file.path).normalize()
        val targetFilePath = ComponentLibrarySupport.targetFilePath(
            componentLibraryRootPath = componentLibraryRootPath,
            packageName = packageName,
            fileName = file.name,
        )
        if (targetFilePath == currentFilePath || Files.exists(targetFilePath)) {
            return null
        }
        return MoveToSharedSourceSetSupport.MovePlan(
            sourceFile = file,
            targetFilePath = targetFilePath,
        )
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
}
