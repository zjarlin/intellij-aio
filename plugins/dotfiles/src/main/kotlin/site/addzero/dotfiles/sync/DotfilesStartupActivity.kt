package site.addzero.dotfiles.sync

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class DotfilesStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        project.getService(DotfilesProjectSyncService::class.java).start()
    }
}
