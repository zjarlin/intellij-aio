package site.addzero.composebuddy.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import site.addzero.composebuddy.refactor.ComposeRefactorEngine

class GenerateWrapperPropsQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("quickfix.generate.wrapper.props")

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(ComposeBuddyBundle.message("quickfix.generate.wrapper.props.preview"))
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val function = descriptor.psiElement.getStrictParentOfType<KtNamedFunction>() ?: return
        val analysis = ComposeFunctionSupport.analyzeWrapper(function) ?: return
        ComposeRefactorEngine(project).generateWrapperProps(analysis)
    }
}
