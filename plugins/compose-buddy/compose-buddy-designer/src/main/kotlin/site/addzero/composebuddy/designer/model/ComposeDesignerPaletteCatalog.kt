package site.addzero.composebuddy.designer.model

import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.settings.ComposeBuddySettingsService

data class ComposeDesignerCustomComponent(
    val displayName: String,
    val functionName: String,
    val imports: List<String>,
    val template: String,
    val width: Int,
    val height: Int,
    val layoutKind: ComposePaletteItem? = null,
) {
    val supportsChildren: Boolean
        get() = "{children}" in template
}

data class ComposePaletteEntry(
    val id: String,
    val kind: ComposePaletteItem,
    val displayName: String,
    val customComponent: ComposeDesignerCustomComponent? = null,
) {
    fun transferId(): String {
        return when (kind) {
            ComposePaletteItem.CUSTOM -> "custom:${customComponent?.functionName.orEmpty()}"
            else -> "builtin:${kind.name}"
        }
    }
}

object ComposeDesignerPaletteCatalog {
    data class ValidationResult(
        val components: List<ComposeDesignerCustomComponent>,
        val errors: List<String>,
    )

    fun paletteEntries(): List<ComposePaletteEntry> {
        return buildList {
            add(builtin(ComposePaletteItem.TEXT, ComposeBuddyBundle.message("designer.palette.text")))
            add(builtin(ComposePaletteItem.BUTTON, ComposeBuddyBundle.message("designer.palette.button")))
            add(builtin(ComposePaletteItem.IMAGE, ComposeBuddyBundle.message("designer.palette.image")))
            add(builtin(ComposePaletteItem.BOX, ComposeBuddyBundle.message("designer.palette.box")))
            add(builtin(ComposePaletteItem.ROW, ComposeBuddyBundle.message("designer.palette.row")))
            add(builtin(ComposePaletteItem.COLUMN, ComposeBuddyBundle.message("designer.palette.column")))
            add(builtin(ComposePaletteItem.SPACER, ComposeBuddyBundle.message("designer.palette.spacer")))
            customComponents().forEach { component ->
                add(
                    ComposePaletteEntry(
                        id = "custom:${component.functionName}",
                        kind = ComposePaletteItem.CUSTOM,
                        displayName = component.displayName,
                        customComponent = component,
                    ),
                )
            }
        }
    }

    fun resolveTransferId(transferId: String): ComposePaletteEntry? {
        if (transferId.startsWith("builtin:")) {
            val kind = runCatching { ComposePaletteItem.valueOf(transferId.removePrefix("builtin:")) }.getOrNull() ?: return null
            return paletteEntries().firstOrNull { it.kind == kind && it.kind != ComposePaletteItem.CUSTOM }
        }
        if (transferId.startsWith("custom:")) {
            val functionName = transferId.removePrefix("custom:")
            return paletteEntries().firstOrNull { it.customComponent?.functionName == functionName }
        }
        return null
    }

    fun resolveCustomByFunction(functionName: String?): ComposeDesignerCustomComponent? {
        if (functionName.isNullOrBlank()) {
            return null
        }
        return customComponents().firstOrNull { it.functionName == functionName }
    }

    fun parseCustomComponents(raw: String): List<ComposeDesignerCustomComponent> {
        return raw.split(Regex("(?m)^---\\s*$"))
            .mapNotNull { parseBlock(it) }
    }

    fun serializeCustomComponents(components: List<ComposeDesignerCustomComponent>): String {
        return components.joinToString("\n---\n") { component ->
            buildString {
                appendLine("name=${component.displayName}")
                appendLine("function=${component.functionName}")
                if (component.imports.isNotEmpty()) {
                    appendLine("imports=${component.imports.joinToString(",")}")
                }
                component.layoutKind?.let { layout ->
                    appendLine("layout=${layout.name.lowercase()}")
                }
                appendLine("width=${component.width}")
                appendLine("height=${component.height}")
                appendLine("template<<")
                appendLine(component.template.trimEnd())
                append(">>")
            }
        }
    }

