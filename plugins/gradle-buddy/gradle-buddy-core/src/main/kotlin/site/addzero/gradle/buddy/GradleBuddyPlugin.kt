package site.addzero.gradle.buddy

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import org.jetbrains.plugins.gradle.settings.GradleSettings

class GradleBuddyPlugin : ProjectActivity {
    override suspend fun execute(project: Project) {
        // 检查是否为 Gradle 项目并显示加载指示器
        if (isGradleProject(project) && !isGradleProjectLoaded(project)) {
            showGradleLoadIndicator(project)
        }
    }

    // 判断是否为 Gradle 项目
    private fun isGradleProject(project: Project): Boolean {
        val baseDir = project.guessProjectDir() ?: return false
        return baseDir.findChild("build.gradle") != null ||
               baseDir.findChild("build.gradle.kts") != null ||
               baseDir.findChild("settings.gradle") != null ||
               baseDir.findChild("settings.gradle.kts") != null
    }

    // 判断 Gradle 项目是否已加载
    private fun isGradleProjectLoaded(project: Project): Boolean {
        return GradleSettings.getInstance(project).linkedProjectsSettings.isNotEmpty()
    }

    // 显示 Gradle 加载指示器
    private fun showGradleLoadIndicator(project: Project) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        val widget = statusBar?.getWidget(GradleBuddyWidgetFactory.WIDGET_ID)
        if (widget is GradleBuddyWidget) {
            widget.showIndicator()
        }
    }
}
