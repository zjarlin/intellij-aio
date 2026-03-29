package site.addzero.kcloud.idea.configcenter

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

internal fun String.toLiteralStringValue(): String? {
    return when {
        startsWith("\"\"\"") && endsWith("\"\"\"") && length >= 6 -> removePrefix("\"\"\"").removeSuffix("\"\"\"")
        startsWith("\"") && endsWith("\"") && length >= 2 -> StringUtil.unescapeStringCharacters(substring(1, length - 1))
        else -> null
    }
}

internal fun PsiFile.isJvmKotlinFile(): Boolean {
    val virtualPath = virtualFile?.path.orEmpty()
    if (!name.endsWith(".kt")) {
        return false
    }
    val blockedMarkers = listOf(
        "/src/commonMain/",
        "/src/commonTest/",
        "/src/js",
        "/src/ios",
        "/src/wasm",
        "/src/linux",
        "/src/macos",
        "/src/mingw",
        "/src/native",
    )
    return blockedMarkers.none { marker -> virtualPath.contains(marker) }
}

internal fun KtFile.ensureImport(
    project: Project,
    importText: String,
) {
    val hasImport = importDirectives.any { directive ->
        directive.importPath?.pathStr == importText
    }
    if (hasImport) {
        return
    }

    val factory = KtPsiFactory(project)
    val importDirective = factory.createFile(
        "KCloudImport.kt",
        buildString {
            packageFqName.asString()
                .takeIf { it.isNotBlank() }
                ?.let { packageName ->
                    append("package ")
                    append(packageName)
                    append("\n\n")
                }
            append("import ")
            append(importText)
            append("\n")
        },
    ).importDirectives.firstOrNull() ?: return

    val importList = importList
    if (importList != null) {
        val anchor = importList.imports.lastOrNull()
        if (anchor != null) {
            importList.addAfter(importDirective, anchor)
        } else {
            importList.add(importDirective)
        }
        return
    }

    val anchor = packageDirective ?: firstChild
    addAfter(factory.createNewLine(), anchor)
    addAfter(importDirective, anchor)
}
