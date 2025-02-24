//package com.addzero.addl.intention.lambda
//
//import com.intellij.codeInsight.intention.IntentionAction
//import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
//import com.intellij.openapi.editor.Editor
//import com.intellij.openapi.project.Project
//import com.intellij.psi.util.PsiTreeUtil
//import com.intellij.openapi.ui.Messages
//import com.intellij.psi.*
//
//class LambdaParameterIntentionAction : PsiElementBaseIntentionAction(), IntentionAction {
//
//    // 返回意图操作的家族名称
//    override fun getFamilyName(): String {
//        return "LambdaParameterIntention"
//    }
//
//    // 返回意图操作的显示文本
//    override fun getText(): String {
//        return "Get Lambda Parameter Constructor Info"
//    }
//
//    // 检查当前上下文是否适用该意图操作
//    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
//        if (editor == null) return false
//
//        // 检查当前元素是否在 Lambda 表达式体内
//        val lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression::class.java)
//        return lambdaExpression != null && lambdaExpression.parameterList.parameters.isNotEmpty()
//    }
//
//
//    // 执行意图操作的逻辑
//    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
//        if (editor == null) return
//
//        // 获取 Lambda 表达式
//        val lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression::class.java)
//            ?: return
//
//        // 获取 Lambda 表达式的参数
//        val parameters = lambdaExpression.parameterList.parameters
//        if (parameters.isEmpty()) {
//            Messages.showInfoMessage("Lambda has no parameters", "Error")
//            return
//        }
//
//        // 获取第一个参数的类型
//        val firstParameter = parameters[0]
//        val parameterType = firstParameter.type
//
//        // 检查参数类型是否是 PsiClassType
//        if (parameterType !is PsiClassType) {
//            Messages.showInfoMessage("Parameter type is not a class type: ${parameterType.canonicalText}", "Error")
//            return
//        }
//
//        // 解析 PsiClassType 为 PsiClass
//        val psiClass = parameterType.resolve() ?: run {
//            Messages.showInfoMessage("Failed to resolve type: ${parameterType.canonicalText}", "Error")
//            return
//        }
//
//        // 获取该类型的构造函数参数列表
//        val constructors = psiClass.constructors
//        if (constructors.isEmpty()) {
//            Messages.showInfoMessage("No constructors found for ${parameterType.canonicalText}", "Info")
//            return
//        }
//
//        // 构建构造函数的参数信息
//        val message = buildString {
//            append("Constructors for ${parameterType.canonicalText}:\n")
//            for (constructor in constructors) {
//                val constructorParameters = constructor.parameterList.parameters
//                append("Constructor with ${constructorParameters.size} parameters:\n")
//                for (param in constructorParameters) {
//                    append("  ${param.type.canonicalText} ${param.name}\n")
//                }
//            }
//        }
//
//        // 显示结果
//        Messages.showInfoMessage(message, "Constructor Info")
//    }
//}
