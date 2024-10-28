//package com.addzero.addl.util
//
//import com.google.gson.Gson
//import com.intellij.notification.NotificationDisplayType
//import com.intellij.notification.NotificationGroup
//import com.intellij.notification.NotificationType
//import com.intellij.notification.Notifications
//import com.intellij.openapi.actionSystem.AnAction
//import com.intellij.openapi.actionSystem.AnActionEvent
//import com.intellij.openapi.actionSystem.CommonDataKeys
//import com.intellij.openapi.project.Project
//import com.intellij.psi.*
//import com.intellij.psi.search.GlobalSearchScope
//import com.intellij.psi.search.PsiShortNamesCache
//import com.intellij.psi.util.PsiTreeUtil
//import org.jetbrains.kotlin.builtins.StandardNames.FqNames.mutableSet
//import java.awt.Toolkit
//import java.awt.datatransfer.StringSelection
//import java.util.*
//
//class ConvertAction : AnAction() {
//    override fun actionPerformed(e: AnActionEvent) {
//        val editor = e.dataContext.getData(CommonDataKeys.EDITOR)
//        val project = editor!!.project
//        val referenceAt = e.dataContext.getData(CommonDataKeys.PSI_FILE)!!.findElementAt(editor.caretModel.offset)
//        val hostClass = PsiTreeUtil.getContextOfType<PsiElement>(
//            referenceAt, *arrayOf<Class<*>>(
//                PsiClass::class.java
//            )
//        ) as PsiClass?
//        val selectedClass = if (hostClass!!.name == referenceAt!!.text) {
//            hostClass
//        } else {
//            detectCorrectClassByName(referenceAt.text, hostClass, project!!)
//        }
//
//        if (selectedClass == null) {
//            val notification =
//                GROUP_DISPLAY_ID_INFO.createNotification("Selection is not a POJO.", NotificationType.ERROR)
//            Notifications.Bus.notify(notification, project)
//        } else {
//            try {
//                val outputMap = this.generateMap(
//                    selectedClass,
//                    project!!
//                )
//                val gson = Gson()
//                val jsonString = gson.toJson(outputMap)
//                val selection = StringSelection(jsonString)
//                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
//                clipboard.setContents(selection, selection)
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
//    private fun generateMap(psiClass: PsiClass, project: Project): Map<String?, Any?> {
//        val outputMap: MutableMap<String?, Any?> = LinkedHashMap<Any?, Any?>()
//        val psiFields = psiClass.fields
//
//        for (idx in psiFields.indices) {
//            val psiField = psiFields[idx]
//            outputMap[psiField.name] = getObjectForField(psiField, project)
//        }
//
//        return outputMap
//    }
//
//    private fun getObjectForField(psiField: PsiField, project: Project): Any {
//        val type = psiField.type
//        if (type is PsiPrimitiveType) {
//            return if (type == PsiType.INT) {
//                0
//            } else if (type == PsiType.BOOLEAN) {
//                java.lang.Boolean.TRUE
//            } else if (type == PsiType.BYTE) {
//                "1".toByte()
//            } else if (type == PsiType.CHAR) {
//                '-'
//            } else if (type == PsiType.DOUBLE) {
//                0.0
//            } else if (type == PsiType.FLOAT) {
//                0.0f
//            } else if (type == PsiType.LONG) {
//                0L
//            } else {
//                if (type == PsiType.SHORT) "0".toShort() else type.getPresentableText()
//            }
//        } else {
//            val typeName = type.presentableText
//            if (typeName != "Integer" && typeName != "Long") {
//                if (typeName != "Double" && typeName != "Float") {
//                    if (typeName == "Boolean") {
//                        return java.lang.Boolean.TRUE
//                    } else if (typeName == "Byte") {
//                        return "1".toByte()
//                    } else if (typeName == "String") {
//                        return "str"
//                    } else if (typeName == "Date") {
//                        return (Date()).time
//                    } else if (typeName.startsWith("List")) {
//                        return this.handleList(type, project, psiField.containingClass!!)
//                    } else {
//                        val fieldClass = this.detectCorrectClassByName(
//                            typeName,
//                            psiField.containingClass!!, project
//                        )
//                        return if (fieldClass != null) this.generateMap(fieldClass, project) else typeName
//                    }
//                } else {
//                    return 0.0f
//                }
//            } else {
//                return 0
//            }
//        }
//    }
//
//    private fun handleList(psiType: PsiType, project: Project, containingClass: PsiClass): Any {
//        val list: MutableList<Any?> = ArrayList()
//        val classType = psiType as PsiClassType
//        val subTypes = classType.parameters
//        if (subTypes.size > 0) {
//            val subType = subTypes[0]
//            val subTypeName = subType.presentableText
//            if (subTypeName.startsWith("List")) {
//                list.add(this.handleList(subType, project, containingClass))
//            } else {
//                val targetClass = this.detectCorrectClassByName(subTypeName, containingClass, project)
//                if (targetClass != null) {
//                    list.add(this.generateMap(targetClass, project))
//                } else if (subTypeName == "String") {
//                    list.add("str")
//                } else if (subTypeName == "Date") {
//                    list.add((Date()).time)
//                } else {
//                    list.add(subTypeName)
//                }
//            }
//        }
//
//        return list
//    }
//
//    private fun detectCorrectClassByName(className: String, containingClass: PsiClass, project: Project): PsiClass? {
//        val classes =
//            PsiShortNamesCache.getInstance(project).getClassesByName(className, GlobalSearchScope.projectScope(project))
//        if (classes.size == 0) {
//            return null
//        } else if (classes.size == 1) {
//            return classes[0]
//        } else {
//            val javaFile = containingClass.containingFile as PsiJavaFile
//            val importList = javaFile.importList
//            val statements = importList!!.importStatements
//            val importedPackageSet: MutableSet<String?> = emptySet<Any?>()
//            var idx = 0
//            while (idx < statements.size) {
//                importedPackageSet.add(statements[idx].qualifiedName)
//                ++idx
//            }
//
//            idx = 0
//            while (idx < classes.size) {
//                val targetClass = classes[idx]
//                val targetClassContainingFile = targetClass.containingFile as PsiJavaFile
//                val packageName = targetClassContainingFile.packageName
//                if (importedPackageSet.contains(packageName + "." + targetClass.name)) {
//                    return targetClass
//                }
//                ++idx
//            }
//
//            return null
//        }
//    }
//
//    companion object {
//        val GROUP_DISPLAY_ID_INFO: NotificationGroup =
//            NotificationGroup("BMPOJOtoJSON.Group", NotificationDisplayType.STICKY_BALLOON, true)
//    }
//}