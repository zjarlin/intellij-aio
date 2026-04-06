package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

class VersionCatalogFixVersionQuickFix : LocalQuickFix {

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getName(): String = GradleBuddyBundle.message("intention.version.catalog.fix.version")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val file = element.containingFile ?: return
        val dep = VersionCatalogDependencyHelper.detectCatalogDependencyLenientAt(element) ?: return
        if (!VersionCatalogFixVersionSupport.isAvailable(file, dep)) return
        VersionCatalogFixVersionSupport.apply(project, file, dep)
    }
}
