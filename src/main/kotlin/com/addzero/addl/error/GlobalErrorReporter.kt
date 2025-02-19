//package com.addzero.addl.error
//
//import com.addzero.addl.util.DialogUtil
//import com.intellij.diagnostic.AbstractMessage
//import com.intellij.ide.DataManager
//import com.intellij.openapi.actionSystem.CommonDataKeys
//import com.intellij.openapi.diagnostic.ErrorReportSubmitter
//import com.intellij.openapi.diagnostic.IdeaLoggingEvent
//import com.intellij.openapi.diagnostic.SubmittedReportInfo
//import com.intellij.openapi.progress.ProgressIndicator
//import com.intellij.openapi.progress.Task
//import com.intellij.util.Consumer
//import java.awt.Component
//
//class GlobalErrorReporter : ErrorReportSubmitter() {
//    override fun getReportActionText(): String = "Report Error to Plugin Developer"
//
//    override fun submit(
//        events: Array<out IdeaLoggingEvent>,
//        additionalInfo: String?,
//        parentComponent: Component,
//        consumer: Consumer<in SubmittedReportInfo>
//    ): Boolean {
//        val project = DataManager.getInstance().getDataContext(parentComponent)
//            .getData(CommonDataKeys.PROJECT)
//
//        events.forEach { event ->
//            val throwable = (event.data as? AbstractMessage)?.throwable
//            // 处理异常
//            throwable?.let { ex ->
//                // 记录日志
//                val message = ex.cause?.message
//                message?.let { DialogUtil.showErrorMsg(it) }
////                logger.error(ex)
//
//                // 显示错误消息
//                project?.let { p ->
//                    object : Task.Backgroundable(p, "Reporting Error") {
//                        override fun run(indicator: ProgressIndicator) {
//                            // 这里可以添加自定义的错误处理逻辑
//                            // 比如发送到错误报告服务器等
//                        }
//                    }.queue()
//                }
//            }
//        }
//
//        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
//        return true
//    }
//}
