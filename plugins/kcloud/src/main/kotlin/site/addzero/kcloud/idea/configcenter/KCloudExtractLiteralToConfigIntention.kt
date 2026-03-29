package site.addzero.kcloud.idea.configcenter

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import java.awt.BorderLayout
import javax.swing.JComponent

class KCloudExtractLiteralToConfigIntention : PsiElementBaseIntentionAction(), IntentionAction {
    override fun getFamilyName(): String {
        return "kcloud"
    }

    override fun getText(): String {
        return "提取到 KCloud 配置中心"
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return findCandidate(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val candidate = findCandidate(element) ?: return
        val settings = KCloudConfigCenterProjectSettings.getInstance(project)
        val dialog = KCloudExtractLiteralDialog(
            project = project,
            candidate = candidate,
            defaultNamespace = settings.resolvedNamespace(),
            defaultProfile = settings.resolvedProfile(),
        )
        if (!dialog.showAndGet()) {
            return
        }
        val request = dialog.buildRequest() ?: return
        runCatching {
            KCloudConfigCenterService.getInstance().saveEntry(project, request)
        }.onSuccess {
            val replacement = candidate.renderReplacement(request.key)
            WriteCommandAction.runWriteCommandAction(project) {
                val replacementExpression = KtPsiFactory(project).createExpression(replacement)
                candidate.targetExpression.replace(replacementExpression)
            }
            KCloudConfigCenterNotification.info(project, "已提取 ${request.key} 到 KCloud 配置中心")
        }.onFailure { error ->
            KCloudConfigCenterNotification.error(project, error.message ?: "提取失败")
        }
    }

    private fun findCandidate(element: PsiElement): KCloudLiteralCandidate? {
        val property = element.getStrictParentOfType<KtProperty>() ?: return null
        if (property.hasModifier(KtTokens.CONST_KEYWORD)) {
            return null
        }
        val initializer = property.initializer ?: return null
        if (!initializer.textRange.contains(element.textRange)) {
            return null
        }
        if (!property.containingFile.isJvmKotlinFile()) {
            return null
        }

        val stringExpression = element.getStrictParentOfType<KtStringTemplateExpression>()
        if (stringExpression != null && stringExpression == initializer) {
            return buildStringCandidate(property, stringExpression)
        }

        val prefixExpression = element.getStrictParentOfType<KtPrefixExpression>()
        if (prefixExpression != null && prefixExpression == initializer) {
            val baseConstant = prefixExpression.baseExpression as? KtConstantExpression ?: return null
            return buildNumericOrBooleanCandidate(property, prefixExpression, baseConstant.text)
        }

        val constantExpression = element.getStrictParentOfType<KtConstantExpression>()
        if (constantExpression != null && constantExpression == initializer) {
            return buildNumericOrBooleanCandidate(property, constantExpression, constantExpression.text)
        }

        return null
    }

    private fun buildStringCandidate(
        property: KtProperty,
        expression: KtStringTemplateExpression,
    ): KCloudLiteralCandidate? {
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) {
            return null
        }
        val value = expression.text.toLiteralStringValue() ?: return null
        val valueType = if (value.contains('\n')) {
            KCloudConfigValueType.TEXT
        } else {
            KCloudConfigValueType.STRING
        }
        return KCloudLiteralCandidate(
            property = property,
            targetExpression = expression,
            rawValue = value,
            valueType = valueType,
            accessorKind = LiteralAccessorKind.STRING,
        )
    }

    private fun buildNumericOrBooleanCandidate(
        property: KtProperty,
        targetExpression: org.jetbrains.kotlin.psi.KtExpression,
        rawText: String,
    ): KCloudLiteralCandidate? {
        val normalized = rawText.trim()
        if (normalized.equals("true", ignoreCase = true) || normalized.equals("false", ignoreCase = true)) {
            return KCloudLiteralCandidate(
                property = property,
                targetExpression = targetExpression,
                rawValue = normalized.lowercase(),
                valueType = KCloudConfigValueType.BOOLEAN,
                accessorKind = LiteralAccessorKind.BOOLEAN,
            )
        }
        val explicitType = property.typeReference?.text?.trim()
        val accessorKind = inferAccessorKind(normalized, explicitType) ?: return null
        val valueType = when (accessorKind) {
            LiteralAccessorKind.INTEGER,
            LiteralAccessorKind.LONG -> KCloudConfigValueType.INTEGER

            LiteralAccessorKind.BOOLEAN -> KCloudConfigValueType.BOOLEAN

            LiteralAccessorKind.FLOAT,
            LiteralAccessorKind.DOUBLE -> KCloudConfigValueType.NUMBER

            LiteralAccessorKind.STRING -> KCloudConfigValueType.STRING
        }
        val storedValue = normalized.removeSuffix("L").removeSuffix("l").removeSuffix("F").removeSuffix("f")
        return KCloudLiteralCandidate(
            property = property,
            targetExpression = targetExpression,
            rawValue = storedValue,
            valueType = valueType,
            accessorKind = accessorKind,
        )
    }

