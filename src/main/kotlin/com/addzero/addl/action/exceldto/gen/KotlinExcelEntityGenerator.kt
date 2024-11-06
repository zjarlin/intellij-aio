import com.addzero.addl.util.fieldinfo.PsiUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass

class KotlinExcelEntityGenerator : ExcelEntityGenerator {

    override fun generateKotlinEntity(ktClass: KtClass, project: Project): String {
        val className = ktClass.name ?: "UnnamedClass"
        val extractInterfaceMetaInfo = PsiUtil.extractInterfaceMetaInfo(ktClass)
        val fields = extractInterfaceMetaInfo.joinToString(System.lineSeparator()) {
            """
            @ExcelProperty("${it.comment}")
            val ${it.name}: ${mapToType(it.type.typeName)}?=null
            """
        }

        return """
            import com.alibaba.excel.annotation.ExcelProperty;
            public open class ${className}ExcelDTO{
                $fields
            }
        """.trimIndent()
    }

    override fun generateJavaEntity(psiClass: PsiClass, project: Project): String {
        TODO("Not yet implemented")
    }

    override fun mapToType(type: String?): String {
        return when (type) {
            "String" -> "String"
            "Int" -> "Int"
            "BigDecimal" -> "BigDecimal"
            "LocalDateTime" -> "LocalDateTime"
            else -> "String"
        }
    }

    override fun saveGeneratedFile(content: String, className: String, directory: PsiDirectory) {
        // 创建新文件
        val fileName = "$className.kt"
        val psiFile = PsiFileFactory.getInstance(directory.project).createFileFromText(fileName, KotlinFileType.INSTANCE, content)

        // 将文件添加到目标目录
        directory.add(psiFile)
    }
}