package site.addzero.dioxus.buddy.model

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.math.abs

object DioxusPreviewResolver {
    private const val CARGO_MANIFEST = "Cargo.toml"

    fun resolveFromCurrentEditor(project: Project): DioxusPreviewTarget? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        return resolveAtEditor(project, editor, file)
    }

    fun resolveAtEditor(
        project: Project,
        editor: Editor,
        file: VirtualFile,
    ): DioxusPreviewTarget? {
        val text = editor.document.text
        val parsedAtCaret = DioxusPreviewParser.findTargetAt(text, editor.caretModel.offset)
        val parsed = if (parsedAtCaret != null) {
            parsedAtCaret.takeIf { it.canRenderDirectly }
        } else {
            DioxusPreviewParser.parse(text).firstOrNull { it.canRenderDirectly }
        }
            ?: return null
        return resolve(project, file, text, parsed)
    }

    fun resolveTargets(
        project: Project,
        file: VirtualFile,
        text: String,
    ): List<DioxusPreviewTarget> {
        if (!isRustFile(file)) return emptyList()
        val manifest = resolveManifest(project, file) ?: return emptyList()
        val sourceRelativePath = sourceRelativePath(manifest.rootPath, file)
        return DioxusPreviewParser.parse(text)
            .filter { parsed -> parsed.canRenderDirectly }
            .map { parsed ->
                DioxusPreviewTarget(
                    sourceFile = file,
                    sourceText = text,
                    sourceRelativePath = sourceRelativePath,
                    cargoManifest = manifest,
                    parsed = parsed,
                    previewPort = stablePreviewPort(manifest.rootPath.toString(), file.path, parsed.functionName),
                )
            }
    }

    fun resolve(
        project: Project,
        file: VirtualFile,
        sourceText: String,
        parsed: ParsedDioxusPreviewTarget,
    ): DioxusPreviewTarget? {
        if (!isRustFile(file)) return null
        if (!parsed.canRenderDirectly) return null
        val manifest = resolveManifest(project, file) ?: return null
        return DioxusPreviewTarget(
            sourceFile = file,
            sourceText = sourceText,
            sourceRelativePath = sourceRelativePath(manifest.rootPath, file),
            cargoManifest = manifest,
            parsed = parsed,
            previewPort = stablePreviewPort(manifest.rootPath.toString(), file.path, parsed.functionName),
        )
    }

    fun isRustFile(file: VirtualFile): Boolean {
        return !file.isDirectory && file.extension == "rs"
    }

    private fun resolveManifest(
        project: Project,
        file: VirtualFile,
    ): CargoManifest? {
        if (!file.isValid) return null
        val basePath = project.basePath ?: return null
        if (!file.path.startsWith(basePath)) return null
        var directory = file.parent
        while (directory != null && directory.path.startsWith(basePath)) {
            val manifestFile = directory.findChild(CARGO_MANIFEST)
            if (manifestFile != null && manifestFile.isValid && !manifestFile.isDirectory) {
                val manifestText = runCatching {
                    String(manifestFile.contentsToByteArray(), Charsets.UTF_8)
                }.getOrElse { return null }
                return CargoManifest(
                    manifestPath = Path(manifestFile.path),
                    rootPath = Path(directory.path),
                    packageName = parseTomlValue(manifestText, "name") ?: directory.name,
                    edition = parseTomlValue(manifestText, "edition") ?: "2021",
                )
            }
            directory = directory.parent
        }
        return null
    }

    private fun parseTomlValue(
        text: String,
        key: String,
    ): String? {
        val packageSection = Regex("""(?m)^\s*\[package]\s*$""").find(text) ?: return null
        val nextSection = Regex("""(?m)^\s*\[""")
            .find(text, packageSection.range.last + 1)
        val packageText = text.substring(
            packageSection.range.last + 1,
            nextSection?.range?.first ?: text.length,
        )
        return Regex("""(?m)^\s*${Regex.escape(key)}\s*=\s*"([^"]+)"""")
            .find(packageText)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun sourceRelativePath(
        rootPath: Path,
        file: VirtualFile,
    ): String {
        val sourcePath = Path(file.path)
        return runCatching {
            rootPath.relativize(sourcePath).toString()
        }.getOrElse {
            sourcePath.fileName.toString()
        }.replace('\\', '/')
    }

    private fun stablePreviewPort(
        crateRootPath: String,
        sourcePath: String,
        functionName: String,
    ): Int {
        val hash = abs("$crateRootPath::$sourcePath::$functionName".hashCode())
        return 31_000 + hash % 1_000
    }
}
