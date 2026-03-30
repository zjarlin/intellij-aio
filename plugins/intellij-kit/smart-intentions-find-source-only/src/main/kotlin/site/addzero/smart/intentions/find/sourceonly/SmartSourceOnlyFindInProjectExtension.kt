package site.addzero.smart.intentions.find.sourceonly

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectExtension
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext

class SmartSourceOnlyFindInProjectExtension : FindInProjectExtension {
    override fun initModelFromContext(model: FindModel, dataContext: DataContext): Boolean {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        if (!shouldUseSourceOnlyScope(model)) {
            return false
        }

        model.isProjectScope = false
        model.isCustomScope = true
        model.customScope = SmartSourceOnlyProjectSearchScope(project)
        return true
    }

    private fun shouldUseSourceOnlyScope(model: FindModel): Boolean {
        if (!model.isProjectScope) {
            return false
        }
        if (model.isCustomScope) {
            return false
        }
        if (model.directoryName != null) {
            return false
        }
        if (model.moduleName != null) {
            return false
        }
        return true
    }
}