    private fun inferAccessorKind(
        literalText: String,
        explicitType: String?,
    ): LiteralAccessorKind? {
        val normalizedType = explicitType
            ?.removeSuffix("?")
            ?.substringAfterLast('.')
        return when (normalizedType) {
            "String" -> LiteralAccessorKind.STRING
            "Boolean" -> LiteralAccessorKind.BOOLEAN
            "Long" -> LiteralAccessorKind.LONG
            "Float" -> LiteralAccessorKind.FLOAT
            "Double" -> LiteralAccessorKind.DOUBLE
            "Int" -> LiteralAccessorKind.INTEGER
            else -> when {
                literalText.endsWith("L", ignoreCase = true) -> LiteralAccessorKind.LONG
                literalText.endsWith("F", ignoreCase = true) -> LiteralAccessorKind.FLOAT
                literalText.contains('.') || literalText.contains('e', ignoreCase = true) -> LiteralAccessorKind.DOUBLE
                literalText.matches(Regex("-?[0-9][0-9_]*")) -> LiteralAccessorKind.INTEGER
                else -> null
            }
        }
    }
}

private enum class LiteralAccessorKind {
    STRING,
    BOOLEAN,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
}

private data class KCloudLiteralCandidate(
    val property: KtProperty,
    val targetExpression: org.jetbrains.kotlin.psi.KtExpression,
    val rawValue: String,
    val valueType: KCloudConfigValueType,
    val accessorKind: LiteralAccessorKind,
) {
    val suggestedKey: String
        get() = buildList {
            generateSequence(property.parent) { current -> current.parent }
                .filterIsInstance<KtClassOrObject>()
                .mapNotNull { declaration ->
                    declaration.name?.takeIf { it.isNotBlank() }
                }
                .toList()
                .asReversed()
                .forEach { add(it.toKCloudKeySegment()) }
            property.name?.takeIf { it.isNotBlank() }?.let { add(it.toKCloudKeySegment()) }
        }.filter { it.isNotBlank() }
            .distinct()
            .joinToString(".")
            .ifBlank { "config.value" }

    val sourceHint: String
        get() = property.containingFile.virtualFile?.path.orEmpty()

    fun renderReplacement(key: String): String {
        val literalText = targetExpression.text
        val body = when (accessorKind) {
            LiteralAccessorKind.STRING -> """System.getProperty("$key") ?: $literalText"""
            LiteralAccessorKind.BOOLEAN -> """System.getProperty("$key")?.toBooleanStrictOrNull() ?: $literalText"""
            LiteralAccessorKind.INTEGER -> """System.getProperty("$key")?.toIntOrNull() ?: $literalText"""
            LiteralAccessorKind.LONG -> """System.getProperty("$key")?.toLongOrNull() ?: $literalText"""
            LiteralAccessorKind.FLOAT -> """System.getProperty("$key")?.toFloatOrNull() ?: $literalText"""
            LiteralAccessorKind.DOUBLE -> """System.getProperty("$key")?.toDoubleOrNull() ?: $literalText"""
        }
        return "($body)"
    }
}

private class KCloudExtractLiteralDialog(
    project: Project,
    private val candidate: KCloudLiteralCandidate,
    defaultNamespace: String,
    defaultProfile: String,
) : DialogWrapper(project) {
    private val keyField = JBTextField(candidate.suggestedKey)
    private val namespaceField = JBTextField(defaultNamespace)
    private val profileField = JBTextField(defaultProfile)
    private val descriptionArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        text = candidate.sourceHint
    }

    init {
        title = "提取到 KCloud 配置中心"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Key") {
                cell(keyField).align(AlignX.FILL)
            }
            row("项目/命名空间") {
                cell(namespaceField).align(AlignX.FILL)
            }
            row("Profile") {
                cell(profileField).align(AlignX.FILL)
            }
            row("值类型") {
                text(candidate.valueType.name)
            }
            row("当前值") {
                text(candidate.rawValue.take(120))
            }
            row("描述") {
                scrollCell(descriptionArea).align(AlignX.FILL)
            }
        }.also {
            val wrapper = javax.swing.JPanel(BorderLayout())
            wrapper.add(it, BorderLayout.CENTER)
            return wrapper
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (keyField.text.trim().isBlank()) {
            return ValidationInfo("Key 不能为空", keyField)
        }
        if (namespaceField.text.trim().isBlank()) {
            return ValidationInfo("项目/命名空间不能为空", namespaceField)
        }
        return null
    }

    fun buildRequest(): KCloudConfigMutation? {
        if (keyField.text.trim().isBlank() || namespaceField.text.trim().isBlank()) {
            return null
        }
        return KCloudConfigMutation(
            key = keyField.text.trim(),
            namespace = namespaceField.text.trim(),
            profile = profileField.text.trim().ifBlank { "default" },
            domain = KCloudConfigDomain.SYSTEM,
            valueType = candidate.valueType,
            storageMode = KCloudConfigStorageMode.REPO_PLAIN,
            value = candidate.rawValue,
            description = descriptionArea.text.trim().ifBlank { null },
            enabled = true,
        )
    }
}

private fun String.toLiteralStringValue(): String? {
    return when {
        startsWith("\"\"\"") && endsWith("\"\"\"") && length >= 6 -> removePrefix("\"\"\"").removeSuffix("\"\"\"")
        startsWith("\"") && endsWith("\"") && length >= 2 -> StringUtil.unescapeStringCharacters(substring(1, length - 1))
        else -> null
    }
}

private fun PsiFile.isJvmKotlinFile(): Boolean {
    val virtualPath = virtualFile?.path.orEmpty()
    if (!name.endsWith(".kt")) {
        return false
    }
    val blockedMarkers = listOf(
        "/src/commonMain/",
        "/src/commonTest/",
        "/src/js",
        "/src/ios",
        "/src/wasm",
        "/src/linux",
        "/src/macos",
        "/src/mingw",
        "/src/native",
    )
    return blockedMarkers.none { marker -> virtualPath.contains(marker) }
}
