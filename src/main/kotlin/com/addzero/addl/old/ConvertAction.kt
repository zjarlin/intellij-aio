//package com.addzero.addl.util
//
//import com.addzero.util.lsi_impl.impl.intellij.anactionevent.getEditor
//import com.addzero.util.lsi_impl.impl.intellij.anactionevent.getPsiFile
//import com.addzero.util.lsi_impl.impl.psi.clazz.toMap
//import com.addzero.util.lsi_impl.impl.psi.element.convertTo
//import com.addzero.util.psi.PsiUtil.getCurrentPsiElement
//import com.google.gson.Gson
//import com.intellij.notification.NotificationType
//import com.intellij.notification.Notifications
//import com.intellij.openapi.actionSystem.AnAction
//import com.intellij.openapi.actionSystem.AnActionEvent
//import com.intellij.openapi.editor.Editor
//import com.intellij.psi.*
//import java.awt.Toolkit
//import java.awt.datatransfer.StringSelection
//import java.util.*
//
//fun Editor.todjaosidjo(): Unit {
//
//}
//
//
//
//
//
//class ConvertAction : AnAction() {
//
//
//    override fun actionPerformed(e: AnActionEvent) {
//        val editor = e.getEditor()
//        val project = editor!!.project
//        val data = e.getPsiFile()
//        val referenceAt = data.getCurrentPsiElement(editor)
//        val convertTo = referenceAt.convertTo<PsiClass>()
//
//        val hostClass : PsiClass= referenceAt.getHostClass()
//
//        val selectedClass = if (hostClass!!.name == referenceAt!!.text) {
//            hostClass
//        } else {
//            detectCorrectClassByName(referenceAt.text, hostClass, project!!)
//        }
//
//        if (selectedClass == null) {
//            val notification = GROUP_DISPLAY_ID_INFO.createNotification("Selection is not a POJO.", NotificationType.ERROR)
//            Notifications.Bus.notify(notification, project)
//        } else {
//            try {
//                val outputMap = selectedClass.toMap( )
//                val gson = Gson()
//                val jsonString = gson.toJson(outputMap)
//                setClipboard()
//                val selection = StringSelection(jsonString)
//                val defaultToolkit = Toolkit.getDefaultToolkit()
//                val clipboard = defaultToolkit.systemClipboard
//                clipboard.setContents(selection, selection)
//
//
//
//                val message = "Convert " + selectedClass.name + " to JSON success, copied to the clipboard."
//                val notification = GROUP_DISPLAY_ID_INFO.createNotification(message, NotificationType.INFORMATION)
//                Notifications.Bus.notify(notification, project)
//            } catch (var15: Exception) {
//                val notification =
//                    GROUP_DISPLAY_ID_INFO.createNotification("Convert to JSON failed.", NotificationType.ERROR)
//                Notifications.Bus.notify(notification, project)
//            }
//        }
//    }
//
//
//
//
//
//}
//
