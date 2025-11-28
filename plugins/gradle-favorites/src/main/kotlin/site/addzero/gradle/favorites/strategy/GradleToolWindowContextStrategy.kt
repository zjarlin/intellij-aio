package site.addzero.gradle.favorites.strategy

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import site.addzero.gradle.favorites.model.FavoriteGradleTask

/**
 * Gradle 工具窗口上下文策略
 * 使用反射访问 ExternalSystemDataKeys (internal API)
 */
class GradleToolWindowContextStrategy : AbstractGradleTaskContextStrategy() {

    companion object {
        private val GRADLE_PLACES = setOf(
            "Gradle.View.ActionsToolbar.RightPanel",
            "Gradle.View.ActionsToolbar.CenterPanel",
            "GRADLE_VIEW_TOOLBAR",
            "ExternalSystemView.ActionsToolbar",
            "ExternalSystemView.BaseTree"
        )

        @Suppress("UNCHECKED_CAST")
        private val SELECTED_NODES_KEY: DataKey<List<Any>>? by lazy {
            try {
                val clazz = Class.forName("com.intellij.openapi.externalSystem.view.ExternalSystemDataKeys")
                val field = clazz.getField("SELECTED_NODES")
                field.get(null) as? DataKey<List<Any>>
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun support(event: AnActionEvent): Boolean {
        val place = event.place
        return place.contains("Gradle", ignoreCase = true) ||
               place.contains("ExternalSystem", ignoreCase = true) ||
               place in GRADLE_PLACES
    }

    override fun extractTaskInfo(event: AnActionEvent): FavoriteGradleTask? {
        val key = SELECTED_NODES_KEY ?: return null
        val selectedNodes = event.getData(key)
        if (selectedNodes.isNullOrEmpty()) return null

        for (node in selectedNodes) {
            val taskData = extractTaskData(node) ?: continue
            
            val linkedExternalProjectPath = invokeGetter(taskData, "getLinkedExternalProjectPath") as? String ?: continue
            val taskName = invokeGetter(taskData, "getName") as? String ?: continue
            val group = (invokeGetter(taskData, "getGroup") as? String) ?: "other"

            return FavoriteGradleTask(
                projectPath = extractModulePath(linkedExternalProjectPath, event.project?.basePath),
                taskName = taskName,
                group = group,
                order = 0
            )
        }
        return null
    }

    override fun getCurrentModulePath(event: AnActionEvent): String? {
        val key = SELECTED_NODES_KEY ?: return null
        val selectedNodes = event.getData(key)
        if (selectedNodes.isNullOrEmpty()) return null

        for (node in selectedNodes) {
            val taskData = extractTaskData(node) ?: continue
            val linkedExternalProjectPath = invokeGetter(taskData, "getLinkedExternalProjectPath") as? String ?: continue
            return extractModulePath(linkedExternalProjectPath, event.project?.basePath)
        }
        return null
    }

    private fun extractTaskData(node: Any): Any? {
        return try {
            val dataNode = invokeGetter(node, "getDataNode") ?: return null
            val data = invokeGetter(dataNode, "getData")
            if (data?.javaClass?.name == "com.intellij.openapi.externalSystem.model.task.TaskData") data else null
        } catch (e: Exception) {
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
