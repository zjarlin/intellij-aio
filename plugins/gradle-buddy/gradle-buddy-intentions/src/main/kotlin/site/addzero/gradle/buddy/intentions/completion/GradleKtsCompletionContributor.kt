package site.addzero.gradle.buddy.intentions.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import site.addzero.network.call.maven.util.MavenArtifact
import site.addzero.network.call.maven.util.MavenCentralSearchUtil

/**
 * Gradle Kotlin Script 依赖补全
 *
 * 支持场景：
 * 1. implementation("tool-cur   -> 在引号内输入时补全
 * 2. implementation(tool-cur    -> 括号内无引号时输入补全（自动加引号）
 * 3. 空输入时显示推荐依赖列表
 */
class GradleKtsCompletionContributor : CompletionContributor() {

   init {
     // 在所有文件类型中触发补全，然后在代码中检查是否是gradle.kts文件
     extend(
       CompletionType.BASIC,
       PlatformPatterns.psiElement(),
       GradleDependencyCompletionProvider()
     )

     extend(
       CompletionType.SMART,
       PlatformPatterns.psiElement(),
       GradleDependencyCompletionProvider()
     )
   }
}

private class GradleDependencyCompletionProvider : CompletionProvider<CompletionParameters>() {

  companion object {
    private val DEPENDENCY_METHODS = setOf(
      "implementation",
      "api",
      "compileOnly",
      "runtimeOnly",
      "testImplementation",
      "testCompileOnly",
      "testRuntimeOnly",
      "kapt",
      "ksp",
      "annotationProcessor",
      "classpath"
    )
  }

  override fun addCompletions(
    parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet
  ) {
    // 只在gradle.kts文件中提供补全
    val fileName = parameters.originalFile.name
    if (!fileName.endsWith(".gradle.kts")) {
      return
    }

    val document = parameters.editor.document
    val offset = parameters.offset
    val text = document.text
    val project = parameters.originalFile.project

    println("GradleBuddy: Completion triggered at offset $offset in file $fileName")

    // 调试：添加一个简单的测试补全项
    val simpleElement = LookupElementBuilder.create("simple-test:1.0.0")
      .withPresentableText("Simple Test")
      .withTypeText("Test")
    result.addElement(simpleElement)
    println("GradleBuddy: Added simple test element")

    // 检测是否在依赖声明上下文
    val ctx = detectContext(text, offset)

    // 如果没有检测到上下文，但处于依赖方法调用中，也提供补全
    if (ctx == null) {
      val lineCtx = detectLineContext(text, offset)
      if (lineCtx != null) {
        // 提供默认补全（空查询）
        provideDefaultCompletions(result, project, lineCtx, offset)
        result.restartCompletionOnAnyPrefixChange()
        return
      } else {
        // 调试：检查为什么没有触发补全
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val lineText = text.substring(lineStart, minOf(lineStart + 50, text.length))
        println("GradleBuddy: No completion context detected. Line: '$lineText', offset: $offset")
        return
      }
    }

    val query = ctx.query
    val prefixMatcher = if (query.isEmpty()) result else result.withPrefixMatcher(query)

    // 优先显示推荐依赖列表（最高优先级）
    val suggestionService = GradleDependencySuggestionService.getInstance(project)
    val suggestions = suggestionService.getAllSuggestions()

    // 按匹配度排序：完全匹配 > artifactId包含 > 任意包含
    val matchedSuggestions = suggestions.mapNotNull { suggestion ->
      val score = calculateMatchScore(suggestion, query)
      if (score > 0) suggestion to score else null
    }.sortedByDescending { it.second }

    println("GradleBuddy: Found ${matchedSuggestions.size} matched suggestions for query '$query'")

    matchedSuggestions.take(20).forEachIndexed { index, (suggestion, score) ->
      val element = createSuggestionElement(suggestion, ctx, priority = 10000.0 - index * 10 - score)
      println("GradleBuddy: Adding suggestion element: ${element.lookupString} with priority ${10000.0 - index * 10 - score}")
      result.addElement(element) // 直接添加到 result 而不是 prefixMatcher
    }

    // 确保补全结果被提交
    println("GradleBuddy: Added ${matchedSuggestions.size} suggestion elements total")

    // 确保补全结果被提交
    result.stopHere()

    // 查询长度太短，只显示推荐
    if (query.length < 2) {
      result.restartCompletionOnAnyPrefixChange()
      return
    }

        // 搜索 Maven Central（较低优先级）- 同步搜索避免UI更新问题
        try {
            val artifacts = searchMavenCentral(query, project)
            println("GradleBuddy: Found ${artifacts.size} Maven Central artifacts for query '$query'")

            // 直接在当前线程中添加结果
            val matchedArtifacts = artifacts.mapNotNull { artifact ->
                val coordinate = "${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}"
                val score = calculateMatchScore(coordinate, query)
                if (score > 0) artifact to score else null
            }.sortedByDescending { it.second }

            matchedArtifacts.take(15).forEachIndexed { index, (artifact, score) ->
                val priority = 5000.0 - index * 10 - score / 10.0
                val element = createArtifactElement(artifact, ctx, priority)
                println("GradleBuddy: Adding Maven artifact element: ${element.lookupString} with priority $priority")
                result.addElement(element) // 直接添加到 result
            }
        } catch (e: Exception) {
            println("GradleBuddy: Failed to search Maven Central: ${e.message}")
        }

    result.restartCompletionOnAnyPrefixChange()
  }

