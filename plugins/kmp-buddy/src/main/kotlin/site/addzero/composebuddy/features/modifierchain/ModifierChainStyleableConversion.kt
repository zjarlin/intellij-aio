package site.addzero.composebuddy.features.modifierchain

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.ImportPath
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object ModifierChainStyleableConversion {
    private const val MODIFIER_TYPE = "Modifier"
    private const val MODIFIER_FQ_TYPE = "androidx.compose.ui.Modifier"
    private const val STYLEABLE_FQ_NAME = "androidx.compose.foundation.style.styleable"
    private const val EXPERIMENTAL_STYLE_API_FQ_NAME = "androidx.compose.foundation.style.ExperimentalFoundationStyleApi"
    private const val OPT_IN_TEXT = "@OptIn(ExperimentalFoundationStyleApi::class)"

    fun isApplicable(function: KtNamedFunction): Boolean {
        return analyze(function) != null
    }

    fun applyToProject(project: Project): ModifierChainStyleableConversionResult {
        val functionPointers = ProgressManager.getInstance()
            .runProcessWithProgressSynchronously<List<SmartPsiElementPointer<KtNamedFunction>>, RuntimeException>(
                {
                    ReadAction.compute<List<SmartPsiElementPointer<KtNamedFunction>>, Throwable> {
                        collectProjectFunctions(project, ProgressManager.getInstance().progressIndicator)
                    }
                },
                "Scan Modifier chain styles",
                true,
                project,
            )

        val scannedFileCount = functionPointers.asSequence()
            .mapNotNull { pointer -> pointer.element?.containingKtFile?.virtualFile?.path }
            .distinct()
            .count()
        if (functionPointers.isEmpty()) {
            return ModifierChainStyleableConversionResult(scannedFiles = 0, changedFunctions = 0)
        }

        var changedFunctions = 0
        val changedFiles = linkedSetOf<KtFile>()
        SmartPsiWriteSupport.runWriteCommand(project, ComposeBuddyBundle.message("command.convert.modifier.chain.styleable")) {
            val psiFactory = KtPsiFactory(project)
            functionPointers.asSequence()
                .mapNotNull { pointer -> pointer.element }
                .filter { function -> function.isValid }
                .forEach { function ->
                    val analysis = analyze(function) ?: return@forEach
                    val file = function.containingKtFile
                    convertFunction(function, analysis, psiFactory)
                    changedFunctions += 1
                    changedFiles += file
                }

            changedFiles.forEach { file ->
                file.ensureImport(psiFactory, STYLEABLE_FQ_NAME)
                file.ensureImport(psiFactory, EXPERIMENTAL_STYLE_API_FQ_NAME)
            }
        }

        return ModifierChainStyleableConversionResult(
            scannedFiles = scannedFileCount,
            changedFunctions = changedFunctions,
        )
    }

    private fun collectProjectFunctions(
        project: Project,
        indicator: ProgressIndicator?,
    ): List<SmartPsiElementPointer<KtNamedFunction>> {
        val psiManager = PsiManager.getInstance(project)
        val pointerManager = SmartPointerManager.getInstance(project)
        val files = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
            .sortedBy { file -> file.path }
        indicator?.isIndeterminate = false
        val total = files.size.coerceAtLeast(1)
        return files.asSequence()
            .mapIndexed { index, virtualFile ->
                indicator?.checkCanceled()
                indicator?.fraction = index.toDouble() / total.toDouble()
                psiManager.findFile(virtualFile) as? KtFile
            }
            .filterNotNull()
            .flatMap { file -> file.collectDescendantsOfType<KtNamedFunction>().asSequence() }
            .filter { function -> isApplicable(function) }
            .map { function -> pointerManager.createSmartPsiElementPointer(function) }
            .toList()
    }

    private fun analyze(function: KtNamedFunction): ModifierChainStyleableAnalysis? {
        if (!function.isModifierExtensionFunction()) {
            return null
        }
        if (function.annotationEntries.any { annotation -> annotation.text.contains("ExperimentalFoundationStyleApi") }) {
            return null
        }

        val expression = function.bodyExpression ?: return null
        val chainExpression = when (expression) {
            is KtBlockExpression -> {
                val statements = expression.statements
                val returnExpression = statements.singleOrNull() as? KtReturnExpression ?: return null
                returnExpression.returnedExpression ?: return null
            }
            else -> expression
        }
        val calls = collectModifierChainCalls(chainExpression)
        if (calls.size < 2) {
            return null
        }
        return ModifierChainStyleableAnalysis(calls)
    }

    private fun KtNamedFunction.isModifierExtensionFunction(): Boolean {
        val receiverText = receiverTypeReference?.text?.trim() ?: return false
        val returnText = typeReference?.text?.trim() ?: return false
        if (receiverText != MODIFIER_TYPE && receiverText != MODIFIER_FQ_TYPE) {
            return false
        }
        return returnText == MODIFIER_TYPE || returnText == MODIFIER_FQ_TYPE
    }

    private fun collectModifierChainCalls(expression: KtExpression): List<String> {
        val calls = mutableListOf<String>()
        var current: KtExpression? = expression
        while (current is KtDotQualifiedExpression) {
            val selector = current.selectorExpression as? KtCallExpression ?: return emptyList()
            calls += normalizeCallText(selector.text)
            current = current.receiverExpression
        }
        return when (current?.text?.trim()) {
            "this", "Modifier" -> calls.asReversed()
            else -> {
                val rootCall = current as? KtCallExpression ?: return emptyList()
                calls += normalizeCallText(rootCall.text)
                calls.asReversed()
            }
        }
    }

    private fun normalizeCallText(text: String): String {
        return text.trim().trimIndent()
    }

    private fun convertFunction(
        function: KtNamedFunction,
        analysis: ModifierChainStyleableAnalysis,
        psiFactory: KtPsiFactory,
    ) {
        val replacement = psiFactory.createFunction(renderReplacement(function, analysis.calls))
        function.replace(replacement)
    }

    private fun renderReplacement(function: KtNamedFunction, calls: List<String>): String {
        return buildString {
            append(renderPrefix(function))
            append(" {\n")
            append("    return styleable {\n")
            calls.forEach { call ->
                append(indentCall(call))
                append("\n")
            }
            append("    }\n")
            append("}")
        }
    }

    private fun indentCall(call: String): String {
        return call.lineSequence()
            .joinToString(separator = "\n") { line -> "        $line" }
    }

    private fun renderPrefix(function: KtNamedFunction): String {
        val body = function.bodyExpression ?: return function.text.substringBefore("{").trim()
        val prefix = function.text.substring(0, body.textRange.startOffset - function.textRange.startOffset)
            .let { text ->
                if (function.hasBlockBody()) {
                    text
                } else {
                    text.substringBeforeLast("=")
                }
            }
            .trim()
        return prefix
            .let { prefix ->
                if (prefix.startsWith(OPT_IN_TEXT)) {
                    prefix
                } else {
                    "$OPT_IN_TEXT\n$prefix"
                }
            }
    }

    private fun KtFile.ensureImport(
        psiFactory: KtPsiFactory,
        importText: String,
    ) {
        if (importDirectives.any { directive -> directive.importPath?.pathStr == importText }) {
            return
        }

        val importDirective = psiFactory.createImportDirective(
            ImportPath(FqName(importText), false),
        )
        val importList = importList
        if (importList != null) {
            val anchor = importList.imports.lastOrNull()
            if (anchor != null) {
                importList.addAfter(importDirective, anchor)
            } else {
                importList.add(importDirective)
            }
            return
        }

        val anchor: PsiElement = packageDirective ?: firstChild
        addAfter(psiFactory.createNewLine(), anchor)
        addAfter(importDirective, anchor)
    }
}

internal data class ModifierChainStyleableAnalysis(
    val calls: List<String>,
)

internal data class ModifierChainStyleableConversionResult(
    val scannedFiles: Int,
    val changedFunctions: Int,
)
