package site.addzero.dioxus.buddy.model

import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

data class CargoManifest(
    val manifestPath: Path,
    val rootPath: Path,
    val packageName: String,
    val edition: String,
)

data class ParsedDioxusPreviewTarget(
    val functionName: String,
    val attributeNames: List<String>,
    val attributeOffset: Int,
    val functionOffset: Int,
    val functionEndOffset: Int,
    val nameOffset: Int,
    val parameterText: String,
) {
    val hasParameters: Boolean = parameterText.trim().isNotBlank()
    val isExplicitPreview: Boolean = attributeNames.any { it == "dioxus_preview" || it == "preview" }
    val isComponent: Boolean = attributeNames.any { it == "component" }
}

data class DioxusPreviewTarget(
    val sourceFile: VirtualFile,
    val sourceText: String,
    val sourceRelativePath: String,
    val cargoManifest: CargoManifest,
    val parsed: ParsedDioxusPreviewTarget,
    val previewPort: Int,
) {
    val functionName: String = parsed.functionName
    val displayName: String = "${cargoManifest.packageName}::$functionName"
}