  /**
   * 搜索 Maven Central 获取依赖建议
   */
  private fun searchMavenCentral(query: String, project: Project): List<MavenArtifact> {
    return try {
      MavenCentralSearchUtil.searchByKeyword(query, 10)
    } catch (_: Exception) {
      // 如果搜索失败，回退到本地建议
      val suggestionService = GradleDependencySuggestionService.getInstance(project)
      val allSuggestions = suggestionService.getAllSuggestions()
      allSuggestions.filter { it.contains(query) }.take(10).mapNotNull { coordinate ->
        val parts = coordinate.split(":")
        if (parts.size >= 2) {
          MavenArtifact(
            id = coordinate,
            groupId = parts[0],
            artifactId = parts[1],
            version = if (parts.size >= 3) parts[2] else "",
            latestVersion = if (parts.size >= 3) parts[2] else "",
            packaging = "jar",
            timestamp = 0L,
            repositoryId = "central"
          )
        } else null
      }
    }
  }



  /**
   * 检测当前行是否包含依赖方法调用（宽松检测）
   */
  private fun detectLineContext(text: String, offset: Int): String? {
    val lineStart = text.lastIndexOf('\n', offset - 1) + 1
    val lineText = text.substring(lineStart, minOf(lineStart + 100, text.length))

    println("GradleBuddy: Detecting line context in line: '$lineText'")

    // 检查当前行是否包含依赖方法
    val methodPattern = Regex("""(\w+)\s*\(""")
    methodPattern.find(lineText)?.let { match ->
      val method = match.groupValues[1]
      println("GradleBuddy: Found method pattern: '$method'")
      if (method in DEPENDENCY_METHODS) {
        println("GradleBuddy: Method '$method' is in DEPENDENCY_METHODS")
        return method
      } else {
        println("GradleBuddy: Method '$method' is NOT in DEPENDENCY_METHODS")
      }
    } ?: println("GradleBuddy: No method pattern found")
    return null
  }

  /**
   * 提供默认补全（当没有具体查询时）
   */
  private fun provideDefaultCompletions(result: CompletionResultSet, project: Project, methodName: String, offset: Int) {
    // 显示热门依赖建议（前20个最流行的）
    val suggestionService = GradleDependencySuggestionService.getInstance(project)
    val suggestions = suggestionService.getAllSuggestions().take(20)
    val prefixMatcher = result // 对于默认补全，不使用前缀匹配器

    println("GradleBuddy: Providing ${suggestions.size} default suggestions for method '$methodName'")

    suggestions.forEachIndexed { index, suggestion ->
      val element = createSuggestionElement(suggestion, createDefaultContext(methodName, offset), priority = 1000.0 - index * 10)
      println("GradleBuddy: Adding default suggestion element: ${element.lookupString} with priority ${1000.0 - index * 10}")
      result.addElement(element) // 直接添加到 result
    }

    println("GradleBuddy: Added ${suggestions.size} default suggestion elements")

    // 确保补全结果被提交
    result.stopHere()
  }

