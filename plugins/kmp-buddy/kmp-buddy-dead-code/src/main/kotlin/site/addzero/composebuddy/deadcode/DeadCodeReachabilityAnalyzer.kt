package site.addzero.composebuddy.deadcode

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.nio.file.Paths

class DeadCodeReachabilityAnalyzer(
    private val project: Project,
) {
    fun analyze(
        entryFile: KtFile,
        entryFunction: KtNamedFunction?,
        sourceModuleRoot: VirtualFile,
    ): DeadCodeAnalysisResult {
        val sourceModulePath = Paths.get(sourceModuleRoot.path)
        val targetFiles = collectKotlinFiles(sourceModuleRoot)
        val targetFileAnalyses = targetFiles.mapNotNull { ktFile ->
            val virtualFile = ktFile.virtualFile ?: return@mapNotNull null
            val filePath = Paths.get(virtualFile.path)
            val relativePath = DeadCodePaths.relativePath(sourceModulePath, filePath)
            val declarations = ktFile.declarations
                .filterIsInstance<KtNamedDeclaration>()
                .mapNotNull { declaration -> declaration.deadCodeKey() }
            if (declarations.isEmpty()) {
                null
            } else {
                DeadCodeFileAnalysis(
                    sourcePath = filePath,
                    relativePath = relativePath,
                    declarations = declarations,
                    liveDeclarations = emptyList(),
                )
            }
        }

        val reachable = collectReachableDeclarations(entryFile, entryFunction)
        val liveKeys = reachable.keys
        val analyzedFiles = targetFileAnalyses.map { file ->
            file.copy(liveDeclarations = file.declarations.filter(liveKeys::contains))
        }

        return DeadCodeAnalysisResult(
            sourceModulePath = sourceModulePath,
            files = analyzedFiles.sortedBy(DeadCodeFileAnalysis::relativePath),
            reachableDeclarationCount = reachable.size,
        )
    }

    private fun collectReachableDeclarations(
        entryFile: KtFile,
        entryFunction: KtNamedFunction?,
    ): Map<DeadCodeDeclarationKey, KtNamedDeclaration> {
        val reachable = linkedMapOf<DeadCodeDeclarationKey, KtNamedDeclaration>()
        val queue = ArrayDeque<KtNamedDeclaration>()

        fun enqueue(declaration: KtNamedDeclaration) {
            val key = declaration.deadCodeKey() ?: return
            if (reachable.putIfAbsent(key, declaration) == null) {
                queue.add(declaration)
            }
        }

        if (entryFunction != null) {
            enqueue(entryFunction)
        } else {
            entryFile.declarations.filterIsInstance<KtNamedDeclaration>().forEach(::enqueue)
        }

        while (queue.isNotEmpty()) {
            val declaration = queue.removeFirst()
            declaration.collectDescendantsOfType<KtNameReferenceExpression>().forEach { reference ->
                val resolved = resolveReference(reference) ?: return@forEach
                val topLevelDeclaration = resolved.sourceTopLevelDeclaration() ?: return@forEach
                enqueue(topLevelDeclaration)
            }
        }

        return reachable
    }

    private fun collectKotlinFiles(root: VirtualFile): List<KtFile> {
        val psiManager = PsiManager.getInstance(project)
        val files = mutableListOf<KtFile>()
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (!file.isValid) {
                    return false
                }
                if (file.isDirectory) {
                    return !file.isExcludedDirectory()
                }
                if (file.extension == "kt" && file.path.contains("/src/")) {
                    (psiManager.findFile(file) as? KtFile)?.let(files::add)
                }
                return true
            }
        })
        return files
    }

    private fun VirtualFile.isExcludedDirectory(): Boolean {
        val name = name
        return name == "build" ||
            name == ".gradle" ||
            name == ".idea" ||
            name == ".kmp-buddy" ||
            name == "out"
    }

    private fun resolveReference(reference: KtNameReferenceExpression): PsiElement? {
        return try {
            reference.mainReference.resolve()
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: Throwable) {
            null
        }
    }

    private fun PsiElement.sourceTopLevelDeclaration(): KtNamedDeclaration? {
        val declaration = generateSequence(this) { element -> element.parent }
            .takeWhile { element -> element !is KtFile }
            .filterIsInstance<KtNamedDeclaration>()
            .lastOrNull() ?: return null

        return declaration.takeIf { it.parent is KtFile }
    }

    private fun KtNamedDeclaration.deadCodeKey(): DeadCodeDeclarationKey? {
        val name = name ?: return null
        val path = containingKtFile.virtualFile?.path ?: return null
        return DeadCodeDeclarationKey(
            filePath = path,
            name = name,
            offset = textRange.startOffset,
        )
    }
}
