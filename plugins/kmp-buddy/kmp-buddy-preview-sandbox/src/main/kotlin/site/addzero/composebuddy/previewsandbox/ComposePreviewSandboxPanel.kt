package site.addzero.composebuddy.previewsandbox

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import kotlin.io.path.exists

class ComposePreviewSandboxPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable, ComposePreviewSandboxListener {
    private val service = ComposePreviewSandboxService.getInstance(project)
    private val titleLabel = JBLabel("No Compose preview selected").apply {
        font = font.deriveFont(Font.BOLD)
    }
    private val subtitleLabel = JBLabel("Click the KMP Buddy gutter icon on a Compose @Preview function.")
    private val statusLabel = JBLabel()
    private val surface = PreviewSandboxSurface()
    private val tabs = JBTabbedPane()
    private val fileModel = DefaultListModel<PreviewSandboxSourceFile>()
    private val fileList = JBList(fileModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = SourceFileRenderer()
    }
    private val sourceArea = JBTextArea().apply {
        isEditable = false
        lineWrap = false
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12))
    }
    private val openEntryButton = JButton("Open Entry Source").apply {
        addActionListener { openEntrySource() }
    }
    private val renderPreviewButton = JButton("Render In Panel").apply {
        addActionListener { renderEmbeddedPreview() }
    }
    private val runPreviewButton = JButton("Run Window Preview").apply {
        addActionListener {
            runPanelAction("Unable to launch the external Compose preview window.") {
                runGraphicalPreview()
            }
        }
    }
    private val refreshButton = JButton("Refresh").apply {
        addActionListener {
            runPanelAction("Unable to refresh the Compose preview sandbox.") {
                service.refreshCurrentPreview()
            }
        }
    }

    private var currentSession: ComposePreviewSandboxSession? = null
    private var autoRenderedSession: ComposePreviewSandboxSession? = null

    init {
        border = JBUI.Borders.empty(8)
        add(createHeader(), BorderLayout.NORTH)
        add(createBody(), BorderLayout.CENTER)
        service.addListener(this, this)
        fileList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                showSelectedSourceFile()
            }
        }
        renderSession(service.currentSession())
    }

    override fun sessionChanged(session: ComposePreviewSandboxSession?) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                renderSession(session)
            }
        }
    }

    override fun dispose() {
    }

    private fun createHeader(): JComponent {
        val buttonPanel = JPanel().apply {
            isOpaque = false
            add(renderPreviewButton)
            add(runPreviewButton)
            add(refreshButton)
            add(openEntryButton)
        }
        return JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(4))).apply {
            border = JBUI.Borders.emptyBottom(8)
            add(
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(titleLabel, BorderLayout.NORTH)
                    add(subtitleLabel, BorderLayout.SOUTH)
                },
                BorderLayout.CENTER,
            )
            add(buttonPanel, BorderLayout.EAST)
        }
    }

    private fun createBody(): JComponent {
        tabs.addTab("Graphical Preview", surface)
        tabs.addTab("Sandbox Sources", createSourceBrowser())
        return tabs
    }

    private fun createSourceBrowser(): JComponent {
        val leftPanel = JPanel(BorderLayout()).apply {
            minimumSize = Dimension(JBUI.scale(220), 0)
            preferredSize = Dimension(JBUI.scale(260), 0)
            border = JBUI.Borders.emptyRight(8)
            add(statusLabel, BorderLayout.NORTH)
            add(JBScrollPane(fileList), BorderLayout.CENTER)
        }
        val rightPanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(sourceArea), BorderLayout.CENTER)
        }
        return JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
            resizeWeight = 0.28
            dividerSize = JBUI.scale(4)
            border = JBUI.Borders.empty()
        }
    }

    private fun renderSession(session: ComposePreviewSandboxSession?) {
        currentSession = session
        fileModel.removeAllElements()
        val nextSession = session
        if (nextSession == null) {
            titleLabel.text = "No Compose preview selected"
            subtitleLabel.text = "Click the KMP Buddy gutter icon on a Compose @Preview function."
            statusLabel.text = "Sandbox: idle"
            surface.updateSession(null)
            sourceArea.text = ""
            openEntryButton.isEnabled = false
            renderPreviewButton.isEnabled = false
            runPreviewButton.isEnabled = false
            refreshButton.isEnabled = false
            return
        }

        val snapshot = nextSession.snapshot
        titleLabel.text = snapshot.previewDisplayName
        subtitleLabel.text = snapshot.originalPreviewPath
        statusLabel.text = "Sandbox: ${nextSession.written.generatedFileCount} file(s), ${nextSession.written.declarationCount} declaration(s)"
        snapshot.files.forEach(fileModel::addElement)
        fileList.selectedIndex = snapshot.files.indexOfFirst { file -> file.key == snapshot.entryFileKey }
            .takeIf { index -> index >= 0 } ?: 0
        surface.updateSession(nextSession)
        tabs.selectedIndex = 0
        openEntryButton.isEnabled = true
        renderPreviewButton.isEnabled = true
        runPreviewButton.isEnabled = true
        refreshButton.isEnabled = true
        showSelectedSourceFile()
        if (autoRenderedSession !== nextSession) {
            autoRenderedSession = nextSession
            renderEmbeddedPreview()
        }
    }

    private fun showSelectedSourceFile() {
        val session = currentSession ?: return
        val sourceFile = fileList.selectedValue ?: return
        val generatedFile = session.written.generatedFiles
            .firstOrNull { generated -> generated.sourceFileKey == sourceFile.key }
            ?.path
        sourceArea.text = if (generatedFile != null && generatedFile.exists()) {
            String(Files.readAllBytes(generatedFile), Charsets.UTF_8)
        } else {
            sourceFile.declarations.joinToString("\n\n")
        }
        sourceArea.caretPosition = 0
    }

    private fun renderEmbeddedPreview() {
        val session = currentSession ?: return
        tabs.selectedIndex = 0
        surface.markRendering()
        ComposePreviewSandboxEmbeddedRenderer.render(
            project = project,
            session = session,
            onStatus = surface::markStatus,
            onRendered = surface::showPreviewComponent,
            onError = surface::markError,
        )
    }

    private fun runGraphicalPreview() {
        val session = currentSession ?: return
        surface.markRunLaunched()
        ComposePreviewSandboxRunConfigurationSupport.run(
            project = project,
            session = session,
            onStatus = surface::markStatus,
            onError = surface::markRunFailed,
        )
    }

    private fun runPanelAction(
        errorContext: String,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (throwable: Throwable) {
            surface.markError(
                ComposePreviewSandboxErrorFormatter.format(
                    context = errorContext,
                    throwable = throwable,
                ),
            )
        }
    }

    private fun openEntrySource() {
        val session = currentSession ?: return
        val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(session.written.entryFile.toFile()) ?: return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private class SourceFileRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val sourceFile = value as? PreviewSandboxSourceFile
            label.text = sourceFile?.outputFileName ?: value?.toString().orEmpty()
            label.toolTipText = sourceFile?.originalPath
            label.border = JBUI.Borders.empty(6)
            return label
        }
    }
}