  /**
   * 创建默认的依赖上下文（用于无查询的补全）
   */
  private fun createDefaultContext(methodName: String, offset: Int = 0): DependencyContext {
    return DependencyContext(
      methodName = methodName,
      hasOpenQuote = false,
      hasCloseQuote = false,
      query = "",
      queryStartOffset = offset
    )
  }

  /**
   * 计算依赖匹配分数
   * 返回值越高，匹配度越好
   */
  private fun calculateMatchScore(dependency: String, query: String): Int {
    if (query.isEmpty()) return 100 // 空查询给高分

    val parts = dependency.split(":")
    if (parts.size < 2) return 0

    val artifactId = parts[1]

    // 1. artifactId 以查询开头（最高优先级）
    if (artifactId.startsWith(query, ignoreCase = true)) {
      return 1000 + (100 - artifactId.length) // 越短的artifactId优先级越高
    }

    // 2. artifactId 包含查询（高优先级）
    if (artifactId.contains(query, ignoreCase = true)) {
      val index = artifactId.indexOf(query, ignoreCase = true)
      return 800 - index // 越靠前的匹配优先级越高
    }

    // 3. 完整坐标完全匹配
    if (dependency.equals(query, ignoreCase = true)) {
      return 600
    }

    // 4. groupId:artifactId 包含查询
    if (dependency.contains(query, ignoreCase = true)) {
      return 400
    }

    // 5. artifactId 部分匹配（模糊匹配）
    // 例如：查询 "api-maven" 能匹配 "tool-api-maven"
    if (query.length >= 3) {
      val queryWords = query.split("-", "_", ".")
      val artifactWords = artifactId.split("-", "_", ".")

      var matchCount = 0
      for (queryWord in queryWords) {
        if (queryWord.length >= 2) { // 只匹配长度>=2的词
          for (artifactWord in artifactWords) {
            if (artifactWord.contains(queryWord, ignoreCase = true)) {
              matchCount++
              break
            }
          }
        }
      }

      if (matchCount > 0) {
        return 200 + matchCount * 50 // 匹配的词越多分数越高
      }
    }

    return 0 // 不匹配
  }

  /**
   * 检测依赖上下文
   *
   * 支持模式：
   * - implementation("com.google") -> hasOpenQuote=true, hasCloseQuote=false
   * - implementation("com.google") -> hasOpenQuote=true, hasCloseQuote=true
   * - implementation(com.google) -> hasOpenQuote=false
   */
  private fun detectContext(text: String, offset: Int): DependencyContext? {
    val lineStart = text.lastIndexOf('\n', offset - 1) + 1
    val lineText = text.substring(lineStart, offset)

    println("GradleBuddy: Detecting context in line: '$lineText'")

    // 模式1: implementation("xxx 或 implementation("xxx"
    val quotedPattern = Regex("""(\w+)\s*\(\s*"([^"]*)(")?$""")
    quotedPattern.find(lineText)?.let { match ->
      val method = match.groupValues[1]
      val query = match.groupValues[2]
      val hasCloseQuote = match.groupValues[3].isNotEmpty()

      println("GradleBuddy: Matched quoted pattern - method: $method, query: '$query', hasCloseQuote: $hasCloseQuote")

      if (method !in DEPENDENCY_METHODS) {
        println("GradleBuddy: Method '$method' not in DEPENDENCY_METHODS")
        return null
      }

      return DependencyContext(
        methodName = method,
        hasOpenQuote = true,
        hasCloseQuote = hasCloseQuote,
        query = query,
        queryStartOffset = offset - query.length - (if (hasCloseQuote) 1 else 0)
      )
    }

    // 模式2: implementation(xxx （无引号）
    val unquotedPattern = Regex("""(\w+)\s*\(\s*([^"\s]*)$""")

    // 调试：测试正则表达式匹配
    println("GradleBuddy: Testing patterns on line: '$lineText'")
    println("GradleBuddy: Quoted pattern matches: ${quotedPattern.findAll(lineText).toList()}")
    println("GradleBuddy: Unquoted pattern matches: ${unquotedPattern.findAll(lineText).toList()}")
    unquotedPattern.find(lineText)?.let { match ->
      val method = match.groupValues[1]
      val query = match.groupValues[2]

      println("GradleBuddy: Matched unquoted pattern - method: $method, query: '$query'")

      if (method !in DEPENDENCY_METHODS) {
        println("GradleBuddy: Method '$method' not in DEPENDENCY_METHODS")
        return null
      }

      return DependencyContext(
        methodName = method,
        hasOpenQuote = false,
        hasCloseQuote = false,
        query = query,
        queryStartOffset = offset - query.length
      )
    }

    println("GradleBuddy: No context detected")
    return null
  }

