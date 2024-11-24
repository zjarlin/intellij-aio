package com.addzero.addl.action.autoddlwithdb.scanner

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_TYPES
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

fun findktEntityClasses( project: Project): List<KtClass> {
    val files1 = FileTypeIndex.getFiles( KotlinFileType.INSTANCE, GlobalSearchScope .projectScope(project))
    val kotlinChecker = KotlinEntityAnnotationChecker()
    val map = files1.flatMap {
        val findFile = PsiManager.getInstance(project).findFile(it)
        val b = findFile as KtFile
        b.declarations.filterIsInstance<KtClass>()
    }
//    .filter { kotlinChecker.isEntityClass(it) }

    return map
}

//fun findjavaEntityClasses(pkg: String, project: Project): List<KtClass> {
//    val files1 = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
//    val kotlinChecker = JavaEntityAnnotationChecker()
//    val map = files1.flatMap {
//        val findFile = PsiManager.getInstance(project).findFile(it)
//        val b = findFile as KtFile
//        b.declarations.filterIsInstance<KtClass>()
//    }.filter { kotlinChecker.isEntityClass(it) }
//
//    return map
//}