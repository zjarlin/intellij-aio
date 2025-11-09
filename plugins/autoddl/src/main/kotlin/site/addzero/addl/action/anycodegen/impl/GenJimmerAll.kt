//package site.addzero.addl.action.anycodegen.impl
//
//import com.intellij.openapi.actionSystem.AnAction
//import com.intellij.openapi.actionSystem.AnActionEvent
//import com.intellij.openapi.project.Project
//
//class GenJimmerAll : AnAction() {
//    override fun update(e: AnActionEvent) {
//        val project = e.project
//        e.presentation.isEnabled = project != null
//    }
//
//    override fun actionPerformed(e: AnActionEvent) {
//        val project = e.project ?: return
//        performAllGenerations(project, e)
//    }
//
//    private fun performAllGenerations(project: Project, e: AnActionEvent) {
//        val excelDTO = GenExcelDTO()
//        val jimmerController = GenJimmerController()
//        val jimmerDTO = GenJimmerDTO()
//
//        excelDTO.performAction(project, e)
//        jimmerController.performAction(project, e)
//        jimmerDTO.performAction(project, e)
//    }
//}