  private fun createSuggestionElement(
    suggestion: String, ctx: DependencyContext, priority: Double
  ): LookupElement {
    val parts = suggestion.split(":")
    val groupId = parts.getOrNull(0) ?: ""
    val artifactId = parts.getOrNull(1) ?: ""
    val version = parts.getOrNull(2) ?: ""

    val displayText = if (version.isNotEmpty()) {
      "$groupId:$artifactId:$version"
    } else {
      "$groupId:$artifactId"
    }

    return LookupElementBuilder.create(displayText)
      .withPresentableText(artifactId)
      .withTailText(if (version.isNotEmpty()) " :$version" else "", true)
      .withTypeText(groupId, true)
      .withIcon(AllIcons.Nodes.PpLib)
      .withBoldness(true)
      .withInsertHandler(createInsertHandler(ctx, suggestion))
  }

    private fun createArtifactElement(
      artifact: MavenArtifact,
      ctx: DependencyContext,
      priority: Double
    ): LookupElement {
      val version = artifact.latestVersion.ifBlank { artifact.version }
      val coordinate = "${artifact.groupId}:${artifact.artifactId}:$version"

      return LookupElementBuilder.create(coordinate)
        .withPresentableText(artifact.artifactId)
        .withTailText(" :$version", true)
        .withTypeText(artifact.groupId, true)
        .withIcon(AllIcons.Nodes.PpLib)
        .withInsertHandler(createInsertHandler(ctx, coordinate))
    }

  private fun createInsertHandler(
    ctx: DependencyContext, coordinate: String
  ): InsertHandler<LookupElement> = InsertHandler { insertCtx, _ ->
    val document = insertCtx.document
    val startOffset = ctx.queryStartOffset
    val endOffset = insertCtx.tailOffset

    // 检查后面是否已有闭合引号、冒号和右括号
    val afterText = document.getText(TextRange(endOffset, minOf(endOffset + 20, document.textLength)))

//       检测已有的闭合符号
    var hasClosingQuote = false
    var hasColon = false
    var hasClosingParen = false

    // 逐个字符检查
    for (char in afterText) {
      when (char) {
        '"' -> {
          hasClosingQuote = true
          hasClosingParen = afterText.substring(afterText.indexOf(char)).trimStart().contains(")")
        }

        ':' -> hasColon = true
        ')' -> hasClosingParen = true
        ' ', '\t', '\n', '\r' -> break
      }
    }

    // 构建插入文本
    val insertText = buildString {
      if (!ctx.hasOpenQuote) append("\"")
      append(coordinate)
      when {
        ctx.hasCloseQuote || hasClosingQuote || hasColon -> {
          // 已有引号、冒号或括号，不需要添加任何闭合符号
        }

        hasClosingParen -> {
          // 已有右括号，只添加引号
          append("\"")
        }

        else -> {
          // 没有任何闭合符号，添加引号和右括号
          append("\")")
        }
      }
    }

    // 替换文本
    document.replaceString(startOffset, endOffset, insertText)
    insertCtx.editor.caretModel.moveToOffset(startOffset + insertText.length)
    insertCtx.commitDocument()
  }
}



private data class DependencyContext(
  val methodName: String,
  val hasOpenQuote: Boolean,
  val hasCloseQuote: Boolean,
  val query: String,
  val queryStartOffset: Int
)
