package site.addzero.smart.intentions.kotlin.inheritbase

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import site.addzero.smart.intentions.core.SmartIntentionsMessages
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object InheritFromBaseSupport {
    fun isApplicable(klass: KtClass, caretOffset: Int): Boolean {
        if (klass.isEnum() || klass.isAnnotation()) {
            return false
        }
        val classHeaderEndOffset = klass.body?.lBrace?.textOffset ?: klass.textRange.endOffset
        return caretOffset <= classHeaderEndOffset
    }

    fun chooseAndApply(project: Project, editor: Editor, klass: KtClass) {
        val dialog = InheritFromBaseDialog(project)
        if (!dialog.showAndGet()) {
            return
        }
        val choice = dialog.selectedChoice()
        InheritFromBaseHistory.remember(project, choice.typeText)
        apply(project, editor, klass, choice)
    }

    fun apply(project: Project, editor: Editor, klass: KtClass, choice: BaseTypeChoice): KtSuperTypeListEntry? {
        var addedEntry: KtSuperTypeListEntry? = null
        var addedSuperType: AddedSuperType? = null
        SmartPsiWriteSupport.runWriteCommand(project, SmartIntentionsMessages.INHERIT_FROM_BASE) {
            val importAwareTypeText = renderImportAwareTypeText(klass, choice)
            val superType = buildSuperType(importAwareTypeText, choice.typeParameterNames)
            addedSuperType = superType
            val psiFactory = KtPsiFactory(project)
            val entry = psiFactory.createSuperTypeEntry(superType.superTypeText)
            val insertedEntry = klass.addSuperTypeListEntry(entry)
            addedEntry = CodeStyleManager.getInstance(project).reformat(insertedEntry) as? KtSuperTypeListEntry
            addImportIfNeeded(klass.containingKtFile, choice)
        }
        val entry = addedEntry ?: return null
        val superType = addedSuperType ?: return entry
        InheritFromBaseTemplateSupport.startTypeArgumentTemplate(editor, entry, superType)
        return entry
    }

    fun classAt(project: Project, editor: Editor): KtClass? {
        val file = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        return file.findElementAt(editor.caretModel.offset)?.getNonStrictParentOfType<KtClass>()
    }

    private fun renderImportAwareTypeText(klass: KtClass, choice: BaseTypeChoice): String {
        val genericSuffix = choice.typeText.substringAfter('<', missingDelimiterValue = "")
            .takeIf { suffix -> suffix.isNotBlank() }
            ?.let { suffix -> "<$suffix" }
            .orEmpty()
        val currentPackageName = klass.containingKtFile.packageFqName.asString()
        val qualifiedName = choice.qualifiedName
        if (qualifiedName == null) {
            return choice.typeText
        }
        if (choice.packageName == currentPackageName) {
            return choice.displayName + genericSuffix
        }
        if (hasImport(klass.containingKtFile, qualifiedName)) {
            return choice.displayName + genericSuffix
        }
        return choice.displayName + genericSuffix
    }

    private fun buildSuperType(typeText: String, typeParameterNames: List<String>): AddedSuperType {
        val normalizedTypeText = typeText.trim().removeSuffix("()")
        if (normalizedTypeText.contains('<')) {
            return AddedSuperType(
                superTypeText = normalizedTypeText,
                typeArgumentRanges = parseExistingTypeArgumentRanges(normalizedTypeText),
            )
        }
        if (typeParameterNames.isEmpty()) {
            return AddedSuperType(normalizedTypeText, emptyList())
        }
        val placeholders = typeParameterNames.mapIndexed { index, typeParameterName ->
            val candidate = typeParameterName.trim().takeIf { value -> value.isNotBlank() } ?: "T${index + 1}"
            "${candidate}Arg"
        }
        val superTypeText = "$normalizedTypeText<${placeholders.joinToString(", ")}>"
        val ranges = mutableListOf<IntRange>()
        var searchStart = normalizedTypeText.length + 1
        placeholders.forEach { placeholder ->
            val start = superTypeText.indexOf(placeholder, searchStart)
            if (start >= 0) {
                ranges += start until start + placeholder.length
                searchStart = start + placeholder.length
            }
        }
        return AddedSuperType(superTypeText, ranges)
    }

    private fun parseExistingTypeArgumentRanges(typeText: String): List<IntRange> {
        val start = typeText.indexOf('<')
        val end = typeText.lastIndexOf('>')
        if (start < 0 || end <= start) {
            return emptyList()
        }
        val genericText = typeText.substring(start + 1, end)
        val ranges = mutableListOf<IntRange>()
        var depth = 0
        var argumentStart = 0
        genericText.forEachIndexed { index, char ->
            when (char) {
                '<' -> depth += 1
                '>' -> depth -= 1
                ',' -> {
                    if (depth == 0) {
                        addArgumentRange(ranges, genericText, argumentStart, index, start + 1)
                        argumentStart = index + 1
                    }
                }
            }
        }
        addArgumentRange(ranges, genericText, argumentStart, genericText.length, start + 1)
        return ranges
    }

    private fun addArgumentRange(
        ranges: MutableList<IntRange>,
        genericText: String,
        rawStart: Int,
        rawEnd: Int,
        offset: Int,
    ) {
        val trimmedStart = rawStart + genericText.substring(rawStart, rawEnd).indexOfFirst { char -> !char.isWhitespace() }
        if (trimmedStart < rawStart) {
            return
        }
        val trimmedEndExclusive = rawEnd - genericText.substring(rawStart, rawEnd).reversed()
            .indexOfFirst { char -> !char.isWhitespace() }
        if (trimmedEndExclusive <= trimmedStart) {
            return
        }
        ranges += (offset + trimmedStart) until (offset + trimmedEndExclusive)
    }

    private fun addImportIfNeeded(file: KtFile, choice: BaseTypeChoice) {
        val qualifiedName = choice.qualifiedName ?: return
        if (choice.packageName == null || choice.packageName == file.packageFqName.asString()) {
            return
        }
        if (hasImport(file, qualifiedName)) {
            return
        }
        val psiFactory = KtPsiFactory(file.project)
        val importDirective = psiFactory.createImportDirective(org.jetbrains.kotlin.resolve.ImportPath.fromString(qualifiedName))
        val importList = file.importList
        if (importList != null) {
            importList.add(importDirective)
            return
        }
        val packageDirective = file.packageDirective
        if (packageDirective != null) {
            file.addAfter(psiFactory.createNewLine(), packageDirective)
            file.addAfter(importDirective, packageDirective.nextSibling)
            return
        }
        file.addBefore(importDirective, file.firstChild)
    }

    private fun hasImport(file: KtFile, qualifiedName: String): Boolean {
        return file.importDirectives.any { directive -> directive.importedFqName?.asString() == qualifiedName }
    }
}
