package site.addzero.aiannotator

import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import site.addzero.aiannotator.intention.custom.AddCustomAnnotationAction
import site.addzero.aiannotator.intention.custom.AddCustomAnnotationJavaAction
import site.addzero.aiannotator.intention.excel.AddExcelAnnotationAction
import site.addzero.aiannotator.intention.excel.AddExcelAnnotationJavaAction
import site.addzero.aiannotator.intention.swagger.AddSwaggerAnnotationAction
import site.addzero.aiannotator.intention.swagger.AddSwaggerAnnotationJavaAction

class AiAnnotatorStartupActivity : ProjectActivity {
    
    override suspend fun execute(project: Project) {
        registerIntentions()
    }

    private fun registerIntentions() {
        val intentionManager = IntentionManager.getInstance()

        // 注册 Java Intention Actions
        intentionManager.addAction(AddSwaggerAnnotationJavaAction())
        intentionManager.addAction(AddExcelAnnotationJavaAction())
        intentionManager.addAction(AddCustomAnnotationJavaAction())

        // 注册 Kotlin Intention Actions
        intentionManager.addAction(AddSwaggerAnnotationAction())
        intentionManager.addAction(AddExcelAnnotationAction())
        intentionManager.addAction(AddCustomAnnotationAction())
    }
}
