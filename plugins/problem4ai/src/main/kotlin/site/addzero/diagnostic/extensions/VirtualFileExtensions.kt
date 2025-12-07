package site.addzero.diagnostic.extensions

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.diagnostic.model.DiagnosticItem
import site.addzero.diagnostic.model.DiagnosticSeverity
import site.addzero.diagnostic.model.FileDiagnostics
import site.addzero.diagnostic.service.GlobalDiagnosticCache

/**
 * VirtualFile 扩展函数集合
 * 
 * 提供便捷的诊断信息访问接口
 * 支持自动推导 Project 或显式传入 Project
 */

/**
 * 尝试从 VirtualFile 推导出所属的 Project
 * 
 * 查找逻辑：
 * 1. 遍历所有打开的项目
 * 2. 检查文件是否在项目的内容根目录下
 * 3. 返回第一个匹配的项目
 * 
 * @return 文件所属的项目，如果找不到或文件在多个项目中则返回 null
 */
fun VirtualFile.inferProject(): Project? {
    val projectManager = ProjectManager.getInstance()
    val openProjects = projectManager.openProjects
    
    // 查找包含此文件的项目
    val matchingProjects = openProjects.filter { project ->
        val fileIndex = ProjectFileIndex.getInstance(project)
        fileIndex.isInContent(this)
    }
    
    // 如果只有一个匹配的项目，返回它
    // 如果有多个或没有，返回 null（避免歧义）
    return matchingProjects.singleOrNull()
}

/**
 * 获取文件所属的项目，如果无法推导则抛出异常
 * 
 * @throws IllegalStateException 如果无法推导出项目
 */
fun VirtualFile.requireProject(): Project {
    return inferProject() ?: throw IllegalStateException(
        "Cannot infer project for file: $path. " +
        "The file may not be in any open project, or it exists in multiple projects. " +
        "Please use the explicit project parameter: file.problems(project)"
    )
}

// ========== 显式 Project 参数版本（推荐，明确且安全） ==========

/**
 * 获取文件的诊断信息
 * 
 * @param project 项目实例
 * @return 文件的诊断信息，如果没有问题则返回 null
 */
fun VirtualFile.problems(project: Project): FileDiagnostics? {
    return GlobalDiagnosticCache.getInstance(project).getDiagnostics(this)
}

// ========== 自动推导 Project 版本（便捷但有限制） ==========

/**
 * 获取文件的诊断信息（自动推导 Project）
 * 
 * 自动推导文件所属的项目。
 * 
 * **限制**：
 * - 文件必须在某个打开的项目的内容根目录下
 * - 如果文件在多个项目中，会抛出异常
 * - 如果无法推导，会抛出异常
 * 
 * **建议**：
 * 在明确知道项目上下文时，优先使用 `problems(project)`
 * 
 * @return 文件的诊断信息，如果没有问题则返回 null
 * @throws IllegalStateException 如果无法推导出项目
 */
fun VirtualFile.problems(): FileDiagnostics? {
    val project = requireProject()
    return problems(project)
}

/**
 * 获取文件的所有问题项
 * 
 * @param project 项目实例
 * @return 问题列表，如果没有问题则返回空列表
 */
fun VirtualFile.problemItems(project: Project): List<DiagnosticItem> {
    return problems(project)?.items ?: emptyList()
}

/**
 * 获取文件的所有问题项（自动推导 Project）
 */
fun VirtualFile.problemItems(): List<DiagnosticItem> {
    return problems()?.items ?: emptyList()
}

/**
 * 获取文件的所有错误
 * 
 * @param project 项目实例
 * @return 错误列表
 */
fun VirtualFile.errors(project: Project): List<DiagnosticItem> {
    return problemItems(project).filter { it.severity == DiagnosticSeverity.ERROR }
}

/**
 * 获取文件的所有错误（自动推导 Project）
 */
fun VirtualFile.errors(): List<DiagnosticItem> {
    return problemItems().filter { it.severity == DiagnosticSeverity.ERROR }
}

/**
 * 获取文件的所有警告
 * 
 * @param project 项目实例
 * @return 警告列表
 */
fun VirtualFile.warnings(project: Project): List<DiagnosticItem> {
    return problemItems(project).filter { it.severity == DiagnosticSeverity.WARNING }
}

/**
 * 获取文件的所有警告（自动推导 Project）
 */
fun VirtualFile.warnings(): List<DiagnosticItem> {
    return problemItems().filter { it.severity == DiagnosticSeverity.WARNING }
}

/**
 * 检查文件是否有问题（错误或警告）
 * 
 * @param project 项目实例
 * @return 如果有问题返回 true，否则返回 false
 */
fun VirtualFile.hasProblems(project: Project): Boolean {
    return GlobalDiagnosticCache.getInstance(project).hasProblems(this)
}

/**
 * 检查文件是否有问题（自动推导 Project）
 */
fun VirtualFile.hasProblems(): Boolean {
    return hasProblems(requireProject())
}

/**
 * 检查文件是否有错误
 * 
 * @param project 项目实例
 * @return 如果有错误返回 true，否则返回 false
 */
fun VirtualFile.hasErrors(project: Project): Boolean {
    return GlobalDiagnosticCache.getInstance(project).hasErrors(this)
}

/**
 * 检查文件是否有错误（自动推导 Project）
 */
fun VirtualFile.hasErrors(): Boolean {
    return hasErrors(requireProject())
}

/**
 * 检查文件是否有警告
 * 
 * @param project 项目实例
 * @return 如果有警告返回 true，否则返回 false
 */
fun VirtualFile.hasWarnings(project: Project): Boolean {
    return GlobalDiagnosticCache.getInstance(project).hasWarnings(this)
}

/**
 * 检查文件是否有警告（自动推导 Project）
 */
fun VirtualFile.hasWarnings(): Boolean {
    return hasWarnings(requireProject())
}

/**
 * 获取文件的问题数量统计
 * 
 * @param project 项目实例
 * @return 问题统计（错误数、警告数）
 */
fun VirtualFile.problemCount(project: Project): ProblemCount {
    val items = problemItems(project)
    return ProblemCount(
        errors = items.count { it.severity == DiagnosticSeverity.ERROR },
        warnings = items.count { it.severity == DiagnosticSeverity.WARNING }
    )
}

/**
 * 获取文件的问题数量统计（自动推导 Project）
 */
fun VirtualFile.problemCount(): ProblemCount {
    val items = problemItems()
    return ProblemCount(
        errors = items.count { it.severity == DiagnosticSeverity.ERROR },
        warnings = items.count { it.severity == DiagnosticSeverity.WARNING }
    )
}

/**
 * 问题数量统计
 */
data class ProblemCount(
    val errors: Int,
    val warnings: Int
) {
    val total: Int get() = errors + warnings
    val hasProblems: Boolean get() = total > 0
}
