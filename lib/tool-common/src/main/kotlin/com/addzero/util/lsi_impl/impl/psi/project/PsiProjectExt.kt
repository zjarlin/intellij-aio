package com.addzero.util.lsi_impl.impl.psi.project

import com.addzero.util.lsi.assist.getDefaultValueForType
import com.addzero.util.lsi.impl.kt.ktClassToJson
import com.addzero.util.lsi.assist.isCustomObjectType
import com.addzero.util.lsi_impl.impl.intellij.virtualfile.toKtClass
import com.addzero.util.lsi_impl.impl.kt.ktClassToJson
import com.addzero.util.lsi_impl.impl.psi.model.PsiCtx
import com.google.gson.JsonObject
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile


fun Project.allpsiCtx(): PsiCtx {
    // 获取所有 Java 文件
    val files = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(this))

    files.map {
        val psiFile = PsiManager.getInstance(this).findFile(it)
    }
    return TODO("提供返回值")
}

fun Project.psiCtx(): PsiCtx {
    val instance = FileEditorManager.getInstance(this)
    val editor = instance.selectedTextEditor
    val virtualFile = instance.getSelectedEditor()?.file
    val psiFile = PsiManager.getInstance(this).findFile(virtualFile!!)

    val ktClass = virtualFile.toKtClass(this)

    val psiClass = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)
    // 如果是Java文件，可以转换成PsiJavaFile

    // 先获取到文件后再获取文件所在包
//        val daoPackage = (psiClass?.getContainingFile() as PsiJavaFile).packageName
//        val name = psiClass.name
//        val addPrefixIfNot = daoPackage.addSuffixIfNot(".$name")
//        val loadClass = ClassUtil.loadClass<Any>(addPrefixIfNot)

    val any = if (psiFile is PsiJavaFile) {
        // 一个文件中可能会定义有多个Class，因此返回的是一个数组
        val classes: Array<PsiClass> = psiFile.getClasses()
        classes
    } else if (psiFile is KtFile) {
        val classes = psiFile.classes
        classes
    } else {
        null
    }

    return PsiCtx(editor, psiClass, ktClass, psiFile, virtualFile, any)
}




fun Project.psiCtx(): PsiCtx {
    val instance = FileEditorManager.getInstance(this)

    val editor = instance.selectedTextEditor
    val virtualFile = instance.getSelectedEditor()?.file

    val psiFile = PsiManager.getInstance(this).findFile(virtualFile!!)

    val psiClass = PsiTreeUtil.findChildOfType(psiFile, PsiClass::class.java)

    val any = if (psiFile is PsiJavaFile) {
        // 一个文件中可能会定义有多个Class，因此返回的是一个数组
        val classes: Array<PsiClass> = psiFile.getClasses()
        classes
    } else {
        null
    }

    return PsiCtx(editor, psiClass, psiFile, virtualFile, any)
}

// 添加判断项目类型的方法
fun Project.isKotlinProject(): Boolean {
    val buildGradle = guessProjectDir()?.findChild("build.gradle.kts") ?: guessProjectDir()
        ?.findChild("build.gradle")

    return when {
        // 检查是否有 Kotlin 构建文件
        buildGradle != null -> {
            val content = buildGradle.inputStream.reader().readText()
            content.contains("kotlin") || content.contains("org.jetbrains.kotlin")
        }
        // 检查是否有 Kotlin 源文件
        else -> {
            false
        }
    }
}


/**
 * Helper: 创建 List 类型的 JSON 内容
 */
fun Project.createListJson(elementType: String): JsonObject {
    val listJson = JsonObject()
    if (isCustomObjectType(elementType)) {
        val elementClass = findKtClassByName(elementType, this)
        elementClass?.let { listJson.add("element", it.ktClassToJson(this)) }
    } else {
        listJson.addProperty("element", getDefaultValueForType(elementType))
    }
    return listJson
}


/**
 * 查找 KtClass by 名称
 */
fun Project.findKtClassByName(className: String): KtClass? {
    // 使用 JavaPsiFacade 查找类
    val psiFacade = JavaPsiFacade.getInstance(this)
    val scope = GlobalSearchScope.projectScope(this)

    // 先尝试直接查找完整类名
    val psiClass = psiFacade.findClass(className, scope)

    // 如果找到了类，并且是 Kotlin Light Class，则获取对应的 KtClass
    if (psiClass is KtLightClass) {
        return psiClass.kotlinOrigin as? KtClass
    }

    // 如果没有找到，尝试在不同的包中查找
    val shortName = className.substringAfterLast('.')
    val foundClasses = psiFacade.findClasses(shortName, scope)

    return foundClasses
        .filterIsInstance<KtLightClass>()
        .firstOrNull { it.qualifiedName == className }
        ?.kotlinOrigin as? KtClass
}
