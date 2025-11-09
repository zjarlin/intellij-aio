package site.addzero.addl.action.autoddlwithdb.scanner

import site.addzero.addl.util.fieldinfo.PsiUtil.guessTableNameByAnno
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

fun findktEntityClasses(project: Project): List<KtClass> {
    val files1 = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
    val kotlinChecker = KotlinEntityAnnotationChecker()
    val filter = files1.flatMap {
        val findFile = PsiManager.getInstance(project).findFile(it)
        val b = findFile as KtFile
        b.declarations.filterIsInstance<KtClass>()
    }
    .filter { kotlinChecker.isEntityClass(it) }

    return filter
}



fun findktEntityClassesMap(project: Project): Map<KtClass, @NlsSafe String?> {
    val findktEntityClasses = findktEntityClasses(project)
    val map = findktEntityClasses.associateWith {
        val toLightClass = it.toLightClass() as PsiClass
        val guessTableNameByAnno = guessTableNameByAnno(toLightClass)
        guessTableNameByAnno
    }
    return map

}
