package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.intentions.select.VersionSelectionDialog
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

internal object VersionCatalogFixVersionSupport {

    fun isAvailable(file: PsiFile, dep: VersionCatalogDependencyHelper.CatalogDependencyInfo): Boolean {
        if (!dep.isVersionRef) return false

        val targetVersionKey = dep.key
        val fullText = file.text
        val versionExists = findVersionValue(fullText, targetVersionKey) != null

        if (dep.versionKey == targetVersionKey) {
            return !versionExists
        }

        return true
    }

    fun apply(project: Project, file: PsiFile, dep: VersionCatalogDependencyHelper.CatalogDependencyInfo) {
        if (!dep.isVersionRef) return

        val targetVersionKey = dep.key
        val fullText = file.text
        val refAlreadyCorrect = dep.versionKey == targetVersionKey

        val existingVersion = findVersionValue(fullText, targetVersionKey)
        if (existingVersion != null) {
            if (refAlreadyCorrect) {
                return
            }
            WriteCommandAction.runWriteCommandAction(project) {
                updateVersionRef(file, dep, targetVersionKey)
            }
            return
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                GradleBuddyBundle.message("intention.version.catalog.fix.version.task"),
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    val versions = runCatching {
                        MavenCentralSearchUtil.searchAllVersions(dep.groupId, dep.artifactId, 50)
                            .map { it.latestVersion }
                            .distinct()
                            .sortedDescending()
                    }.getOrElse { listOf() }

                    ApplicationManager.getApplication().invokeLater {
                        if (versions.isEmpty()) {
                            WriteCommandAction.runWriteCommandAction(project) {
                                if (refAlreadyCorrect) {
                                    addVersionVariable(file, targetVersionKey, FALLBACK_VERSION)
                                } else {
                                    addVersionVariableAndUpdateRef(file, dep, targetVersionKey, FALLBACK_VERSION)
                                }
                            }
                            return@invokeLater
                        }

                        val dialog = VersionSelectionDialog(
                            project,
                            GradleBuddyBundle.message("dialog.select.version.title", dep.groupId, dep.artifactId),
                            versions,
                            dep.currentVersion
                        )
                        if (!dialog.showAndGet()) return@invokeLater
                        val selectedVersion = dialog.getSelectedVersion() ?: return@invokeLater

                        WriteCommandAction.runWriteCommandAction(project) {
                            if (refAlreadyCorrect) {
                                addVersionVariable(file, targetVersionKey, selectedVersion)
                            } else {
                                addVersionVariableAndUpdateRef(file, dep, targetVersionKey, selectedVersion)
                            }
                        }
                    }
                }
            }
        )
    }

    private fun findVersionValue(fullText: String, versionKey: String): String? {
        val pattern = Regex("""(?m)^\s*${Regex.escape(versionKey)}\s*=\s*["']([^"']+)["']""")
        return pattern.find(fullText)?.groupValues?.get(1)
    }

    private fun updateVersionRef(
        file: PsiFile,
        dep: VersionCatalogDependencyHelper.CatalogDependencyInfo,
        newVersionKey: String
    ) {
        val document = file.viewProvider.document ?: return
        val oldRef = """version.ref = "${dep.versionKey}""""
        val newRef = """version.ref = "$newVersionKey""""
        val newLine = dep.lineText.replace(oldRef, newRef)
        document.replaceString(
            dep.lineStartOffset,
            dep.lineStartOffset + dep.lineText.length,
            newLine
        )
    }

    private fun addVersionVariable(file: PsiFile, versionKey: String, version: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text
        val insertOffset = findVersionsSectionEndOffset(text)
        if (insertOffset < 0) {
            val versionBlock = "[versions]\n$versionKey = \"$version\"\n\n"
            document.insertString(0, versionBlock)
        } else {
            val newVersionLine = "$versionKey = \"$version\"\n"
            document.insertString(insertOffset, newVersionLine)
        }
    }

    private fun addVersionVariableAndUpdateRef(
        file: PsiFile,
        dep: VersionCatalogDependencyHelper.CatalogDependencyInfo,
        newVersionKey: String,
        version: String
    ) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        val insertOffset = findVersionsSectionEndOffset(text)
        if (insertOffset < 0) {
            val versionBlock = "[versions]\n$newVersionKey = \"$version\"\n\n"
            document.insertString(0, versionBlock)
            updateVersionRefInText(document, document.text, dep, newVersionKey)
        } else {
            val newVersionLine = "$newVersionKey = \"$version\"\n"
            document.insertString(insertOffset, newVersionLine)
            updateVersionRefInText(document, document.text, dep, newVersionKey)
        }
    }

    private fun updateVersionRefInText(
        document: com.intellij.openapi.editor.Document,
        text: String,
        dep: VersionCatalogDependencyHelper.CatalogDependencyInfo,
        newVersionKey: String
    ) {
        val depKeyPattern = Regex("""(?m)^\s*${Regex.escape(dep.key)}\s*=\s*\{[^}]*version\.ref\s*=\s*"${Regex.escape(dep.versionKey)}"[^}]*\}""")
        val match = depKeyPattern.find(text) ?: return
        val oldRef = """version.ref = "${dep.versionKey}""""
        val newRef = """version.ref = "$newVersionKey""""
        val newLine = match.value.replace(oldRef, newRef)
        document.replaceString(match.range.first, match.range.last + 1, newLine)
    }

    private fun findVersionsSectionEndOffset(text: String): Int {
        val lines = text.split('\n')
        var inVersions = false
        var lastVersionLineEnd = -1
        var offset = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "[versions]") {
                inVersions = true
                offset += line.length + 1
                continue
            }
            if (inVersions) {
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    return if (lastVersionLineEnd >= 0) lastVersionLineEnd else offset
                }
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    lastVersionLineEnd = offset + line.length + 1
                }
            }
            offset += line.length + 1
        }

        return if (inVersions && lastVersionLineEnd >= 0) lastVersionLineEnd else -1
    }

    private const val FALLBACK_VERSION = "+"
}
