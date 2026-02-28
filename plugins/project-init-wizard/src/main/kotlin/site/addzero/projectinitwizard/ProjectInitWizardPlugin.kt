package site.addzero.projectinitwizard

import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.startup.StartupActivity

class ProjectInitWizardPlugin : StartupActivity {

    override fun runActivity(project: com.intellij.openapi.project.Project) {
        // Plugin initialization
        // Register actions dynamically if needed
    }
}
