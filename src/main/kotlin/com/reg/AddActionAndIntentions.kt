package com.reg

//import com.addzero.addl.intention.RemoveShitCodeIntention
import com.addzero.addl.action.dictgen.intention.GenEnumByFieldCommentIntention
import com.addzero.addl.intention.custom.AddCusTomAnnotationAction
import com.addzero.addl.intention.custom.AddCusTomAnnotationJavaAction
import com.addzero.addl.intention.excel.AddExcelPropertyAnnotationAction
import com.addzero.addl.intention.excel.AddExcelPropertyAnnotationJavaAction
import com.addzero.addl.intention.swagger.AddSwaggerAnnotationAction
import com.addzero.addl.intention.swagger.AddSwaggerAnnotationJavaAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity


class AddActionAndIntentions : ProjectActivity {
//    override fun runActivity(project: Project) {
//    }

    override suspend fun execute(project: Project) {

//        val intentionBaseClass = JavaPsiFacade.getInstance(project)
//            .findClass("com.intellij.codeInsight.intention.IntentionAction", GlobalSearchScope.allScope(project))
//        intentionBaseClass ?: return
//        val toList =
//            ClassInheritorsSearch.search(intentionBaseClass, ProjectScope.getContentScope(project), false, false, false)
//                .toList()
//        println()



        registerIntentions()
//        registerActions()

    }

}

private fun registerIntentions() {
    val intentionManager = IntentionManager.getInstance()

    // 检查并注册Java意图
    val javaIntentions = listOf(
        AddSwaggerAnnotationJavaAction(),
        AddExcelPropertyAnnotationJavaAction(),
        AddCusTomAnnotationJavaAction(),
        GenEnumByFieldCommentIntention()
    )

    // 检查并注册Kotlin意图
    val kotlinIntentions = listOf(
        AddSwaggerAnnotationAction(),
        AddExcelPropertyAnnotationAction(),
        AddCusTomAnnotationAction(),
//        ConvertToVersionCatalogIntention()
    )

    // 注册所有意图，避免重复
    (javaIntentions + kotlinIntentions).forEach { intention ->
        val existingIntentions = intentionManager.availableIntentions
        if (!existingIntentions.any { it.javaClass == intention.javaClass }) {
            intentionManager.addAction(intention)
        }
    }
}


//fun registerActions() {
//    val actionManager = ActionManager.getInstance()
//
//    // 注册主菜单
//    val autoDDLAction = AutoDDL()
//    actionManager.registerAction("AutoDDL", autoDDLAction)
//    actionManager.getAction("ToolsMenu")?.let { toolsMenu ->
//        if (toolsMenu is DefaultActionGroup) {
//            toolsMenu.addAction(autoDDLAction, com.intellij.openapi.actionSystem.Constraints.FIRST)
//        }
//    }
//
//    // 注册生成菜单组
//    val generateGroup = DefaultActionGroup("AutoDDL.GenerateGroup", true)
//
//
//    actionManager.registerAction("AutoDDL.GenerateGroup", generateGroup)
//    actionManager.getAction("GenerateGroup")?.let { genGroup ->
//        if (genGroup is DefaultActionGroup) {
//            genGroup.addAction(generateGroup, com.intellij.openapi.actionSystem.Constraints.FIRST)
//        }
//    }
//
//    // 添加生成菜单组的子Action
//    generateGroup.apply {
//        add(GenDDL())
//        add(GenJimmerDTO())
//        add(GenController())
//        add(GenExcelDTO())
//    }
//}
