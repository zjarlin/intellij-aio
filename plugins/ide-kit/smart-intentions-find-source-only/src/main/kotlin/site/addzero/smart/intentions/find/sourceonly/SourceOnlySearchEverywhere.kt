package site.addzero.smart.intentions.find.sourceonly

import com.intellij.ide.actions.searcheverywhere.SearchAdapter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.util.gotoByName.SearchEverywhereConfiguration
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.Processor
import javax.swing.DefaultListCellRenderer
import javax.swing.ListCellRenderer

class SourceOnlySearchEverywhereContributorFactory : SearchEverywhereContributorFactory<Any> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any> {
        return SourceOnlySearchEverywhereContributor(initEvent.project)
    }

    override fun isAvailable(project: Project): Boolean {
        return !project.isDisposed
    }
}

private class SourceOnlySearchEverywhereContributor(
    private val project: Project?,
) : SearchEverywhereContributor<Any> {
    override fun getSearchProviderId(): String {
        return SourceOnlySearchEverywhereFilter.PROVIDER_ID
    }

    override fun getGroupName(): String {
        return SourceOnlySearchEverywhereFilter.GROUP_NAME
    }

    override fun getSortWeight(): Int {
        return 90
    }

    override fun showInFindResults(): Boolean {
        return false
    }

    override fun isShownInSeparateTab(): Boolean {
        return false
    }

    override fun fetchElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in Any>,
    ) {
        project?.service<SourceOnlySearchProjectService>()?.syncFromSearchEverywhereFilter()
    }

    override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean {
        return false
    }

    override fun getElementsRenderer(): ListCellRenderer<in Any> {
        return DefaultListCellRenderer()
    }

    override fun isDumbAware(): Boolean {
        return true
    }
}

class SourceOnlySearchProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.service<SourceOnlySearchProjectService>()
        service.syncSearchEverywhereFilterToProjectState()
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            SearchEverywhereUI.SEARCH_EVENTS,
            object : SearchAdapter() {
                override fun searchStarted(
                    pattern: String,
                    contributors: MutableCollection<out SearchEverywhereContributor<*>>,
                ) {
                    service.syncFromSearchEverywhereFilter()
                }
            },
        )
    }
}

internal object SourceOnlySearchEverywhereFilter {
    const val PROVIDER_ID = "IdeKit.SourceOnlySearch"
    const val GROUP_NAME = "只搜源码"

    fun isEnabled(): Boolean {
        return SearchEverywhereConfiguration.getInstance().isVisible(PROVIDER_ID)
    }

    fun setEnabled(enabled: Boolean) {
        SearchEverywhereConfiguration.getInstance().setVisible(PROVIDER_ID, enabled)
    }
}

class SourceOnlySearchEverywhereResultsEqualityProvider : SEResultsEqualityProvider {
    override fun compareItems(
        newItem: SearchEverywhereFoundElementInfo,
        alreadyFoundItems: List<SearchEverywhereFoundElementInfo>,
    ): SEResultsEqualityProvider.SEEqualElementsActionType {
        val virtualFile = newItem.element.findVirtualFile() ?: return DoNothing
        val project = ProjectLocator.getInstance().guessProjectForFile(virtualFile) ?: return DoNothing
        val service = project.getServiceIfCreated(SourceOnlySearchProjectService::class.java) ?: return DoNothing
        if (!service.isFilterGeneratedCodeEnabled()) {
            return DoNothing
        }
        if (service.isIncludedInSourceOnlySearch(virtualFile)) {
            return DoNothing
        }
        return SEResultsEqualityProvider.SEEqualElementsActionType.Skip
    }

    private fun Any.findVirtualFile(): VirtualFile? {
        if (this is VirtualFile) {
            return this
        }
        if (this is PsiElement) {
            return PsiUtilCore.getVirtualFile(this)
        }
        return null
    }

    private companion object {
        private val DoNothing = SEResultsEqualityProvider.SEEqualElementsActionType.DoNothing
    }
}
