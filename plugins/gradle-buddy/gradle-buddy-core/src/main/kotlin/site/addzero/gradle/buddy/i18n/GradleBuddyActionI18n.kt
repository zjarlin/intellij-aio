package site.addzero.gradle.buddy.i18n

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation

object GradleBuddyActionI18n {

    @JvmStatic
    fun sync(
        action: AnAction,
        presentation: Presentation?,
        textKey: String,
        descriptionKey: String? = null
    ) {
        val text = GradleBuddyBundle.message(textKey)
        val description = descriptionKey?.let(GradleBuddyBundle::message)

        action.templatePresentation.text = text
        action.templatePresentation.description = description

        if (presentation != null) {
            presentation.text = text
            presentation.description = description
        }
    }
}
