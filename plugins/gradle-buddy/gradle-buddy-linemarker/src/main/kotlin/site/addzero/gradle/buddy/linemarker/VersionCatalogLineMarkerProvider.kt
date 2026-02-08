package site.addzero.gradle.buddy.linemarker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import javax.swing.Icon

/**
 * Provides gutter icons (Line Markers) for each library entry in *.versions.toml.
 *
 * In IntelliJ plugin development this is called a **Line Marker Provider** (行标记提供者).
 *
 * IMPORTANT: [getLineMarkerInfo] is called for EVERY PsiElement in the file.
 * We must only return a marker for leaf elements, specifically the first leaf
 * of a [TomlKeyValue]'s key inside the [libraries] table.
 */
class VersionCatalogLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only leaf elements — this is a hard requirement for LineMarkerProvider
        if (element !is LeafPsiElement) return null

        // Only *.versions.toml files
        val file = element.containingFile?.virtualFile ?: return null
        if (!file.name.endsWith(".versions.toml")) return null

        // Walk up to find enclosing TomlKeyValue
        val keyValue = element.findAncestor<TomlKeyValue>() ?: return null

        // Skip if this key-value is inside an inline table (e.g. group = "..." inside { ... })
        // We only want top-level entries directly under [libraries]
        if (keyValue.parent is TomlInlineTable) return null

        // This leaf must be inside the key part (not the value part)
        val key = keyValue.key
        if (!key.textRange.contains(element.textRange)) return null

        // Must be the FIRST leaf of the key (to avoid duplicate markers per key segment)
        val firstLeafOfKey = generateSequence<PsiElement>(key) { it.firstChild }.last()
        if (firstLeafOfKey != element) return null

        // Must be inside a [libraries] table
        val table = keyValue.findAncestor<TomlTable>() ?: return null
        val headerText = table.header.key?.text ?: return null
        if (headerText != "libraries") return null

        // Parse group:artifact from the key-value text
        val alias = key.text?.trim() ?: return null
        val lineText = keyValue.text ?: return null
        val info = parseLibraryLine(alias, lineText) ?: return null

        // Build marker with appropriate icon
        val service = DeprecatedArtifactService.getInstance()
        val deprecated = service.isDeprecated(info.group, info.artifact)
        val icon: Icon = if (deprecated) ICON_DEPRECATED else ICON_ARTIFACT
        val tooltip = buildTooltip(info, deprecated, service)

        return object : LineMarkerInfo<PsiElement>(
            element, element.textRange, icon,
            { tooltip }, null,
            GutterIconRenderer.Alignment.LEFT,
            { "${info.group}:${info.artifact}" }
        ) {
            override fun createGutterRenderer(): GutterIconRenderer {
                return object : LineMarkerGutterIconRenderer<PsiElement>(this) {
                    override fun getPopupMenuActions(): ActionGroup = createActionGroup(info)
                    override fun getClickAction(): AnAction? = null
                }
            }
        }
    }

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) { /* no slow markers */ }

    /**
     * Walk up the PSI tree to find an ancestor of the given type.
     * Max 10 levels to avoid runaway traversal.
     */
    private inline fun <reified T : PsiElement> PsiElement.findAncestor(): T? {
        var current: PsiElement? = this.parent
        repeat(10) {
            if (current == null) return null
            if (current is T) return current as T
            current = current?.parent
        }
        return null
    }

    private fun createActionGroup(info: LibraryInfo): DefaultActionGroup {
        val group = DefaultActionGroup("${info.group}:${info.artifact}", true)
        group.add(DeprecateArtifactAction(info.group, info.artifact))
        return group
    }

    private fun buildTooltip(info: LibraryInfo, deprecated: Boolean, svc: DeprecatedArtifactService): String {
        val base = "${info.group}:${info.artifact}"
        if (!deprecated) return base
        val msg = svc.getEntry(info.group, info.artifact)?.message?.takeIf { it.isNotBlank() } ?: "无弃用信息"
        return "⚠ 已弃用: $base\n$msg"
    }

    private fun parseLibraryLine(alias: String, line: String): LibraryInfo? {
        // module = "group:artifact"
        MODULE_PATTERN.find(line)?.let {
            return LibraryInfo(alias, it.groupValues[1], it.groupValues[2])
        }
        // group = "...", name = "..."
        val g = GROUP_PATTERN.find(line)
        val n = NAME_PATTERN.find(line)
        if (g != null && n != null) return LibraryInfo(alias, g.groupValues[1], n.groupValues[1])
        // "group:artifact:version" short format
        SHORT_FORMAT_PATTERN.find(line)?.let {
            return LibraryInfo(alias, it.groupValues[1], it.groupValues[2])
        }
        return null
    }

    data class LibraryInfo(val alias: String, val group: String, val artifact: String)

    companion object {
        private val ICON_ARTIFACT: Icon = IconLoader.getIcon("/icons/catalogArtifact.svg", VersionCatalogLineMarkerProvider::class.java)
        private val ICON_DEPRECATED: Icon = IconLoader.getIcon("/icons/catalogArtifactDeprecated.svg", VersionCatalogLineMarkerProvider::class.java)

        private val MODULE_PATTERN = Regex("""module\s*=\s*"([^":]+):([^"]+)"""")
        private val GROUP_PATTERN = Regex("""group\s*=\s*"([^"]+)"""")
        private val NAME_PATTERN = Regex("""\bname\s*=\s*"([^"]+)"""")
        private val SHORT_FORMAT_PATTERN = Regex("""=\s*"([^":]+):([^":]+):([^"]+)"""")
    }
}
