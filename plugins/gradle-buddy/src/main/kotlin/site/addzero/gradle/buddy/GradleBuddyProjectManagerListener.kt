package site.addzero.gradle.buddy

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class GradleBuddyProjectManagerListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        // 项目打开时初始化服务
        project.service<GradleBuddyService>().init()
    }
    
    override fun projectClosing(project: Project) {
        // 项目关闭时清理资源
        project.service<GradleBuddyService>().dispose()
    }
}