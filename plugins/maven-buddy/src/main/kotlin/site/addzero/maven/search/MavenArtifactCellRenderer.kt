package site.addzero.maven.search

import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import site.addzero.network.call.maven.util.MavenArtifact
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

/**
 * Maven 工件列表单元格渲染器
 * 
 * 显示格式：
 * [来源标签] groupId:artifactId
 * Version: version | Updated: timestamp
 * 
 * 来源标签：
 * - 📜 Recent: 历史记录（repositoryId = "history"）
 * - 💾 Cached: 缓存结果（repositoryId = "cached"）
 * - 🔍 Search: 实时搜索结果
 */
class MavenArtifactCellRenderer : ListCellRenderer<MavenArtifact> {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    override fun getListCellRendererComponent(
        list: JList<out MavenArtifact>,
        value: MavenArtifact?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val panel = object : JPanel(BorderLayout()) {
            override fun getAccessibleContext() = null
        }
        panel.border = JBUI.Borders.empty(4, 8)

        if (value == null) {
            return panel
        }

        val sourceType = SourceType.from(value.repositoryId)

        // 设置背景颜色（历史记录和缓存用不同背景）
        panel.background = when {
            isSelected -> UIUtil.getListSelectionBackground(cellHasFocus)
            sourceType == SourceType.HISTORY -> JBColor(Color(255, 250, 240), Color(50, 45, 40))
            sourceType == SourceType.CACHED -> JBColor(Color(240, 248, 255), Color(40, 45, 50))
            else -> UIUtil.getListBackground()
        }

        // 主标题：[标签] groupId:artifactId
        val titleComponent = SimpleColoredComponent()
        
        // 添加来源标签
        titleComponent.append(
            sourceType.label,
            SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, sourceType.color)
        )
        titleComponent.append(" ")
        
        titleComponent.append(
            "${value.groupId}:",
            SimpleTextAttributes.REGULAR_ATTRIBUTES
        )
        titleComponent.append(
            value.artifactId,
            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        )

        // 副标题：版本和时间信息
        val subtitleComponent = SimpleColoredComponent()
        subtitleComponent.append(
            "v${value.latestVersion}",
            SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
        )
        
        // 添加更新时间
        if (value.timestamp > 0) {
            val dateStr = dateFormat.format(Date(value.timestamp))
            subtitleComponent.append(
                " | $dateStr",
                SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
            )
        }

        // 添加打包类型
        if (value.packaging != "jar") {
            subtitleComponent.append(
                " | ${value.packaging}",
                SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
            )
        }

        // 组装面板
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.isOpaque = false
        contentPanel.add(titleComponent)
        contentPanel.add(Box.createVerticalStrut(2))
        contentPanel.add(subtitleComponent)

        panel.add(contentPanel, BorderLayout.CENTER)

        return panel
    }

    private enum class SourceType(val label: String, val color: Color) {
        HISTORY("📜", JBColor(Color(255, 140, 0), Color(255, 180, 100))),
        CACHED("💾", JBColor(Color(30, 144, 255), Color(100, 180, 255))),
        SEARCH("🔍", JBColor(Color(60, 179, 113), Color(100, 200, 150)));

        companion object {
            fun from(repositoryId: String): SourceType = when (repositoryId) {
                "history" -> HISTORY
                "cached" -> CACHED
                else -> SEARCH
            }
        }
    }
}