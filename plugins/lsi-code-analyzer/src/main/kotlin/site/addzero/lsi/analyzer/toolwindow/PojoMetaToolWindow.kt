package site.addzero.lsi.analyzer.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
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
import site.addzero.lsi.analyzer.service.DatabaseSchemaService
import site.addzero.lsi.analyzer.service.JdbcConnectionDetectorService
import site.addzero.util.db.DatabaseType
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.guessTableName
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
    private val jdbcDetector = com.intellij.openapi.components.ServiceManager.getService(JdbcConnectionDetectorService::class.java)
    private val databaseSchemaService = DatabaseSchemaService(project)

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)

        val tabbedPane = JBTabbedPane()

        // POJO 元数据页面
        val pojoPanel = JPanel(BorderLayout()).apply {
            val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
                topComponent = JBScrollPane(pojoTable)
                bottomComponent = JBScrollPane(fieldTable)
                dividerLocation = 300
            }
            add(splitPane, BorderLayout.CENTER)
        }
        tabbedPane.addTab("POJO元数据", pojoPanel)

        // DDL 生成页面
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
        }
    }

    private fun createDdlPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            // 顶部控制面板
            val controlPanel = JPanel(BorderLayout())

            // 第一行：方言选择和生成按钮
            val topPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("数据库方言:"))
                val dialectCombo = ComboBox(DatabaseType.entries.map { it.name }.toTypedArray())
                dialectCombo.selectedItem = selectedDialect.name
                dialectCombo.addActionListener {
                    selectedDialect = DatabaseType.entries[dialectCombo.selectedIndex]
                }
                add(dialectCombo)

                add(JButton("全量生成", AllIcons.Actions.Selectall).apply {
                    toolTipText = "生成所有表的完整DDL（包括外键、索引等）"
                    addActionListener { generateFullDdl() }
                })

                add(JButton("差量生成", AllIcons.Actions.Diff).apply {
                    toolTipText = "基于数据库当前状态生成差量DDL"
                    addActionListener { generateDeltaDdl() }
                })
            }
            controlPanel.add(topPanel, BorderLayout.NORTH)

            // 第二行：连接信息显示和操作按钮
            val bottomPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                // 显示当前连接信息
                val connInfo = jdbcDetector.detectConnectionInfo(project)
                val connLabel = JLabel("连接: ${connInfo.url.ifEmpty { "未配置" }}").apply {
                    toolTipText = if (connInfo.url.isNotEmpty()) {
                        "用户名: ${connInfo.username}\n数据库类型: ${connInfo.dialect}"
                    } else {
                        "请在设置中配置数据库连接信息"
                    }
                }
                add(connLabel)

                // 显示推断的数据库方言
                val dialectLabel = if (connInfo.dialect.isNotEmpty()) {
                    JLabel("数据库: ${connInfo.dialect}").apply {
                        foreground = java.awt.Color.BLUE
                    }
                } else {
                    JLabel("")
                }
                add(dialectLabel)

                add(JButton("刷新连接", AllIcons.Actions.Refresh).apply {
                    toolTipText = "重新检测数据库连接信息"
                    addActionListener {
                        val newConnInfo = jdbcDetector.detectConnectionInfo(project)
                        connLabel.text = "连接: ${newConnInfo.url.ifEmpty { "未配置" }}"
                        dialectLabel.text = if (newConnInfo.dialect.isNotEmpty()) {
                            "数据库: ${newConnInfo.dialect}"
                        } else {
                            ""
                        }
                        connLabel.toolTipText = if (newConnInfo.url.isNotEmpty()) {
                            "用户名: ${newConnInfo.username}\n数据库类型: ${newConnInfo.dialect}"
                        } else {
                            "请在设置中配置数据库连接信息"
                        }
                    }
                })

                add(Box.createHorizontalStrut(10))

                add(JButton("复制", AllIcons.Actions.Copy).apply {
                    addActionListener {
                        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(java.awt.datatransfer.StringSelection(ddlOutputArea.text), null)
                        statusLabel.text = "已复制到剪贴板"
                    }
                })

                add(JButton("应用到数据库", AllIcons.Actions.Execute).apply {
                    toolTipText = "将生成的 DDL 应用到数据库"
                    addActionListener { applyDdlToDatabase() }
                })
            }
            controlPanel.add(bottomPanel, BorderLayout.CENTER)

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
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val rows = ReadAction.compute<List<Array<String?>>, Throwable> {
                    pojos.map { pojo ->
                        val packageName = pojo.qualifiedName?.substringBeforeLast('.', "") ?: "-"
                        arrayOf(
                            pojo.name ?: "-",
                            packageName,
                            pojo.fields.size.toString(),
                            pojo.guessTableName,
                            pojo.comment ?: "-"
                        )
                    }
                } ?: emptyList()

                SwingUtilities.invokeLater {
                    pojoTableModel.rowCount = 0
                    rows.forEach { rowArray ->
                        pojoTableModel.addRow(rowArray)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showFieldsForSelectedPojo() {
        val row = pojoTable.selectedRow
        if (row < 0 || row >= currentPojoList.size) return

        val pojo = currentPojoList[row]
        fieldTableModel.rowCount = 0

        // 在 pooled thread 中处理字段访问，避免 EDT 线程违规
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                ReadAction.compute<List<Array<String?>?>, Throwable> {
                    pojo.fields.map { field ->
                        arrayOf(
                            field.name,
                            field.typeName,
                            field.columnName,
                            field.comment,
                            if (field.isPrimaryKey) "✓" else "",
                            if (field.isNullable) "✓" else ""
                        )
                    }
                }?.let { rows ->
                    SwingUtilities.invokeLater {
                        rows.forEach { rowArray ->
                            fieldTableModel.addRow(rowArray)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    private fun generateFullDdl() {
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
            statusLabel.text = "已生成 ${currentPojoList.size} 个表的 ${selectedDialect.name} 全量 DDL"
        } catch (e: Exception) {
            e.printStackTrace()
            Messages.showErrorDialog("DDL生成失败: ${e.message}", "错误")
        }
    }

    private fun generateDeltaDdl() {
        if (currentPojoList.isEmpty()) {
            Messages.showWarningDialog("请先扫描POJO", "提示")
            return
        }

        // 检查数据库连接配置
        val connInfo = jdbcDetector.detectConnectionInfo(project)
        if (connInfo.url.isEmpty() || connInfo.username.isEmpty()) {
            val result = Messages.showYesNoDialog(
                "未检测到数据库连接配置，是否去设置？",
                "配置未找到",
                Messages.getYesButton(),
                Messages.getNoButton(),
                null
            )
            if (result == Messages.YES) {
                // TODO: 打开设置界面
            }
            return
        }

        // 使用后台任务执行差量 DDL 生成
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "生成差量DDL", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "正在连接数据库并获取表结构..."

                try {
                    // 使用新的 delta 生成逻辑
                    SwingUtilities.invokeLater {
                        statusLabel.text = "正在比较数据库表结构..."
                    }

                    val deltaDdl = databaseSchemaService.generateDeltaDdl(currentPojoList, selectedDialect)

                    SwingUtilities.invokeLater {
                        ddlOutputArea.text = deltaDdl
                        statusLabel.text = "✓ 差量 DDL 生成完成"
                    }

                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        val errorMsg = "-- 生成差量 DDL 失败\n-- 错误信息: ${e.message}\n" +
                                "-- 请检查数据库连接配置"
                        ddlOutputArea.text = errorMsg
                        statusLabel.text = "✗ 差量 DDL 生成失败: ${e.message}"
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    private fun applyDdlToDatabase() {
        val ddlText = ddlOutputArea.text
        if (ddlText.isBlank()) {
            Messages.showWarningDialog("请先生成DDL", "提示")
            return
        }

        // 检查数据库连接
        val connInfo = jdbcDetector.detectConnectionInfo(project)
        if (connInfo.url.isEmpty()) {
            Messages.showErrorDialog("未配置数据库连接信息", "错误")
            return
        }

        // 确认对话框
        val result = Messages.showYesNoDialog(
            "确定要将以下 DDL 应用到数据库吗？\n\n" +
                    "数据库: ${connInfo.url}\n\n" +
                    "警告：此操作将修改数据库结构，请确保已备份数据！",
            "确认应用 DDL",
            Messages.getYesButton(),
            Messages.getNoButton(),
            AllIcons.General.Warning
        )

        if (result != Messages.YES) return

        // 执行 DDL
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "应用DDL到数据库", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "正在连接数据库..."

                try {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "正在连接数据库..."
                    }

                    // 分割 SQL 语句
                    val statements = ddlText.split(";")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("--") }

                    indicator.text = "正在执行 DDL 语句..."

                    // 使用 SqlExecutor 执行 DDL
                    val sqlExecutor = databaseSchemaService.getSqlExecutor(connInfo)

                    statements.forEachIndexed { index, sql ->
                        indicator.fraction = (index + 1).toDouble() / statements.size
                        indicator.text2 = "执行语句 ${index + 1}/${statements.size}"

                        if (sql.isNotBlank()) {
                            try {
                                val executeUpdate = sqlExecutor.executeUpdate(sql)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // 对于 DDL 语句，如果 executeUpdate 失败，尝试使用 execute
                                sqlExecutor.execute(sql)
                            }
                        }
                    }

                    SwingUtilities.invokeLater {
                        statusLabel.text = "✓ DDL 已成功应用到数据库"
                        Messages.showInfoMessage(
                            "DDL 已成功应用到数据库！\n执行了 ${statements.size} 个语句",
                            "成功"
                        )
                    }

                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        statusLabel.text = "✗ 应用 DDL 失败: ${e.message}"
                        Messages.showErrorDialog(
                            "应用 DDL 失败：\n${e.message}",
                            "错误"
                        )
                        e.printStackTrace()
                    }
                }
            }
        })
    }
}
