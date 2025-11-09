package site.addzero.util.lsi_impl.impl.intellij.virtualfile

import site.addzero.util.lsi.constant.Language
import site.addzero.util.lsi.exp.IllegalFileFormatException
import site.addzero.util.lsi_impl.impl.kt.anno.qualifiedName
import site.addzero.util.lsi_impl.impl.psi.element.convertTo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtClass

/**
 * 获取Kotlin类文件中的实体类定义
 *
 * @param propPath 进一步获取[propPath]属性的类型的类定义
 */
fun VirtualFile.toKtClass(project: Project): KtClass? {
    val toPsiFile = this.toPsiFile(project)
    val ktClass = toPsiFile?.convertTo<KtClass>()
    return ktClass
}


fun VirtualFile.toPsiClass(project: Project): PsiClass? {
    val toPsiFile = this.toPsiFile(project)
    val ktClass = toPsiFile?.convertTo<PsiClass>()
    return ktClass
}


/**
 * 扩展属性：获取 VirtualFile 的语言类型
 * 根据文件扩展名判断语言类型
 * @return Language 枚举值，包括 Java、Kotlin 等
 * @throws site.addzero.util.lsi.exp.IllegalFileFormatException 当文件类型不支持时抛出异常
 */
val VirtualFile.language: Language
    get() {
        return when (val fileType = extension) {
            "java" -> Language.Java
            "kt" -> Language.Kotlin
            else -> throw IllegalFileFormatException(fileType ?: "<no-type>")
        }
    }

/**
 * 获取类文件中的类名Element
 */
fun VirtualFile.toPsiNameIdentifierOwner(project: Project): PsiNameIdentifierOwner? {
    return try {
        when (language) {
            Language.Java -> {
                toPsiClass(project) as PsiNameIdentifierOwner?
            }

            Language.Kotlin -> {
                toKtClass(project) as PsiNameIdentifierOwner?
            }
        }
    } catch (e: IllegalFileFormatException) {
        null
    }
}

/**
 * 获取类文件中类的注解，全限定名
 */
fun VirtualFile.annotations(project: Project): List<String> {
    val annotations = try {
        when (this.language) {
            Language.Java -> {
                this.toPsiClass(project)?.annotations?.map { it.qualifiedName ?: "" }
            }

            Language.Kotlin -> {
                this.toKtClass(project)?.annotationEntries?.map { it.qualifiedName }
            }
        }
    } catch (e: IllegalFileFormatException) {
        emptyList()
    }
    return annotations ?: emptyList()
}



