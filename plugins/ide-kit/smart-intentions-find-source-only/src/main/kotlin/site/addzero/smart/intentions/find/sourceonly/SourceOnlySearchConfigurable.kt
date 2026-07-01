package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class SourceOnlySearchConfigurable(
    private val project: Project,
) : SearchableConfigurable {
    private val service: SourceOnlySearchProjectService
        get() = project.service()

    private var filterGeneratedCodeCheckBox: JBCheckBox? = null
    private var excludedFileNameSuffixesTextArea: JBTextArea? = null
    private var mainPanel: JPanel? = null

    override fun getId(): String {
        return "site.addzero.smart.intentions.find.sourceonly"
    }

    override fun getDisplayName(): String {
        return "ide-kit 搜索"
    }

    override fun createComponent(): JComponent {
        filterGeneratedCodeCheckBox = JBCheckBox("只搜源码：过滤 build、out、target、generated 等生成目录")
        excludedFileNameSuffixesTextArea = JBTextArea(8, 48).apply {
            lineWrap = false
            toolTipText = "每行一个后缀，也支持逗号或分号分隔"
        }

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(filterGeneratedCodeCheckBox!!)
            .addLabeledComponent("排除的文件名后缀：", JBScrollPane(excludedFileNameSuffixesTextArea!!))
            .addComponent(JBLabel("示例：Draft 会匹配 SmsLogDraft.kt；Draft.kt 会匹配以 Draft.kt 结尾的文件。"))
            .addComponent(JBLabel("该规则应用于 ide-kit 的“只搜源码”和“源码目录”搜索范围。"))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val currentFilterGeneratedCode = filterGeneratedCodeCheckBox?.isSelected ?: true
        val currentSuffixes = currentExcludedFileNameSuffixes()
        return currentFilterGeneratedCode != service.isFilterGeneratedCodeEnabled() ||
            currentSuffixes != service.getExcludedFileNameSuffixes()
    }

    override fun apply() {
        val filterGeneratedCode = filterGeneratedCodeCheckBox?.isSelected ?: true
        val suffixes = currentExcludedFileNameSuffixes()
        service.setFilterGeneratedCode(filterGeneratedCode)
        service.setExcludedFileNameSuffixes(suffixes)
        reset()
    }

    override fun reset() {
        filterGeneratedCodeCheckBox?.isSelected = service.isFilterGeneratedCodeEnabled()
        excludedFileNameSuffixesTextArea?.text = SourceOnlyFileNameSuffixFilter.toText(
            service.getExcludedFileNameSuffixes(),
        )
    }

    override fun disposeUIResources() {
        filterGeneratedCodeCheckBox = null
        excludedFileNameSuffixesTextArea = null
        mainPanel = null
    }

    private fun currentExcludedFileNameSuffixes(): List<String> {
        val text = excludedFileNameSuffixesTextArea?.text.orEmpty()
        return SourceOnlyFileNameSuffixFilter.parseText(text)
    }
}
