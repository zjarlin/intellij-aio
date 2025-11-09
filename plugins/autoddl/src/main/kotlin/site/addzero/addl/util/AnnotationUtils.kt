package site.addzero.addl.util

import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.constants.*

class AnnotationUtils {

    companion object {

        fun getAnnotationValue(
            annotationEntry: PsiAnnotation,
            attributeName: String
        ): String {
            val s = annotationEntry.findAttributeValue(attributeName)?.text ?: ""
            return s
        }



        fun getAnnotationValue(
            annotationEntry: KtAnnotationEntry,
            attributeName: String
        ): String? {
            val toLightAnnotation = annotationEntry.toLightAnnotation()

            return annotationEntry.valueArguments.find {
                it.getArgumentName()?.asName?.asString() == attributeName
            }?.getArgumentExpression()?.let {
                when (it) {
                    is KtConstantExpression -> it.text
                    is KtStringTemplateExpression -> it.text
                    else -> null
                }
            }?.replace("\"", "")
        }

    }
}
