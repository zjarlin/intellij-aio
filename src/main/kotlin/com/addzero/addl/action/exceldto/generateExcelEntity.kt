//package com.addzero.addl.action.exceldto
//
//import com.addzero.addl.util.ShowSqlUtil
//import com.intellij.ide.highlighter.JavaFileType
//import com.intellij.openapi.command.WriteCommandAction
//import com.intellij.openapi.project.Project
//import com.intellij.psi.PsiFileFactory
//import com.intellij.psi.PsiManager
//import org.jetbrains.kotlin.psi.KtClass
//
//fun generateExcelEntity(ktClass: KtClass, project: Project): String {
//    // 生成类名
//    val className = ktClass.name
//
//    // 生成字段部分
//    val fields = ktClass.getProperties().joinToString("\n") { property ->
//        val propertyName = property.name
//        val propertyType = property.typeReference?.text
//        val columnAnnotation = property.annotationEntries.find { it.shortName?.asString() == "Column" }
//        val excelProperty =
//            columnAnnotation?.valueArguments?.getOrNull(0)?.getArgumentExpression()?.text ?: propertyName
//
//        // 返回每个字段的模板字符串
//        """
//        @ExcelProperty("$excelProperty")
//        private ${mapToJavaType(propertyType)} $propertyName;
//        """
//    }
//
//    // 使用模板字符串生成整个类
//    return """
//        import com.alibaba.excel.annotation.ExcelProperty;
//        public class ${className}ExcelEntity {
//
//        $fields
//
//        }
//    """.trimIndent()
//}
//
//private fun mapToJavaType(type: String?): String {
//    return when (type) {
//        "String" -> "String"
//        "Int" -> "Integer"
//        "BigDecimal" -> "BigDecimal"
//        "LocalDateTime" -> "LocalDateTime"
//        else -> "String"  // 默认处理成 String 类型，特殊类型可以按需扩展
//    }
//}
//
//
//fun writeExcelEntityToFile(project: Project, className: String, classContent: String) {
//    val psiDirectory = PsiManager.getInstance(project).findDirectory(project.baseDir!!)
//    val psiFileFactory = PsiFileFactory.getInstance(project)
//
//    val fileName = "${className}ExcelDTO.java"
//    val psiFile = psiFileFactory.createFileFromText(fileName, JavaFileType.INSTANCE, classContent)
//
//    WriteCommandAction.runWriteCommandAction(project) {
//        psiDirectory?.add(psiFile)
//    }
//}
//fun doiasdjo(): Unit {
//    ShowSqlUtil.openSqlInEditor()
//
//}