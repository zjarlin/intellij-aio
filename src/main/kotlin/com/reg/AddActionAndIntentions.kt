package com.reg

import com.addzero.addl.action.dictgen.intention.GenEnumByFieldCommentIntention
import com.addzero.addl.intention.ConvertToVersionCatalogIntention
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
        registerIntentions()
//        registerActions()

    }

}

private fun registerIntentions() {
    // 在插件启动时注册所有意图动作
    val intentionManager = IntentionManager.getInstance()


    //注册java意图
    intentionManager.addAction(AddSwaggerAnnotationJavaAction())
    intentionManager.addAction(AddExcelPropertyAnnotationJavaAction())
    intentionManager.addAction(AddCusTomAnnotationJavaAction())
    intentionManager.addAction(GenEnumByFieldCommentIntention())


    //注册kt意图
    intentionManager.addAction(AddSwaggerAnnotationAction())
    intentionManager.addAction(AddExcelPropertyAnnotationAction())
    intentionManager.addAction(AddCusTomAnnotationAction())
    intentionManager.addAction(ConvertToVersionCatalogIntention())
//    intentionManager.addAction(GenEnumByFieldCommentIntention())
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
