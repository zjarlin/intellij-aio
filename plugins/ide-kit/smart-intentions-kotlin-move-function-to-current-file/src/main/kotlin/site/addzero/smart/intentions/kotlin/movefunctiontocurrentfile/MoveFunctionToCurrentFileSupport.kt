package site.addzero.smart.intentions.kotlin.movefunctiontocurrentfile

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object MoveFunctionToCurrentFileSupport {
    fun isApplicable(currentFile: KtFile, call: KtCallExpression): Boolean {
        return buildPlan(currentFile, call) != null
    }

    fun apply(currentFile: KtFile, call: KtCallExpression) {
        val plan = buildPlan(currentFile, call) ?: return
        val project = currentFile.project
        val psiFactory = KtPsiFactory(project)

        SmartPsiWriteSupport.runWriteCommand(project, "将函数移动到当前文件") {
            val sourceFile = plan.sourceFunction.containingKtFile

            plan.importDirectives.forEach { importDirective ->
                ensureImport(currentFile, importDirective.copy() as KtImportDirective)
            }

            currentFile.add(psiFactory.createWhiteSpace("\n\n"))
            currentFile.add(psiFactory.createDeclaration<KtNamedFunction>(plan.sourceFunction.text))

            plan.sourceFunction.delete()
            if (sourceFile.isValid && sourceFile.declarations.isEmpty()) {
                sourceFile.delete()
            }

            CodeStyleManager.getInstance(project).reformat(currentFile)
        }
    }

    private fun buildPlan(currentFile: KtFile, call: KtCallExpression): MoveFunctionPlan? {
        val callName = call.simpleName() ?: return null
        val currentPackage = currentFile.packageFqName.asString()
        val currentFilePath = currentFile.virtualFile?.path
        val project = currentFile.project
        val psiManager = currentFile.manager

        val sourceFunction = ReadAction.compute<KtNamedFunction?, Throwable> {
            FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .asSequence()
                .mapNotNull { virtualFile -> psiManager.findFile(virtualFile) as? KtFile }
                .filter { file -> file.virtualFile?.path != currentFilePath }
                .filter { file -> file.packageFqName.asString() == currentPackage }
                .flatMap { file -> file.declarations.filterIsInstance<KtNamedFunction>().asSequence() }
                .filter { function -> function.parent is KtFile }
                .filter { function -> function.name == callName }
                .filter { function -> function.hasModifier(KtTokens.PRIVATE_KEYWORD) }
                .singleOrNull()
        } ?: return null

        val resolvedCallee = call.calleeExpression?.mainReference?.resolve()
        if (resolvedCallee != null && resolvedCallee != sourceFunction) {
            return null
        }

        if (!isCallCompatible(call, sourceFunction)) {
            return null
        }
        if (!isSafeToMove(sourceFunction)) {
            return null
        }

        return MoveFunctionPlan(
            sourceFunction = sourceFunction,
            importDirectives = sourceFunction.containingKtFile.importDirectives.toList(),
        )
    }

    private fun isCallCompatible(call: KtCallExpression, function: KtNamedFunction): Boolean {
        val suppliedCount = call.valueArguments.size + call.lambdaArguments.size
        val parameters = function.valueParameters
        val requiredCount = parameters.count { parameter -> parameter.defaultValue == null }
        if (suppliedCount < requiredCount || suppliedCount > parameters.size) {
            return false
        }

        val namedArguments = call.valueArguments.mapNotNull { argument ->
            argument.getArgumentName()?.asName?.identifier
        }.toSet()
        if (namedArguments.isNotEmpty()) {
            val parameterNames = parameters.mapNotNull { parameter -> parameter.name }.toSet()
            if (!namedArguments.all(parameterNames::contains)) {
                return false
            }
        }

        return true
    }

    private fun isSafeToMove(function: KtNamedFunction): Boolean {
        val sourceFile = function.containingKtFile
        return function.collectDescendantsOfType<KtNameReferenceExpression>().all { reference ->
            val resolved = runCatching { reference.mainReference.resolve() }.getOrNull() ?: return@all false
            if (resolved == function) {
                return@all true
            }

            val declaration = resolved as? KtNamedDeclaration ?: return@all true
            if (declaration.containingKtFile != sourceFile) {
                return@all true
            }
            if (declaration.parent !is KtFile) {
                return@all false
            }
            !declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)
        }
    }

    private fun ensureImport(
        currentFile: KtFile,
        importDirective: KtImportDirective,
    ) {
        if (currentFile.importDirectives.any { directive -> directive.text == importDirective.text }) {
            return
        }

        val importList = currentFile.importList
        if (importList != null) {
            val anchor = importList.imports.lastOrNull()
            if (anchor != null) {
                importList.addAfter(importDirective, anchor)
            } else {
                importList.add(importDirective)
            }
            return
        }

        val packageDirective = currentFile.packageDirective
        if (packageDirective != null) {
            currentFile.addAfter(importDirective, packageDirective)
            return
        }

        val firstChild = currentFile.firstChild
        if (firstChild != null) {
            currentFile.addBefore(importDirective, firstChild)
        } else {
            currentFile.add(importDirective)
        }
    }

    private fun KtCallExpression.simpleName(): String? {
        return (calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
    }

    private data class MoveFunctionPlan(
        val sourceFunction: KtNamedFunction,
        val importDirectives: List<KtImportDirective>,
    )
}
