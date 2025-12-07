package site.addzero.shitcode.service

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import site.addzero.shitcode.settings.ShitCodeSettingsService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ShitCode 全局缓存服务
 * 
 * 采用饿汉式策略：项目启动后立即扫描所有标记的代码元素
 * 缓存扫描结果，提供快速查询接口
 */
@Service(Service.Level.PROJECT)
class ShitCodeCacheService(private val project: Project) : Disposable {

    // 缓存：文件路径 -> 标记的元素列表
    private val cache = ConcurrentHashMap<String, MutableList<ShitCodeElement>>()
    
    // 是否已完成初始扫描
    private val initialized = AtomicBoolean(false)
    
    // 更新监听器
    private val listeners = CopyOnWriteArrayList<CacheUpdateListener>()

    companion object {
        fun getInstance(project: Project): ShitCodeCacheService =
            project.getService(ShitCodeCacheService::class.java)
    }

    /**
     * 执行全量扫描
     */
    fun performFullScan() {
        if (DumbService.getInstance(project).isDumb) {
            // 等待索引完成后再扫描
            DumbService.getInstance(project).runWhenSmart {
                doFullScan()
            }
        } else {
            doFullScan()
        }
    }

    private fun doFullScan() {
        ApplicationManager.getApplication().executeOnPooledThread {
            ReadAction.run<Throwable> {
                val elements = scanAnnotatedElements()
                updateCache(elements)
                initialized.set(true)
                notifyListeners()
            }
        }
    }

    /**
     * 扫描所有标记的元素
     */
    private fun scanAnnotatedElements(): List<ShitCodeElement> {
        if (DumbService.getInstance(project).isDumb) {
            return emptyList()
        }

        val elements = mutableListOf<ShitCodeElement>()
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        val annotationName = ShitCodeSettingsService.getInstance().state.shitAnnotation

        // 扫描 Kotlin 文件
        val ktFiles = com.intellij.psi.search.FileTypeIndex.getFiles(
            KotlinFileType.INSTANCE,
            scope
        )

        for (virtualFile in ktFiles) {
            // 跳过构建目录
            if (shouldSkipFile(virtualFile.path)) continue
            
            val ktFile = psiManager.findFile(virtualFile) as? KtFile ?: continue
            
            ktFile.declarations.forEach { declaration ->
                when (declaration) {
                    is KtClass -> {
                        if (hasAnnotation(declaration, annotationName)) {
                            elements.add(ShitCodeElement.fromKtClass(declaration))
                        }
                        // 扫描类成员
                        declaration.declarations.forEach { member ->
                            when (member) {
                                is KtFunction -> {
                                    if (hasAnnotation(member, annotationName)) {
                                        elements.add(ShitCodeElement.fromKtFunction(member))
                                    }
                                }
                                is KtProperty -> {
                                    if (hasAnnotation(member, annotationName)) {
                                        elements.add(ShitCodeElement.fromKtProperty(member))
                                    }
                                }
                            }
                        }
                    }
                    is KtFunction -> {
                        if (hasAnnotation(declaration, annotationName)) {
                            elements.add(ShitCodeElement.fromKtFunction(declaration))
                        }
                    }
                    is KtProperty -> {
                        if (hasAnnotation(declaration, annotationName)) {
                            elements.add(ShitCodeElement.fromKtProperty(declaration))
                        }
                    }
                }
            }
        }

        // 扫描 Java 文件
        val javaFiles = com.intellij.psi.search.FileTypeIndex.getFiles(
            JavaFileType.INSTANCE,
            scope
        )

        for (virtualFile in javaFiles) {
            // 跳过构建目录
            if (shouldSkipFile(virtualFile.path)) continue
            
            val javaFile = psiManager.findFile(virtualFile) as? PsiJavaFile ?: continue
            
            javaFile.classes.forEach { psiClass ->
                if (hasJavaAnnotation(psiClass, annotationName)) {
                    elements.add(ShitCodeElement.fromPsiClass(psiClass))
                }
                
                psiClass.methods.forEach { method ->
                    if (hasJavaAnnotation(method, annotationName)) {
                        elements.add(ShitCodeElement.fromPsiMethod(method))
                    }
                }
                
                psiClass.fields.forEach { field ->
                    if (hasJavaAnnotation(field, annotationName)) {
                        elements.add(ShitCodeElement.fromPsiField(field))
                    }
                }
            }
        }

        return elements
    }

    /**
     * 更新缓存
     */
    private fun updateCache(elements: List<ShitCodeElement>) {
        cache.clear()
        elements.groupBy { it.filePath }.forEach { (filePath, fileElements) ->
            cache[filePath] = fileElements.toMutableList()
        }
    }

    /**
     * 是否跳过该文件
     */
    private fun shouldSkipFile(path: String): Boolean {
        return path.contains("/build/") || 
               path.contains("/out/") ||
               path.contains("/.gradle/")
    }

