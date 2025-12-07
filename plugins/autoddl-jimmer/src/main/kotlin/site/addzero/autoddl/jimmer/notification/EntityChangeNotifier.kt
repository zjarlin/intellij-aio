package site.addzero.autoddl.jimmer.notification

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * 实体变更通知器
 * 在右上角状态栏显示通知图标（类似 Gradle）
 */
@Service(Service.Level.PROJECT)
class EntityChangeNotifier(private val project: Project) {

    @Volatile
    private var hasChanges = false

    @Volatile
    private var changedFileCount = 0

    /**
     * 通知实体已变更
     */
    fun notifyEntityChanged(fileCount: Int) {
        hasChanges = true
        changedFileCount = fileCount
        updateWidget()
    }

    /**
     * 清除变更标记
     */
    fun clearChanges() {
        hasChanges = false
        changedFileCount = 0
        updateWidget()
    }

    /**
     * 是否有变更
     */
    fun hasChanges(): Boolean = hasChanges

    /**
     * 更新状态栏 Widget
     */
    private fun updateWidget() {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        statusBar?.updateWidget(EntityChangeWidget.ID)
    }

    companion object {
        fun getInstance(project: Project): EntityChangeNotifier {
            return project.service<EntityChangeNotifier>()
        }
    }
}

/**
 * 状态栏 Widget
 * 类似 Gradle 的小图标
 */
class EntityChangeWidget(private val project: Project) : StatusBarWidget {

    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return IconPresentation()
    }

    override fun install(statusBar: StatusBar) {
        statusBar.updateWidget(ID)
    }

    override fun dispose() {
    }

    /**
     * 图标展示
     */
    inner class IconPresentation : StatusBarWidget.IconPresentation {

        override fun getIcon(): Icon? {
            val notifier = EntityChangeNotifier.getInstance(project)
            return if (notifier.hasChanges()) {
                AllIcons.Actions.Execute  // 有变更时显示执行图标
            } else {
                null  // 无变更时隐藏
            }
        }

        override fun getTooltipText(): String? {
            val notifier = EntityChangeNotifier.getInstance(project)
            return if (notifier.hasChanges()) {
                "Jimmer 实体已变更，点击重新生成 DDL" // noinspection SpellCheckingInspection
            } else {
                null
            }
        }

        override fun getClickConsumer(): Consumer<MouseEvent> {
            return Consumer { _ ->
                val notifier = EntityChangeNotifier.getInstance(project)
                if (notifier.hasChanges()) {
                    // 触发重新生成 - 简化版，直接调用 action
                    val action = RegenerateDdlAction()
                    // 创建一个简单的 AnActionEvent
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                        val dataContext = com.intellij.openapi.actionSystem.DataContext { dataId ->
                            when (dataId) {
                                com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.name -> project
                                else -> null
                            }
                        }
                        val place = "EntityChangeWidget"
                        val presentation = action.templatePresentation.clone()
                        // 使用新的创建方式，避免已弃用的构造函数
                        val event = AnActionEvent.createFromDataContext(
                            place,
                            presentation,
                            dataContext
                        )
                        action.actionPerformed(event)
                    }
                }
            }
        }
    }

    companion object {
        const val ID = "JimmerDdl.EntityChange" // noinspection SpellCheckingInspection
    }
}

/**
 * Widget Factory
 */
class EntityChangeWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = EntityChangeWidget.ID

    override fun getDisplayName(): String = "Jimmer DDL" // noinspection SpellCheckingInspection

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        return EntityChangeWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

/**
 * 重新生成 DDL Action
 */
class RegenerateDdlAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 清除变更标记
        EntityChangeNotifier.getInstance(project).clearChanges()

        // 触发生成
        val action = site.addzero.autoddl.jimmer.action.GenerateDeltaDdlAction()
        action.actionPerformed(e)
    }
}
