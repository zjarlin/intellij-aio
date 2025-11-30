package site.addzero.gradle.buddy

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener

/**
 * 监听项目关闭事件，清理 GradleBuddyService 资源
 */
class GradleBuddyProjectManagerListener : ProjectCloseListener {
    override fun projectClosing(project: Project) {
        project.service<GradleBuddyService>().dispose()
    }
}