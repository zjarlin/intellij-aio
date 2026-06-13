package site.addzero.composebuddy.features.modifierchain

internal data class ModifierStyleableRendering(
    val declarations: List<String>,
    val calls: List<String>,
)

internal object ModifierStyleableRenderSupport {
    private val materialThemePropertyRegex = Regex("""\bMaterialTheme\.([A-Za-z_][A-Za-z0-9_]*)(?=\.)""")
    private val compositionLocalCurrentRegex = Regex("""\b(Local[A-Z][A-Za-z0-9_]*)\.current\b""")

    fun rewriteComposableReads(
        calls: List<String>,
        reservedNames: Set<String> = emptySet(),
    ): ModifierStyleableRendering {
        val usedNames = reservedNames.toMutableSet()
        val replacements = linkedMapOf<String, String>()

        calls.forEach { call ->
            materialThemePropertyRegex.findAll(call).forEach { match ->
                val source = match.value
                replacements.getOrPut(source) {
                    uniqueName(match.groupValues[1], usedNames)
                }
            }
            compositionLocalCurrentRegex.findAll(call).forEach { match ->
                val source = match.value
                replacements.getOrPut(source) {
                    uniqueName(localBaseName(match.groupValues[1]), usedNames)
                }
            }
        }

        val rewrittenCalls = calls.map { call ->
            replacements.entries
                .sortedByDescending { (source, _) -> source.length }
                .fold(call) { text, (source, target) -> text.replace(source, target) }
        }
        val declarations = replacements.map { (source, target) -> "val $target = $source" }

        return ModifierStyleableRendering(
            declarations = declarations,
            calls = rewrittenCalls,
        )
    }

    private fun uniqueName(
        baseName: String,
        usedNames: MutableSet<String>,
    ): String {
        val safeBaseName = baseName.takeIf { it.isNotBlank() } ?: "styleValue"
        if (usedNames.add(safeBaseName)) {
            return safeBaseName
        }
        var index = 2
        while (true) {
            val candidate = "$safeBaseName$index"
            if (usedNames.add(candidate)) {
                return candidate
            }
            index++
        }
    }

    private fun localBaseName(localName: String): String {
        val withoutPrefix = localName.removePrefix("Local")
        if (withoutPrefix.isBlank()) {
            return "compositionLocal"
        }
        return withoutPrefix.replaceFirstChar { char -> char.lowercaseChar() }
    }
}
