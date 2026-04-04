package site.addzero.composebuddy.support

import org.jetbrains.kotlin.psi.KtParameter

object ComposePreviewSupport {
    fun sampleExpression(parameter: KtParameter, variant: String = "default"): String {
        val typeText = parameter.typeReference?.text.orEmpty()
        return when {
            typeText == "String" && variant == "error" -> "\"Error\""
            typeText == "String" -> "\"Sample\""
            typeText == "Int" -> "1"
            typeText == "Long" -> "1L"
            typeText == "Float" -> "1f"
            typeText == "Double" -> "1.0"
            typeText == "Boolean" && variant == "loading" -> "true"
            typeText == "Boolean" -> "false"
            typeText.contains("->") -> "{ }"
            typeText.startsWith("List<") -> "emptyList()"
            typeText.startsWith("Set<") -> "emptySet()"
            typeText.startsWith("Map<") -> "emptyMap()"
            typeText.endsWith("?") -> "null"
            else -> "TODO(\"sample\")"
        }
    }
}
