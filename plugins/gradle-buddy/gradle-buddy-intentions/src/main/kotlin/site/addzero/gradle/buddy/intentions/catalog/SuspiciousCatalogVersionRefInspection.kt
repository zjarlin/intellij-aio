package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

class SuspiciousCatalogVersionRefInspection : LocalInspectionTool() {

    override fun getDisplayName(): String =
        GradleBuddyBundle.message("inspection.suspicious.catalog.version.ref.name")

    override fun getShortName(): String = "SuspiciousCatalogVersionRef"

    override fun getGroupDisplayName(): String = "Gradle"

    override fun getStaticDescription(): String =
        GradleBuddyBundle.message("inspection.suspicious.catalog.version.ref.description")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file.virtualFile ?: return PsiElementVisitor.EMPTY_VISITOR
        if (!file.name.endsWith(".versions.toml")) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                val keyValue = element as? TomlKeyValue ?: return
                if (!isLibrariesEntry(keyValue)) return

                val dep = VersionCatalogDependencyHelper.detectCatalogDependencyLenientAt(keyValue.key) ?: return
                if (!dep.isVersionRef) return
                if (!VersionCatalogVersionRefHeuristics.isSuspicious(dep)) return

                val highlightElement = keyValue.key
                holder.registerProblem(
                    highlightElement,
                    GradleBuddyBundle.message(
                        "inspection.suspicious.catalog.version.ref.message",
                        dep.artifactId,
                        dep.versionKey
                    ),
                    ProblemHighlightType.WARNING,
                    VersionCatalogFixVersionQuickFix()
                )
            }
        }
    }

    private fun isLibrariesEntry(keyValue: TomlKeyValue): Boolean {
        val table = generateSequence(keyValue.parent) { it.parent }
            .filterIsInstance<TomlTable>()
            .firstOrNull() ?: return false
        return table.header.key?.text == "libraries" && !keyValue.key.text.isNullOrBlank()
    }
}

internal object VersionCatalogVersionRefHeuristics {

    fun isSuspicious(dep: VersionCatalogDependencyHelper.CatalogDependencyInfo): Boolean {
        if (!dep.isVersionRef) return false
        if (dep.versionKey.equals(dep.key, ignoreCase = true)) return false

        val sourceTokens = tokenize(dep.key) + tokenize(dep.artifactId)
        val versionRefTokens = tokenize(dep.versionKey)
        if (sourceTokens.isEmpty() || versionRefTokens.isEmpty()) return false

        return !hasOverlap(sourceTokens, versionRefTokens)
    }

    private fun hasOverlap(left: Set<String>, right: Set<String>): Boolean {
        return left.any { a ->
            right.any { b ->
                a == b || isPrefixMatch(a, b)
            }
        }
    }

    private fun isPrefixMatch(left: String, right: String): Boolean {
        val minLength = minOf(left.length, right.length)
        if (minLength < 4) return false
        return left.startsWith(right) || right.startsWith(left)
    }

    private fun tokenize(value: String): Set<String> {
        return value
            .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
            .replace(Regex("([A-Za-z])(\\d)"), "$1-$2")
            .replace(Regex("(\\d)([A-Za-z])"), "$1-$2")
            .split('.', '-', '_')
            .map { it.trim().lowercase() }
            .filter { it.length >= 2 }
            .filterNot { it in STOP_WORDS }
            .toSet()
    }

    private val STOP_WORDS = setOf(
        "version",
        "versions",
        "ref",
        "lib",
        "libs",
        "library"
    )
}
