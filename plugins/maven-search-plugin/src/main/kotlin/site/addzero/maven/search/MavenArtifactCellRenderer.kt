package site.addzero.maven.search

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import site.addzero.network.call.maven.util.MavenArtifact
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

/**
 * Maven 工件列表单元格渲染器
 * 
 * 显示格式：
 * groupId:artifactId
 * Version: version | Updated: timestamp
 */
class MavenArtifactCellRenderer : ListCellRenderer<MavenArtifact> {

    override fun getListCellRendererComponent(
        list: JList<out MavenArtifact>,
        value: MavenArtifact?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(4, 8)

        if (value == null) {
            return panel
        }

        // 设置背景颜色
        panel.background = if (isSelected) {
            UIUtil.getListSelectionBackground(cellHasFocus)
        } else {
            UIUtil.getListBackground()
        }

        // 主标题：groupId:artifactId
        val titleComponent = SimpleColoredComponent()
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
            "Version: ${value.latestVersion}",
            SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
        )
        
        // 添加仓库信息
        if (value.repositoryId.isNotBlank() && value.repositoryId != "central") {
            subtitleComponent.append(
                " | Repo: ${value.repositoryId}",
                SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
            )
        }

        // 添加打包类型
        if (value.packaging != "jar") {
            subtitleComponent.append(
                " | Type: ${value.packaging}",
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
}
