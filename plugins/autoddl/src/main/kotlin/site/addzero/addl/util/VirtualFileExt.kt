package site.addzero.addl.util

import com.intellij.openapi.vfs.VirtualFile
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi_impl.impl.intellij.virtualfile.language
import site.addzero.util.lsi_impl.impl.kt.virtualfile.toKotlinLsiClass
import site.addzero.util.lsi_impl.impl.psi.virtualfile.toJavaLsiClass
import site.addzero.util.lsi.constant.Language

/**
 * Convert VirtualFile to LsiClass
 * Unified implementation that handles both Java and Kotlin files
 *
 * Dynamically determines if file is Java or Kotlin and converts to appropriate class type:
 * - For Java files: uses `toJavaLsiClass()` from lsi-psi module
 * - For Kotlin files: uses `toKotlinLsiClass()` from lsi-kt module
 *
 * @return LsiClass instance or null if file doesn't contain a valid class
 */
fun VirtualFile?.toLsiClass(): LsiClass? {
    if (this == null) return null
    
    return when (language) {
        Language.Java -> toJavaLsiClass()
        Language.Kotlin -> toKotlinLsiClass()
    }
}
