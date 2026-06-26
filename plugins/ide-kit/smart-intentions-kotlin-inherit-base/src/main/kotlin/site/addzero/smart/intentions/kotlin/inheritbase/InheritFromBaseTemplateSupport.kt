package site.addzero.smart.intentions.kotlin.inheritbase

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TextResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import site.addzero.smart.intentions.core.SmartIntentionsMessages
import site.addzero.smart.intentions.core.SmartPsiWriteSupport

internal object InheritFromBaseTemplateSupport {
    fun startTypeArgumentTemplate(
        editor: Editor,
        entry: KtSuperTypeListEntry,
        addedSuperType: AddedSuperType,
    ) {
        if (addedSuperType.typeArgumentRanges.isEmpty()) {
            moveCaretToEnd(editor, entry)
            return
        }
        val typeReference = entry.typeReference ?: return
        val typeReferenceStartOffset = typeReference.textRange.startOffset
        val builder = TemplateBuilderImpl(typeReference)
        addedSuperType.typeArgumentRanges.forEachIndexed { index, range ->
            val startOffset = typeReferenceStartOffset + range.first
            val endOffset = typeReferenceStartOffset + range.last + 1
            val placeholder = typeReference.findElementAt(range.first) ?: return@forEachIndexed
            val relativeRange = com.intellij.openapi.util.TextRange(
                startOffset - placeholder.textRange.startOffset,
                endOffset - placeholder.textRange.startOffset,
            )
            builder.replaceElement(
                placeholder,
                relativeRange,
                "TYPE_ARG_$index",
                TypeArgumentExpression(placeholder.text),
                true,
            )
        }
        SmartPsiWriteSupport.runWriteCommand(entry.project, SmartIntentionsMessages.INHERIT_FROM_BASE) {
            builder.run(editor, false)
        }
        ApplicationManager.getApplication().invokeLater {
            AutoPopupController.getInstance(entry.project).scheduleAutoPopup(editor)
        }
    }

    private fun moveCaretToEnd(editor: Editor, entry: PsiElement) {
        editor.caretModel.moveToOffset(entry.textRange.endOffset)
    }

    private class TypeArgumentExpression(
        private val defaultValue: String,
    ) : Expression() {
        override fun calculateResult(context: ExpressionContext): Result {
            return TextResult(defaultValue)
        }

        override fun calculateQuickResult(context: ExpressionContext): Result {
            return TextResult(defaultValue)
        }

        override fun calculateLookupItems(context: ExpressionContext): Array<LookupElement> {
            return emptyArray()
        }
    }
}
