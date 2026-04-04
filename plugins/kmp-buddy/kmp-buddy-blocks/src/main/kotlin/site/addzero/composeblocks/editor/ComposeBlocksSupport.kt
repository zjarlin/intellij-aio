package site.addzero.composeblocks.editor

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.composeblocks.managed.ManagedComposeDocumentCodec
import site.addzero.composeblocks.model.ComposeBlocksMode

private val SELECTED_MODE_KEY = Key.create<ComposeBlocksMode>("compose.blocks.selected.mode")
private val PROGRESSIVE_EXPANSION_KEY = Key.create<Boolean>("compose.blocks.progressive.expansion")

internal fun VirtualFile.isComposeKotlinFile(project: Project): Boolean {
    if (extension != "kt" && extension != "kts") {
        return false
    }

    val text = readText(project) ?: return false
    return text.contains("@Composable")
}

internal fun VirtualFile.isManagedComposeBlocksFile(project: Project): Boolean {
    val text = readText(project) ?: return false
    return ManagedComposeDocumentCodec.isManagedDocument(text)
}

internal fun VirtualFile.defaultComposeBlocksMode(project: Project): ComposeBlocksMode? {
    if (!isComposeKotlinFile(project)) {
        return null
    }
    return if (isManagedComposeBlocksFile(project)) {
        ComposeBlocksMode.BUILDER
    } else {
        ComposeBlocksMode.INSPECT
    }
}

internal fun VirtualFile.selectedComposeBlocksMode(project: Project): ComposeBlocksMode {
    if (!isComposeKotlinFile(project)) {
        return ComposeBlocksMode.TEXT
    }
    return getUserData(SELECTED_MODE_KEY) ?: ComposeBlocksMode.TEXT
}

internal fun VirtualFile.setSelectedComposeBlocksMode(mode: ComposeBlocksMode) {
    putUserData(SELECTED_MODE_KEY, mode)
}

internal fun VirtualFile.isProgressiveExpansionEnabled(): Boolean {
    return getUserData(PROGRESSIVE_EXPANSION_KEY) ?: false
}

internal fun VirtualFile.setProgressiveExpansionEnabled(enabled: Boolean) {
    putUserData(PROGRESSIVE_EXPANSION_KEY, enabled)
}

internal fun VirtualFile.supportsComposeBlocksBuilder(project: Project): Boolean {
    return isManagedComposeBlocksFile(project)
}

private fun VirtualFile.readText(project: Project): String? {
    val document = FileDocumentManager.getInstance().getDocument(this)
    if (document != null) {
        return document.text
    }

    return try {
        inputStream.bufferedReader().use { reader ->
            reader.readText()
        }
    } catch (_: Exception) {
        null
    }
}
