package site.addzero.composeblocks.editor

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal fun VirtualFile.isComposeKotlinFile(project: Project): Boolean {
    if (extension != "kt" && extension != "kts") {
        return false
    }

    val document = FileDocumentManager.getInstance().getDocument(this)
    if (document != null) {
        return document.text.contains("@Composable")
    }

    return try {
        inputStream.bufferedReader().use { reader ->
            reader.readText().contains("@Composable")
        }
    } catch (_: Exception) {
        false
    }
}
