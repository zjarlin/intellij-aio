/**
 * 针对类定义文件的工具方法
 */
package com.addzero.util.psi

// K2兼容API导入
import com.addzero.util.lsi.impl.psi.JavaNullableType
import com.addzero.util.meta.*
import com.addzero.util.meta.VirtualFileUtils.language
import com.addzero.util.psi.clazz
import com.addzero.util.psi.javaclass.PsiClassUtil
import com.addzero.util.psi.ktclass.KtClassUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import kotlin.collections.contains

/**
 * 获取类文件中的类名Element
 */
fun VirtualFile.nameIdentifier(project: Project): PsiNameIdentifierOwner? {
    return try {
        when (language) {
            Language.Java -> {
                psiClass(project) as PsiNameIdentifierOwner?
            }

            Language.Kotlin -> {
                ktClass(project) as PsiNameIdentifierOwner?
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
                psiClass(project)?.annotations?.map { it.qualifiedName ?: "" }
            }

            Language.Kotlin -> {
                val file = this
                file.ktClass(project)?.annotationEntries?.map { it.qualifiedName }
            }
        }
    } catch (e: IllegalFileFormatException) {
        emptyList()
    }
    return annotations ?: emptyList()
}



/**
 * 获取Kotlin类文件中的实体类定义
 *
 * @param propPath 进一步获取[propPath]属性的类型的类定义
 */
fun VirtualFile.ktClass(project: Project): KtClass? {
    val toPsiFile = toPsiFile(project)
    val ktClass = toPsiFile?.clazz<KtClass>()
    return ktClass
}

/**
 * 获取KtAnnotationEntry对应注解的全限定名
 */
private val KtAnnotationEntry.qualifiedName: String
    get() {
        val shortName = typeReference?.text ?: return ""
// 解析为 FqName（需要解析导入）
        return FqName(shortName).asString()
    }




