package site.addzero.gradle.favorites.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import site.addzero.gradle.favorites.model.FavoriteGradleTask
import site.addzero.gradle.favorites.service.GradleFavoritesService
import site.addzero.gradle.favorites.strategy.EditorContextStrategy
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class GradleFavoritesPanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = GradleFavoritesService.getInstance(project)
    private val listModel = DefaultListModel<String>()
    private val taskList = JBList(listModel)
    private val searchField = SearchTextField()
    private var allTasks = listOf<FavoriteGradleTask>()
    
    init {
        setupUI()
        refreshList()
    }
    
    private fun setupUI() {
        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyFilter()
        })
        
        val topPanel = JPanel(BorderLayout())
        topPanel.add(searchField, BorderLayout.CENTER)
        add(topPanel, BorderLayout.NORTH)
        
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
    
    private fun applyFilter() {
        val filterText = searchField.text.lowercase()
        listModel.clear()
        
        val filteredTasks = allTasks
            .filter { filterText.isBlank() || it.displayName.lowercase().contains(filterText) }
        
        filteredTasks
            .groupBy { it.group }
            .toSortedMap()
            .forEach { (group, tasks) ->
                if (group != "Default" || filteredTasks.any { it.group != "Default" }) {
                    listModel.addElement("[$group]")
                }
                tasks.forEach { listModel.addElement("  ${it.displayName}") }
            }
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
        
        val groups = service.getAllGroups().ifEmpty { listOf("Default") }
        val selectedGroup = Messages.showEditableChooseDialog(
            "Select or enter group name:",
            "Task Group",
            Messages.getQuestionIcon(),
            groups.toTypedArray(),
            groups.firstOrNull() ?: "Default",
            null
        ) ?: "Default"
        
        val maxOrder = service.getAllFavorites()
            .filter { it.group == selectedGroup }
            .maxOfOrNull { it.order } ?: -1
        
        val task = FavoriteGradleTask(
            projectPath = projectPath.trim(),
            taskName = taskName.trim(),
            group = selectedGroup.trim(),
            order = maxOrder + 1
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
        allTasks = service.getAllFavorites()
        applyFilter()
    }
    
    private fun parseTaskFromDisplayString(displayString: String): FavoriteGradleTask? {
        val cleanedString = displayString.trim().removePrefix("  ")
        return if (cleanedString.startsWith("[")) {
            null
        } else {
            service.getAllFavorites().firstOrNull { it.displayName == cleanedString }
        }
    }
}
