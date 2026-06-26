package site.addzero.smart.intentions.kotlin.inheritbase

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

internal class InheritFromBaseDialog(
    private val project: Project,
) : DialogWrapper(project) {
    private val typeField = TextFieldWithHistory().apply {
        setHistory(InheritFromBaseHistory.load(project))
        isEditable = true
        setHistorySize(20)
        addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                refreshSuggestions()
                validate()
            }
        })
    }

    init {
        title = "继承自基类"
        setOKButtonText("添加继承")
        init()
        refreshSuggestions()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return typeField.getTextEditor()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("接口或抽象类:"), typeField, 1, false)
            .addComponentFillVertically(JBLabel("输入类名后会显示项目类候选；确认后会记入历史。"), 0)
            .panel
    }

    override fun doValidate(): ValidationInfo? {
        val input = selectedTypeText()
        if (input.isBlank()) {
            return ValidationInfo("请输入接口或抽象类名称。", typeField)
        }
        val simpleName = input.substringAfterLast('.').substringBefore('<').trim()
        if (!StringUtil.isJavaIdentifier(simpleName)) {
            return ValidationInfo("类型名称不是合法标识符。", typeField)
        }
        return null
    }

    fun selectedChoice(): BaseTypeChoice {
        val input = selectedTypeText()
        val resolvedChoice = InheritFromBaseClassSearch.findExactChoice(project, input)
        if (resolvedChoice != null) {
            if (input.contains('<')) {
                return resolvedChoice.copy(typeText = input, typeParameterNames = emptyList())
            }
            return resolvedChoice
        }
        return BaseTypeChoice(
            displayName = input.substringAfterLast('.').substringBefore('<').trim(),
            typeText = input,
            qualifiedName = input.takeIf { value -> value.contains('.') }?.substringBefore('<'),
            packageName = input.takeIf { value -> value.contains('.') }?.substringBeforeLast('.', missingDelimiterValue = ""),
            typeParameterNames = parseTypeParameterNames(input),
        )
    }

    private fun selectedTypeText(): String {
        return typeField.text.trim()
    }

    private fun refreshSuggestions() {
        val input = selectedTypeText()
        val suggestions = buildList {
            addAll(InheritFromBaseHistory.load(project))
            addAll(InheritFromBaseClassSearch.findChoices(project, input).map { choice -> choice.typeText })
        }.filter { value -> value.isNotBlank() }
            .distinct()
            .take(30)
        typeField.setHistory(suggestions)
    }

    private fun parseTypeParameterNames(input: String): List<String> {
        val genericText = input.substringAfter('<', missingDelimiterValue = "")
            .substringBeforeLast('>', missingDelimiterValue = "")
        if (genericText.isBlank()) {
            return emptyList()
        }
        return genericText.split(',')
            .mapIndexed { index, value -> value.trim().takeIf { it.isNotBlank() } ?: "T${index + 1}" }
    }
}
