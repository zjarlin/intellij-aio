package site.addzero.smart.intentions.kotlin.entityfieldmerge

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createPrimaryConstructorParameterListIfAbsent
import org.jetbrains.kotlin.resolve.ImportPath

internal object EntityFieldMergeApplier {
    fun apply(
        project: Project,
        plan: EntityFieldMergePlan,
        rows: List<EntityFieldMergeRow>,
    ) {
        if (rows.isEmpty()) {
            return
        }

        WriteCommandAction.writeCommandAction(project)
            .withName("(ide-kit) 合并实体字段")
            .run<RuntimeException> {
                addImports(
                    file = plan.target.klass.containingKtFile,
                    fqNames = rows.flatMap { row -> row.sourceField.importFqNames }.distinct().sorted(),
                )
                val targetClass = plan.target.klass
                rows.forEach { row ->
                    addField(targetClass, row.sourceField)
                }
                CodeStyleManager.getInstance(project).reformat(targetClass)
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
    }

    private fun addField(
        targetClass: KtClass,
        field: EntityFieldModel,
    ) {
        val psiFactory = KtPsiFactory(targetClass.project)
        if (shouldAddToConstructor(targetClass)) {
            val parameterList = targetClass.createPrimaryConstructorParameterListIfAbsent()
            val parameter = psiFactory.createParameter(field.declarationText.toConstructorParameterText())
            parameterList.addParameter(parameter)
            normalizeConstructorParameterList(parameterList)
            return
        }

        val property = psiFactory.createProperty(field.declarationText.toBodyPropertyText(targetClass.isInterface()))
        targetClass.addDeclaration(property)
    }

    private fun normalizeConstructorParameterList(parameterList: KtParameterList) {
        val psiFactory = KtPsiFactory(parameterList.project)
        val parameterText = parameterList.parameters.joinToString(",\n") { parameter ->
            "    ${parameter.text.trim()}"
        }
        val replacement = psiFactory.createParameterList("(\n$parameterText,\n)")
        parameterList.replace(replacement)
    }

    private fun shouldAddToConstructor(targetClass: KtClass): Boolean {
        if (targetClass.isInterface()) {
            return false
        }
        if (targetClass.isData()) {
            return true
        }
        return targetClass.primaryConstructor != null && targetClass.body == null
    }

    private fun addImports(
        file: KtFile,
        fqNames: List<String>,
    ) {
        val importList = file.importList ?: return
        val existingImports = file.importDirectives
            .mapNotNull { directive -> directive.importedFqName?.asString() }
            .toSet()
        val psiFactory = KtPsiFactory(file.project)
        fqNames
            .filter { fqName -> fqName !in existingImports }
            .filter { fqName -> fqName.substringBeforeLast('.', missingDelimiterValue = "") != file.packageFqName.asString() }
            .map { fqName -> psiFactory.createImportDirective(ImportPath(FqName(fqName), false)) }
            .forEach { directive -> importList.add(directive) }
    }

    private fun String.toConstructorParameterText(): String {
        return lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .joinToString(" ")
    }

    private fun String.toBodyPropertyText(targetIsInterface: Boolean): String {
        return lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .filterNot { line -> targetIsInterface && line.startsWith("@field:") }
            .joinToString("\n")
    }
}
