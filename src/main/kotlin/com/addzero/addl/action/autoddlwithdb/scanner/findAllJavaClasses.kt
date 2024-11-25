package com.addzero.addl.action.autoddlwithdb.scanner

import com.addzero.addl.util.fieldinfo.PsiUtil.guessTableNameByAnno
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

/**
 * 在项目中查找所有符合条件的 PsiClass
 * @param project 当前项目
 * @return 符合条件的 PsiClass 列表
 */
fun findJavaEntityClasses(project: Project): List<PsiClass> {
    // 获取所有 .java 文件
    val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

    val psiManager = PsiManager.getInstance(project)
    val javaChecker = JavaEntityAnnotationChecker()

    // 遍历每个文件，解析其中的 PsiClass
    return javaFiles.flatMap { virtualFile ->
        val psiFile = psiManager.findFile(virtualFile)
        if (psiFile != null) {
            // 从 Psi 文件中提取所有类
            psiFile.children.filterIsInstance<PsiClass>()
        } else {
            emptyList()
        }
    }.filter { javaChecker.isEntityClass(it) }
}

fun findJavaEntityClassesMap(project: Project): List<Pair<PsiClass, @NlsSafe String?>> {
    val findktEntityClasses = findJavaEntityClasses(project)
    val mapNotNull = findktEntityClasses.map {
        val guessTableNameByAnno = guessTableNameByAnno(it)
        it to guessTableNameByAnno
    }
    return mapNotNull
}