package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider

class SmartSourceOnlySearchScopeProvider : SearchScopeProvider {
    override fun getDisplayName(): String {
        return "Smart Intentions"
    }

    override fun getGeneralSearchScopes(project: Project, dataContext: DataContext): List<SearchScope> {
        return listOf(SmartSourceOnlyProjectSearchScope(project))
    }
}
