package site.addzero.autoddl.jimmer.listener

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.autoddl.jimmer.notification.EntityChangeNotifier

/**
 * Jimmer 实体变更监听器
 * 监听带 @Entity 注解的文件变化
 */
class JimmerEntityChangeListener(private val project: Project) : BulkFileListener {
    
    private val notifier = EntityChangeNotifier.getInstance(project)
    private var lastChangeTime = 0L
    private val DEBOUNCE_DELAY = 2000L  // 防抖：2秒内的多次变更只通知一次
    
    override fun after(events: List<VFileEvent>) {
        // 过滤出可能的实体文件变更
        val entityFileChanges = events.filter { event ->
            val file = event.file ?: return@filter false
            isEntityFile(file)
        }
        
        if (entityFileChanges.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            // 防抖：避免频繁通知
            if (currentTime - lastChangeTime > DEBOUNCE_DELAY) {
                lastChangeTime = currentTime
                notifier.notifyEntityChanged(entityFileChanges.size)
            }
        }
    }
    
    /**
     * 检查是否是实体文件
     */
    private fun isEntityFile(file: VirtualFile): Boolean {
        // 只关心 .java 和 .kt 文件
        if (!file.name.endsWith(".java") && !file.name.endsWith(".kt")) {
            return false
        }
        
        try {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
            
            // Java 文件
            if (psiFile is PsiJavaFile) {
                return psiFile.classes.any { psiClass ->
                    psiClass.annotations.any { annotation ->
                        isEntityAnnotation(annotation.qualifiedName)
                    }
                }
            }
            
            // Kotlin 文件
            if (psiFile is KtFile) {
                return psiFile.declarations.any { declaration ->
                    declaration.annotationEntries.any { annotation ->
                        isEntityAnnotation(annotation.shortName?.asString())
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误
        }
        
        return false
    }
    
    /**
     * 检查是否是实体注解
     */
    private fun isEntityAnnotation(annotationName: String?): Boolean {
        if (annotationName == null) return false
        return annotationName.contains("Entity") || 
               annotationName == "org.babyfish.jimmer.sql.Entity" ||
               annotationName == "javax.persistence.Entity" ||
               annotationName == "jakarta.persistence.Entity"
    }
}
