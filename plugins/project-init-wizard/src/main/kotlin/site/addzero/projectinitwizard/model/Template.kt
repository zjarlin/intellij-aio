package site.addzero.projectinitwizard.model

import java.io.File

data class Template(
    val name: String,
    val description: String,
    val rootDir: File,
    val isBuiltIn: Boolean,
    val variables: List<TemplateVariable> = emptyList()
)

data class TemplateVariable(
    val name: String,
    val type: String = "string",
    val defaultValue: String = "",
    val required: Boolean = false,
    val description: String = ""
)