private class PreviewSandboxSurface : JPanel(BorderLayout()) {
    private var centerComponent: Component? = null
    private val titleLabel = JBLabel("Graphical Compose Preview").apply {
        font = font.deriveFont(Font.BOLD, JBUI.scale(16).toFloat())
        alignmentX = Component.CENTER_ALIGNMENT
    }
    private val previewLabel = JBLabel("No preview selected").apply {
        alignmentX = Component.CENTER_ALIGNMENT
    }
    private val sandboxLabel = JBLabel("Sandbox idle").apply {
        alignmentX = Component.CENTER_ALIGNMENT
    }
    private val runnerLabel = JBLabel("").apply {
        alignmentX = Component.CENTER_ALIGNMENT
    }
    private val runStatusLabel = JBLabel("Click the gutter icon on a Compose @Preview function.").apply {
        alignmentX = Component.CENTER_ALIGNMENT
    }
    private val errorArea = JBTextArea().apply {
        isEditable = false
        lineWrap = false
        rows = 6
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12))
    }
    private val copyErrorButton = JButton("Copy Error").apply {
        addActionListener {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .setContents(StringSelection(errorArea.text), null)
        }
    }
    private val errorPanel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(4))).apply {
        isVisible = false
        border = JBUI.Borders.emptyTop(8)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(copyErrorButton, BorderLayout.EAST)
            },
            BorderLayout.NORTH,
        )
        add(JBScrollPane(errorArea), BorderLayout.CENTER)
    }
    private val placeholderPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(40, 24)
        isOpaque = false
        add(Box.createVerticalGlue())
        add(titleLabel)
        add(Box.createVerticalStrut(JBUI.scale(16)))
        add(previewLabel)
        add(Box.createVerticalStrut(JBUI.scale(8)))
        add(sandboxLabel)
        add(Box.createVerticalStrut(JBUI.scale(8)))
        add(runnerLabel)
        add(Box.createVerticalGlue())
    }

    init {
        border = JBUI.Borders.empty(24)
        setCenterComponent(placeholderPanel)
        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(8)
                add(runStatusLabel, BorderLayout.CENTER)
                add(errorPanel, BorderLayout.SOUTH)
            },
            BorderLayout.SOUTH,
        )
    }

    fun updateSession(session: ComposePreviewSandboxSession?) {
        setCenterComponent(placeholderPanel)
        if (session == null) {
            previewLabel.text = "No preview selected"
            sandboxLabel.text = "Sandbox idle"
            runnerLabel.text = ""
            runStatusLabel.text = "Click the gutter icon on a Compose @Preview function."
            clearError()
            return
        }

        previewLabel.text = session.snapshot.previewDisplayName
        sandboxLabel.text = "Reachable AST: ${session.written.generatedFileCount} file(s), ${session.written.declarationCount} declaration(s)"
        runnerLabel.text = "Runner: ${session.written.runnerMainClass}"
        runStatusLabel.text = "Ready to render the graphical Compose preview in this panel."
        clearError()
    }

    fun markRendering() {
        setCenterComponent(placeholderPanel)
        runStatusLabel.text = "Building and loading graphical Compose preview..."
        clearError()
    }

    fun markStatus(message: String) {
        runStatusLabel.text = message
    }

    fun showPreviewComponent(component: JComponent) {
        setCenterComponent(component)
        runStatusLabel.text = "Graphical Compose preview rendered."
        clearError()
    }

    fun markError(message: String) {
        runStatusLabel.text = "Graphical Compose preview failed. Error details are copyable below."
        errorArea.text = message
        errorArea.caretPosition = 0
        errorPanel.isVisible = true
        revalidate()
        repaint()
    }

    fun markRunLaunched() {
        runStatusLabel.text = "Gradle :run launched. The Compose preview opens in a desktop window."
    }

    fun markRunFailed(message: String) {
        markError(message)
    }

    private fun setCenterComponent(component: Component) {
        centerComponent?.let(::remove)
        centerComponent = component
        add(component, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun clearError() {
        errorArea.text = ""
        errorPanel.isVisible = false
    }
}
