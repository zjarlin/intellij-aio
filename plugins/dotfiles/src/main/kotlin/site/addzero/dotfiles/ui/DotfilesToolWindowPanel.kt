package site.addzero.dotfiles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import site.addzero.dotfiles.model.DotfilesSpec
import site.addzero.dotfiles.model.TemplateSourceType
import site.addzero.dotfiles.model.TemplateSpec
import site.addzero.dotfiles.repo.DotfilesLayout
import site.addzero.dotfiles.repo.FileDotfilesRepository
import site.addzero.dotfiles.template.KotlinScriptTemplateEngine
import site.addzero.dotfiles.template.TemplateContext
import site.addzero.dotfiles.template.TemplateSourceResolver
import java.awt.BorderLayout
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.JPanel

class DotfilesToolWindowPanel(
    private val project: Project,
) : JPanel(BorderLayout()) {

    private val repository = FileDotfilesRepository()
    private val resolver = TemplateSourceResolver()
    private val engine = KotlinScriptTemplateEngine()

    init {
        val compose = ComposePanel()
        compose.setContent { DotfilesScreen() }
        add(compose, BorderLayout.CENTER)
    }

    @Composable
    private fun DotfilesScreen() {
        var spec by remember { mutableStateOf(repository.load(project)) }
        var selectedId by remember { mutableStateOf(spec.templates.firstOrNull()?.id) }
        var editorText by remember { mutableStateOf("") }
        var previewText by remember { mutableStateOf("") }

        LaunchedEffect(selectedId, spec.templates) {
            val template = spec.templates.firstOrNull { it.id == selectedId }
            editorText = template?.let { readTemplateText(it) } ?: ""
            previewText = ""
        }

        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Toolbar(
                    onNew = {
                        val created = createTemplate(spec)
                        if (created != null) {
                            spec = created.first
                            selectedId = created.second
                        }
                    },
                    onSave = {
                        val selected = spec.templates.firstOrNull { it.id == selectedId } ?: return@Toolbar
                        if (selected.sourceType != TemplateSourceType.LOCAL) {
                            Messages.showErrorDialog(project, "Remote templates are read-only in the editor.", "Dotfiles")
                            return@Toolbar
                        }
                        writeLocalTemplateFile(selected, editorText)
                        repository.save(project, spec)
                    },
                    onPreview = {
                        val selected = spec.templates.firstOrNull { it.id == selectedId } ?: return@Toolbar
                        val context = TemplateContext(
                            env = spec.env,
                            constants = spec.constants,
                            vars = buildTargetVars(spec, selected.id),
                        )
                        previewText = runCatching { engine.render(editorText, context) }
                            .getOrElse { ex -> "Render failed: ${ex.message}" }
                    },
                    onReload = {
                        spec = repository.load(project)
                        selectedId = spec.templates.firstOrNull()?.id
                    }
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    TemplateList(
                        templates = spec.templates,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("Template", fontFamily = FontFamily.Monospace)
                        TextField(
                            value = editorText,
                            onValueChange = { editorText = it },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            textStyle = MaterialTheme.typography.body1.copy(fontFamily = FontFamily.Monospace)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Preview", fontFamily = FontFamily.Monospace)
                        TextField(
                            value = previewText,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            textStyle = MaterialTheme.typography.body1.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Toolbar(
        onNew: () -> Unit,
        onSave: () -> Unit,
        onPreview: () -> Unit,
        onReload: () -> Unit,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNew) { Text("New") }
            Button(onClick = onSave) { Text("Save") }
            Button(onClick = onPreview) { Text("Preview") }
            Button(onClick = onReload) { Text("Reload") }
        }
    }

    @Composable
    private fun TemplateList(
        templates: List<TemplateSpec>,
        selectedId: String?,
        onSelect: (String) -> Unit,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().width(220.dp)
                .background(MaterialTheme.colors.surface)
                .padding(4.dp)
        ) {
            items(templates) { template ->
                val isSelected = template.id == selectedId
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (isSelected) Color(0xFFE8E8E8) else Color.Transparent)
                        .clickable { onSelect(template.id) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(template.id)
                }
            }
        }
    }

    private fun createTemplate(spec: DotfilesSpec): Pair<DotfilesSpec, String>? {
        val id = Messages.showInputDialog(
            project,
            "Template id",
            "New Template",
            null
        )?.trim().orEmpty()
        if (id.isEmpty()) return null
        if (spec.templates.any { it.id == id }) {
            Messages.showErrorDialog(project, "Template id already exists.", "Dotfiles")
            return null
        }
        val file = "$id.kts"
        val template = TemplateSpec(id = id, file = file)
        writeLocalTemplateFile(template, defaultTemplateText(id))
        val next = spec.copy(templates = spec.templates + template)
        repository.save(project, next)
        return next to id
    }

    private fun buildTargetVars(spec: DotfilesSpec, templateId: String): Map<String, Any?> {
        val target = spec.targets.firstOrNull { it.templateId == templateId }
        return if (target == null) emptyMap() else mapOf(
            "targetId" to target.id,
            "language" to target.language,
            "outputPath" to target.outputPath,
            "packageName" to target.packageName,
        )
    }

    private fun readTemplateText(template: TemplateSpec): String {
        return runCatching { resolver.resolveText(project, template) }
            .getOrElse { "" }
    }

    private fun writeLocalTemplateFile(template: TemplateSpec, content: String) {
        val dir = DotfilesLayout.templatesDir(project) ?: return
        Files.createDirectories(dir)
        val path = dir.resolve(template.file)
        Files.write(path, content.toByteArray(StandardCharsets.UTF_8))
    }

    private fun defaultTemplateText(id: String): String = """
        // template: $id
        val pkg = ctx.vars["packageName"] ?: ""
        if (pkg.toString().isNotEmpty()) {
            "package ${'$'}pkg\n\n" +
        } else {
            ""
        } +
        constants.joinToString("\n") { "const val ${'$'}{it.name} = \"${'$'}{it.value}\"" }
    """.trimIndent()
}
