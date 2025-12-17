package site.addzero.lsi.analyzer.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.pom.Navigatable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import site.addzero.util.db.DatabaseType
import site.addzero.util.ddlgenerator.extension.toCompleteSchemaDDL
import site.addzero.util.ddlgenerator.extension.toCreateTableDDL
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
import site.addzero.util.lsi.database.isPrimaryKey
import site.addzero.util.lsi.field.isNullable
import site.addzero.util.lsi_impl.impl.intellij.virtualfile.toAllLsiClassesUnified
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

class PojoMetaToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setIcon(AllIcons.Nodes.Class)
        val panel = PojoMetaPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "POJO元数据", false)
        toolWindow.contentManager.addContent(content)
    }
}

class PojoMetaPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val pojoTableModel = DefaultTableModel(arrayOf("类名", "包名", "字段数", "表名", "注释"), 0)
    private val pojoTable = JBTable(pojoTableModel).apply {
        setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
    }
    private val fieldTableModel = DefaultTableModel(arrayOf("字段名", "类型", "列名", "注释", "主键", "可空"), 0)
    private val fieldTable = JBTable(fieldTableModel)
    private val ddlOutputArea = JBTextArea().apply {
        isEditable = false
        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
    }
    private val statusLabel = JLabel("就绪")
    private val progressBar = JProgressBar(0, 100).apply {
        isStringPainted = true
        string = ""
        isVisible = false
    }
    private val progressLabel = JLabel("").apply {
        isVisible = false
    }

    private var currentPojoList: List<LsiClass> = emptyList()
    private var selectedDialect: DatabaseType = DatabaseType.MYSQL

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)

        val tabbedPane = JBTabbedPane()

        // POJO 列表面板
        val pojoPanel = JPanel(BorderLayout()).apply {
            val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
                topComponent = JBScrollPane(pojoTable)
                bottomComponent = JBScrollPane(fieldTable)
                dividerLocation = 300
            }
            add(splitPane, BorderLayout.CENTER)
        }
        tabbedPane.addTab("POJO列表", pojoPanel)

        // DDL 生成面板
        val ddlPanel = createDdlPanel()
        tabbedPane.addTab("DDL生成", ddlPanel)

        add(tabbedPane, BorderLayout.CENTER)

        // 底部状态栏：包含进度条和状态文字
        val statusPanel = JPanel(BorderLayout(5, 0)).apply {
            add(progressLabel, BorderLayout.WEST)
            add(progressBar, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.EAST)
        }
        add(statusPanel, BorderLayout.SOUTH)
    }

    private fun createToolbar(): JToolBar {
        return JToolBar().apply {
            isFloatable = false

            add(JButton("扫描", AllIcons.Actions.Refresh).apply {
                addActionListener { scanPojos() }
            })

            add(JButton("定位类", AllIcons.Nodes.Class).apply {
                toolTipText = "打开所选POJO的源文件"
                addActionListener { navigateToSelectedPojo() }
            })

            addSeparator()

            add(JButton("生成DDL", AllIcons.Nodes.DataTables).apply {
                addActionListener { generateDdlForSelected() }
            })
        }
    }

    private fun createDdlPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("数据库方言:"))
                val dialectCombo = ComboBox(DatabaseType.entries.map { it.name }.toTypedArray())
                dialectCombo.addActionListener {
                    selectedDialect = DatabaseType.entries[dialectCombo.selectedIndex]
                }
                add(dialectCombo)

                add(JButton("生成选中", AllIcons.Actions.Execute).apply {
                    addActionListener { generateDdlForSelected() }
                })

                add(JButton("生成全部", AllIcons.Actions.Selectall).apply {
                    addActionListener { generateDdlForAll() }
                })

                add(JButton("复制", AllIcons.Actions.Copy).apply {
                    addActionListener {
                        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(java.awt.datatransfer.StringSelection(ddlOutputArea.text), null)
                        statusLabel.text = "已复制到剪贴板"
                    }
                })
            }
            add(controlPanel, BorderLayout.NORTH)
            add(JBScrollPane(ddlOutputArea), BorderLayout.CENTER)
        }
    }

    private fun setupListeners() {
        pojoTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && pojoTable.selectedRow >= 0) {
                showFieldsForSelectedPojo()
            }
        }
    }

    private fun scanPojos() {
        if (DumbService.getInstance(project).isDumb) {
            Messages.showInfoMessage("索引构建中，请稍后再试", "暂不可用")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "扫描POJO类", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "正在收集源文件..."

                SwingUtilities.invokeLater {
                    progressBar.isVisible = true
                    progressLabel.isVisible = true
                    progressBar.isIndeterminate = true
                    progressBar.string = "收集文件中..."
                    progressLabel.text = "准备扫描"
                    statusLabel.text = "正在收集源文件..."
                }

                try {
                    // 第一阶段：收集所有源文件
                    val sourceFiles = ReadAction.compute<List<com.intellij.openapi.vfs.VirtualFile>, Throwable> {
                        val files = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
                        val fileIndex = ProjectFileIndex.getInstance(project)
                        var scannedFiles = 0

                        fileIndex.iterateContent { file ->
                            if (indicator.isCanceled) return@iterateContent false
                            scannedFiles++

                            // 每100个文件更新一次状态（避免过于频繁）
                            if (scannedFiles % 100 == 0) {
                                indicator.text2 = "已扫描 $scannedFiles 个文件..."
                            }

                            val ext = file.extension?.lowercase()
                            if ((ext == "java" || ext == "kt") && fileIndex.isInSourceContent(file)) {
                                files.add(file)
                            }
                            true
                        }
                        files
                    }

                    if (indicator.isCanceled) {
                        SwingUtilities.invokeLater {
                            progressBar.isVisible = false
                            progressLabel.isVisible = false
                            statusLabel.text = "扫描已取消"
                        }
                        return
                    }

                    // 第二阶段：解析类
                    val total = sourceFiles.size
                    indicator.isIndeterminate = false
                    indicator.text = "正在解析类... (共 $total 个文件)"
                    indicator.fraction = 0.0

                    SwingUtilities.invokeLater {
                        progressBar.isIndeterminate = false
                        progressBar.minimum = 0
                        progressBar.maximum = total
                        progressBar.value = 0
                        progressLabel.text = "0 / $total"
                        statusLabel.text = "正在解析 $total 个源文件..."
                    }

                    val result = mutableListOf<LsiClass>()
                    var pojoCount = 0

                    sourceFiles.forEachIndexed { index, file ->
                        if (indicator.isCanceled) {
                            SwingUtilities.invokeLater {
                                progressBar.isVisible = false
                                progressLabel.isVisible = false
                                statusLabel.text = "扫描已取消"
                            }
                            return
                        }

                        val currentIndex = index + 1
                        val progress = currentIndex.toDouble() / total
                        indicator.fraction = progress
                        indicator.text = "正在解析类... ($currentIndex/$total)"
                        indicator.text2 = file.name

                        ReadAction.run<Throwable> {
                            try {
                                val classes = file.toAllLsiClassesUnified()
                                val pojos = classes.filter { it.isPojo }
                                if (pojos.isNotEmpty()) {
                                    result.addAll(pojos)
                                    pojoCount += pojos.size
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // 忽略单个文件的解析错误
                            }
                        }

                        // 定期更新进度条（每10个文件或最后一个）
                        if (currentIndex % 10 == 0 || currentIndex == total) {
                            val currentProgress = (currentIndex * 100 / total)
                            val capturedPojoCount = pojoCount
                            SwingUtilities.invokeLater {
                                progressBar.value = currentIndex
                                progressBar.string = "$currentProgress%"
                                progressLabel.text = "$currentIndex / $total"
                                statusLabel.text = "已找到 $capturedPojoCount 个POJO"
                            }
                        }
                    }

                    val entities = result.distinctBy { it.qualifiedName }

                    // ReadAction 内部处理，避免 EDT 线程违规
                    ReadAction.run<Throwable> {
                        currentPojoList = entities
                        updateTable(entities)
                    }

                    SwingUtilities.invokeLater {
                        progressBar.isVisible = false
                        progressLabel.isVisible = false
                        statusLabel.text = "✓ 扫描完成，共找到 ${entities.size} 个 POJO (来自 $total 个文件)"
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        progressBar.isVisible = false
                        progressLabel.isVisible = false
                        statusLabel.text = "✗ 扫描失败: ${e.message}"
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    private fun updateTable(pojos: List<LsiClass>) {
        pojoTableModel.rowCount = 0
        pojos.forEach { pojo ->
            val packageName = pojo.qualifiedName?.substringBeforeLast('.', "") ?: "-"
            pojoTableModel.addRow(arrayOf(
                pojo.name ?: "-",
                packageName,
                pojo.fields.size,
                pojo.guessTableName,
                pojo.comment ?: "-"
            ))
        }
    }

    private fun showFieldsForSelectedPojo() {
        val row = pojoTable.selectedRow
        if (row < 0 || row >= currentPojoList.size) return

        val pojo = currentPojoList[row]
        fieldTableModel.rowCount = 0
        pojo.fields.forEach { field ->
            fieldTableModel.addRow(arrayOf(
                field.name ?: "-",
                field.typeName ?: "-",
                field.columnName ?: "-",
                field.comment ?: "-",
                if (field.isPrimaryKey) "✓" else "",
                if (field.isNullable) "✓" else ""
            ))
        }
    }

    private fun navigateToSelectedPojo() {
        if (DumbService.getInstance(project).isDumb) {
            Messages.showInfoMessage("索引构建中，请稍后再试", "暂不可用")
            return
        }
        val row = pojoTable.selectedRow
        if (row < 0 || row >= currentPojoList.size) {
            Messages.showWarningDialog("请先选择一个POJO", "提示")
            return
        }

        val pojo = currentPojoList[row]
        val qualifiedName = pojo.qualifiedName ?: return
        val scope = GlobalSearchScope.allScope(project)

        // 尝试 Java 类
        val javaPsi = JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope)
        if (navigatePsiElement(javaPsi)) {
            statusLabel.text = "已打开 ${pojo.name}"
            return
        }

        // 尝试 Kotlin 类
        val ktElements = KotlinFullClassNameIndex.get(qualifiedName, project, scope)
        if (navigatePsiElement(ktElements.firstOrNull())) {
            statusLabel.text = "已打开 ${pojo.name}"
            return
        }

        Messages.showWarningDialog("未能在项目中找到类: $qualifiedName", "跳转失败")
    }

    private fun navigatePsiElement(element: PsiElement?): Boolean {
        val navigatable = (element?.navigationElement ?: element) as? Navigatable
        if (navigatable?.canNavigate() == true) {
            navigatable.navigate(true)
            return true
        }
        return false
    }

    private fun generateDdlForSelected() {
        val selectedRows = pojoTable.selectedRows
        val selectedPojos = if (selectedRows.isNotEmpty()) {
            selectedRows.filter { it in currentPojoList.indices }.map { currentPojoList[it] }
        } else {
            currentPojoList // 未选中时生成全部
        }
        
        if (selectedPojos.isEmpty()) {
            Messages.showWarningDialog("请先扫描POJO", "提示")
            return
        }

        try {
            val ddl = ReadAction.compute<String, Throwable> {
                selectedPojos.joinToString("\n\n") { pojo ->
                    pojo.toCreateTableDDL(selectedDialect)
                }
            }
            ddlOutputArea.text = ddl
            statusLabel.text = "已生成 ${selectedPojos.size} 个表的 ${selectedDialect.name} DDL"
        } catch (e: Exception) {
            e.printStackTrace()
            Messages.showErrorDialog("DDL生成失败: ${e.message}", "错误")
        }
    }

    private fun generateDdlForAll() {
        if (currentPojoList.isEmpty()) {
            Messages.showWarningDialog("请先扫描POJO", "提示")
            return
        }

        try {
            val ddl = ReadAction.compute<String, Throwable> {
                currentPojoList.toCompleteSchemaDDL(
                    dialect = selectedDialect,
                    includeIndexes = true,
                    includeManyToManyTables = true,
                    includeForeignKeys = true
                )
            }
            ddlOutputArea.text = ddl
            statusLabel.text = "已生成 ${currentPojoList.size} 个表的 ${selectedDialect.name} DDL"
        } catch (e: Exception) {
            e.printStackTrace()
            Messages.showErrorDialog("DDL生成失败: ${e.message}", "错误")
        }
    }
}
