package com.addzero.util.lsi_impl.impl.psi.clazz

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

/**
 * PsiClass 㐌�~iU
 */

/**
 * 9n{
�~cn� PsiClass
 * Sy�-X(*
{�H�SM{� import -�~
 *
 * @param className ��~�{
�U
�
&

 * @param project y
 * @return ~0� PsiClass��~
0�� null
 */
fun PsiClass.resolveClassByName(className: String, project: Project): PsiClass? {
    val classes = PsiShortNamesCache.getInstance(project)
        .getClassesByName(className, GlobalSearchScope.projectScope(project))

    return when {
        classes.isEmpty() -> null
        classes.size == 1 -> classes[0]
        else -> findClassFromImports(classes)
    }
}

/**
 * ��e��-�~9M�{
 * (��d
{�gI
 *
 * @param classes 	{p�
 * @return 9M� PsiClass��~
0�� null
 */
fun PsiClass.findClassFromImports(classes: Array<PsiClass>): PsiClass? {
    val containingFile = this.containingFile as? PsiJavaFile ?: return null
    val importList = containingFile.importList ?: return null
    val importedQualifiedNames = importList.importStatements.mapNotNull { it.qualifiedName }.toSet()

    return classes.firstOrNull { psiClass ->
        val qualifiedName = psiClass.qualifiedName
        qualifiedName != null && importedQualifiedNames.contains(qualifiedName)
    }
}

/**
 * @deprecated ( resolveClassByName ��}
�p
 */
@Deprecated(
    "Use resolveClassByName instead for better naming clarity",
    ReplaceWith("this.resolveClassByName(className, project)", "com.addzero.util.lsi.impl.psi.clazz.resolveClassByName")
)
fun PsiClass.detectCorrectClassByName(className: String, project: Project): PsiClass? {
    return resolveClassByName(className, project)
}
