package site.addzero.cargo.buddy.automod

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

object AzAutomodExpander {
    private val macroCall = Regex(
        """automod\s*::\s*dir!\s*\(\s*(?:(pub(?:\s*\([^)]*\))?)\s*)?"((?:\\.|[^"\\])*)"\s*\)\s*;?""",
    )
    private val rustIdentifier = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    private val ignoredSourceFiles = setOf("mod.rs", "lib.rs", "main.rs")

    fun hasAutomodCall(source: String): Boolean = macroCall.containsMatchIn(source)

    fun hasSingleAutomodMacroCall(source: String): Boolean {
        val match = macroCall.matchEntire(source.trim()) ?: return false
        return match.range.first == 0 && match.range.last == source.trim().lastIndex
    }

    fun parseSingleMacroCall(
        source: String,
        manifestDirectory: Path,
    ): AutomodMacroCall {
        val match = macroCall.matchEntire(source.trim())
            ?: throw AzAutomodExpansionException("not an az-automod dir macro call")
        return parseMacroMatch(match, manifestDirectory)
    }

    fun parseMacroCalls(
        source: String,
        manifestDirectory: Path,
    ): List<AutomodMacroCall> {
        return macroCall.findAll(source)
            .map { match -> parseMacroMatch(match, manifestDirectory) }
            .toList()
    }

    fun expandMacroCallText(
        source: String,
        manifestDirectory: Path,
    ): String {
        val expansion = parseMacroCall(source, manifestDirectory)
        return renderModules(expansion.modules, expansion.visibility).trimEnd()
    }

    fun parseMacroCall(
        source: String,
        manifestDirectory: Path,
    ): AutomodMacroExpansion {
        val call = parseSingleMacroCall(source, manifestDirectory)
        return AutomodMacroExpansion(
            visibility = call.visibility,
            relativePath = call.relativePath,
            directory = call.directory,
            modules = collectModules(call.directory),
        )
    }

    private fun parseMacroMatch(
        match: MatchResult,
        manifestDirectory: Path,
    ): AutomodMacroCall {
        val visibility = normalizeVisibility(match.groups[1]?.value.orEmpty())
        val relativePath = unescapeRustString(match.groups[2]?.value.orEmpty())
        val directory = manifestDirectory.resolve(relativePath).normalize()
        return AutomodMacroCall(
            visibility = visibility,
            relativePath = relativePath,
            directory = directory,
        )
    }

    fun expandText(
        source: String,
        manifestDirectory: Path,
    ): ExpansionResult {
        var expansionCount = 0
        val expanded = macroCall.replace(source) { match ->
            val visibility = normalizeVisibility(match.groups[1]?.value.orEmpty())
            val relativePath = unescapeRustString(match.groups[2]?.value.orEmpty())
            val modules = collectModules(manifestDirectory.resolve(relativePath).normalize())
            expansionCount += 1
            renderModules(modules, visibility).trimEnd()
        }
        return ExpansionResult(
            expandedText = expanded,
            expansionCount = expansionCount,
        )
    }

    private fun collectModules(directory: Path): List<Module> {
        if (!Files.isDirectory(directory)) {
            throw AzAutomodExpansionException("az-automod directory does not exist: $directory")
        }

        val files = linkedMapOf<String, String>()
        val directories = linkedMapOf<String, PathModuleDirectory>()

        Files.list(directory).use { stream ->
            stream.sorted(compareBy<Path> { it.fileName.toString() }).forEach { child ->
                when {
                    child.isRegularFile() -> collectFile(child, files)
                    child.isDirectory() && shouldCollectDirectory(child) -> collectDirectory(child, directories)
                }
            }
        }

        val fileModuleNames = files.keys
        val modules = mutableListOf<Module>()
        files.values.forEach { sourceName ->
            modules += Module(sourceName, ModuleKind.File)
        }
        directories.forEach { (moduleName, directoryModule) ->
            if (moduleName !in fileModuleNames) {
                modules += Module(
                    sourceName = directoryModule.sourceName,
                    kind = ModuleKind.Directory(collectModules(directoryModule.path)),
                )
            }
        }

        if (modules.isEmpty()) {
            throw AzAutomodExpansionException("az-automod directory is empty: $directory")
        }

        return modules.sortedBy { normalizeModuleName(it.sourceName) }
    }

