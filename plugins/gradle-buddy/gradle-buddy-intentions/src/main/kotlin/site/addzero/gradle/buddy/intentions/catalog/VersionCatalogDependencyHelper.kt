package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import site.addzero.gradle.buddy.settings.GradleBuddySettingsService

object VersionCatalogDependencyHelper {

    data class CatalogDependencyInfo(
        val key: String,
        val groupId: String,
        val artifactId: String,
        val currentVersion: String,
        val versionKey: String,
        val isVersionRef: Boolean,
        val lineText: String,
        val lineStartOffset: Int
    )

    fun detectCatalogDependencyAt(element: PsiElement): CatalogDependencyInfo? {
        val file = element.containingFile ?: return null
        val document = file.viewProvider.document ?: return null
        val offset = element.textOffset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStart, lineEnd))
        val fullText = file.text
        return parseCatalogDependencyLine(lineText, fullText, lineStart)
    }

    fun findCatalogDependencyByAccessor(project: Project, accessor: String): Pair<PsiFile, CatalogDependencyInfo>? {
        val catalogFile = resolveCatalogFile(project) ?: return null
        val document = catalogFile.viewProvider.document ?: return null
        val fullText = document.text
        val lines = fullText.split('\n')
        var offset = 0
        var inLibraries = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inLibraries = trimmed == "[libraries]"
                offset += line.length + 1
                continue
            }

            if (inLibraries && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val key = trimmed.substringBefore("=").trim()
                if (key.isNotEmpty() && accessorMatchesKey(accessor, key)) {
                    val info = parseCatalogDependencyLine(line, fullText, offset)
                    if (info != null) {
                        return catalogFile to info
                    }
                }
            }
            offset += line.length + 1
        }

        return null
    }

    fun updateCatalogDependency(file: PsiFile, info: CatalogDependencyInfo, newVersion: String) {
        val document = file.viewProvider.document ?: return
        val text = document.text

        if (info.isVersionRef) {
            val versionPattern = Regex("""(?m)^\s*${Regex.escape(info.versionKey)}\s*=\s*["']${Regex.escape(info.currentVersion)}["']""")
            val versionMatch = versionPattern.find(text) ?: return
            val newVersionText = versionMatch.value.replace(info.currentVersion, newVersion)
            document.replaceString(versionMatch.range.first, versionMatch.range.last + 1, newVersionText)
            return
        }

        val newLine = info.lineText.replace(info.currentVersion, newVersion)
        document.replaceString(
            info.lineStartOffset,
            info.lineStartOffset + info.lineText.length,
            newLine
        )
    }

    private fun parseCatalogDependencyLine(
        lineText: String,
        fullText: String,
        lineStartOffset: Int
    ): CatalogDependencyInfo? {
        val keyPattern = """([A-Za-z0-9_.-]+)"""

        val groupPattern = Regex(
            """^\s*$keyPattern\s*=\s*\{\s*group\s*=\s*"([^"]+)"\s*,\s*name\s*=\s*"([^"]+)"\s*,\s*version\.ref\s*=\s*"([^"]+)"\s*\}\s*$"""
        )
        groupPattern.find(lineText)?.let { match ->
            val (key, groupId, artifactId, versionRef) = match.destructured
            val currentVersion = findVersionRef(fullText, versionRef) ?: return null
            return CatalogDependencyInfo(
                key = key,
                groupId = groupId,
                artifactId = artifactId,
                currentVersion = currentVersion,
                versionKey = versionRef,
                isVersionRef = true,
                lineText = lineText,
                lineStartOffset = lineStartOffset
            )
        }

        val groupDirectPattern = Regex(
            """^\s*$keyPattern\s*=\s*\{\s*group\s*=\s*"([^"]+)"\s*,\s*name\s*=\s*"([^"]+)"\s*,\s*version\s*=\s*"([^"]+)"\s*\}\s*$"""
        )
        groupDirectPattern.find(lineText)?.let { match ->
            val (key, groupId, artifactId, version) = match.destructured
            return CatalogDependencyInfo(
                key = key,
                groupId = groupId,
                artifactId = artifactId,
                currentVersion = version,
                versionKey = "",
                isVersionRef = false,
                lineText = lineText,
                lineStartOffset = lineStartOffset
            )
        }

        val modulePattern = Regex(
            """^\s*$keyPattern\s*=\s*\{\s*module\s*=\s*"([^:]+):([^"]+)"\s*,\s*version\.ref\s*=\s*"([^"]+)"\s*\}\s*$"""
        )
        modulePattern.find(lineText)?.let { match ->
            val (key, groupId, artifactId, versionRef) = match.destructured
            val currentVersion = findVersionRef(fullText, versionRef) ?: return null
            return CatalogDependencyInfo(
                key = key,
                groupId = groupId,
                artifactId = artifactId,
                currentVersion = currentVersion,
                versionKey = versionRef,
                isVersionRef = true,
                lineText = lineText,
                lineStartOffset = lineStartOffset
            )
        }

        val moduleDirectPattern = Regex(
            """^\s*$keyPattern\s*=\s*\{\s*module\s*=\s*"([^:]+):([^"]+)"\s*,\s*version\s*=\s*"([^"]+)"\s*\}\s*$"""
        )
        moduleDirectPattern.find(lineText)?.let { match ->
            val (key, groupId, artifactId, version) = match.destructured
            return CatalogDependencyInfo(
                key = key,
                groupId = groupId,
                artifactId = artifactId,
                currentVersion = version,
                versionKey = "",
                isVersionRef = false,
                lineText = lineText,
                lineStartOffset = lineStartOffset
            )
        }

        val shortPattern = Regex(
            """^\s*$keyPattern\s*=\s*"([^:]+):([^:]+):([^"]+)"\s*$"""
        )
        shortPattern.find(lineText)?.let { match ->
            val (key, groupId, artifactId, version) = match.destructured
            return CatalogDependencyInfo(
                key = key,
                groupId = groupId,
                artifactId = artifactId,
                currentVersion = version,
                versionKey = "",
                isVersionRef = false,
                lineText = lineText,
                lineStartOffset = lineStartOffset
            )
        }

        return null
    }

    private fun findVersionRef(fullText: String, versionKey: String): String? {
        val versionPattern = Regex("""(?m)^\s*${Regex.escape(versionKey)}\s*=\s*["']([^"']+)["']""")
        return versionPattern.find(fullText)?.groupValues?.get(1)
    }

    private fun accessorMatchesKey(accessor: String, key: String): Boolean {
        if (accessor == key) return true
        val normalized = key.replace('-', '.').replace('_', '.')
        return accessor == normalized
    }

    private fun resolveCatalogFile(project: Project): PsiFile? {
        val catalogPath = GradleBuddySettingsService.getInstance(project).getVersionCatalogPath()
        val basePath = project.basePath ?: return null
        val ioFile = java.io.File(basePath, catalogPath)
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(ioFile) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }
}
