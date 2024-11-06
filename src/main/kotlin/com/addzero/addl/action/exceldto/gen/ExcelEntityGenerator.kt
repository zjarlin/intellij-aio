import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import org.jetbrains.kotlin.psi.KtClass

interface ExcelEntityGenerator {
    fun generateKotlinEntity(ktClass: KtClass, project: Project): String
    fun generateJavaEntity(psiClass: PsiClass, project: Project): String
    fun mapToType(type: String?): String
    fun saveGeneratedFile(content: String, className: String, directory: PsiDirectory)
}