package site.addzero.gradle.buddy.linemarker

import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Application-level service that manages deprecated artifact metadata.
 * Data is persisted to ~/.config/gradle-buddy/cache/deprecated-artifacts.json
 * so it can be shared across projects.
 *
 * Registered in plugin.xml as applicationService.
 */
class DeprecatedArtifactService {

    private val cacheDir = File(System.getProperty("user.home"), ".config/gradle-buddy/cache")
    private val cacheFile = File(cacheDir, "deprecated-artifacts.json")
    private val entries = mutableMapOf<String, DeprecatedEntry>()

    init {
        loadFromDisk()
    }

    data class DeprecatedEntry(
        val group: String,
        val artifact: String,
        val message: String,
        val deprecatedAt: String
    ) {
        val coordinate: String get() = "$group:$artifact"
    }

    fun isDeprecated(group: String, artifact: String): Boolean {
        return entries.containsKey("$group:$artifact")
    }

    fun getEntry(group: String, artifact: String): DeprecatedEntry? {
        return entries["$group:$artifact"]
    }

    fun deprecate(group: String, artifact: String, message: String) {
        val entry = DeprecatedEntry(
            group = group,
            artifact = artifact,
            message = message,
            deprecatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
        entries[entry.coordinate] = entry
        saveToDisk()
    }

    fun undeprecate(group: String, artifact: String) {
        entries.remove("$group:$artifact")
        saveToDisk()
    }

    fun getAllDeprecated(): List<DeprecatedEntry> = entries.values.toList()

    private fun loadFromDisk() {
        if (!cacheFile.exists()) return
        try {
            val text = cacheFile.readText()
            parseJson(text).forEach { entries[it.coordinate] = it }
        } catch (e: Exception) {
            LOG.warn("Failed to load deprecated artifacts cache", e)
        }
    }

    private fun saveToDisk() {
        try {
            cacheDir.mkdirs()
            cacheFile.writeText(toJson(entries.values.toList()))
        } catch (e: Exception) {
            LOG.warn("Failed to save deprecated artifacts cache", e)
        }
    }

    // Minimal JSON serialization without external dependencies
    private fun toJson(list: List<DeprecatedEntry>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        list.forEachIndexed { index, entry ->
            sb.append("  {\n")
            sb.append("    \"group\": ${jsonEscape(entry.group)},\n")
            sb.append("    \"artifact\": ${jsonEscape(entry.artifact)},\n")
            sb.append("    \"message\": ${jsonEscape(entry.message)},\n")
            sb.append("    \"deprecatedAt\": ${jsonEscape(entry.deprecatedAt)}\n")
            sb.append("  }")
            if (index < list.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun parseJson(text: String): List<DeprecatedEntry> {
        val result = mutableListOf<DeprecatedEntry>()
        // Simple regex-based parser for our known JSON structure
        val entryPattern = Regex("""\{[^}]+}""", RegexOption.DOT_MATCHES_ALL)
        val fieldPattern = Regex(""""(\w+)"\s*:\s*"((?:[^"\\]|\\.)*)"""")

        for (match in entryPattern.findAll(text)) {
            val fields = mutableMapOf<String, String>()
            for (fieldMatch in fieldPattern.findAll(match.value)) {
                fields[fieldMatch.groupValues[1]] = fieldMatch.groupValues[2]
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
            }
            val group = fields["group"] ?: continue
            val artifact = fields["artifact"] ?: continue
            val message = fields["message"] ?: ""
            val deprecatedAt = fields["deprecatedAt"] ?: ""
            result.add(DeprecatedEntry(group, artifact, message, deprecatedAt))
        }
        return result
    }

    private fun jsonEscape(s: String): String {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    }

    companion object {
        private val LOG = logger<DeprecatedArtifactService>()

        fun getInstance(): DeprecatedArtifactService =
            com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(DeprecatedArtifactService::class.java)
    }
}
