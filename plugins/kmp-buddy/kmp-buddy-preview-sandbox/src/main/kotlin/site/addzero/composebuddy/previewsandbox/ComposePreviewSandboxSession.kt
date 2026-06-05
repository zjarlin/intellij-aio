package site.addzero.composebuddy.previewsandbox

import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.psi.KtNamedFunction

data class ComposePreviewSandboxSession(
    val snapshot: PreviewSandboxSnapshot,
    val written: PreviewSandboxWriteResult,
    val previewFunctionPointer: SmartPsiElementPointer<KtNamedFunction>,
)

