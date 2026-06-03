package site.addzero.composebuddy.run

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

object KmpStartupRunConfigurationSupport {
    fun run(project: Project, target: KmpStartupRunTarget) {
        val taskSettings = ExternalSystemTaskExecutionSettings().apply {
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            externalProjectPath = target.externalProjectPath
            taskNames = listOf(target.fullTaskName)
        }

        val runManager = RunManager.getInstance(project)
        val configurationSettings = findExistingConfiguration(
            runManager = runManager,
            configurationName = target.configurationName,
            taskSettings = taskSettings,
        ) ?: createConfiguration(
            project = project,
            runManager = runManager,
            configurationName = target.configurationName,
            taskSettings = taskSettings,
        )

        runManager.selectedConfiguration = configurationSettings
        ProgramRunnerUtil.executeConfiguration(
            configurationSettings,
            DefaultRunExecutor.getRunExecutorInstance(),
        )
    }

    private fun createConfiguration(
        project: Project,
        runManager: RunManager,
        configurationName: String,
        taskSettings: ExternalSystemTaskExecutionSettings,
    ): RunnerAndConfigurationSettings {
        val created = requireNotNull(
            ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(
                taskSettings,
                project,
                GradleConstants.SYSTEM_ID,
            ),
        ) {
            "Unable to create Gradle run configuration: $configurationName"
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
        taskSettings: ExternalSystemTaskExecutionSettings,
    ): RunnerAndConfigurationSettings? {
        return runManager.allSettings.firstOrNull { settings ->
            settings.name == configurationName && settings.isSameGradleTaskConfiguration(taskSettings)
        }
    }

    private fun RunnerAndConfigurationSettings.isSameGradleTaskConfiguration(
        taskSettings: ExternalSystemTaskExecutionSettings,
    ): Boolean {
        if (type != ConfigurationTypeUtil.findConfigurationType(GradleExternalTaskConfigurationType::class.java)) {
            return false
        }

        val configuration = configuration as? ExternalSystemRunConfiguration ?: return false
        val existingSettings = configuration.settings

        return existingSettings.externalSystemIdString == GradleConstants.SYSTEM_ID.id &&
            existingSettings.externalProjectPath == taskSettings.externalProjectPath &&
            existingSettings.taskNames == taskSettings.taskNames &&
            existingSettings.scriptParameters == taskSettings.scriptParameters &&
            existingSettings.vmOptions == taskSettings.vmOptions
    }
}
