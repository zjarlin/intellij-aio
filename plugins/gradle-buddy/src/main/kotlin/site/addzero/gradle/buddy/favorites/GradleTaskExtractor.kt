package site.addzero.gradle.buddy.favorites

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.TaskData

/**
 * 从 AnActionEvent 中提取 Gradle 任务信息
 */
object GradleTaskExtractor {

    private val logger = Logger.getInstance(GradleTaskExtractor::class.java)
    
    // 通过反射获取 ExternalSystemDataKeys.SELECTED_NODES
    @Suppress("UNCHECKED_CAST")
    private val SELECTED_NODES_KEY: DataKey<List<Any>>? by lazy {
        try {
            val clazz = Class.forName("com.intellij.openapi.externalSystem.view.ExternalSystemDataKeys")
            val field = clazz.getDeclaredField("SELECTED_NODES")
            field.isAccessible = true
            field.get(null) as? DataKey<List<Any>>
        } catch (e: Exception) {
            logger.warn("Failed to get SELECTED_NODES DataKey: ${e.message}")
            null
        }
    }

    /**
     * 从事件中提取任务信息
     */
    fun extractFromEvent(event: AnActionEvent): FavoriteGradleTask? {
        return try {
            extractFromExternalSystemNodes(event)
        } catch (e: Exception) {
            logger.warn("Failed to extract task info", e)
            null
        }
    }

    private fun extractFromExternalSystemNodes(event: AnActionEvent): FavoriteGradleTask? {
        val key = SELECTED_NODES_KEY
        if (key == null) {
            return null
        }
        
        val selectedNodes = event.getData(key)
        if (selectedNodes.isNullOrEmpty()) {
            return null
        }

        for (node in selectedNodes) {
            val task = extractFromNode(node, event)
            if (task != null) return task
        }
        
        return null
    }

    private fun extractFromNode(node: Any, event: AnActionEvent): FavoriteGradleTask? {
        return try {
            // 调用 node.getData() 获取 DataNode
            val getDataMethod = node.javaClass.getMethod("getData")
            val dataNode = getDataMethod.invoke(node) ?: return null
            
            // 调用 dataNode.getData() 获取实际数据
            val getDataNodeDataMethod = dataNode.javaClass.getMethod("getData")
            val data = getDataNodeDataMethod.invoke(dataNode) ?: return null
            
            // 检查是否是 TaskData
            if (data !is TaskData) return null
            
            val linkedExternalProjectPath = data.linkedExternalProjectPath
            val taskName = data.name
            val group = data.group ?: "other"
            
            val projectPath = extractModulePath(linkedExternalProjectPath, event.project?.basePath)
            
            FavoriteGradleTask(
                projectPath = projectPath,
                taskName = taskName,
                group = group,
                order = 0
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractModulePath(fullPath: String, basePath: String?): String {
        if (basePath == null) return ":"
        
        val relativePath = fullPath.removePrefix(basePath).trim('/')
        return if (relativePath.isEmpty()) {
            ":"
        } else {
            ":" + relativePath.replace("/", ":")
        }
    }
}
