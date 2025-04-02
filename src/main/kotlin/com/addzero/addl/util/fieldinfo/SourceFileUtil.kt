/**
 * 针对类定义文件的工具方法
 */
package com.addzero.addl.util.fieldinfo

// K2兼容API导入
import com.addzero.addl.util.meta.*
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

/**
 * 获取类文件中的类名Element
 */
fun VirtualFile.nameIdentifier(project: Project): PsiNameIdentifierOwner? {
    return try {
        when (language) {
            Language.Java -> {
                psiClass(project)
            }

            Language.Kotlin -> {
                ktClass(project)
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
        when (language) {
            Language.Java -> {
                psiClass(project)?.annotations?.map { it.qualifiedName ?: "" }
            }

            Language.Kotlin -> {
                val file = this
                file.ktClass(project)?.annotationEntries?.map(KtAnnotationEntry::qualifiedName)
            }
        }
    } catch (e: IllegalFileFormatException) {
        emptyList()
    }
    return annotations ?: emptyList()
}

fun PsiClass.supers(): List<PsiClass> {
    return supers.toList() + supers.map { it.supers.toList() }.flatten()
}


/**
 * 获取Java类文件中的实体类定义
 *
 * @param propPath 进一步获取[propPath]属性的类型的类定义
 */
fun VirtualFile.psiClass(project: Project, propPath: List<String> = emptyList()): PsiClass? {
    val psiClass = toPsiFile(project)?.clazz<PsiClass>()
    return if (propPath.isNotEmpty()) {
        psiClass?.prop(propPath, 0)?.returnType?.clazz()
    } else {
        psiClass
    }
}

fun PsiClass.prop(propPath: List<String>, level: Int): PsiMethod? {
    val prop = methods().find { it.name == propPath[level] }
    return if (propPath.lastIndex == level) {
        prop
    } else {
        prop?.returnType?.clazz()?.prop(propPath, level + 1)
    }
}

//fun getKtClassesFromVirtualFile(project: Project, virtualFile: VirtualFile): List<KtClass> {
//    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: return emptyList()
//
//    val filterIsInstance = psiFile.declarations.filterIsInstance<KtClass>()
//    return filterIsInstance
////    return analyze(psiFile) {
////    }
//}

/**
 * 获取Kotlin类文件中的实体类定义
 *
 * @param propPath 进一步获取[propPath]属性的类型的类定义
 */
fun VirtualFile.ktClass(project: Project): KtClass? {
    val ktClass = toPsiFile(project)?.clazz<KtClass>()
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

val PsiType.nullable: Boolean
    get() = presentableText in JavaNullableType.values().map { it.name }

fun PsiType.clazz(): PsiClass? {
    val generic = PsiUtil.resolveGenericsClassInType(this)
    return if (generic.substitutor == PsiSubstitutor.EMPTY) {
        generic.element
    } else {
        val propTypeParameters = generic.element?.typeParameters ?: return null
        generic.substitutor.substitute(propTypeParameters[0])?.clazz()
    }
}


inline fun <reified T : PsiNameIdentifierOwner> PsiFile.clazz(): T? {
    return PsiTreeUtil.findChildOfType(originalElement, T::class.java)
}

fun PsiClass.methods(): List<PsiMethod> {
    val supers = interfaces.filter { interfaceClass ->
        interfaceClass.annotations.any {
            it.qualifiedName in listOf(Constant.Annotation.ENTITY, Constant.Annotation.MAPPED_SUPERCLASS)
        }
    }
    return methods.toList() + supers.map { it.methods() }.flatten()
}

fun KtClass.properties(): List<KtProperty> {

    return this.getProperties()
        .map {
            it
        }


}

fun PsiClass.hasAnnotation(vararg annotations: String) = annotations.any { hasAnnotation(it) }

fun KtClass.hasAnnotation(vararg annotations: String) =
    annotations.any { annotationEntries.map(KtAnnotationEntry::qualifiedName).contains(it) }



