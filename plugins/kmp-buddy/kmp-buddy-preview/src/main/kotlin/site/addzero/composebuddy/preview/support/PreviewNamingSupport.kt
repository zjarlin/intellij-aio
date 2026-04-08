package site.addzero.composebuddy.preview.support

import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object PreviewNamingSupport {
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
}
