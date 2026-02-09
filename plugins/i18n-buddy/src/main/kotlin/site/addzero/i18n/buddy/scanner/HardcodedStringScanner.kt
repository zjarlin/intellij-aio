package site.addzero.i18n.buddy.scanner

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.i18n.buddy.keygen.CompositeKeyGenerator
import site.addzero.i18n.buddy.keygen.KeyGenerator
import site.addzero.i18n.buddy.settings.I18nBuddySettingsService

/**
 * Scans the project for hardcoded string literals in Kotlin files.
 *
 * Exclusion rules:
 * - Package declarations, import statements
 * - Annotation arguments
 * - Strings that match exclude content patterns (e.g. pure identifiers, paths)
 * - Strings already wrapped by the configured i18n function
 * - Empty / blank strings
 */
class HardcodedStringScanner(private val project: Project) {

    private val keyGenerator: KeyGenerator = CompositeKeyGenerator()

    fun scan(): List<ScanResult> {
        val settings = I18nBuddySettingsService.getInstance(project).state
        val basePath = project.basePath ?: return emptyList()
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()

        val extensions = settings.scanFileExtensions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val excludeGlobs = settings.excludePatterns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val excludeContentRegexes = settings.excludeContentPatterns
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { runCatching { Regex(it) }.getOrNull() }

        val files = mutableListOf<VirtualFile>()
        collectFiles(baseDir, basePath, extensions, excludeGlobs, files)

        val results = mutableListOf<ScanResult>()
        val seenTexts = mutableMapOf<String, String>() // text → key (dedup)

        for (vFile in files) {
            val fileResults = ReadAction.compute<List<ScanResult>, Throwable> {
                scanFile(vFile, basePath, settings, excludeContentRegexes, seenTexts)
            }
            results.addAll(fileResults)
        }

        return results
    }

    private fun scanFile(
        vFile: VirtualFile,
        basePath: String,
        settings: I18nBuddySettingsService.State,
        excludeContentRegexes: List<Regex>,
        seenTexts: MutableMap<String, String>,
    ): List<ScanResult> {
        val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return emptyList()
        if (psiFile !is KtFile) return emptyList()

        val document = psiFile.viewProvider.document ?: return emptyList()
        val relativePath = vFile.path.removePrefix(basePath).trimStart('/')
        val results = mutableListOf<ScanResult>()

        val stringExprs = psiFile.collectDescendantsOfType<KtStringTemplateExpression>()

        for (expr in stringExprs) {
            // Skip string templates with interpolation
            if (expr.hasInterpolation()) continue

            val text = expr.entries.joinToString("") { it.text }
            if (text.isBlank()) continue

            // Skip if matches exclude content patterns
            if (excludeContentRegexes.any { it.containsMatchIn(text) }) continue

            // Skip if inside package/import declaration
            if (isInPackageOrImport(expr)) continue

            // Skip if inside annotation
            if (isInAnnotation(expr)) continue

            // Skip if already wrapped by i18n function
            if (isAlreadyWrapped(expr, settings.wrapperFunction)) continue

            // Generate or reuse key
            val key = seenTexts.getOrPut(text) { keyGenerator.generateKey(text) }

            val line = document.getLineNumber(expr.textOffset)
            val composable = isInComposableFunction(expr)
            results.add(
                ScanResult(
                    text = text,
                    file = vFile,
                    relativePath = relativePath,
                    line = line,
                    startOffset = expr.textOffset,
                    endOffset = expr.textOffset + expr.textLength,
                    generatedKey = key,
                    inComposable = composable,
                )
            )
        }

        return results
    }

    private fun isInPackageOrImport(expr: KtStringTemplateExpression): Boolean {
        var parent = expr.parent
        while (parent != null) {
            val className = parent.javaClass.simpleName
            if (className.contains("KtPackageDirective") || className.contains("KtImportDirective")) return true
            parent = parent.parent
        }
        return false
    }

    private fun isInAnnotation(expr: KtStringTemplateExpression): Boolean {
        var parent = expr.parent
        while (parent != null) {
            val className = parent.javaClass.simpleName
            if (className.contains("KtAnnotationEntry")) return true
            // Stop at function/class level
            if (className.contains("KtNamedFunction") || className.contains("KtClass")) break
            parent = parent.parent
        }
        return false
    }

    /**
     * Check if the string expression is inside a @Composable function.
     * Walks up the PSI tree to find the enclosing KtNamedFunction and checks its annotations.
     */
    private fun isInComposableFunction(expr: KtStringTemplateExpression): Boolean {
        var parent = expr.parent
        while (parent != null) {
            if (parent is KtNamedFunction) {
                return parent.annotationEntries.any { annotation ->
                    val name = annotation.shortName?.asString()
                    name == "Composable"
                }
            }
            parent = parent.parent
        }
        return false
    }

    private fun isAlreadyWrapped(expr: KtStringTemplateExpression, wrapperFunction: String): Boolean {
        if (wrapperFunction.isBlank()) return false
        // Check if parent call expression is the wrapper function
        val parent = expr.parent ?: return false
        val grandParent = parent.parent ?: return false
        val gpText = grandParent.text
        return gpText.startsWith("$wrapperFunction(")
    }

    private fun collectFiles(
        dir: VirtualFile,
        basePath: String,
        extensions: List<String>,
        excludeGlobs: List<String>,
        result: MutableList<VirtualFile>,
    ) {
        val name = dir.name
        if (name.startsWith(".") || name == "build" || name == "node_modules" || name == "buildSrc") return

        for (child in dir.children) {
            if (child.isDirectory) {
                collectFiles(child, basePath, extensions, excludeGlobs, result)
            } else {
                val ext = child.extension ?: continue
                if (ext !in extensions) continue
                val relPath = child.path.removePrefix(basePath).trimStart('/')
                if (excludeGlobs.any { matchGlob(it, relPath) || matchGlob(it, child.name) }) continue
                result.add(child)
            }
        }
    }

    private fun matchGlob(glob: String, text: String): Boolean {
        val regex = glob
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return Regex(regex).matches(text)
    }
}
