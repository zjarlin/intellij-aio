package site.addzero.gradle.buddy.i18n

import com.intellij.openapi.application.ApplicationManager
import java.text.MessageFormat
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

object GradleBuddyBundle {

    private const val BUNDLE_NAME = "messages.GradleBundle"

    fun message(key: String, vararg params: Any?): String {
        val bundle = loadBundle()
        val pattern = try {
            bundle.getString(key)
        } catch (_: MissingResourceException) {
            key
        }

        // 无占位参数时直接返回原文，避免 TOML / JSON 示例里的花括号被 MessageFormat 误解析。
        if (params.isEmpty()) {
            return pattern
        }

        return MessageFormat(pattern, currentLocale()).format(params)
    }

    private fun loadBundle(): ResourceBundle {
        return ResourceBundle.getBundle(BUNDLE_NAME, currentLocale(), javaClass.classLoader)
    }

    private fun currentLocale(): Locale {
        val application = ApplicationManager.getApplication()
        if (application == null || application.isDisposed) {
            return GradleBuddyLanguage.ZH.locale
        }
        return GradleBuddyUiSettingsService.getInstance().getLocale()
    }
}
