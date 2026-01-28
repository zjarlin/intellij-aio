package site.addzero.dotfiles.repo

import site.addzero.dotfiles.model.ConstantSpec
import site.addzero.dotfiles.model.DotfilesSpec
import site.addzero.dotfiles.model.EnvScope
import site.addzero.dotfiles.model.EnvVarSpec
import site.addzero.dotfiles.model.TargetTemplate
import site.addzero.dotfiles.model.TemplateEngineId
import site.addzero.dotfiles.model.TemplateSourceType
import site.addzero.dotfiles.model.TemplateSpec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

class TomlDotfilesFormat(
    private val codec: DotfilesTomlCodec = DotfilesTomlCodec(),
) : DotfilesFormat {
    override val fileName: String = DotfilesLayout.specFileName

    override fun read(path: Path): DotfilesSpec {
        val content = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        return codec.decode(content)
    }

    override fun write(path: Path, spec: DotfilesSpec) {
        Files.write(path, codec.encode(spec).toByteArray(StandardCharsets.UTF_8))
    }
}

class DotfilesTomlCodec {
    fun decode(content: String): DotfilesSpec {
        var version = "1"
        val env = mutableListOf<EnvVarSpec>()
        val constants = mutableListOf<ConstantSpec>()
        val templates = mutableListOf<TemplateSpec>()
        val targets = mutableListOf<TargetTemplate>()

        var section: String? = null
        var current = mutableMapOf<String, Any?>()

        fun flush() {
            val active = section ?: return
            when (active) {
                "env" -> {
                    val key = current["key"] as? String ?: return
                    val value = current["value"] as? String ?: ""
                    val scope = (current["scope"] as? String)
                        ?.let { runCatching { EnvScope.valueOf(it) }.getOrNull() }
                        ?: EnvScope.USER
                    val secret = current["secret"] as? Boolean ?: false
                    env.add(EnvVarSpec(key = key, value = value, scope = scope, isSecret = secret))
                }
                "constants" -> {
                    val id = current["id"] as? String ?: return
                    val name = (current["name"] as? String) ?: id
                    val value = (current["value"] as? String) ?: ""
                    val type = (current["type"] as? String) ?: "String"
                    val languages = (current["languages"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    constants.add(ConstantSpec(id = id, name = name, value = value, type = type, languages = languages))
                }
                "templates" -> {
                    val id = current["id"] as? String ?: return
                    val description = current["description"] as? String ?: ""
                    val engine = (current["engine"] as? String)
                        ?.let { runCatching { TemplateEngineId.valueOf(it) }.getOrNull() }
                        ?: TemplateEngineId.KOTLIN_SCRIPT
                    val file = current["file"] as? String ?: ""
                    val sourceType = (current["sourceType"] as? String)
                        ?.let { runCatching { TemplateSourceType.valueOf(it) }.getOrNull() }
                        ?: TemplateSourceType.LOCAL
                    val sourceUri = current["sourceUri"] as? String
                    val sourcePath = current["sourcePath"] as? String
                    val sourceRef = current["sourceRef"] as? String
                    val cacheTtlSeconds = (current["cacheTtlSeconds"] as? Long)
                    val sha256 = current["sha256"] as? String
                    templates.add(
                        TemplateSpec(
                            id = id,
                            description = description,
                            engine = engine,
                            file = file,
                            sourceType = sourceType,
                            sourceUri = sourceUri,
                            sourcePath = sourcePath,
                            sourceRef = sourceRef,
                            cacheTtlSeconds = cacheTtlSeconds,
                            sha256 = sha256,
                        )
                    )
                }
                "targets" -> {
                    val id = current["id"] as? String ?: return
                    val templateId = current["templateId"] as? String ?: return
                    val outputPath = current["outputPath"] as? String ?: ""
                    val language = current["language"] as? String ?: ""
                    val packageName = current["packageName"] as? String
                    targets.add(
                        TargetTemplate(
                            id = id,
                            templateId = templateId,
                            outputPath = outputPath,
                            language = language,
                            packageName = packageName,
                        )
                    )
                }
            }
        }

        var envTableMode = false
        content.lines().forEach { rawLine ->
            val line = stripComments(rawLine).trim()
            if (line.isEmpty()) return@forEach

            if (line == "[env]") {
                flush()
                section = null
                current = mutableMapOf()
                envTableMode = true
                return@forEach
            }

            if (line.startsWith("[[") && line.endsWith("]]")) {
                flush()
                envTableMode = false
                section = line.removePrefix("[[").removeSuffix("]]").trim()
                current = mutableMapOf()
                return@forEach
            }

            val keyValue = splitKeyValue(line) ?: return@forEach
            val key = keyValue.first
            val value = parseValue(keyValue.second)

            if (key == "version" && section == null && !envTableMode) {
                version = value as? String ?: version
                return@forEach
            }

            if (envTableMode) {
                val envValue = value as? String ?: return@forEach
                env.add(EnvVarSpec(key = key, value = envValue))
                return@forEach
            }

            current[key] = value
        }

        flush()

        return DotfilesSpec(
            version = version,
            env = env,
            constants = constants,
            templates = templates,
            targets = targets,
        )
    }

    fun encode(spec: DotfilesSpec): String = buildString {
        appendLine("# dotfiles spec v${spec.version}")
        appendLine()
        appendLine("version = \"${spec.version}\"")
        appendLine()

        if (spec.env.isNotEmpty()) {
            spec.env.forEach { env ->
                appendLine("[[env]]")
                appendLine("key = \"${env.key}\"")
                appendLine("value = \"${env.value.replace("\"", "\\\"")}\"")
                if (env.scope != EnvScope.USER) {
                    appendLine("scope = \"${env.scope}\"")
                }
                if (env.isSecret) {
                    appendLine("secret = true")
                }
                appendLine()
            }
        }

        spec.constants.forEach { constant ->
            appendLine("[[constants]]")
            appendLine("id = \"${constant.id}\"")
            appendLine("name = \"${constant.name}\"")
            appendLine("value = \"${constant.value.replace("\"", "\\\"")}\"")
            appendLine("type = \"${constant.type}\"")
            if (constant.languages.isNotEmpty()) {
                val langs = constant.languages.joinToString(", ") { "\"$it\"" }
                appendLine("languages = [$langs]")
            }
            appendLine()
        }

        spec.templates.forEach { template ->
            appendLine("[[templates]]")
            appendLine("id = \"${template.id}\"")
            appendLine("description = \"${template.description.replace("\"", "\\\"")}\"")
            appendLine("engine = \"${template.engine}\"")
            appendLine("file = \"${template.file}\"")
            if (template.sourceType != TemplateSourceType.LOCAL) {
                appendLine("sourceType = \"${template.sourceType}\"")
            }
            template.sourceUri?.let { appendLine("sourceUri = \"${it.replace("\"", "\\\"")}\"") }
            template.sourcePath?.let { appendLine("sourcePath = \"${it.replace("\"", "\\\"")}\"") }
            template.sourceRef?.let { appendLine("sourceRef = \"${it.replace("\"", "\\\"")}\"") }
            template.cacheTtlSeconds?.let { appendLine("cacheTtlSeconds = $it") }
            template.sha256?.let { appendLine("sha256 = \"${it.replace("\"", "\\\"")}\"") }
            appendLine()
        }

        spec.targets.forEach { target ->
            appendLine("[[targets]]")
            appendLine("id = \"${target.id}\"")
            appendLine("templateId = \"${target.templateId}\"")
            appendLine("outputPath = \"${target.outputPath}\"")
            appendLine("language = \"${target.language}\"")
            target.packageName?.let { appendLine("packageName = \"$it\"") }
            appendLine()
        }
    }

    private fun stripComments(line: String): String {
        var inQuotes = false
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"' && (i == 0 || line[i - 1] != '\\')) {
                inQuotes = !inQuotes
                sb.append(ch)
            } else if (ch == '#' && !inQuotes) {
                break
            } else {
                sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    private fun splitKeyValue(line: String): Pair<String, String>? {
        val idx = line.indexOf('=')
        if (idx <= 0) return null
        val key = line.substring(0, idx).trim()
        val value = line.substring(idx + 1).trim()
        if (key.isEmpty() || value.isEmpty()) return null
        return key to value
    }

    private fun parseValue(raw: String): Any? {
        val value = raw.trim()
        if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
            return unescapeString(value.substring(1, value.length - 1))
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return parseStringArray(value.substring(1, value.length - 1))
        }
        if (value.equals("true", true)) return true
        if (value.equals("false", true)) return false
        return value.toLongOrNull() ?: value
    }

    private fun parseStringArray(raw: String): List<String> {
        if (raw.trim().isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var inQuotes = false
        val token = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '"' && (i == 0 || raw[i - 1] != '\\')) {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                addArrayToken(result, token.toString())
                token.setLength(0)
            } else {
                token.append(ch)
            }
            i++
        }
        addArrayToken(result, token.toString())
        return result
    }

    private fun addArrayToken(result: MutableList<String>, raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val value = if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            unescapeString(trimmed.substring(1, trimmed.length - 1))
        } else {
            trimmed
        }
        result.add(value)
    }

    private fun unescapeString(value: String): String =
        value.replace("\\\"", "\"").replace("\\\\", "\\")
}
