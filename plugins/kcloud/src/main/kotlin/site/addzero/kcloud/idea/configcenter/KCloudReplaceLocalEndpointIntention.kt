package site.addzero.kcloud.idea.configcenter

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class KCloudReplaceLocalEndpointIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return "kcloud"
    }

    override fun getText(): String {
        return "替换为 KCloud 本地端点"
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(
        project: Project,
        editor: Editor?,
        element: PsiElement,
    ): Boolean {
        return findCandidate(element) != null
    }

    override fun invoke(
        project: Project,
        editor: Editor?,
        element: PsiElement,
    ) {
        val candidate = findCandidate(element) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            candidate.apply(project)
        }
    }

    private fun findCandidate(element: PsiElement): KCloudLocalEndpointCandidate? {
        val property = element.getStrictParentOfType<KtProperty>() ?: return null
        val initializer = property.initializer as? KtStringTemplateExpression ?: return null
        if (!initializer.textRange.contains(element.textRange)) {
            return null
        }
        if (!property.containingFile.isJvmKotlinFile()) {
            return null
        }
        val url = initializer.text.toLiteralStringValue() ?: return null
        if (url !in supportedBaseUrls) {
            return null
        }
        return KCloudLocalEndpointCandidate(property, initializer)
    }
}

private data class KCloudLocalEndpointCandidate(
    val property: KtProperty,
    val initializer: KtStringTemplateExpression,
) {
    fun apply(project: Project) {
        if (property.hasModifier(KtTokens.CONST_KEYWORD)) {
            property.removeModifier(KtTokens.CONST_KEYWORD)
        }
        val factory = KtPsiFactory(project)
        val replacement = factory.createExpression("KCloudLocalServerEndpoint.currentBaseUrl()")
        initializer.replace(replacement)
        property.containingKtFile.ensureImport(
            project = project,
            importText = K_CLOUD_LOCAL_SERVER_ENDPOINT_IMPORT,
        )
    }
}

private const val K_CLOUD_LOCAL_SERVER_ENDPOINT_IMPORT = "site.addzero.kcloud.KCloudLocalServerEndpoint"

private val supportedBaseUrls = setOf(
    "http://localhost:18080/",
    "http://127.0.0.1:18080/",
)
