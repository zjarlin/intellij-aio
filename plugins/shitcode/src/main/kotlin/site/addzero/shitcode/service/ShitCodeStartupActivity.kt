package site.addzero.shitcode.service

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * ShitCode 启动活动
 * 项目启动时触发全局扫描
 */
class ShitCodeStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // 等待索引完成后执行扫描
        DumbService.getInstance(project).runWhenSmart {
            ShitCodeCacheService.getInstance(project).performFullScan()
        }
    }
}
