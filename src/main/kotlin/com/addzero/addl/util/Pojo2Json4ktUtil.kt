import com.addzero.addl.util.fieldinfo.clazz
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtClass
import java.util.*

object Pojo2Json4ktUtil {
    /**
     * 将 KtClass 转换为 Map 结构
     */
    fun generateMap(ktClass: KtClass, project: Project): Map<String, Any?> {
        val outputMap = LinkedHashMap<String, Any?>()

        ktClass.getProperties().forEach { property ->
            val kotlinType = property.type()
            val clazz = kotlinType?.clazz()
            val propertyType = property.typeReference?.text
            val propertyName = property.name
            if (propertyName != null) {
                outputMap[propertyName] =
//                    clazz
                    getObjectForType(propertyType, project, ktClass)
            }
        }

        return outputMap
    }

    /**
     * 获取属性的对象值，递归处理复合类型
     */
    private fun getObjectForType(typeName: String?, project: Project, containingClass: KtClass): Any? {
        return when (typeName) {
            "Int" -> 0
            "Boolean" -> true
            "Byte" -> 1.toByte()
            "Char" -> '-'
            "Double" -> 0.0
            "Float" -> 0.0f
            "Long" -> 0L
            "Short" -> 0.toShort()
            "String" -> "str"
            "Date" -> Date().time
            null -> null
            else -> if (typeName.startsWith("List")) {
                handleListType(typeName, project, containingClass)
            } else {
                // 检查是否是其他复杂类型
                val targetClass = findKtClassByName(typeName, project)
                targetClass?.let { generateMap(it, project) } ?: typeName
            }
        }
    }

    /**
     * 处理 List 类型
     */
    private fun handleListType(typeName: String, project: Project, containingClass: KtClass): List<Any?> {
        val list = mutableListOf<Any?>()
        val elementType = typeName.substringAfter("List<").substringBeforeLast(">")
        val elementClass = findKtClassByName(elementType, project)

        if (elementClass != null) {
            list.add(generateMap(elementClass, project))
        } else if (elementType == "String") {
            list.add("str")
        } else if (elementType == "Date") {
            list.add(Date().time)
        } else {
            list.add(elementType)
        }
        return list
    }

    /**
     * 根据类型名查找 KtClass
     */
    private fun findKtClassByName(className: String, project: Project): KtClass? {
        // 使用 Helper 查找 Kotlin 类
        val possibleClasses =
            KotlinFullClassNameIndex.Helper.get(className, project, GlobalSearchScope.projectScope(project))

        return possibleClasses.firstOrNull() as? KtClass
    }
}