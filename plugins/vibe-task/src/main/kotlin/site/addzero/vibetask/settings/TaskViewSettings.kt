package site.addzero.vibetask.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XCollection

@Service(Service.Level.APP)
@State(
    name = "TaskViewSettings",
    storages = [Storage("vibe-task-views.xml")]
)
class TaskViewSettings : PersistentStateComponent<TaskViewSettings.State> {

    data class State(
        @XCollection(style = XCollection.Style.v2, elementName = "rule")
        var rules: MutableList<TaskViewRule> = mutableListOf(),

        @XCollection(style = XCollection.Style.v2, elementName = "customView")
        var customViews: MutableList<CustomView> = mutableListOf(),

        var showGlobalView: Boolean = true,
        var showAllTasksView: Boolean = true,
        var showProjectLevelView: Boolean = true
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    /**
     * 获取所有规则（如果为空返回默认规则）
     */
    fun getRules(): List<TaskViewRule> {
        if (state.rules.isEmpty()) {
            state.rules = TaskViewConfig.createDefault().rules
        }
        return state.rules.filter { it.enabled }
    }

    /**
     * 添加规则
     */
    fun addRule(rule: TaskViewRule) {
        state.rules.add(rule)
    }

    /**
     * 更新规则
     */
    fun updateRule(updatedRule: TaskViewRule) {
        val index = state.rules.indexOfFirst { it.id == updatedRule.id }
        if (index >= 0) {
            state.rules[index] = updatedRule
        }
    }

    /**
     * 删除规则
     */
    fun removeRule(ruleId: String) {
        state.rules.removeIf { it.id == ruleId }
    }

    /**
     * 获取自定义视图
     */
    fun getCustomViews(): List<CustomView> = state.customViews

    /**
     * 添加自定义视图
     */
    fun addCustomView(view: CustomView) {
        state.customViews.add(view)
    }

    /**
     * 更新自定义视图
     */
    fun updateCustomView(updatedView: CustomView) {
        val index = state.customViews.indexOfFirst { it.id == updatedView.id }
        if (index >= 0) {
            state.customViews[index] = updatedView
        }
    }

    /**
     * 删除自定义视图
     */
    fun removeCustomView(viewId: String) {
        state.customViews.removeIf { it.id == viewId }
    }

    /**
     * 根据规则对模块进行分组
     */
    fun groupModulesByRules(modules: List<site.addzero.vibetask.model.ProjectModule>): Map<String, List<site.addzero.vibetask.model.ProjectModule>> {
        val result = mutableMapOf<String, MutableList<site.addzero.vibetask.model.ProjectModule>>()
        val rules = getRules()

        // 为每个规则创建列表
        rules.forEach { rule ->
            result[rule.id] = mutableListOf()
        }

        // 未分类
        result["uncategorized"] = mutableListOf()

        // 分类模块
        modules.forEach { module ->
            var matched = false
            for (rule in rules) {
                if (rule.matches(module.path)) {
                    result[rule.id]?.add(module)
                    matched = true
                    break
                }
            }
            if (!matched) {
                result["uncategorized"]?.add(module)
            }
        }

        return result
    }

    companion object {
        fun getInstance(): TaskViewSettings {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(TaskViewSettings::class.java)
        }
    }
}