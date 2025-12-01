package site.addzero.gradle.buddy.filter

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Gradle 任务过滤服务
 */
@Service(Service.Level.PROJECT)
class GradleTaskFilterService {

    private var currentFilter: String? = null

    fun setFilter(modulePath: String) {
        currentFilter = modulePath
    }

    fun clearFilter() {
        currentFilter = null
    }

    fun getCurrentFilter(): String? = currentFilter

    fun isFiltered(): Boolean = currentFilter != null

    companion object {
        fun getInstance(project: Project): GradleTaskFilterService {
            return project.service()
        }
    }
}
