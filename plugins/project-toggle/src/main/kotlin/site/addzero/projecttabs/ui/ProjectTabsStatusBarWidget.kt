package site.addzero.projecttabs.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import site.addzero.projecttabs.settings.ProjectTabsSettings
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.FlowLayout
import java.awt.Color

/**
 * Status Bar Widget Factory for Project Tabs
 */
class ProjectTabsStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "ProjectTabsWidget"
    override fun getDisplayName(): String = "Project Tabs"

    override fun isAvailable(project: Project): Boolean {
        return true // Always available, visibility controlled by settings
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return ProjectTabsStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

/**
 * Custom Status Bar Widget displaying open projects
 */
class ProjectTabsStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private val panel = ProjectTabsStatusBarPanel(project)

    override fun ID(): String = "ProjectTabsWidget"

    override fun getComponent(): JComponent = panel

    override fun install(statusBar: StatusBar) {
        panel.refreshButtons()
    }

    override fun dispose() {
        // Cleanup if needed
    }
}

/**
 * Panel displaying project buttons
 */
class ProjectTabsStatusBarPanel(private val project: Project) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)) {

    private val settings = ProjectTabsSettings.getInstance()

    init {
        isOpaque = false
        refreshButtons()

        // Listen for project changes
        project.messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
            override fun projectOpened(project: Project) = refreshButtons()
            override fun projectClosed(project: Project) = refreshButtons()
        })
    }

    fun refreshButtons() {
        removeAll()

        if (!settings.enabled) {
            revalidate()
            repaint()
            return
        }

        val openProjects = ProjectManager.getInstance().openProjects
        val currentProject = project

        if (openProjects.isEmpty()) {
            revalidate()
            repaint()
            return
        }

        // Add separator
        add(javax.swing.JLabel("|").apply {
            foreground = JBColor.GRAY
        })

        // Add button for each project
        openProjects.forEach { proj ->
            val isActive = proj == currentProject
            val btn = StatusBarProjectButton(proj.name, isActive) {
                if (proj != currentProject) {
                    val window = WindowManager.getInstance().getFrame(proj)
                    window?.toFront()
                    window?.requestFocus()
                }
            }
            add(btn)
        }

        // Add + button
        add(JButton("+").apply {
            preferredSize = Dimension(20, 20)
            toolTipText = "Open project"
            isOpaque = false
            isContentAreaFilled = false
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            addActionListener {
                OpenProjectDialog(project).show()
            }
        })

        revalidate()
        repaint()
    }
}

/**
 * Project button for status bar
 */
private class StatusBarProjectButton(
    private val projectName: String,
    private val isActive: Boolean,
    private val onClick: () -> Unit
) : JButton(projectName) {

    init {
        isOpaque = false
        isContentAreaFilled = false
        cursor = java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)
        toolTipText = "Switch to $projectName"

        preferredSize = Dimension(
            getFontMetrics(font).stringWidth(projectName) + 16,
            20
        )

        border = BorderFactory.createEmptyBorder(2, 6, 2, 6)

        addActionListener { onClick() }

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                if (!isActive) {
                    background = JBColor.namedColor("TabbedPane.hoverBackground",
                        JBColor(Color(0xE5E5E5), Color(0x4E5254)))
                    isOpaque = true
                    repaint()
                }
            }

            override fun mouseExited(e: MouseEvent) {
                isOpaque = false
                repaint()
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Active indicator - underline
        if (isActive) {
            g2.color = JBColor.namedColor("TabbedPane.underlineColor", Color(0x4B6EAF))
            g2.fillRect(4, height - 3, width - 8, 2)
        }

        super.paintComponent(g)
    }
}

/**
 * Dialog for opening a project
 */
class OpenProjectDialog(private val project: Project) {
    fun show() {
        val descriptor = com.intellij.openapi.fileChooser.FileChooserDescriptor(
            false, true, false, false, false, false
        )
        descriptor.title = "Open Project"
        descriptor.description = "Select a project directory to open"

        com.intellij.openapi.fileChooser.FileChooser.chooseFile(
            descriptor,
            project,
            null
        ) { virtualFile ->
            virtualFile?.path?.let { path ->
                com.intellij.ide.impl.ProjectUtil.openOrImport(path, null, true)
            }
        }
    }
}
