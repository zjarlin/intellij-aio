package site.addzero.composebuddy.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.analysis.ComposeFunctionSupport
import site.addzero.composebuddy.refactor.ComposeRefactorEngine
import site.addzero.composebuddy.refactor.ComposeRefactorRequest

class NormalizeComposeSignatureIntention : PsiElementBaseIntentionAction(), DumbAware {
    override fun getFamilyName(): String = ComposeBuddyBundle.message("plugin.name")

    override fun getText(): String = ComposeBuddyBundle.message("intention.normalize.signature")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return false
        val analysis = ComposeFunctionSupport.analyzeSignature(function) ?: return false
        return analysis.propsCandidates.isNotEmpty() || analysis.eventCandidates.isNotEmpty() || analysis.statePairs.isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = element.getStrictParentOfType<KtNamedFunction>() ?: return
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