    private fun collectFile(
        file: Path,
        files: MutableMap<String, String>,
    ) {
        val fileName = file.fileName.toString()
        if (fileName in ignoredSourceFiles || file.extension != "rs") return

        val sourceName = fileName.removeSuffix(".rs")
        val moduleName = normalizeModuleName(sourceName)
        ensureUniqueModuleName(moduleName, sourceName, files[moduleName])
        files[moduleName] = sourceName
    }

    private fun collectDirectory(
        directory: Path,
        directories: MutableMap<String, PathModuleDirectory>,
    ) {
        val sourceName = directory.name
        val moduleName = normalizeModuleName(sourceName)
        ensureUniqueModuleName(moduleName, sourceName, directories[moduleName]?.sourceName)
        directories[moduleName] = PathModuleDirectory(sourceName, directory)
    }

    private fun ensureUniqueModuleName(
        moduleName: String,
        sourceName: String,
        previousSourceName: String?,
    ) {
        if (previousSourceName != null && previousSourceName != sourceName) {
            throw AzAutomodExpansionException(
                "az-automod names '$previousSourceName' and '$sourceName' both normalize to '$moduleName'",
            )
        }
    }

    private fun shouldCollectDirectory(directory: Path): Boolean {
        return !(directory.fileName?.toString() == "bin" &&
            directory.parent?.fileName?.toString() == "src")
    }

    private fun renderModules(
        modules: List<Module>,
        visibility: String,
        indentLevel: Int = 0,
    ): String {
        return modules.joinToString(separator = "\n") { module ->
            renderModule(module, visibility, indentLevel)
        }
    }

    private fun renderModule(
        module: Module,
        visibility: String,
        indentLevel: Int,
    ): String {
        val indent = "    ".repeat(indentLevel)
        val moduleName = normalizeModuleName(module.sourceName)
        val visibilityPrefix = if (visibility.isBlank()) "" else "$visibility "
        return when (val kind = module.kind) {
            ModuleKind.File -> {
                val pathAttribute = if (module.sourceName == moduleName) {
                    ""
                } else {
                    "$indent#[path = ${quoteRustString("${module.sourceName}.rs")}]\n"
                }
                "$pathAttribute$indent${visibilityPrefix}mod $moduleName;"
            }
            is ModuleKind.Directory -> {
                val pathAttribute = if (module.sourceName == moduleName) {
                    ""
                } else {
                    "$indent#[path = ${quoteRustString(module.sourceName)}]\n"
                }
                val body = renderModules(kind.items, visibility, indentLevel + 1)
                "$pathAttribute$indent${visibilityPrefix}mod $moduleName {\n$body\n$indent}"
            }
        }
    }

    private fun normalizeVisibility(visibility: String): String {
        return visibility
            .trim()
            .replace(Regex("""\s+"""), " ")
            .replace("pub (", "pub(")
            .replace(" )", ")")
    }

    private fun normalizeModuleName(name: String): String {
        val normalized = name.replace('-', '_')
        return if (normalized.firstOrNull()?.isDigit() == true) {
            "_$normalized"
        } else {
            normalized
        }.also {
            if (!rustIdentifier.matches(it)) {
                throw AzAutomodExpansionException("Invalid Rust module name after normalization: $name")
            }
        }
    }

    private fun quoteRustString(value: String): String {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""
    }

    private fun unescapeRustString(value: String): String {
        return buildString {
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char != '\\' || index == value.lastIndex) {
                    append(char)
                    index += 1
                    continue
                }

                when (val escaped = value[index + 1]) {
                    '\\', '"' -> append(escaped)
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(escaped)
                }
                index += 2
            }
        }
    }
}

data class ExpansionResult(
    val expandedText: String,
    val expansionCount: Int,
)

class AzAutomodExpansionException(message: String) : RuntimeException(message)

data class AutomodMacroExpansion(
    val visibility: String,
    val relativePath: String,
    val directory: Path,
    val modules: List<Module>,
)

data class AutomodMacroCall(
    val visibility: String,
    val relativePath: String,
    val directory: Path,
)

data class Module(
    val sourceName: String,
    val kind: ModuleKind,
) {
    val moduleName: String = sourceName
        .replace('-', '_')
        .let { normalized ->
            if (normalized.firstOrNull()?.isDigit() == true) "_$normalized" else normalized
        }
}

sealed interface ModuleKind {
    data object File : ModuleKind

    data class Directory(
        val items: List<Module>,
    ) : ModuleKind
}

private data class PathModuleDirectory(
    val sourceName: String,
    val path: Path,
)
