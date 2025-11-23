package site.addzero.gradle.favorites.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import site.addzero.gradle.favorites.model.FavoriteGradleTask
import site.addzero.gradle.favorites.service.GradleFavoritesService
import site.addzero.gradle.favorites.strategy.EditorContextStrategy
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

class GradleFavoritesPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = GradleFavoritesService.getInstance(project)
    private val listModel = DefaultListModel<String>()
    private val taskList = JBList(listModel)
    
    init {
        setupUI()
        refreshList()
    }
    
    private fun setupUI() {
        val scrollPane = JBScrollPane(taskList)
        add(scrollPane, BorderLayout.CENTER)
        
        val buttonPanel = JPanel(GridLayout(1, 3, 5, 5))
        
        val addButton = JButton("Add Favorite")
        addButton.addActionListener { showAddDialog() }
        
        val removeButton = JButton("Remove")
        removeButton.addActionListener { removeSelected() }
        
        val executeButton = JButton("Execute")
        executeButton.addActionListener { executeSelected() }
        
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(executeButton)
        
        add(buttonPanel, BorderLayout.SOUTH)
    }
    
    private fun showAddDialog() {
        val projectPath = Messages.showInputDialog(
            project,
            "Enter module path (e.g., :lib:tool-psi):",
            "Add Favorite Task",
            Messages.getQuestionIcon()
        ) ?: return
        
        val taskName = Messages.showInputDialog(
            project,
            "Enter task name (e.g., kspKotlin):",
            "Add Favorite Task",
            Messages.getQuestionIcon()
        ) ?: return
        
        if (projectPath.isBlank() || taskName.isBlank()) {
            Messages.showErrorDialog(project, "Module path and task name cannot be empty!", "Error")
            return
        }
        
        val task = FavoriteGradleTask(
            projectPath = projectPath.trim(),
            taskName = taskName.trim()
        )
        
        service.addFavorite(task)
        refreshList()
        
        Messages.showInfoMessage(project, "Added '${task.displayName}' to favorites.", "Success")
    }
    
    private fun removeSelected() {
        val selectedValue = taskList.selectedValue ?: run {
            Messages.showWarningDialog(project, "Please select a task to remove.", "No Selection")
            return
        }
        
        val task = parseTaskFromDisplayString(selectedValue) ?: return
        
        service.removeFavorite(task)
        refreshList()
        
        Messages.showInfoMessage(project, "Removed '${task.displayName}' from favorites.", "Success")
    }
    
    private fun executeSelected() {
        val selectedValue = taskList.selectedValue ?: run {
            Messages.showWarningDialog(project, "Please select a task to execute.", "No Selection")
            return
        }
        
        val task = parseTaskFromDisplayString(selectedValue) ?: return
        
        try {
            val strategy = EditorContextStrategy()
            strategy.executeTask(project, task)
            Messages.showInfoMessage(project, "Executing '${task.displayName}'...", "Task Started")
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to execute task: ${e.message}", "Execution Error")
        }
    }
    
    private fun refreshList() {
        listModel.clear()
        service.getAllFavorites()
            .map { it.displayName }
            .forEach { listModel.addElement(it) }
    }
    
    private fun parseTaskFromDisplayString(displayString: String): FavoriteGradleTask? {
        return service.getAllFavorites().firstOrNull { it.displayName == displayString }
    }
}
