package site.addzero.composebuddy.designer.ui

import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import site.addzero.composebuddy.ComposeBuddyBundle
import site.addzero.composebuddy.designer.model.ComposePaletteItem
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.TransferHandler

class ComposeDesignerPanel(
    private val project: Project,
) : JPanel(BorderLayout()), CopyProvider {
    private val previewArea = JBTextArea().apply {
        isEditable = false
        emptyText.text = ComposeBuddyBundle.message("designer.preview.title")
    }

    private val canvas = ComposeDesignerCanvas { nodes ->
        previewArea.text = ComposeDesignerCodeGenerator.generate(nodes)
    }

    init {
        val palette = createPalette()
        val leftPanel = panel {
            row {
                label(ComposeBuddyBundle.message("designer.palette.title"))
                    .bold()
                    .align(AlignX.LEFT)
            }
            row {
                cell(JBScrollPane(palette))
                    .resizableColumn()
            }
            row {
                button(ComposeBuddyBundle.message("designer.preview.clear")) {
                    canvas.clear()
                }
                button(ComposeBuddyBundle.message("designer.preview.copy")) {
                    copyText()
                }
            }
        }.apply {
            border = JBUI.Borders.empty(8)
        }

        val previewPanel = panel {
            row {
                label(ComposeBuddyBundle.message("designer.preview.title")).bold()
            }
            row {
                cell(JBScrollPane(previewArea))
                    .resizableColumn()
            }
        }.apply {
            border = JBUI.Borders.empty(8)
        }

        val right = OnePixelSplitter(true, 0.7f).apply {
            firstComponent = JBScrollPane(canvas)
            secondComponent = previewPanel
        }

        add(OnePixelSplitter(false, 0.18f).apply {
            firstComponent = leftPanel
            secondComponent = right
        }, BorderLayout.CENTER)

        previewArea.text = ComposeDesignerCodeGenerator.generate(emptyList())
    }

    private fun createPalette(): JBList<ComposePaletteItem> {
        return JBList(ComposePaletteItem.values().toList()).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            dragEnabled = true
            transferHandler = object : TransferHandler() {
                override fun getSourceActions(c: JComponent): Int = COPY

                override fun createTransferable(c: JComponent): StringSelection? {
                    val value = selectedValue ?: return null
                    return StringSelection(value.name)
                }
            }
            cellRenderer = DefaultListCellRenderer().also { renderer ->
                renderer.horizontalAlignment = JLabel.LEFT
            }.let { base ->
                object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: javax.swing.JList<*>,
                        value: Any?,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean,
                    ): java.awt.Component {
                        val label = base.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                        label.text = labelFor(value as ComposePaletteItem)
                        label.border = JBUI.Borders.empty(8)
                        return label
                    }
                }
            }
        }
    }

    private fun labelFor(item: ComposePaletteItem): String {
        return when (item) {
            ComposePaletteItem.TEXT -> ComposeBuddyBundle.message("designer.palette.text")
            ComposePaletteItem.BUTTON -> ComposeBuddyBundle.message("designer.palette.button")
            ComposePaletteItem.IMAGE -> ComposeBuddyBundle.message("designer.palette.image")
            ComposePaletteItem.BOX -> ComposeBuddyBundle.message("designer.palette.box")
            ComposePaletteItem.ROW -> ComposeBuddyBundle.message("designer.palette.row")
            ComposePaletteItem.COLUMN -> ComposeBuddyBundle.message("designer.palette.column")
            ComposePaletteItem.SPACER -> ComposeBuddyBundle.message("designer.palette.spacer")
        }
    }

    private fun copyText() {
        CopyPasteManager.getInstance().setContents(StringSelection(previewArea.text))
        Messages.showInfoMessage(
            project,
            ComposeBuddyBundle.message("designer.preview.copied"),
            ComposeBuddyBundle.message("designer.toolwindow.title"),
        )
    }

    override fun performCopy(dataContext: com.intellij.openapi.actionSystem.DataContext) {
        copyText()
    }

    override fun isCopyEnabled(dataContext: com.intellij.openapi.actionSystem.DataContext): Boolean = previewArea.text.isNotBlank()

    override fun isCopyVisible(dataContext: com.intellij.openapi.actionSystem.DataContext): Boolean = true
}
