package com.addzero.addl.action.autoddlwithdb

import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class AutoDDLDialog(private val project: Project) : DialogWrapper(project) {
    private val packagePathField = JBTextField()
    private val dataSourceComboBox = ComboBox<DbDataSource>()

    init {
        title = "Auto DDL Configuration"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            fill = GridBagConstraints.HORIZONTAL
        }

        // 包路径选择
        gbc.apply {
            gridx = 0
            gridy = 0
        }
        panel.add(JLabel("Entity Package Path:"), gbc)

        gbc.apply {
            gridx = 1
            weightx = 1.0
        }
        panel.add(packagePathField, gbc)

        // 数据源选择
        gbc.apply {
            gridx = 0
            gridy = 1
            weightx = 0.0
        }
        panel.add(JLabel("Data Source:"), gbc)

        gbc.gridx = 1
        loadDataSources()
        panel.add(dataSourceComboBox, gbc)

        return panel
    }

    private fun loadDataSources() {
        val dbPsiFacade = DbPsiFacade.getInstance(project)
        val dataSources = dbPsiFacade.dataSources

        val model = DefaultComboBoxModel<DbDataSource>()
        dataSources.forEach { model.addElement(it) }
        dataSourceComboBox.model = model
        
        dataSourceComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is DbDataSource) {
                    text = value.name
                }
                return this
            }
        }
    }

    val selectedPackagePath: String
        get() = packagePathField.text

    val selectedDataSource: DbDataSource?
        get() = dataSourceComboBox.selectedItem as? DbDataSource

    fun hideDataSourceSelection() {
        dataSourceComboBox.isVisible = false
        (dataSourceComboBox.parent as? JPanel)?.components?.forEach { component ->
            if (component is JLabel && component.text == "Data Source:") {
                component.isVisible = false
            }
        }
    }
} 