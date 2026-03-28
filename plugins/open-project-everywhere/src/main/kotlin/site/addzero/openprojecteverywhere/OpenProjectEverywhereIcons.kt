package site.addzero.openprojecteverywhere

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import site.addzero.openprojecteverywhere.search.SearchResultKind
import site.addzero.openprojecteverywhere.search.SearchScope
import javax.swing.Icon

/**
 * Open Project Everywhere 图标集合
 */
object OpenProjectEverywhereIcons {

    @JvmField
    val GitLab: Icon = IconLoader.getIcon("/icons/gitlab.svg", OpenProjectEverywhereIcons::class.java)

    @JvmField
    val Gitee: Icon = IconLoader.getIcon("/icons/gitee.svg", OpenProjectEverywhereIcons::class.java)

    fun scopeIcon(scope: SearchScope): Icon {
        return when (scope) {
            SearchScope.OWN -> AllIcons.Nodes.Folder
            SearchScope.GITHUB_PUBLIC -> AllIcons.Vcs.Vendors.Github
            SearchScope.GITLAB_PUBLIC -> GitLab
            SearchScope.GITEE_PUBLIC -> Gitee
            SearchScope.CUSTOM_PUBLIC -> AllIcons.Actions.CheckOut
        }
    }

    fun resultIcon(kind: SearchResultKind): Icon {
        return when (kind) {
            SearchResultKind.LOCAL -> AllIcons.Nodes.Folder
            SearchResultKind.GITHUB -> AllIcons.Vcs.Vendors.Github
            SearchResultKind.GITLAB -> GitLab
            SearchResultKind.GITEE -> Gitee
            SearchResultKind.CUSTOM -> AllIcons.Actions.CheckOut
        }
    }
}
