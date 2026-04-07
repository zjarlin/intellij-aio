package site.addzero.kcp.transformoverload.idea

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class TransformOverloadProjectActivity : ProjectActivity {

    private val logger = Logger.getInstance(TransformOverloadProjectActivity::class.java)

    override suspend fun execute(project: Project) {
        logger.info("Scheduling transform-overload IDEA stub refresh")
        project.getService(TransformOverloadStubService::class.java).scheduleRefresh()
    }
}
