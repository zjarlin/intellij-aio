import com.addzero.addl.util.fieldinfo.PsiUtil
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.psi.KtClass

class JavaExcelEntityGenerator : ExcelEntityGenerator {
    override fun generateKotlinEntity(ktClass: KtClass, project: Project): String {
        TODO("Not yet implemented")
    }

    override fun generateJavaEntity(psiClass: PsiClass, project: Project): String {
        val className = psiClass.name ?: "UnnamedClass"

        var extractInterfaceMetaInfo = PsiUtil.getJavaFieldMetaInfo(psiClass)

        if (extractInterfaceMetaInfo.isEmpty()) {
            extractInterfaceMetaInfo = PsiUtil.extractInterfaceMetaInfo(psiClass)
        }


        val fields = extractInterfaceMetaInfo.joinToString (System.lineSeparator()){

            """
            @ExcelProperty("${it.comment}")
            private ${mapToType(it.type.typeName)} ${it.name};
            """

        }

        return """
            import com.alibaba.excel.annotation.ExcelProperty;
            @Data
            public class ${className}ExcelEntity {
                $fields
            }
        """.trimIndent()
    }

    override fun mapToType(type: String?): String {
        return when (type) {
            "String" -> "String"
            "Int" -> "Integer"
            "BigDecimal" -> "BigDecimal"
            "LocalDateTime" -> "LocalDateTime"
            else -> "String"
        }
    }

    override fun saveGeneratedFile(content: String, className: String, directory: PsiDirectory) {
        // 创建新文件
        val fileName = "$className.java"
        val psiFile = PsiFileFactory.getInstance(directory.project)
            .createFileFromText(fileName, JavaFileType.INSTANCE, content)
        // 将文件添加到目标目录
        directory.add(psiFile)
    }
}