package site.addzero.composeblocks.editor

import com.intellij.diagnostic.PluginException
import com.intellij.util.IncorrectOperationException

internal fun PluginException.isEditorDisposalRace(): Boolean {
    return generateSequence(cause) { it.cause }
        .any { it is IncorrectOperationException }
}
