package site.addzero.smart.intentions.kotlin.inheritbase

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

internal object InheritFromBaseHistory {
    private const val HISTORY_KEY = "site.addzero.smart.intentions.kotlin.inheritbase.history"
    private const val MAX_HISTORY_SIZE = 20

    fun load(project: Project): List<String> {
        return PropertiesComponent.getInstance(project)
            .getValue(HISTORY_KEY)
            ?.lineSequence()
            ?.map { value -> value.trim() }
            ?.filter { value -> value.isNotBlank() }
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    fun remember(project: Project, typeText: String) {
        val normalizedTypeText = typeText.trim()
        if (normalizedTypeText.isBlank()) {
            return
        }
        val nextHistory = (listOf(normalizedTypeText) + load(project))
            .distinct()
            .take(MAX_HISTORY_SIZE)
        PropertiesComponent.getInstance(project).setValue(HISTORY_KEY, nextHistory.joinToString("\n"))
    }
}
