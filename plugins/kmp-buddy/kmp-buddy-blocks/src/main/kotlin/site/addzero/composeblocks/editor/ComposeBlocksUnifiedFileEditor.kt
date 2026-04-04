package site.addzero.composeblocks.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import site.addzero.composeblocks.model.ComposeBlocksMode
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class ComposeBlocksUnifiedFileEditor(
    project: com.intellij.openapi.project.Project,
    file: VirtualFile,
) : ComposeBlocksFileEditorBase(project, file) {

    private val inspectEditor = ComposeBlocksInspectFileEditor(project, file)
    private val builderEditor = ComposeBlocksBuilderFileEditor(project, file)
    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout)
    private val modeHintLabel = JBLabel().apply {
        foreground = JBColor.GRAY
    }
    private val progressiveExpansionCheckBox = JBCheckBox("Progressive Expand", sourceFile.isProgressiveExpansionEnabled()).apply {
        isOpaque = false
        toolTipText = "Fold non-focused Compose blocks in Text and Compose Blocks views."
        addActionListener {
            sourceFile.setProgressiveExpansionEnabled(isSelected)
            applyProgressiveExpansionSetting()
            updateHeader()
        }
    }
    private val viewToolbar: ActionToolbar
    private val headerPanel: JComponent

    private var currentMode = sanitizeMode(sourceFile.selectedComposeBlocksMode(project))

    init {
        inspectEditor.setProgressiveExpansionEnabled(sourceFile.isProgressiveExpansionEnabled())
        registerChildDisposable(inspectEditor)
        registerChildDisposable(builderEditor)
        contentPanel.add(inspectEditor.component, ComposeBlocksMode.INSPECT.name)
        contentPanel.add(builderEditor.component, ComposeBlocksMode.BUILDER.name)

        val actionGroup = DefaultActionGroup().apply {
            add(ViewSwitchAction(ComposeBlocksMode.INSPECT, "Compose Blocks"))
            add(ViewSwitchAction(ComposeBlocksMode.BUILDER, "Builder"))
        }
        viewToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true).apply {
                targetComponent = rootPanel
            }
        headerPanel = buildHeader()

        rootPanel.layout = BorderLayout()
        rootPanel.add(contentPanel, BorderLayout.CENTER)
        rootPanel.add(headerPanel, BorderLayout.SOUTH)

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                updateModeAvailability()
            }
        }, this)
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    if (event.newEditor !== this@ComposeBlocksUnifiedFileEditor) {
                        return
                    }
                    schedulePreferredFocusRestore()
                }
            },
        )

        applyProgressiveExpansionSetting()
        selectMode(currentMode, requestFocus = false, persist = false)
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return when (currentMode) {
            ComposeBlocksMode.INSPECT -> inspectEditor.preferredFocusedComponent
            ComposeBlocksMode.BUILDER -> builderEditor.preferredFocusedComponent
            ComposeBlocksMode.TEXT -> inspectEditor.preferredFocusedComponent
        }
    }

    fun selectMode(
        mode: ComposeBlocksMode,
        requestFocus: Boolean = true,
    ) {
        selectMode(mode, requestFocus, persist = true)
    }

    override fun dispose() {
    }

    private fun buildHeader(): JComponent {
        val rightControls = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0)).apply {
            isOpaque = false
            add(viewToolbar.component)
            add(progressiveExpansionCheckBox)
        }

        return JPanel(BorderLayout(12, 0)).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineTop(JBColor.border()),
                JBUI.Borders.empty(4, 8),
            )
            add(modeHintLabel, BorderLayout.WEST)
            add(rightControls, BorderLayout.EAST)
        }
    }

    private fun updateModeAvailability() {
        if (currentMode == ComposeBlocksMode.BUILDER && !supportsBuilder()) {
            selectMode(ComposeBlocksMode.INSPECT, requestFocus = false)
            return
        }
        refreshViewToolbar()
        updateHeader()
    }

    private fun updateHeader() {
        progressiveExpansionCheckBox.isEnabled = currentMode != ComposeBlocksMode.BUILDER
        modeHintLabel.text = when (currentMode) {
            ComposeBlocksMode.INSPECT -> "Split block browser and live source editor."
            ComposeBlocksMode.BUILDER -> "Palette, canvas, and named-slot layout builder."
            ComposeBlocksMode.TEXT -> "Text editing stays on the native IntelliJ editor."
        }
        refreshViewToolbar()
    }

    private fun applyProgressiveExpansionSetting() {
        val enabled = sourceFile.isProgressiveExpansionEnabled()
        inspectEditor.setProgressiveExpansionEnabled(enabled)
    }

    private fun selectMode(
        mode: ComposeBlocksMode,
        requestFocus: Boolean,
        persist: Boolean,
    ) {
        val nextMode = sanitizeMode(mode)
        currentMode = nextMode
        if (persist) {
            sourceFile.setSelectedComposeBlocksMode(nextMode)
        }
        cardLayout.show(contentPanel, nextMode.name)
        updateHeader()
        if (requestFocus) {
            preferredFocusedComponent.requestFocusInWindow()
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshViewToolbar() {
        viewToolbar.updateActionsImmediately()
    }

    private fun supportsBuilder(): Boolean {
        return sourceFile.supportsComposeBlocksBuilder(project)
    }

    private fun sanitizeMode(mode: ComposeBlocksMode): ComposeBlocksMode {
        return when {
            mode == ComposeBlocksMode.TEXT && supportsBuilder() -> ComposeBlocksMode.BUILDER
            mode == ComposeBlocksMode.TEXT -> ComposeBlocksMode.INSPECT
            mode == ComposeBlocksMode.BUILDER && !supportsBuilder() -> ComposeBlocksMode.INSPECT
            else -> mode
        }
    }

    private fun schedulePreferredFocusRestore() {
        ApplicationManager.getApplication().invokeLater(
            {
                if (project.isDisposed) {
                    return@invokeLater
                }
                val manager = FileEditorManager.getInstance(project)
                if (manager.selectedEditors.none { editor -> editor === this }) {
                    return@invokeLater
                }
                val target = preferredFocusedComponent
                if (!target.isShowing) {
                    return@invokeLater
                }
                IdeFocusManager.getInstance(project).requestFocus(target, true)
            },
            ModalityState.defaultModalityState(),
        )
    }

    private inner class ViewSwitchAction(
        private val mode: ComposeBlocksMode,
        text: String,
    ) : ToggleAction(text, null, modeIcon(mode)) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun isSelected(event: AnActionEvent): Boolean {
            return currentMode == mode
        }

        override fun setSelected(
            event: AnActionEvent,
            state: Boolean,
        ) {
            if (state) {
                selectMode(mode)
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun displayTextInToolbar(): Boolean = true

        override fun update(event: AnActionEvent) {
            super.update(event)
            event.presentation.isEnabledAndVisible = when (mode) {
                ComposeBlocksMode.INSPECT,
                -> true

                ComposeBlocksMode.BUILDER -> supportsBuilder()
                ComposeBlocksMode.TEXT -> false
            }
        }
    }

    private fun modeIcon(mode: ComposeBlocksMode): Icon {
        return when (mode) {
            ComposeBlocksMode.INSPECT -> AllIcons.Actions.PreviewDetails
            ComposeBlocksMode.BUILDER -> AllIcons.Toolwindows.ToolWindowPalette
            ComposeBlocksMode.TEXT -> AllIcons.FileTypes.Text
        }
    }

    private fun registerChildDisposable(child: ComposeBlocksFileEditorBase) {
        com.intellij.openapi.util.Disposer.register(this, child)
    }
}
