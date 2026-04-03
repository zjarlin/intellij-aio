package site.addzero.composeblocks.editor

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import site.addzero.composeblocks.model.ComposeBlocksMode
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class ComposeBlocksUnifiedFileEditor(
    project: com.intellij.openapi.project.Project,
    file: VirtualFile,
) : ComposeBlocksFileEditorBase(project, file) {

    private val textEditor: EditorEx = (EditorFactory.getInstance().createEditor(
        document,
        project,
        sourceFile,
        false,
    ) as EditorEx).also { editor ->
        editor.component.border = BorderFactory.createEmptyBorder()
    }

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

    private var currentMode = sanitizeMode(sourceFile.selectedComposeBlocksMode(project))

    init {
        project.service<ComposeBlocksTextEditorService>().installIfNeeded(sourceFile, textEditor, this)
        inspectEditor.setProgressiveExpansionEnabled(sourceFile.isProgressiveExpansionEnabled())

        com.intellij.openapi.util.Disposer.register(this, inspectEditor)
        com.intellij.openapi.util.Disposer.register(this, builderEditor)
        com.intellij.openapi.util.Disposer.register(this) {
            EditorFactory.getInstance().releaseEditor(textEditor)
        }

        contentPanel.add(textEditor.component, ComposeBlocksMode.TEXT.name)
        contentPanel.add(inspectEditor.component, ComposeBlocksMode.INSPECT.name)
        contentPanel.add(builderEditor.component, ComposeBlocksMode.BUILDER.name)

        val actionGroup = DefaultActionGroup().apply {
            add(ViewSwitchAction(ComposeBlocksMode.TEXT, "Text"))
            add(ViewSwitchAction(ComposeBlocksMode.INSPECT, "Compose Blocks"))
            add(ViewSwitchAction(ComposeBlocksMode.BUILDER, "Builder"))
        }
        viewToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true).apply {
                targetComponent = rootPanel
            }

        rootPanel.layout = BorderLayout()
        rootPanel.add(buildHeader(), BorderLayout.NORTH)
        rootPanel.add(contentPanel, BorderLayout.CENTER)

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                updateModeAvailability()
            }
        }, this)

        applyProgressiveExpansionSetting()
        selectMode(currentMode, requestFocus = false, persist = false)
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return when (currentMode) {
            ComposeBlocksMode.TEXT -> textEditor.contentComponent
            ComposeBlocksMode.INSPECT -> inspectEditor.preferredFocusedComponent
            ComposeBlocksMode.BUILDER -> builderEditor.preferredFocusedComponent
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
        val rightControls = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(progressiveExpansionCheckBox, BorderLayout.EAST)
        }

        return JPanel(BorderLayout(12, 0)).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.empty(6, 8),
            )
            add(viewToolbar.component, BorderLayout.WEST)
            add(modeHintLabel, BorderLayout.CENTER)
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
            ComposeBlocksMode.TEXT -> "Text view with block highlighting and folding."
            ComposeBlocksMode.INSPECT -> "Split block browser and live source editor."
            ComposeBlocksMode.BUILDER -> "Palette, canvas, and named-slot layout builder."
        }
        refreshViewToolbar()
    }

    private fun applyProgressiveExpansionSetting() {
        val enabled = sourceFile.isProgressiveExpansionEnabled()
        project.service<ComposeBlocksTextEditorService>().updateProgressiveExpansion(textEditor, enabled)
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
            mode == ComposeBlocksMode.BUILDER && !supportsBuilder() -> ComposeBlocksMode.INSPECT
            else -> mode
        }
    }

    private inner class ViewSwitchAction(
        private val mode: ComposeBlocksMode,
        text: String,
    ) : ToggleAction(text) {

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
                ComposeBlocksMode.TEXT,
                ComposeBlocksMode.INSPECT,
                -> true

                ComposeBlocksMode.BUILDER -> supportsBuilder()
            }
        }
    }
}
