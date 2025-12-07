@file:OptIn(org.jetbrains.kotlin.analysis.api.KaExperimentalApi::class)

package site.addzero.autoddl.jimmer.lsi

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi_impl.impl.kt2.ext.toLsiClassK2
import site.addzero.util.lsi_impl.impl.psi.clazz.PsiLsiClass

/**
 * 统一的 LSI 解析器
 * 根据文件类型自动选择合适的解析策略：
 * - Java 文件 (.java) -> lsi-psi (PsiLsiClass)
 * - Kotlin 文件 (.kt) -> lsi-kt2 (K2 Analysis API)
 */
object UnifiedLsiResolver {

    private val log = Logger.getInstance(UnifiedLsiResolver::class.java)

    /**
     * 解析策略接口
     */
    sealed interface LsiResolveStrategy {
        fun support(file: VirtualFile): Boolean
        fun resolve(file: VirtualFile, project: Project): List<LsiClass>
    }

    /**
     * Java PSI 解析策略
     */
    private object JavaPsiStrategy : LsiResolveStrategy {
        override fun support(file: VirtualFile): Boolean {
            return file.extension?.lowercase() == "java"
        }

        override fun resolve(file: VirtualFile, project: Project): List<LsiClass> {
            return ReadAction.compute<List<LsiClass>, Throwable> {
                val psiFile = PsiManager.getInstance(project).findFile(file) as? PsiJavaFile ?: return@compute emptyList()
                psiFile.classes.map { PsiLsiClass(it) }
            }
        }
    }

    /**
     * Kotlin K2 Analysis API 解析策略
     */
    private object KotlinK2Strategy : LsiResolveStrategy {
        override fun support(file: VirtualFile): Boolean {
            return file.extension?.lowercase() == "kt"
        }

        override fun resolve(file: VirtualFile, project: Project): List<LsiClass> {
            return ReadAction.compute<List<LsiClass>, Throwable> {
                try {
                    val psiFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return@compute emptyList()
                    psiFile.declarations
                        .filterIsInstance<KtClass>()
                        .mapNotNull { ktClass ->
                            try {
                                ktClass.toLsiClassK2()
                            } catch (e: Exception) {
                                log.warn("Failed to convert KtClass to LsiClass: ${ktClass.name}", e)
                                null
                            }
                        }
                } catch (e: Exception) {
                    log.warn("Failed to parse Kotlin file: ${file.name}", e)
                    emptyList()
                }
            }
        }
    }

    private val strategies = listOf(JavaPsiStrategy, KotlinK2Strategy)

    /**
     * 从 VirtualFile 解析所有 LsiClass
     */
    fun resolveClasses(file: VirtualFile, project: Project): List<LsiClass> {
        val strategy = strategies.find { it.support(file) } ?: return emptyList()
        return strategy.resolve(file, project)
    }

    /**
     * 从 VirtualFile 解析所有 POJO 类
     */
    fun resolvePojoClasses(file: VirtualFile, project: Project): List<LsiClass> {
        return resolveClasses(file, project).filter { it.isPojo }
    }

    /**
     * 批量解析多个文件
     */
    fun resolveClasses(files: Collection<VirtualFile>, project: Project): List<LsiClass> {
        return files.flatMap { resolveClasses(it, project) }
    }

    /**
     * 批量解析多个文件中的 POJO 类
     */
    fun resolvePojoClasses(files: Collection<VirtualFile>, project: Project): List<LsiClass> {
        return files.flatMap { resolvePojoClasses(it, project) }
    }

    /**
     * 判断文件是否为支持的源文件类型
     */
    fun isSupportedSourceFile(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext == "java" || ext == "kt"
    }

    /**
     * 获取文件使用的解析策略名称（用于调试）
     */
    fun getStrategyName(file: VirtualFile): String {
        return when {
            JavaPsiStrategy.support(file) -> "Java PSI"
            KotlinK2Strategy.support(file) -> "Kotlin K2"
            else -> "Unknown"
        }
    }
}
