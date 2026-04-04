package site.addzero.composeblocks.editor

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class ComposeLayoutSketchWorkspace : JPanel(BorderLayout(12, 0)) {

    private val canvasPanel = SketchCanvasPanel()
    private val inspectorPanel = JPanel(BorderLayout())
    private var nextRegionIndex = 1

    init {
        preferredSize = Dimension(980, 620)
        add(
            JPanel(BorderLayout()).apply {
                add(
                    JBLabel("Drag on the canvas to create named slot regions. This sketch only defines layout and slot names.").apply {
                        border = JBUI.Borders.empty(0, 0, 8, 0)
                        foreground = JBColor.GRAY
                    },
                    BorderLayout.NORTH,
                )
                add(JBScrollPane(canvasPanel), BorderLayout.CENTER)
            },
            BorderLayout.CENTER,
        )
        add(
            JPanel(BorderLayout()).apply {
                preferredSize = Dimension(260, 620)
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLineLeft(JBColor.border()),
                    JBUI.Borders.emptyLeft(12),
                )
                add(inspectorPanel, BorderLayout.CENTER)
            },
            BorderLayout.EAST,
        )
        updateInspector()
    }

    fun regions(): List<LayoutSketchRegion> = canvasPanel.regions()

    fun clearAllRegions() {
        canvasPanel.clearRegions()
        nextRegionIndex = 1
        updateInspector()
    }

    private fun updateInspector() {
        inspectorPanel.removeAll()
        val selectedRegion = canvasPanel.selectedRegion()
        if (selectedRegion == null) {
            inspectorPanel.add(
                JBLabel("Select a region to rename it, or drag on the canvas to add a new slot.").apply {
                    foreground = JBColor.GRAY
                },
                BorderLayout.NORTH,
            )
            inspectorPanel.revalidate()
            inspectorPanel.repaint()
            return
        }

        val nameField = JBTextField(selectedRegion.name).apply {
            addActionListener {
                canvasPanel.renameSelectedRegion(text)
                updateInspector()
            }
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(event: FocusEvent) {
                    canvasPanel.renameSelectedRegion(text)
                    updateInspector()
                }
            })
        }

        val actionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            isOpaque = false
            add(
                JButton("Delete").apply {
                    addActionListener {
                        canvasPanel.deleteSelectedRegion()
                        updateInspector()
                    }
                }
            )
            add(
                JButton("Clear All").apply {
                    addActionListener {
                        clearAllRegions()
                    }
                }
            )
        }

        val regionListPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            canvasPanel.regions().forEach { region ->
                add(
                    JButton(region.name).apply {
                        horizontalAlignment = SwingConstants.LEFT
                        alignmentX = Component.LEFT_ALIGNMENT
                        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                        addActionListener {
                            canvasPanel.selectRegion(region.id)
                            updateInspector()
                        }
                    }
                )
                add(javax.swing.Box.createVerticalStrut(6))
            }
        }

        inspectorPanel.add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(JBLabel("Slot Name").apply { foreground = JBColor.GRAY })
                add(javax.swing.Box.createVerticalStrut(4))
                add(nameField)
                add(javax.swing.Box.createVerticalStrut(12))
                add(actionsPanel)
                add(javax.swing.Box.createVerticalStrut(16))
                add(JBLabel("Sketch Regions").apply { foreground = JBColor.GRAY })
                add(javax.swing.Box.createVerticalStrut(8))
                add(regionListPanel)
            },
            BorderLayout.NORTH,
        )
        inspectorPanel.revalidate()
        inspectorPanel.repaint()
    }

    private inner class SketchCanvasPanel : JPanel() {
        private val regions = mutableListOf<CanvasRegion>()
        private var selectedRegionId: String? = null
        private var dragStartPoint: Point? = null
        private var draftRectangle: Rectangle? = null

        init {
            preferredSize = Dimension(720, 520)
            minimumSize = Dimension(720, 520)
            background = JBColor(Color(32, 34, 38), Color(32, 34, 38))
            border = BorderFactory.createLineBorder(JBColor.border())
            cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

            val listener = object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    val region = findRegionAt(event.point)
                    if (region != null) {
                        selectedRegionId = region.id
                        dragStartPoint = null
                        draftRectangle = null
                        updateInspector()
                        repaint()
                        return
                    }
                    selectedRegionId = null
                    dragStartPoint = event.point
                    draftRectangle = Rectangle(event.point)
                    updateInspector()
                    repaint()
                }

                override fun mouseDragged(event: MouseEvent) {
                    val start = dragStartPoint ?: return
                    draftRectangle = normalizeRectangle(start, event.point)
                    repaint()
                }

                override fun mouseReleased(event: MouseEvent) {
                    val rectangle = draftRectangle
                    dragStartPoint = null
                    draftRectangle = null
                    if (rectangle == null || rectangle.width < 28 || rectangle.height < 28) {
                        repaint()
                        return
                    }
                    val region = CanvasRegion(
                        id = "slot-${nextRegionIndex++}",
                        name = "slot${nextRegionIndex - 1}",
                        bounds = normalizeToUnit(rectangle),
                    )
                    regions += region
                    selectedRegionId = region.id
                    updateInspector()
                    repaint()
                }
            }
            addMouseListener(listener)
            addMouseMotionListener(listener)
        }

        fun regions(): List<LayoutSketchRegion> {
            return regions.map { region ->
                LayoutSketchRegion(
                    id = region.id,
                    name = region.name,
                    left = region.bounds.x,
                    top = region.bounds.y,
                    width = region.bounds.width,
                    height = region.bounds.height,
                )
            }
        }

        fun selectedRegion(): LayoutSketchRegion? {
            val region = regions.firstOrNull { it.id == selectedRegionId } ?: return null
            return LayoutSketchRegion(
                id = region.id,
                name = region.name,
                left = region.bounds.x,
                top = region.bounds.y,
                width = region.bounds.width,
                height = region.bounds.height,
            )
        }

        fun renameSelectedRegion(name: String) {
            val regionIndex = regions.indexOfFirst { it.id == selectedRegionId }
            if (regionIndex < 0) {
                return
            }
            regions[regionIndex] = regions[regionIndex].copy(
                name = name.trim().ifBlank { "slot${regionIndex + 1}" },
            )
            repaint()
        }

        fun deleteSelectedRegion() {
            val targetId = selectedRegionId ?: return
            regions.removeAll { region -> region.id == targetId }
            selectedRegionId = regions.lastOrNull()?.id
            repaint()
        }

        fun clearRegions() {
            regions.clear()
            selectedRegionId = null
            repaint()
        }

        fun selectRegion(regionId: String) {
            selectedRegionId = regionId
            repaint()
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val g2 = graphics.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintGrid(g2)
            regions.forEach { region ->
                paintRegion(g2, region)
            }
            draftRectangle?.let { rectangle ->
                g2.color = JBColor(Color(84, 181, 165, 120), Color(84, 181, 165, 120))
                g2.fillRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, 14, 14)
                g2.color = JBColor(Color(116, 235, 216), Color(116, 235, 216))
                g2.stroke = BasicStroke(2f)
                g2.drawRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, 14, 14)
            }
            g2.dispose()
        }

        private fun paintGrid(graphics: Graphics2D) {
            graphics.color = JBColor(Color(54, 57, 64), Color(54, 57, 64))
            val columns = 12
            val rows = 8
            for (column in 1 until columns) {
                val x = width * column / columns
                graphics.drawLine(x, 0, x, height)
            }
            for (row in 1 until rows) {
                val y = height * row / rows
                graphics.drawLine(0, y, width, y)
            }
        }

        private fun paintRegion(
            graphics: Graphics2D,
            region: CanvasRegion,
        ) {
            val bounds = denormalize(region.bounds)
            val selected = region.id == selectedRegionId
            graphics.color = if (selected) {
                JBColor(Color(96, 206, 186, 168), Color(96, 206, 186, 168))
            } else {
                JBColor(Color(70, 148, 210, 120), Color(70, 148, 210, 120))
            }
            graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14)
            graphics.color = if (selected) {
                JBColor(Color(122, 245, 223), Color(122, 245, 223))
            } else {
                JBColor(Color(121, 180, 230), Color(121, 180, 230))
            }
            graphics.stroke = BasicStroke(if (selected) 2.4f else 1.6f)
            graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14)
            graphics.color = JBColor.WHITE
            graphics.font = graphics.font.deriveFont(16f)
            graphics.drawString(region.name, bounds.x + 12, bounds.y + 24)
        }

        private fun findRegionAt(point: Point): CanvasRegion? {
            return regions.lastOrNull { region ->
                denormalize(region.bounds).contains(point)
            }
        }

        private fun normalizeToUnit(rectangle: Rectangle): RectangleFloat {
            val widthValue = width.coerceAtLeast(1)
            val heightValue = height.coerceAtLeast(1)
            return RectangleFloat(
                x = rectangle.x.toFloat() / widthValue,
                y = rectangle.y.toFloat() / heightValue,
                width = rectangle.width.toFloat() / widthValue,
                height = rectangle.height.toFloat() / heightValue,
            )
        }

        private fun denormalize(bounds: RectangleFloat): Rectangle {
            return Rectangle(
                (bounds.x * width).toInt(),
                (bounds.y * height).toInt(),
                (bounds.width * width).toInt(),
                (bounds.height * height).toInt(),
            )
        }

        private fun normalizeRectangle(
            start: Point,
            end: Point,
        ): Rectangle {
            val x = minOf(start.x, end.x)
            val y = minOf(start.y, end.y)
            val width = kotlin.math.abs(start.x - end.x)
            val height = kotlin.math.abs(start.y - end.y)
            return Rectangle(x, y, width, height)
        }
    }

    private data class CanvasRegion(
        val id: String,
        val name: String,
        val bounds: RectangleFloat,
    )

    private data class RectangleFloat(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    )
}
