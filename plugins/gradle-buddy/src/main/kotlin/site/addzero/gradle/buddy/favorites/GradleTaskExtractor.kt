package site.addzero.gradle.buddy.favorites

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.Logger

/**
 * 从 AnActionEvent 中提取 Gradle 任务信息
 */
object GradleTaskExtractor {

    private val logger = Logger.getInstance(GradleTaskExtractor::class.java)

    // ExternalSystemDataKeys.SELECTED_NODES
    @Suppress("UNCHECKED_CAST")
    private val SELECTED_NODES_KEY: DataKey<List<Any>>? by lazy {
        try {
            val clazz = Class.forName("com.intellij.openapi.externalSystem.view.ExternalSystemDataKeys")
            val field = clazz.getField("SELECTED_NODES")
            field.get(null) as? DataKey<List<Any>>
        } catch (e: Exception) {
            logger.warn("Failed to get SELECTED_NODES key", e)
            null
        }
    }
    
    // 直接使用 DataKey 创建
    @Suppress("UNCHECKED_CAST")
    private val SELECTED_NODES_KEY_DIRECT: DataKey<List<Any>> by lazy {
        DataKey.create("ExternalSystem.View.SelectedNodes")
    }

    /**
     * 从事件中提取任务信息
     */
    fun extractFromEvent(event: AnActionEvent): FavoriteGradleTask? {
        logger.info("Extracting from event, place: ${event.place}")
        
        // 尝试多种方式提取
        return extractFromSelectedNodes(event) 
            ?: extractFromSelectedNodesDirect(event)
    }

    private fun extractFromSelectedNodes(event: AnActionEvent): FavoriteGradleTask? {
        val key = SELECTED_NODES_KEY
        if (key == null) {
            logger.info("SELECTED_NODES_KEY is null")
            return null
        }
        
        val selectedNodes = event.getData(key)
        logger.info("Selected nodes via reflection: ${selectedNodes?.size ?: 0}")
        
        if (selectedNodes.isNullOrEmpty()) return null

        for (node in selectedNodes) {
            logger.info("Node class: ${node.javaClass.name}")
            val task = extractFromNode(node, event) 
            if (task != null) return task
        }
        
        return null
    }
    
    private fun extractFromSelectedNodesDirect(event: AnActionEvent): FavoriteGradleTask? {
        val selectedNodes = event.getData(SELECTED_NODES_KEY_DIRECT)
        logger.info("Selected nodes via direct key: ${selectedNodes?.size ?: 0}")
        
        if (selectedNodes.isNullOrEmpty()) return null

        for (node in selectedNodes) {
            logger.info("Node class: ${node.javaClass.name}")
            val task = extractFromNode(node, event) 
            if (task != null) return task
        }
        
        return null
    }

    private fun extractFromNode(node: Any, event: AnActionEvent): FavoriteGradleTask? {
        return try {
            // 获取 DataNode
            val dataNode = invokeGetter(node, "getDataNode") ?: return null
            val data = invokeGetter(dataNode, "getData") ?: return null
            
            // 检查是否是 TaskData
            if (!data.javaClass.name.contains("TaskData")) return null
            
            val linkedExternalProjectPath = invokeGetter(data, "getLinkedExternalProjectPath") as? String ?: return null
            val taskName = invokeGetter(data, "getName") as? String ?: return null
            val group = (invokeGetter(data, "getGroup") as? String) ?: "other"
            
            val projectPath = extractModulePath(linkedExternalProjectPath, event.project?.basePath)
            
            FavoriteGradleTask(
                projectPath = projectPath,
                taskName = taskName,
                group = group,
                order = 0
            )
        } catch (e: Exception) {
            logger.warn("Failed to extract task from node", e)
            null
        }
    }

    private fun invokeGetter(obj: Any, methodName: String): Any? {
        return try {
            val method = obj.javaClass.getMethod(methodName)
            method.invoke(obj)
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
