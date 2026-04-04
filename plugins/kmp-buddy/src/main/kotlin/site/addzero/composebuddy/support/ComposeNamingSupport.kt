package site.addzero.composebuddy.support

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object ComposeNamingSupport {
    fun uniqueFunctionName(ownerFunction: KtNamedFunction, baseName: String): String {
        val existingNames = ownerFunction.containingKtFile.collectDescendantsOfType<KtNamedFunction>()
            .mapNotNull { it.name }
            .toSet()
        if (baseName !in existingNames) {
            return baseName
        }
        var index = 2
        while (true) {
            val candidate = "$baseName$index"
            if (candidate !in existingNames) {
                return candidate
            }
            index++
        }
    }

    fun uniqueTypeName(ownerFunction: KtNamedFunction, baseName: String): String {
        val text = ownerFunction.containingKtFile.text
        if (!Regex("\\b$baseName\\b").containsMatchIn(text)) {
            return baseName
        }
        var index = 2
        while (true) {
            val candidate = "$baseName$index"
            if (!Regex("\\b$candidate\\b").containsMatchIn(text)) {
                return candidate
            }
            index++
        }
    }
}
