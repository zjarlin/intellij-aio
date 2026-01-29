package site.addzero.split.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

/**
 * 模块拆分服务
 */
class ModuleSplitter(private val project: Project) {

    private val logger = Logger.getInstance(ModuleSplitter::class.java)

    /**
     * 执行模块拆分
     * @param sourceModule 源模块目录
     * @param selectedFiles 选中的文件列表
     * @param newModuleName 新模块名称
     * @return 是否成功
     */
    fun split(sourceModule: VirtualFile, selectedFiles: List<VirtualFile>, newModuleName: String): Boolean {
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                performSplit(sourceModule, selectedFiles, newModuleName)
            }
            showSuccessNotification(sourceModule.name, newModuleName)
            true
        } catch (e: Exception) {
            logger.error("Failed to split module", e)
            showErrorNotification(e.message ?: "Unknown error")
            false
        }
    }

    private fun performSplit(sourceModule: VirtualFile, selectedFiles: List<VirtualFile>, newModuleName: String) {
        val projectRoot = File(project.basePath!!)
        val sourceModuleDir = File(sourceModule.path)

        // 1. 检测构建系统
        val buildSystem = BuildSystem.detect(sourceModule)

        // 2. 计算新模块路径
        val newModuleDir = PathCalculator.calculateSiblingPath(sourceModuleDir, newModuleName)

        // 3. 检查模块是否已存在
        if (newModuleDir.exists()) {
            throw IllegalStateException("Module '$newModuleName' already exists")
        }

        // 4. 创建新模块目录
        newModuleDir.mkdirs()

        // 5. 复制并调整构建文件
        buildSystem.copyAndAdjustBuildFile(sourceModule, newModuleDir, newModuleName)

        // 6. 移动选中的文件
        moveFiles(selectedFiles, sourceModule, newModuleDir)

        // 7. 添加依赖到源模块
        buildSystem.addDependency(sourceModule, projectRoot, newModuleDir)

        // 8. 刷新文件系统
        VfsUtil.markDirtyAndRefresh(false, true, true, newModuleDir)
    }

    private fun moveFiles(selectedFiles: List<VirtualFile>, sourceModule: VirtualFile, newModuleDir: File) {
        val sourceModulePath = File(sourceModule.path)
        val selectedDirs = selectedFiles.filter { it.isDirectory }.map { File(it.path).toPath() }

        val effectiveSelection = selectedFiles.filter { file ->
            val filePath = File(file.path).toPath()
            selectedDirs.none { dirPath ->
                dirPath != filePath && filePath.startsWith(dirPath)
            }
        }

        effectiveSelection.forEach { file ->
            val filePath = File(file.path)
            val relativePath = filePath.relativeTo(sourceModulePath)
            val targetPath = File(newModuleDir, relativePath.path)

            if (file.isDirectory) {
                filePath.copyRecursively(targetPath, overwrite = false)
                filePath.deleteRecursively()
            } else {
                targetPath.parentFile.mkdirs()
                filePath.copyTo(targetPath, overwrite = false)
                filePath.delete()
            }
        }
    }

    private fun showSuccessNotification(sourceModuleName: String, newModuleName: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SplitModule")
            .createNotification(
                "Split Module 成功",
                "已生成模块 $newModuleName，模块 $sourceModuleName 已自动引入该依赖",
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    private fun showErrorNotification(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SplitModule")
            .createNotification(
                "Split Module 失败",
                message,
                NotificationType.ERROR
            )
            .notify(project)
    }
}
