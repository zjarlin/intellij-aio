package site.addzero.gradle.buddy

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class GradleBuddyWidgetFactory : StatusBarWidgetFactory {
    
    override fun getId(): String = WIDGET_ID
    
    override fun getDisplayName(): String = "Gradle Buddy"
    
    override fun isAvailable(project: Project): Boolean = true
    
    override fun createWidget(project: Project): StatusBarWidget = GradleBuddyWidget(project)
    
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
    
    companion object {
        const val WIDGET_ID = "GradleBuddyWidget"
    }
}
