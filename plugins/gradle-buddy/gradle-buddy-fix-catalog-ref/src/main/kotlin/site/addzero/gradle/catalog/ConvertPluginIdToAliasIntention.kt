package site.addzero.gradle.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.*

/**
 * 修复 plugins {} 块中截断/错误的 id("...") 插件引用。
 *
 * 类似 [SelectCatalogReferenceIntentionGroup] 对 libs.xxx 断裂引用的修复，
 * 但作用于 id("plugin.id") 形式的插件声明。
 *
 * 场景：用户写了 id("koin.compiler")，但 TOML 中实际的 plugin ID 是
 * "io.insert-koin.koin-compiler"。本 intention 通过 plugin ID 匹配找到
 * 正确的 TOML 条目，替换为 alias(libs.plugins.xxx)。
 *
 * 与 GradleKtsPluginToAliasIntention 的区别：
 * - 那个处理有 version 后缀的 id("...") version "..."（需要写入 TOML）
 * - 这个处理无 version 的 id("...")（TOML 中已有声明，修复截断引用）
 */
class ConvertPluginIdToAliasIntention : IntentionAction, PriorityAction {

    private var cachedCandidates: List<CandidateItem>? = null

    override fun getPriority() = PriorityAction.Priority.HIGH
    override fun getFamilyName() = "Gradle Buddy"
    override fun startInWriteAction() = false

    override fun getText(): String {
        val n = cachedCandidates?.size ?: 0
        return if (n > 0) {
            "(Gradle Buddy) Select correct catalog reference ($n candidates)"
        } else {
            "(Gradle Buddy) Select correct catalog reference"
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (file !is KtFile || !file.name.endsWith(".gradle.kts") || editor == null) return false
        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        val callExpr = findIdCallInPluginsBlock(element) ?: return false
        val pluginIdStr = extractPluginIdString(callExpr) ?: return false

        val scanner = CatalogReferenceScanner(project)
        val pluginIdMaps = scanner.scanPluginIdToAlias()
        val candidates = mutableListOf<CandidateItem>()

        for ((catalogName, idToAccessor) in pluginIdMaps) {
            // 精确匹配 plugin ID
            val exact = idToAccessor[pluginIdStr]
            if (exact != null) {
                candidates.add(CandidateItem(catalogName, exact, pluginIdStr, 1.0))
                continue
            }
            // 模糊匹配：截断的 / 格式不同的 plugin ID
            for ((tomlId, accessor) in idToAccessor) {
                val score = pluginIdSimilarity(pluginIdStr, tomlId)
                if (score > 0.0) candidates.add(CandidateItem(catalogName, accessor, tomlId, score))
            }
        }
        if (candidates.isEmpty()) return false
        cachedCandidates = candidates.sortedByDescending { it.score }
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (file !is KtFile || editor == null) return
        val candidates = cachedCandidates ?: return
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val callExpr = findIdCallInPluginsBlock(element) ?: return

        if (candidates.size == 1) {
            val c = candidates[0]
            WriteCommandAction.runWriteCommandAction(project) {
                replaceIdWithAlias(project, callExpr, c.catalogName, c.accessor)
            }
            return
        }

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<CandidateItem>("选择正确的插件引用", candidates) {
                override fun getTextFor(value: CandidateItem): String {
                    val pct = String.format("%.0f", value.score * 100)
                    val tokens = value.tomlPluginId
                    return "${value.catalogName}.${value.accessor} ← $tokens [$pct%]"
                }
                override fun onChosen(selectedValue: CandidateItem?, finalChoice: Boolean): PopupStep<*>? {
                    if (selectedValue != null && finalChoice) {
                        WriteCommandAction.runWriteCommandAction(project) {
                            replaceIdWithAlias(project, callExpr, selectedValue.catalogName, selectedValue.accessor)
                        }
                    }
                    return PopupStep.FINAL_CHOICE
                }
            }
        )
        popup.showInBestPositionFor(editor)
    }

    // ── PSI helpers ─────────────────────────────────────────────────────

    private fun findIdCallInPluginsBlock(element: PsiElement): KtCallExpression? {
        val call = element.parentOfType<KtCallExpression>(true) ?: return null
        if (call.calleeExpression?.text != "id") return null
        val args = call.valueArguments
        if (args.size != 1) return null
        if (args[0].getArgumentExpression() !is KtStringTemplateExpression) return null
        if (!isInsidePluginsBlock(call)) return null
        if (hasVersionSuffix(call)) return null
        return call
    }

    private fun extractPluginIdString(call: KtCallExpression): String? {
        val arg = call.valueArguments.firstOrNull()
            ?.getArgumentExpression() as? KtStringTemplateExpression ?: return null
        val entries = arg.entries
        if (entries.size != 1) return null
        return (entries[0] as? KtLiteralStringTemplateEntry)?.text
    }

    private fun isInsidePluginsBlock(element: PsiElement): Boolean {
        var cur: PsiElement? = element.parent
        while (cur != null) {
            if (cur is KtCallExpression && cur.calleeExpression?.text == "plugins") return true
            if (cur is KtBlockExpression) {
                val p = cur.parent
                if (p is KtLambdaExpression) {
                    val gp = p.parent
                    val callP = when (gp) {
                        is KtValueArgument, is KtLambdaArgument ->
                            gp.parent?.parent as? KtCallExpression ?: gp.parent as? KtCallExpression
                        else -> null
                    }
                    if (callP?.calleeExpression?.text == "plugins") return true
                }
            }
            cur = cur.parent
        }
        return false
    }

    private fun hasVersionSuffix(call: KtCallExpression): Boolean {
        val parent = call.parent ?: return false
        val after = parent.text.substringAfter(call.text, "").trimStart()
        return after.startsWith("version")
    }

    private fun replaceIdWithAlias(
        project: Project, call: KtCallExpression,
        catalogName: String, accessor: String
    ) {
        val factory = KtPsiFactory(project)
        val newExpr = factory.createExpression("alias($catalogName.$accessor)")
        call.replace(newExpr)
    }

    // ── Similarity ──────────────────────────────────────────────────────

    /**
     * 计算用户输入的 plugin ID 与 TOML 中声明的 plugin ID 的相似度。
     *
     * 匹配策略（按优先级）:
     * 1. 精确匹配 → 1.0
     * 2. 标准化后匹配（. 和 - 互换）→ 0.95
     * 3. 后缀匹配（用户写的是 TOML ID 的后缀）→ 0.8~0.85
     * 4. Token 交集匹配 → 基于 Jaccard + coverage
     */
    private fun pluginIdSimilarity(userId: String, tomlId: String): Double {
        if (userId == tomlId) return 1.0
        val nu = userId.replace('-', '.')
        val nt = tomlId.replace('-', '.')
        if (nu == nt) return 0.95
        if (nt.endsWith(".$nu")) return 0.85
        if (nt.endsWith(nu)) return 0.8

        val ut = nu.split('.').map { it.lowercase() }.toSet()
        val tt = nt.split('.').map { it.lowercase() }.toSet()
        val inter = ut.intersect(tt).size
        if (inter == 0) return 0.0
        val union = ut.union(tt).size
        val jaccard = inter.toDouble() / union
        val coverage = inter.toDouble() / ut.size
        return if (coverage >= 1.0) 0.6 + jaccard * 0.2 else jaccard * 0.5
    }

    private data class CandidateItem(
        val catalogName: String,
        val accessor: String,
        val tomlPluginId: String,
        val score: Double
    )
}
