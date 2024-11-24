package com.addzero.addl.action.autoddlwithdb.scanner

import com.addzero.addl.settings.SettingContext
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

fun findAllEntityClasses(project: Project): List<PsiClass> {
    val scanPkg = SettingContext.settings.scanPkg
    val javaChecker = JavaEntityAnnotationChecker()
    val kotlinChecker = KotlinEntityAnnotationChecker()

    // 获取 JavaPsiFacade 实例
    val javaPsiFacade = JavaPsiFacade.getInstance(project)
    val psiPackage = javaPsiFacade.findPackage(scanPkg) ?: return emptyList()

    val entityClasses = mutableListOf<PsiClass>()

    // 遍历所有目录
    psiPackage.directories.forEach { directory ->
        // 处理 Java 类
        val files = directory.files
        files.filterIsInstance<PsiClass>()
//            .filter { javaChecker.isEntityClass(it) }
            .forEach { entityClasses.add(it) }

        // 处理 Kotlin 类
        files.filterIsInstance<KtFile>().forEach { ktFile ->
            ktFile.declarations.filterIsInstance<KtClass>()
//                .filter { kotlinChecker.isEntityClass(it) }
                .mapNotNull { it.toLightClass() }
                .forEach { entityClasses.add(it) }
        }
    }

    return entityClasses
}