package site.addzero.gradle.buddy.filter

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * 负责把 Gradle task 绑定到 IDEA 右上角运行配置。
 * 目前仅用于 runIde，保证工具条点击后可以出现在运行配置下拉框中，并可直接重跑。
 */
object GradleRunConfigurationSupport {

    fun ensureRunConfigurationAndExecute(
        project: Project,
        configurationName: String,
        taskSettings: ExternalSystemTaskExecutionSettings
    ) {
        val runManager = RunManager.getInstance(project)
        val existing = findExistingConfiguration(runManager, configurationName, taskSettings)
        val configurationSettings = existing ?: createConfiguration(project, runManager, configurationName, taskSettings)

        runManager.selectedConfiguration = configurationSettings
        ProgramRunnerUtil.executeConfiguration(
            configurationSettings,
            DefaultRunExecutor.getRunExecutorInstance()
        )
    }

    private fun createConfiguration(
        project: Project,
        runManager: RunManager,
        configurationName: String,
        taskSettings: ExternalSystemTaskExecutionSettings
    ): RunnerAndConfigurationSettings {
        val created = requireNotNull(
            ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(
                taskSettings,
                project,
                GradleConstants.SYSTEM_ID
            )
        ) {
            "无法为 Gradle 任务创建运行配置：$configurationName"
        }

        created.name = configurationName
        created.isTemporary = false
        created.storeInLocalWorkspace()
        runManager.addConfiguration(created)
        return created
    }

    private fun findExistingConfiguration(
        runManager: RunManager,
        configurationName: String,
        taskSettings: ExternalSystemTaskExecutionSettings
    ): RunnerAndConfigurationSettings? {
        return runManager.allSettings.firstOrNull { settings ->
            settings.name == configurationName && isSameGradleTaskConfiguration(settings, taskSettings)
        }
    }

    private fun isSameGradleTaskConfiguration(
        settings: RunnerAndConfigurationSettings,
        taskSettings: ExternalSystemTaskExecutionSettings
    ): Boolean {
        if (settings.type != ConfigurationTypeUtil.findConfigurationType(GradleExternalTaskConfigurationType::class.java)) {
            return false
        }

        val configuration = settings.configuration as? ExternalSystemRunConfiguration ?: return false
        val existingSettings = configuration.settings

        return existingSettings.externalSystemIdString == GradleConstants.SYSTEM_ID.id &&
            existingSettings.externalProjectPath == taskSettings.externalProjectPath &&
            existingSettings.taskNames == taskSettings.taskNames &&
            existingSettings.scriptParameters == taskSettings.scriptParameters &&
            existingSettings.vmOptions == taskSettings.vmOptions
    }
}