    /**
     * 检查 Kotlin 元素是否有指定注解
     */
    private fun hasAnnotation(element: KtAnnotated, annotationName: String): Boolean {
        return element.annotationEntries.any { annotation ->
            val shortName = annotation.shortName?.asString()
            val fullName = annotation.typeReference?.text
            
            shortName == annotationName || 
            fullName == annotationName ||
            fullName?.endsWith(".$annotationName") == true
        }
    }
    
    /**
     * 检查 Java 元素是否有指定注解
     */
    private fun hasJavaAnnotation(element: PsiModifierListOwner, annotationName: String): Boolean {
        val annotations = element.modifierList?.annotations ?: return false
        return annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            val shortName = annotation.nameReferenceElement?.referenceName
            
            shortName == annotationName ||
            qualifiedName == annotationName ||
            qualifiedName?.endsWith(".$annotationName") == true
        }
    }

    /**
     * 获取所有标记的元素（按文件分组）
     */
    fun getAllElements(): Map<String, List<ShitCodeElement>> {
        return cache.toMap()
    }

    /**
     * 获取所有标记的元素（扁平列表）
     */
    fun getAllElementsList(): List<ShitCodeElement> {
        return cache.values.flatten()
    }

    /**
     * 获取指定文件的标记元素
     */
    fun getElementsForFile(filePath: String): List<ShitCodeElement> {
        return cache[filePath] ?: emptyList()
    }

    /**
     * 获取统计信息
     */
    fun getStatistics(): ShitCodeStatistics {
        val allElements = getAllElementsList()
        return ShitCodeStatistics(
            totalFiles = cache.keys.size,
            totalElements = allElements.size,
            classes = allElements.count { it.type == ElementType.CLASS },
            methods = allElements.count { it.type == ElementType.METHOD },
            fields = allElements.count { it.type == ElementType.FIELD }
        )
    }

    /**
     * 是否已完成初始化
     */
    fun isInitialized(): Boolean = initialized.get()

    /**
     * 添加更新监听器
     */
    fun addListener(listener: CacheUpdateListener) {
        listeners.add(listener)
    }

    /**
     * 移除更新监听器
     */
    fun removeListener(listener: CacheUpdateListener) {
        listeners.remove(listener)
    }

    /**
     * 通知监听器
     */
    private fun notifyListeners() {
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it.onCacheUpdate() }
        }
    }

    override fun dispose() {
        cache.clear()
        listeners.clear()
    }
}

/**
 * 标记的代码元素
 */
data class ShitCodeElement(
    val name: String,
    val type: ElementType,
    val filePath: String,
    val fileName: String,
    val psiElement: PsiElement
) {
    companion object {
        fun fromKtClass(ktClass: KtClass): ShitCodeElement {
            return ShitCodeElement(
                name = "类: ${ktClass.name}",
                type = ElementType.CLASS,
                filePath = ktClass.containingFile.virtualFile.path,
                fileName = ktClass.containingFile.name,
                psiElement = ktClass
            )
        }

        fun fromKtFunction(function: KtFunction): ShitCodeElement {
            return ShitCodeElement(
                name = "函数: ${function.name}",
                type = ElementType.METHOD,
                filePath = function.containingFile.virtualFile.path,
                fileName = function.containingFile.name,
                psiElement = function
            )
        }

        fun fromKtProperty(property: KtProperty): ShitCodeElement {
            return ShitCodeElement(
                name = "属性: ${property.name}",
                type = ElementType.FIELD,
                filePath = property.containingFile.virtualFile.path,
                fileName = property.containingFile.name,
                psiElement = property
            )
        }

        fun fromPsiClass(psiClass: PsiClass): ShitCodeElement {
            return ShitCodeElement(
                name = "类: ${psiClass.name}",
                type = ElementType.CLASS,
                filePath = psiClass.containingFile.virtualFile.path,
                fileName = psiClass.containingFile.name,
                psiElement = psiClass
            )
        }

        fun fromPsiMethod(method: PsiMethod): ShitCodeElement {
            return ShitCodeElement(
                name = "方法: ${method.name}",
                type = ElementType.METHOD,
                filePath = method.containingFile.virtualFile.path,
                fileName = method.containingFile.name,
                psiElement = method
            )
        }

        fun fromPsiField(field: PsiField): ShitCodeElement {
            return ShitCodeElement(
                name = "字段: ${field.name}",
                type = ElementType.FIELD,
                filePath = field.containingFile.virtualFile.path,
                fileName = field.containingFile.name,
                psiElement = field
            )
        }
    }
}

/**
 * 元素类型
 */
enum class ElementType {
    CLASS, METHOD, FIELD
}

/**
 * 统计信息
 */
data class ShitCodeStatistics(
    val totalFiles: Int,
    val totalElements: Int,
    val classes: Int,
    val methods: Int,
    val fields: Int
)

/**
 * 缓存更新监听器
 */
fun interface CacheUpdateListener {
    fun onCacheUpdate()
}
