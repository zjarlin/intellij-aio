package site.addzero.addl.intention

//import org.tomlj.Toml
//import org.tomlj.TomlParseResult
import cn.hutool.core.util.StrUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.addl.util.catalogutil.*
import site.addzero.addl.util.catalogutil.VersionCatalogPsiUtil.wrightToToml
import site.addzero.addl.util.removeAnyQuote
import java.io.File
import com.intellij.openapi.vfs.LocalFileSystem

class ConvertToVersionCatalogIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String = "Convert to version catalog"

    override fun getText(): String = "Convert to version catalog"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (element.containingFile !is KtFile) return false
        val fileName = element.containingFile.name
        if (fileName != "build.gradle.kts") return false

        // 检查是否在dependencies块内
        val dependencyDeclaration = isDependencyDeclaration(element, "dependencies")
        val dependencyDeclaration1 = isDependencyDeclaration(element, "plugins")
        return dependencyDeclaration || dependencyDeclaration1
    }

    private fun isDependencyDeclaration(element: PsiElement, blockName: String): Boolean {
        var current = element
        while (current.parent != null) {
            if (current is KtCallExpression) {
                val text = current.text
                val startsWith = text.startsWith(blockName)
                if (startsWith) {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile as? KtFile ?: return
        val text1 = element.text
        val versionDto = psiTextToTomlDTO(text1)
        if (versionDto == null) {
            return
        }
        val lb = versionDto.libraries?.firstOrNull()
        val vn = versionDto.versions?.firstOrNull()
        val libraryKey = lb?.key
        if (libraryKey == null) {
            return

        }


        val trimIndent = """
            $libraryKey ={group="${lb.group}",name= "${lb.name}",version.ref="${lb.versionRef}"}
        """.trimIndent()
        
        val versionCatalog = findVersionCatalogFile(project) ?: return
        val readText = versionCatalog.readText()

        val toToml = TomlUtils.appendAfterTag(readText, "libraries", trimIndent)

        val versionRef = vn?.versionRef
        val versionValue = vn?.version
        val trimIndent1 = """
          $versionRef  = "$versionValue" 
        """.trimIndent()


        val toToml1 = TomlUtils.appendAfterTag(toToml, "versions", trimIndent1)


//        val toTomlDTO = TomlUtils.toTomlDTO(versionCatalog.absolutePath)
//        val let = versionDto?.let { TomlUtils.merge(it, toTomlDTO) }
//        val toToml = let?.toToml()

        // 使用ApplicationManager.getApplication().invokeLater确保写操作在正确的线程上下文中执行
//        if (toToml.isBlank()) {
//            return
//        }
//        wrightToToml(project, toToml)

        // 替换依赖字符串为版本目录引用
//        val removeAnyQuote = StrUtil.replace(libraryKey, "-", ".").removeAnyQuote()
//        val depStr = "libs.$removeAnyQuote"


            WriteCommandAction.runWriteCommandAction(project) {
                if (toToml.isNotBlank()) {
                    wrightToToml(project, toToml1)
//                    FileUtil.writeUtf8String(toToml, versionCatalog.absolutePath)
                }

                // 替换element的字符串为新的versionDto里的LibraryEntry的 libs.key
                versionDto?.libraries?.firstOrNull()?.let { libraryEntry ->
                    val key = libraryEntry.key
                    val removeAnyQuote1 = StrUtil.replace(key, "-", ".").removeAnyQuote()
                    val string = "libs.$removeAnyQuote1"
                    val factory = PsiElementFactory.getInstance(project)
                    val newText = string
                    val newElement = factory.createExpressionFromText(newText, element)
                    element.replace(newElement)
                }
            }


//        if (libraryKey != null) {
//            // 将新的依赖引用复制到剪贴板
//            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
//            val selection = java.awt.datatransfer.StringSelection(depStr)
//            clipboard.setContents(selection, null)
//
////            val factory = com.intellij.psi.PsiElementFactory.getInstance(project)
////            val newExpression = factory.createExpressionFromText(depStr, null)
////            element.replace(newExpression)
//        }
    }
    private fun psiTextToTomlDTO(callText: String): VersionCatalogDTO? {
        val split = callText.split(":")
        if (split.size == 1) {
//            DialogUtil.showErrorMsg("只支持group:name:version这种格式")
            return null
        }
        val groupName = split.getOrNull(0) ?: ""
        val name = split.getOrNull(1) ?: ""

        val versionRefKey = "${name}Version"
        val version = split.getOrNull(2)
        val concat = StrUtil.join(".", groupName, name)

        val key = StrUtil.replace(concat, ".", "-")

        val entries = if (version == null) null else listOf(VersionEntry(versionRefKey, version))

        val libraryEntry =
            LibraryEntry(key = key, group = groupName, name = name, version = null, versionRef = versionRefKey)
        val listOf = listOf<LibraryEntry>(libraryEntry)
//        PluginEntry( key = TODO(), id = TODO(), version = TODO(), versionRef = TODO() )

        val libraryCatLog = VersionCatalogDTO(versions = entries, libraries = listOf, plugins = null, bundles = null)
        return libraryCatLog

    }

    private fun findVersionCatalogFile(project: Project): File? {
        val basePath = project.basePath ?: return null
        
        val possiblePaths = listOf(
            "$basePath/gradle/libs.versions.toml",
            "$basePath/checkouts/build-logic/gradle/libs.versions.toml",
            "$basePath/build-logic/gradle/libs.versions.toml"
        )
        
        return possiblePaths.map { File(it) }
            .firstOrNull { it.exists() && it.isFile }
    }

}
