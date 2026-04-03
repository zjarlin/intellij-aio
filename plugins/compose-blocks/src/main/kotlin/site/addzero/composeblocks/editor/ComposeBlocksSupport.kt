package site.addzero.composeblocks.editor

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.composeblocks.managed.ManagedComposeDocumentCodec
import site.addzero.composeblocks.model.ComposeBlocksMode

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

internal fun VirtualFile.composeBlocksMode(project: Project): ComposeBlocksMode? {
    if (!isComposeKotlinFile(project)) {
        return null
    }
    return if (isManagedComposeBlocksFile(project)) {
        ComposeBlocksMode.BUILDER
    } else {
        ComposeBlocksMode.INSPECT
    }
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
