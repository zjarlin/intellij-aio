package site.addzero.gradle.buddy.i18n

import com.intellij.openapi.actionSystem.ActionManager

/**
 * 将 plugin.xml 中注册的 action/group 文案同步为当前插件语言。
 *
 * 不能只依赖 plugin.xml 静态文本，否则自定义语言设置切换后，
 * ActionManager 里缓存的模板文案仍可能保留英文默认值。
 */
object GradleBuddyRegisteredActionI18n {

    private data class ActionTextSpec(
        val actionId: String,
        val textKey: String,
        val descriptionKey: String? = null
    )

    private val specs = listOf(
        ActionTextSpec(
            actionId = "site.addzero.idfixer.FixAllPluginIdsAction",
            textKey = "action.fix.all.plugin.ids.title",
            descriptionKey = "action.fix.all.plugin.ids.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.VersionCatalogFloatingToolbarGroup",
            textKey = "group.version.catalog.title"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.OrganizeVersionCatalog",
            textKey = "action.organize.version.catalog.title",
            descriptionKey = "action.organize.version.catalog.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.MergeOtherToml",
            textKey = "action.merge.other.toml.title",
            descriptionKey = "action.merge.other.toml.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.NormalizeVersionCatalog",
            textKey = "action.normalize.version.catalog.title",
            descriptionKey = "action.normalize.version.catalog.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.FixBrokenCatalogReferences",
            textKey = "action.fix.broken.catalog.references.menu.title",
            descriptionKey = "action.fix.broken.catalog.references.menu.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.FixBrokenProjectReferences",
            textKey = "action.fix.broken.project.references.menu.title",
            descriptionKey = "action.fix.broken.project.references.menu.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.DismissVersionCatalogToolbar",
            textKey = "action.dismiss.version.catalog.toolbar.title",
            descriptionKey = "action.dismiss.version.catalog.toolbar.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.CopyModuleDependency",
            textKey = "action.copy.module.dependency.title",
            descriptionKey = "action.copy.module.dependency.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.MigrateProjectDependencies",
            textKey = "action.migrate.project.dependencies.title",
            descriptionKey = "action.migrate.project.dependencies.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.MigrateToVersionCatalog",
            textKey = "action.migrate.version.catalog.title",
            descriptionKey = "action.migrate.version.catalog.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.ResolveAllPluginArtifacts",
            textKey = "action.resolve.plugin.artifacts.title",
            descriptionKey = "action.resolve.plugin.artifacts.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.UpdateGradleWrapper",
            textKey = "action.update.gradle.wrapper.title",
            descriptionKey = "action.update.gradle.wrapper.description"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.FavoriteTasksToolbarGroup",
            textKey = "group.favorite.tasks.title"
        ),
        ActionTextSpec(
            actionId = "GradleBuddy.FixBuildDirGitIgnore",
            textKey = "action.fix.build.dir.gitignore.title",
            descriptionKey = "action.fix.build.dir.gitignore.description"
        )
    )

    @JvmStatic
    fun refreshAll() {
        val actionManager = ActionManager.getInstance()
        specs.forEach { spec ->
            val action = actionManager.getAction(spec.actionId) ?: return@forEach
            GradleBuddyActionI18n.sync(
                action = action,
                presentation = null,
                textKey = spec.textKey,
                descriptionKey = spec.descriptionKey
            )
        }
    }
}
