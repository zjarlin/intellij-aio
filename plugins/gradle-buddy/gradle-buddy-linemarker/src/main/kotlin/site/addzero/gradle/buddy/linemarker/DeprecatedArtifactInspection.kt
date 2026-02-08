package site.addzero.gradle.buddy.linemarker

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService

/**
 * Inspection that highlights usages of deprecated artifacts in .gradle.kts files.
 *
 * When a user marks an artifact as deprecated via the TOML gutter icon,
 * all `libs.xxx.yyy` references to that artifact in .gradle.kts files
 * will show a warning with the deprecation message.
 */
class DeprecatedArtifactInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "已弃用的版本目录依赖"
    override fun getShortName(): String = "DeprecatedCatalogArtifact"
    override fun getGroupDisplayName(): String = "Gradle"
    override fun getStaticDescription(): String =
        "检查 .gradle.kts 中引用的版本目录依赖是否已被标记为弃用"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file.virtualFile ?: return PsiElementVisitor.EMPTY_VISITOR
        if (!file.name.endsWith(".gradle.kts")) return PsiElementVisitor.EMPTY_VISITOR

        val service = DeprecatedArtifactService.getInstance()
        if (service.getAllDeprecated().isEmpty()) return PsiElementVisitor.EMPTY_VISITOR

        // Build alias → (group, artifact) lookup from TOML once per file
        val artifactMap = buildArtifactMap(holder) ?: return PsiElementVisitor.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)

                // Only process top-level expressions (not sub-parts)
                if (expression.parent is KtDotQualifiedExpression) return

                if (!isCatalogReference(expression)) return

                val fullText = expression.text
                val parts = fullText.split(".")
                if (parts.size < 2) return

                // libs.jimmer.spring.boot.starter → jimmer.spring.boot.starter
                val accessor = parts.drop(1).joinToString(".")

                val (group, artifact) = artifactMap[accessor] ?: return

                if (!service.isDeprecated(group, artifact)) return

                val entry = service.getEntry(group, artifact)
                val reason = entry?.message?.takeIf { it.isNotBlank() } ?: ""
                val msg = buildString {
                    append("⚠ 已弃用: $group:$artifact")
                    if (reason.isNotEmpty()) append(" — $reason")
                }

                holder.registerProblem(
                    expression,
                    msg,
                    ProblemHighlightType.LIKE_DEPRECATED
                )
            }
        }
    }

    /**
     * Parse the version catalog TOML and build a map of
     * dot-notation accessor → (group, artifact).
     */
    private fun buildArtifactMap(holder: ProblemsHolder): Map<String, Pair<String, String>>? {
        val project = holder.project
        val basePath = project.basePath ?: return null

        val catalogPath = try {
            GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        } catch (_: Exception) {
            GradleBuddySettingsService.DEFAULT_VERSION_CATALOG_PATH
        }

        val vFile = LocalFileSystem.getInstance()
            .findFileByPath("$basePath/$catalogPath") ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(vFile) as? TomlFile ?: return null

        val map = mutableMapOf<String, Pair<String, String>>()

        psiFile.children.forEach { element ->
            if (element is TomlTable && element.header.key?.text == "libraries") {
                element.entries.forEach { entry ->
                    if (entry is TomlKeyValue) {
                        val alias = entry.key.text?.trim() ?: return@forEach
                        val lineText = entry.text ?: return@forEach
                        val info = parseLibraryLine(lineText) ?: return@forEach
                        // TOML alias "my-lib" → kts accessor "my.lib"
                        val accessor = alias.replace('-', '.').replace('_', '.')
                        map[accessor] = info
                    }
                }
            }
        }

        return map
    }

    private fun isCatalogReference(expression: KtDotQualifiedExpression): Boolean {
        var current = expression
        while (current.receiverExpression is KtDotQualifiedExpression) {
            current = current.receiverExpression as KtDotQualifiedExpression
        }
        val rootName = (current.receiverExpression as? KtNameReferenceExpression)?.getReferencedName()
        return rootName in CATALOG_NAMES
    }

    companion object {
        private val CATALOG_NAMES = setOf("libs", "zlibs", "klibs", "testLibs")

        private val MODULE_PATTERN = Regex("""module\s*=\s*"([^":]+):([^"]+)"""")
        private val GROUP_PATTERN = Regex("""group\s*=\s*"([^"]+)"""")
        private val NAME_PATTERN = Regex("""\bname\s*=\s*"([^"]+)"""")
        private val SHORT_FORMAT_PATTERN = Regex("""=\s*"([^":]+):([^":]+):([^"]+)"""")

        private fun parseLibraryLine(line: String): Pair<String, String>? {
            MODULE_PATTERN.find(line)?.let {
                return it.groupValues[1] to it.groupValues[2]
            }
            val g = GROUP_PATTERN.find(line)
            val n = NAME_PATTERN.find(line)
            if (g != null && n != null) return g.groupValues[1] to n.groupValues[1]
            SHORT_FORMAT_PATTERN.find(line)?.let {
                return it.groupValues[1] to it.groupValues[2]
            }
            return null
        }
    }
}
