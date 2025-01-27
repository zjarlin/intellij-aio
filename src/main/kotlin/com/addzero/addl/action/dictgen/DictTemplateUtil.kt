package com.addzero.addl.action.dictgen

import cn.hutool.core.util.StrUtil
import com.addzero.addl.settings.SettingContext
import com.addzero.addl.util.DialogUtil
import com.addzero.addl.util.JlStrUtil
import com.addzero.addl.util.PinYin4JUtils
import com.addzero.addl.util.ShowContentUtil
import com.addzero.addl.util.fieldinfo.PsiUtil
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.KotlinFileType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

object DictTemplateUtil {

    private fun createPackageDirectory(project: Project, packagePath: String): PsiDirectory {
        // 尝试获取当前激活的模块
        val activeModule = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.let {
            PsiManager.getInstance(project).findFile(it)
        }?.let {
            ModuleUtil.findModuleForPsiElement(
                it
            )
        }

        // 获取源代码根目录
        val sourceRoot = when {
            // 如果有活动模块，使用模块的源代码根目录
            activeModule != null -> {
                ModuleRootManager.getInstance(activeModule).sourceRoots.firstOrNull { root ->
                    // 优先选择 src/main/kotlin 目录
                    root.path.contains("src/main/kotlin") || root.path.contains("src/main/java")
                }
            }
            // 否则使用项目根目录
            else -> project.guessProjectDir()
        } ?: throw IllegalStateException("Cannot find source root directory")

        val psiManager = PsiManager.getInstance(project)
        var currentDir = psiManager.findDirectory(sourceRoot) ?: throw IllegalStateException("Cannot find source directory")

        // 创建包路径目录
        packagePath.split(".").forEach { name ->
            currentDir = currentDir.findSubdirectory(name) ?: run {
                var newDir: PsiDirectory? = null
                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        newDir = currentDir.createSubdirectory(name)
                    } catch (e: Exception) {
                        throw IllegalStateException("Failed to create directory: $name", e)
                    }
                }
                newDir ?: throw IllegalStateException("Failed to create directory: $name")
            }
        }

        return currentDir
    }


    public fun generateEnumsByMeta(
        project: Project,
        dictData: Map<DictInfo, List<DictItemInfo>>,
        psiEleInfo: PsiUtil.PsiEleInfo,
        isKotlin: Boolean
    ) {
        dictData.forEach { (dictInfo, items) ->
            val enumName = dictInfo.code.toEnumName()
            val fileName = "$enumName${if (isKotlin) ".kt" else ".java"}"

            val enumContent = generateEnumContent(psiEleInfo, enumName, dictInfo.description, items, isKotlin)

            ShowContentUtil.openTextInEditor(
                project,
                enumContent,
                enumName,
                if (isKotlin) ".kt" else ".java",
                filePath = psiEleInfo.directoryPath,
            )
        }
    }

    private fun generateEnums(
        project: Project,
        dictData: Map<DictInfo, List<DictItemInfo>>,
    ) {
        val packagePath = SettingContext.settings.enumPkg
        val directory = createPackageDirectory(project, packagePath)
        var skipCount = 0
        var createCount = 0

        val isKotlin = PsiUtil.isKotlinProject(project)

        dictData.forEach { (dictInfo, items) ->
            val enumName = dictInfo.code.toEnumName()
            val fileName = "$enumName${if (isKotlin) ".kt" else ".java"}"

            // 检查枚举类是否已存在
            if (directory.findFile(fileName) != null) {
                DialogUtil.showWarningMsg("$fileName 已存在,请检查文件或字典表中是否重复定义")
                skipCount++
                return@forEach
            }

            val enumContent = generateEnumContent(null, enumName, dictInfo.description, items, isKotlin)
            val fileType = if (isKotlin) KotlinFileType.INSTANCE else JavaFileType.INSTANCE

            WriteCommandAction.runWriteCommandAction(project) {
                val psiFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, enumContent)
                CodeStyleManager.getInstance(project).reformat(psiFile)
                directory.add(psiFile)
                createCount++
            }
        }

        // 显示处理结果
        if (createCount > 0) {
            DialogUtil.showInfoMsg(
                "成功生成 $createCount 个枚举类" + if (skipCount > 0) "，跳过 $skipCount 个已存在的枚举类" else ""
            )
        } else if (skipCount > 0) {
            DialogUtil.showWarningMsg("所有枚举类($skipCount 个)都已存在，未生成新文件")
        }
    }

    private fun generateEnumContent(
        psiEleInfo: PsiUtil.PsiEleInfo?,
        enumName: String,
        description: String,
        items: List<DictItemInfo>,
        isKotlin: Boolean,
    ): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val packageName = SettingContext.settings.enumPkg

        val pkg = if (psiEleInfo == null) {
            packageName
        } else if (packageName == "./") {
            psiEleInfo.packageName
        } else {
            packageName
        }

        return when (isKotlin) {
            true -> generateKotlinEnum( pkg, enumName, description, items, timestamp)
            false -> generateJavaEnum( pkg, enumName, description, items, timestamp)
        }
    }

    private fun generateKotlinEnum(
        packageName: String,
        enumName: String,
        description: String,
        items: List<DictItemInfo>,
        timestamp: String,
    ): String {




        val enumItems = items.joinToString(",\n") { item ->
            val itemCode = item.itemCode
            val itemDescription = item.itemDescription


            val format = StrUtil.format(SettingContext.settings.enumAnnotation, itemCode)




            """
    /**
     * $itemDescription
     */
     $format
    ${itemDescription.toPinyinUpper()}("$itemCode", "$itemDescription")""".trimIndent()
        }

        return """
            package $packageName

            import com.fasterxml.jackson.annotation.JsonCreator
            import com.fasterxml.jackson.annotation.JsonValue

            /**
             * $description
             *
             * @author AutoDDL
             * @date $timestamp
             */
            enum class $enumName(
                val code: String,
                val desc: String
            ) {
                $enumItems;

                @JsonValue
                fun getValue(): String {
                    return desc
                }

                companion object {
                    @JsonCreator
                    fun fromCode(code: String): $enumName? = values().find { it.code == code }
                }
            }
        """.trimIndent()
    }

    private fun generateJavaEnum(
        packageName: String,
        enumName: String,
        description: String,
        items: List<DictItemInfo>,
        timestamp: String,
    ): String {


        val enumItems = items.joinToString(",\n") { item ->
            val itemCode = item.itemCode
            val itemDescription = item.itemDescription
            val format = StrUtil.format(SettingContext.settings.enumAnnotation, itemCode)
            """
    /**
     * $itemDescription
     */
    $format
    ${itemDescription.toPinyinUpper()}("$itemCode", "$itemDescription")""".trimIndent()
        }

        return """
            package $packageName;

            import com.fasterxml.jackson.annotation.JsonCreator;
            import com.fasterxml.jackson.annotation.JsonValue;
            import java.util.Arrays;

            /**
             * $description
             *
             * @author AutoDDL
             * @date $timestamp
             */
            public enum $enumName {
                $enumItems;

                private final String code;
                private final String desc;

                $enumName(String code, String desc) {
                    this.code = code;
                    this.desc = desc;
                }

                public String getCode() {
                    return code;
                }

                @JsonValue
                public String getDesc() {
                    return desc;
                }

                @JsonCreator
                public static $enumName fromCode(String code) {
                    return Arrays.stream(values())
                        .filter(e -> e.code.equals(code))
                        .findFirst()
                        .orElse(null);
                }
            }
        """.trimIndent()
    }

    private fun String.toEnumName(): String {
        val toCamelCase = StrUtil.toCamelCase(this)
        val upperFirstAndAddPre = StrUtil.upperFirstAndAddPre(toCamelCase, "Enum")
        return upperFirstAndAddPre
    }

    private fun String.toEnumConstant(): String {
        return uppercase()
    }
}

private fun String.toPinyinUpper(): String {
    val stringToPinyin = PinYin4JUtils.hanziToPinyin(this, "_")
    val unerline = StrUtil.toUnderlineCase(stringToPinyin)
    val toUpperCase = unerline.uppercase(Locale.getDefault())
    val toValidVariableName = JlStrUtil.toValidVariableName(toUpperCase, JlStrUtil.VariableType.CONSTANT)

    return toValidVariableName
}