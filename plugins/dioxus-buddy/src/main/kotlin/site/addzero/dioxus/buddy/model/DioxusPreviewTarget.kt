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
    val canRenderDirectly: Boolean = when {
        isComponent -> componentParametersCanBeOmitted(parameterText)
        isExplicitPreview -> !hasParameters
        else -> !hasParameters
    }
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

private fun componentParametersCanBeOmitted(parameterText: String): Boolean {
    return splitTopLevelParameters(parameterText).all { parameter ->
        parameter.isBlank() || parameter.isChildrenParameter() || parameter.hasOptionalPropsMarker() || parameter.isOptionParameter()
    }
}

private fun String.isChildrenParameter(): Boolean {
    val cleaned = withoutOuterAttributes()
    val name = cleaned.substringBefore(':', missingDelimiterValue = "")
        .trim()
        .removePrefix("mut ")
        .trim()
        .substringAfterLast(' ')
    val type = cleaned.substringAfter(':', missingDelimiterValue = "").trim()
    return name == "children" && (type.contains("Element") || type.contains("Children"))
}

private fun String.hasOptionalPropsMarker(): Boolean {
    return Regex("""#\s*\[\s*props\s*\([^]]*\b(default|optional)\b""").containsMatchIn(this)
}

private fun String.isOptionParameter(): Boolean {
    val type = withoutOuterAttributes()
        .substringAfter(':', missingDelimiterValue = "")
        .trim()
    return type.startsWith("Option<") || type.startsWith("std::option::Option<")
}

private fun String.withoutOuterAttributes(): String {
    return replace(Regex("""#\s*\[[^]]*]\s*"""), "").trim()
}

private fun splitTopLevelParameters(text: String): List<String> {
    if (text.trim().isEmpty()) return emptyList()

    val parameters = mutableListOf<String>()
    var start = 0
    var parenDepth = 0
    var bracketDepth = 0
    var braceDepth = 0
    var angleDepth = 0
    var stringDelimiter: Char? = null
    var escaped = false
    var index = 0

    while (index < text.length) {
        val char = text[index]
        val delimiter = stringDelimiter
        if (delimiter != null) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == delimiter) {
                stringDelimiter = null
            }
            index++
            continue
        }

        when (char) {
            '"', '\'' -> stringDelimiter = char
            '(' -> parenDepth++
            ')' -> if (parenDepth > 0) parenDepth--
            '[' -> bracketDepth++
            ']' -> if (bracketDepth > 0) bracketDepth--
            '{' -> braceDepth++
            '}' -> if (braceDepth > 0) braceDepth--
            '<' -> angleDepth++
            '>' -> if (angleDepth > 0) angleDepth--
            ',' -> if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0) {
                parameters += text.substring(start, index).trim()
                start = index + 1
            }
        }
        index++
    }

    parameters += text.substring(start).trim()
    return parameters.filter { it.isNotBlank() }
}
