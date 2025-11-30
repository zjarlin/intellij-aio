package site.addzero.gradle.buddy

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Gradle Buddy 插件图标
 */
object GradleBuddyIcons {
    
    @JvmField
    val ModuleTasks: Icon = IconLoader.getIcon("/icons/moduleTasksIcon.svg", GradleBuddyIcons::class.java)
    
    @JvmField
    val PluginIcon: Icon = IconLoader.getIcon("/META-INF/pluginIcon.svg", GradleBuddyIcons::class.java)
}
