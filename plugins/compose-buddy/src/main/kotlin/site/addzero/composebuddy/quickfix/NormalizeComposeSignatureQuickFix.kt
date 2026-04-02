package site.addzero.composebuddy.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import site.addzero.composebuddy.refactor.ComposeRefactorEngine
import site.addzero.composebuddy.refactor.ComposeRefactorRequest

class NormalizeComposeSignatureQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("quickfix.normalize.signature")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val function = descriptor.psiElement.getStrictParentOfType<KtNamedFunction>() ?: return
        val analysis = ComposeFunctionSupport.analyzeSignature(function) ?: return
        ComposeRefactorEngine(project).normalizeSignature(
            analysis = analysis,
            request = ComposeRefactorRequest(
                extractProps = analysis.propsCandidates.isNotEmpty(),
                extractEvents = analysis.eventCandidates.isNotEmpty(),
                extractState = analysis.statePairs.isNotEmpty(),
                propsTypeName = "${function.name}Props",
                eventsTypeName = "${function.name}Events",
                stateTypeName = "${function.name}State",
                keepCompatibilityFunction = false,
            ),
        )
    }
}
