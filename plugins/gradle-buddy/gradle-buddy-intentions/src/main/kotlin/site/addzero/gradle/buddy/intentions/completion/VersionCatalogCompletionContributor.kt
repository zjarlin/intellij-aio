package site.addzero.gradle.buddy.intentions.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * TOML 版本目录文件补全(不起作用)
 *
 * 支持补全格式：
 * 1. 简写形式：`guava = "com.google.guava:guava:32.1.3-jre"`
 * 2. module 形式：`guava = { module = "com.google.guava:guava", version = "32.1.3-jre" }`
 * 3. 完整形式：`guava = { group = "com.google.guava", name = "guava", version = "32.1.3-jre" }`
 * 4. version.ref 引用：`guava = { version.ref = "guava" }`
 */
class VersionCatalogCompletionContributor : CompletionContributor() {

  init {
    extend(
      CompletionType.BASIC,
      PlatformPatterns.psiFile().withName(PlatformPatterns.string().endsWith(".versions.toml")),
      VersionCatalogCompletionProvider()
    )
  }
}

private class VersionCatalogCompletionProvider : CompletionProvider<CompletionParameters>() {

  override fun addCompletions(
    parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet
  ) {
    val document = parameters.editor.document
    val offset = parameters.offset
    val text = document.text
    val project = parameters.originalFile.project

    // 检查当前上下文
    val catalogContext = detectCatalogContext(text, offset) ?: return
    val prefixMatcher = result.withPrefixMatcher(catalogContext.query)

    // 补全已定义的版本键名
    catalogContext.definedVersions.forEach { (name, version) ->
      if (name.startsWith(catalogContext.query)) {
        prefixMatcher.addElement(
          createVersionLookupElement(name, version, "version [defined]", AllIcons.Nodes.Tag)
        )
      }
    }

    // 在 [versions] 区域不补全 Maven 搜索
    if (catalogContext.inVersionsSection) {
      result.restartCompletionOnAnyPrefixChange()
      return
    }

    // 在 [libraries] 区域，补全 Maven Central 搜索
    ProgressManager.checkCanceled()

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Searching Maven Central...", true) {
      override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        runCatching {
          // 搜索 artifactId - 简化处理，只返回空结果
          val results = emptyList<Any>()

          results.forEachIndexed { index, artifact ->
            ProgressManager.checkCanceled()
            prefixMatcher.addElement(
              createLibraryArtifactElement(artifact, catalogContext, 5000.0 - index)
            )
          }
        }.onFailure {
          // 搜索失败，只返回已定义的补全
        }
      }
    })

    result.restartCompletionOnAnyPrefixChange()
  }

  private fun detectCatalogContext(text: String, offset: Int): CatalogContext? {
    val lineStart = text.lastIndexOf('\n', offset - 1) + 1
    val lineText = text.substring(lineStart, offset)

    // 检查是否在 [versions] 或 [libraries] 区域
    var inVersionsSection = false
    var inLibrariesSection = false
    for (i in (lineStart - 1) downTo 0) {
      if (text[i] == '\n') {
        val sectionHeader = text.substring(i, minOf(i + 20, text.length))
        if (sectionHeader.contains("[versions]")) {
          inVersionsSection = true
          inLibrariesSection = false
          break
        } else if (sectionHeader.contains("[libraries]") || sectionHeader.contains("[plugins]")) {
          inLibrariesSection = true
          inVersionsSection = false
          break
        }
      }
    }

    // 解析已定义的版本
    val definedVersions = parseVersions(text)

    // 模式：xxx = "value" 或 xxx = { ... }
    val assignmentPattern = Regex("""^\s*([\w-]+)\s*=""")
    val match = assignmentPattern.find(lineText) ?: return null

    val key = match.groupValues[1].trim()
    val query = key

    return CatalogContext(
      query = query,
      keyStartOffset = lineStart,
      inVersionsSection = inVersionsSection,
      inLibrariesSection = inLibrariesSection,
      definedVersions = definedVersions
    )
  }

  private fun parseVersions(text: String): Map<String, String> {
    val versions = mutableMapOf<String, String>()
    var inVersions = false

    text.lines().forEach { line ->
      val trimmed = line.trim()
      when {
        trimmed == "[versions]" -> inVersions = true
        trimmed.startsWith("[") && !trimmed.startsWith("[versions]") -> inVersions = false
        inVersions && trimmed.contains("=") -> {
          val parts = trimmed.split("=", limit = 2)
          if (parts.size == 2) {
            val name = parts[0].trim()
            val version = parts[1].trim().removeSurrounding("\"")
            versions[name] = version
          }
        }
      }
    }

    return versions
  }

  private fun createVersionLookupElement(
    name: String, version: String, type: String, icon: javax.swing.Icon
  ): LookupElement {
    val value = "\"$version\""
    return PrioritizedLookupElement.withPriority(
      LookupElementBuilder.create(value).withPresentableText(name).withTailText(version, true).withTypeText(type, true)
        .withIcon(icon).withBoldness(false), 10000.0
    )
  }

  private fun createLibraryArtifactElement(
    artifact: Any, context: CatalogContext, priority: Double
  ): LookupElement {
    // 构建 artifact 信息（假设是 MavenArtifact 类型）
    val artifactId = artifact.toString()
    val version = "latest"
    val group = "com.example"

    val template = when {
      // 简写形式：guava = "com.google.guava:guava:32.1.3-jre"
      context.inLibrariesSection -> {
        val sb = StringBuilder()
        sb.append("$artifactId = \"$artifactId:$version\"")
        sb.toString()
      }

      else -> {
        // module 形式
        "$artifactId = { module = \"com.example:$artifactId\", version = \"${artifactId}-version\" }"
      }
    }

    return PrioritizedLookupElement.withPriority(
      LookupElementBuilder.create(template).withPresentableText(artifactId).withTailText(" [Maven]", true)
        .withTypeText(group, true).withIcon(AllIcons.Nodes.PpLib).withBoldness(true)
        .withInsertHandler(createCatalogInsertHandler(context)), priority
    )
  }

  private fun createCatalogInsertHandler(
    context: CatalogContext
  ): InsertHandler<LookupElement> = InsertHandler { insertCtx, item ->
    val document = insertCtx.document
    val startOffset = context.keyStartOffset
    val endOffset = insertCtx.tailOffset

    // 获取当前行内容
    val lineStart = document.getText(TextRange(startOffset, endOffset)).indexOf('\n') + startOffset + 1
    val lineEnd = document.getText(TextRange(lineStart, endOffset + 100))
    val lineEndPos = lineEnd.indexOf('\n')
    val actualLineEnd = if (lineEndPos >= 0) lineStart + lineEndPos else lineEnd.lastIndex

    // 替换整行
    val lineText = document.getText(TextRange(lineStart, actualLineEnd))
    val newText = item.lookupString

    document.replaceString(lineStart, actualLineEnd, newText)
    insertCtx.editor.caretModel.moveToOffset(lineStart + newText.length)
    insertCtx.commitDocument()
  }
}

private data class CatalogContext(
  val query: String,
  val keyStartOffset: Int,
  val inVersionsSection: Boolean,
  val inLibrariesSection: Boolean,
  val definedVersions: Map<String, String>
)