    fun validateCustomComponents(raw: String): ValidationResult {
        if (raw.isBlank()) {
            return ValidationResult(emptyList(), emptyList())
        }
        val components = mutableListOf<ComposeDesignerCustomComponent>()
        val errors = mutableListOf<String>()
        raw.split(Regex("(?m)^---\\s*$")).forEachIndexed { index, block ->
            val parsed = parseBlock(block)
            if (block.isBlank()) {
                return@forEachIndexed
            }
            if (parsed == null) {
                errors += "Block ${index + 1}: missing required keys. Expected name=, function=, and template<< ... >>."
                return@forEachIndexed
            }
            if (!Regex("[A-Za-z_][A-Za-z0-9_]*").matches(parsed.functionName)) {
                errors += "Block ${index + 1}: function must be a valid Kotlin identifier."
            }
            if (parsed.supportsChildren && parsed.layoutKind == null) {
                errors += "Block ${index + 1}: custom container using {children} must declare layout=box|row|column."
            }
            if (components.any { it.functionName == parsed.functionName }) {
                errors += "Block ${index + 1}: duplicated function ${parsed.functionName}."
            }
            components += parsed
        }
        return ValidationResult(components, errors)
    }

    fun exampleDsl(): String {
        return """
name=HeroCard
function=HeroCard
imports=site.addzero.widgets.HeroCard
width=220
height=140
template<<
HeroCard(
    modifier = {modifier},
)
>>
---
name=AvatarChip
function=AvatarChip
imports=site.addzero.widgets.AvatarChip,site.addzero.widgets.AvatarModel
width=160
height=56
template<<
AvatarChip(
    modifier = {modifier},
)
>>
---
name=FormSection
function=FormSection
imports=site.addzero.widgets.FormSection
layout=column
width=280
height=180
template<<
FormSection(
    modifier = {modifier},
) {
{children}
}
>>
        """.trimIndent()
    }

    private fun customComponents(): List<ComposeDesignerCustomComponent> {
        return parseCustomComponents(ComposeBuddySettingsService.getInstance().state.designerCustomComponentsDsl)
    }

    private fun builtin(kind: ComposePaletteItem, label: String): ComposePaletteEntry {
        return ComposePaletteEntry(
            id = "builtin:${kind.name}",
            kind = kind,
            displayName = label,
        )
    }

    private fun parseBlock(block: String): ComposeDesignerCustomComponent? {
        val lines = block.lines()
        var name = ""
        var functionName = ""
        val imports = mutableListOf<String>()
        var width = 180
        var height = 56
        var template = ""
        var layoutKind: ComposePaletteItem? = null
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            index++
            if (line.isBlank()) {
                continue
            }
            when {
                line.startsWith("name=") -> name = line.removePrefix("name=").trim()
                line.startsWith("function=") -> functionName = line.removePrefix("function=").trim()
                line.startsWith("imports=") -> {
                    imports += line.removePrefix("imports=")
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
                line.startsWith("width=") -> width = line.removePrefix("width=").trim().toIntOrNull() ?: width
                line.startsWith("height=") -> height = line.removePrefix("height=").trim().toIntOrNull() ?: height
                line.startsWith("layout=") -> {
                    layoutKind = when (line.removePrefix("layout=").trim().lowercase()) {
                        "box" -> ComposePaletteItem.BOX
                        "row" -> ComposePaletteItem.ROW
                        "column" -> ComposePaletteItem.COLUMN
                        else -> null
                    }
                }
                line == "template<<" -> {
                    val templateLines = mutableListOf<String>()
                    while (index < lines.size && lines[index].trim() != ">>") {
                        templateLines += lines[index]
                        index++
                    }
                    while (index < lines.size && lines[index].trim() != ">>") {
                        index++
                    }
                    if (index < lines.size && lines[index].trim() == ">>") {
                        index++
                    }
                    template = templateLines.joinToString("\n").trim()
                }
            }
        }
        if (name.isBlank() || functionName.isBlank() || template.isBlank()) {
            return null
        }
        return ComposeDesignerCustomComponent(
            displayName = name,
            functionName = functionName,
            imports = imports.distinct(),
            template = template,
            width = width.coerceAtLeast(48),
            height = height.coerceAtLeast(24),
            layoutKind = layoutKind,
        )
    }
}
