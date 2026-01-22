# Gradle Plugin ID Fixer - Design Document

## Architecture Overview

The Gradle Plugin ID Fixer consists of three main components:

1. **Plugin ID Scanner** - Discovers and indexes local precompiled script plugins
2. **ID Replacement Engine** - Performs the actual ID replacements in build files
3. **User Interface Layer** - Provides context menu actions and intention actions

```
┌─────────────────────────────────────────────────────────┐
│                    User Interface Layer                  │
│  ┌──────────────────────┐  ┌─────────────────────────┐ │
│  │ Context Menu Action  │  │  Intention Action       │ │
│  │ (Bulk Fix)           │  │  (Single Fix)           │ │
│  └──────────┬───────────┘  └───────────┬─────────────┘ │
└─────────────┼──────────────────────────┼───────────────┘
              │                          │
              ▼                          ▼
┌─────────────────────────────────────────────────────────┐
│              ID Replacement Engine                       │
│  ┌──────────────────────────────────────────────────┐  │
│  │  - Find plugin ID references                     │  │
│  │  - Validate replacement candidates               │  │
│  │  - Perform safe replacements                     │  │
│  │  - Track changes                                 │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────┬───────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────┐
│              Plugin ID Scanner                           │
│  ┌──────────────────────────────────────────────────┐  │
│  │  - Scan build-logic directory                    │  │
│  │  - Extract plugin IDs and packages               │  │
│  │  - Build plugin ID mapping                       │  │
│  │  - Cache results                                 │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Component Design

### 1. Plugin ID Scanner

**Purpose**: Discover all local precompiled script plugins and build a mapping of short IDs to fully qualified IDs.

**Key Classes**:

```kotlin
data class PluginIdInfo(
    val shortId: String,              // e.g., "kmp-core"
    val fullyQualifiedId: String,     // e.g., "site.addzero.buildlogic.kmp.platform.kmp-core"
    val packageName: String,          // e.g., "site.addzero.buildlogic.kmp.platform"
    val file: VirtualFile             // The .gradle.kts file
)

class PluginIdScanner(private val project: Project) {
    fun scanBuildLogic(buildLogicDir: VirtualFile): List<PluginIdInfo>
    fun findBuildLogicDirectories(): List<VirtualFile>
    private fun extractPluginInfo(file: VirtualFile): PluginIdInfo?
    private fun extractPackageName(psiFile: KtFile): String?
}

class PluginIdCache(private val project: Project) {
    private val cache: MutableMap<VirtualFile, List<PluginIdInfo>>

    fun get(buildLogicDir: VirtualFile): List<PluginIdInfo>?
    fun put(buildLogicDir: VirtualFile, plugins: List<PluginIdInfo>)
    fun invalidate(buildLogicDir: VirtualFile)
    fun clear()
}
```

**Algorithm**:
1. Find all `build-logic` directories in the project
2. Scan `src/main/kotlin/**/*.gradle.kts` files
3. For each file:
   - Extract filename without extension (e.g., `kmp-core`)
   - Parse the file to find package declaration
   - If package exists, construct fully qualified ID: `package.shortId`
   - Store in `PluginIdInfo` object
4. Cache results for performance

### 2. ID Replacement Engine

**Purpose**: Find and replace plugin ID references in build files.

**Key Classes**:

```kotlin
data class ReplacementCandidate(
    val file: PsiFile,
    val element: KtStringTemplateExpression,  // The "plugin-id" string
    val currentId: String,
    val suggestedId: String,
    val lineNumber: Int
)

class IdReplacementEngine(
    private val project: Project,
    private val pluginIdMapping: Map<String, PluginIdInfo>
) {
    fun findReplacementCandidates(scope: SearchScope): List<ReplacementCandidate>
    fun applyReplacements(candidates: List<ReplacementCandidate>): ReplacementResult
    private fun isPluginIdReference(element: PsiElement): Boolean
    private fun isInPluginsBlock(element: PsiElement): Boolean
}

data class ReplacementResult(
    val filesModified: Int,
    val replacementsMade: Int,
    val errors: List<String>
)
```

**Algorithm**:
1. Search for all `id("...")` calls in `plugins {}` blocks
2. For each found reference:
   - Extract the plugin ID string
   - Check if it matches a local plugin short ID
   - If yes, create a `ReplacementCandidate`
3. Apply replacements:
   - Use PSI manipulation to replace the string
   - Preserve formatting and comments
   - Track changes for reporting

### 3. User Interface Layer

#### 3.1 Context Menu Action (Bulk Fix)

```kotlin
class FixAllPluginIdsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val buildLogicDir = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // 1. Scan for plugin IDs
        val scanner = PluginIdScanner(project)
        val plugins = scanner.scanBuildLogic(buildLogicDir)

        // 2. Build mapping
        val mapping = plugins.associateBy { it.shortId }

        // 3. Find candidates
        val engine = IdReplacementEngine(project, mapping)
        val candidates = engine.findReplacementCandidates(GlobalSearchScope.projectScope(project))

        // 4. Show preview dialog
        val dialog = ReplacementPreviewDialog(project, candidates)
        if (dialog.showAndGet()) {
            // 5. Apply replacements
            val result = engine.applyReplacements(candidates)

            // 6. Show result notification
            showResultNotification(project, result)
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file?.name == "build-logic"
    }
}
```

#### 3.2 Intention Action (Single Fix)

```kotlin
class FixPluginIdIntention : IntentionAction {
    override fun getText(): String = "Fix plugin ID to use fully qualified name"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        return isPluginIdInPluginsBlock(element) && needsQualification(element)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        val pluginId = extractPluginId(element) ?: return

        // Find the fully qualified ID
        val scanner = PluginIdScanner(project)
        val buildLogicDirs = scanner.findBuildLogicDirectories()
        val allPlugins = buildLogicDirs.flatMap { scanner.scanBuildLogic(it) }
        val pluginInfo = allPlugins.find { it.shortId == pluginId } ?: return

        // Replace
        val stringElement = findStringElement(element) ?: return
        replacePluginId(stringElement, pluginInfo.fullyQualifiedId)
    }

    private fun needsQualification(element: PsiElement): Boolean {
        val pluginId = extractPluginId(element) ?: return false
        // Check if this is a local plugin that needs qualification
        return isLocalPlugin(pluginId) && !isFullyQualified(pluginId)
    }
}
```

## Data Flow

### Bulk Fix Flow

```
User Right-Clicks build-logic
         │
         ▼
Action Triggered
         │
         ▼
Scan build-logic Directory
         │
         ▼
Extract Plugin IDs + Packages
         │
         ▼
Build Short ID → Full ID Mapping
         │
         ▼
Search Project for id("...") in plugins {}
         │
         ▼
Match Against Local Plugin IDs
         │
         ▼
Generate Replacement Candidates
         │
         ▼
Show Preview Dialog
         │
         ▼
User Confirms
         │
         ▼
Apply Replacements via PSI
         │
         ▼
Show Result Notification
```

### Intention Action Flow

```
User Presses Alt+Enter on Plugin ID
         │
         ▼
Check if in plugins {} Block
         │
         ▼
Extract Plugin ID String
         │
         ▼
Check if Local Plugin
         │
         ▼
Check if Needs Qualification
         │
         ▼
Show Intention Action
         │
         ▼
User Selects Action
         │
         ▼
Find Fully Qualified ID
         │
         ▼
Replace String via PSI
```

## Key Algorithms

### 1. Package Name Extraction

```kotlin
fun extractPackageName(ktFile: KtFile): String? {
    return ktFile.packageDirective?.fqName?.asString()
}
```

### 2. Plugin ID Detection in plugins {} Block

```kotlin
fun isInPluginsBlock(element: PsiElement): Boolean {
    var parent = element.parent
    while (parent != null) {
        if (parent is KtCallExpression) {
            val callName = parent.calleeExpression?.text
            if (callName == "plugins") {
                return true
            }
        }
        parent = parent.parent
    }
    return false
}
```

### 3. Plugin ID Extraction from id("...") Call

```kotlin
fun extractPluginId(element: PsiElement): String? {
    // Find the string template expression
    val stringExpr = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java)
        ?: return null

    // Check if it's inside an id() call
    val callExpr = PsiTreeUtil.getParentOfType(stringExpr, KtCallExpression::class.java)
        ?: return null

    if (callExpr.calleeExpression?.text != "id") return null

    // Extract the string content
    return stringExpr.entries.firstOrNull()?.text
}
```

### 4. Safe String Replacement

```kotlin
fun replacePluginId(stringElement: KtStringTemplateExpression, newId: String) {
    val factory = KtPsiFactory(stringElement.project)
    val newString = factory.createStringTemplate("\"$newId\"")
    stringElement.replace(newString)
}
```

## Performance Considerations

1. **Caching**: Cache plugin ID mappings to avoid repeated file system scans
2. **Incremental Updates**: Invalidate cache only for changed build-logic files
3. **Lazy Loading**: Load plugin information only when needed
4. **Batch Processing**: Process multiple replacements in a single write action
5. **Background Tasks**: Run scanning in background threads with progress indication

## Error Handling

1. **File Access Errors**: Handle read-only files, missing files gracefully
2. **Parse Errors**: Skip malformed .gradle.kts files with warning
3. **Ambiguous IDs**: Warn if multiple plugins have the same short ID
4. **Concurrent Modifications**: Use write actions and document locking
5. **Rollback**: Support undo for bulk operations

## Testing Strategy

### Unit Tests
- Plugin ID extraction from filenames
- Package name parsing
- Fully qualified ID construction
- Plugin ID detection in PSI trees
- String replacement logic

### Integration Tests
- End-to-end bulk fix on sample project
- Intention action on various plugin ID formats
- Cache invalidation on file changes
- Multi-module project handling

### Test Data
```
test-project/
├── build-logic/
│   └── src/main/kotlin/
│       └── com/example/
│           ├── kmp-core.gradle.kts (with package)
│           └── simple-plugin.gradle.kts (no package)
└── module/
    └── build.gradle.kts (with id("kmp-core"))
```

## Security Considerations

1. **File System Access**: Only read/write within project boundaries
2. **User Confirmation**: Require confirmation before bulk changes
3. **Backup**: Suggest VCS commit before bulk operations
4. **Validation**: Validate all file paths to prevent directory traversal

## Correctness Properties

### Property 1: ID Mapping Correctness
**For all** local precompiled script plugins with package declarations:
- The fully qualified ID MUST equal `packageName + "." + shortId`
- The short ID MUST equal the filename without `.gradle.kts` extension

**Validates**: Requirements 3.1, 3.2, 3.3, 3.4

### Property 2: Replacement Accuracy
**For all** plugin ID replacements:
- IF the ID is a local plugin short ID
- AND the plugin has a package declaration
- THEN the replacement MUST use the fully qualified ID
- AND external library plugin IDs MUST NOT be modified

**Validates**: Requirements 1.8, 2.6

### Property 3: Scope Correctness
**For all** plugin ID references:
- Replacements MUST only occur within `plugins {}` blocks
- String literals outside `plugins {}` blocks MUST NOT be modified
- Comments and formatting MUST be preserved

**Validates**: Requirements 1.6, 2.4

### Property 4: Cache Consistency
**For all** cached plugin ID mappings:
- IF a build-logic file is modified
- THEN the cache for that file MUST be invalidated
- AND subsequent queries MUST reflect the updated state

**Validates**: Requirements 3.6

## Future Enhancements

1. Support for Groovy build scripts (`.gradle`)
2. Batch processing across multiple projects
3. Configuration to exclude certain plugin IDs
4. Integration with refactoring tools
5. Support for version catalog plugin references


## Concrete Example

### Intention Action Example

**Scenario**:
- Project file: `/Users/zjarlin/IdeaProjects/addzero-lib-jvm/lib/tool-kmp/network-starter/build.gradle.kts`
- Current code:
  ```kotlin
  plugins {
      id("kmp-ktor")
  }
  ```

**Plugin Definition**:
- Plugin file: `/Users/zjarlin/IdeaProjects/addzero-lib-jvm/checkouts/build-logic/src/main/kotlin/site/addzero/buildlogic/kmp/libs/kmp-ktor.gradle.kts`
- Package declaration: `package site.addzero.buildlogic.kmp.libs`

**User Action**:
1. User places cursor on `"kmp-ktor"` in the `id("kmp-ktor")` call
2. User presses Alt+Enter
3. Intention action appears: **"Fix build-logic qualified name"**
4. User selects the intention

**Result**:
- **All files** in the project with `id("kmp-ktor")` are updated to `id("site.addzero.buildlogic.kmp.libs.kmp-ktor")`
- Not just the current file, but every occurrence across the entire project
- Shows a notification: "Fixed 5 occurrences of 'kmp-ktor' in 3 files"

### Detection Logic

The intention action should:
1. Detect that `"kmp-ktor"` is inside an `id()` call within a `plugins {}` block
2. Search for `kmp-ktor.gradle.kts` in all `build-logic` directories
3. Find the file at `checkouts/build-logic/src/main/kotlin/site/addzero/buildlogic/kmp/libs/kmp-ktor.gradle.kts`
4. Parse the file and extract package: `site.addzero.buildlogic.kmp.libs`
5. Construct fully qualified ID: `site.addzero.buildlogic.kmp.libs.kmp-ktor`
6. **Search the entire project** for all occurrences of `id("kmp-ktor")` in `plugins {}` blocks
7. Offer the fix with the intention text: "Fix build-logic qualified name"
8. When user confirms, replace **all occurrences** across the project with the fully qualified ID
9. Show a summary notification of how many files were modified

### Edge Cases

1. **Plugin without package**: If `kmp-ktor.gradle.kts` has no package declaration, no fix is needed
2. **Already qualified**: If the ID is already `site.addzero.buildlogic.kmp.libs.kmp-ktor`, no fix is offered
3. **External plugin**: If the ID is `org.jetbrains.kotlin.multiplatform`, it's not a local plugin, no fix is offered
4. **Multiple build-logic dirs**: Search all `build-logic` directories in the project
5. **Plugin not found**: If no matching `.gradle.kts` file is found, no fix is offered


## Important: Project-Wide Replacement

### Intention Action Behavior

The "Fix build-logic qualified name" intention action performs a **project-wide replacement**, not just a single-file fix:

1. **Trigger**: User presses Alt+Enter on any `id("kmp-ktor")` occurrence
2. **Scope**: Searches the **entire project** for all `id("kmp-ktor")` references
3. **Action**: Replaces **all occurrences** with `id("site.addzero.buildlogic.kmp.libs.kmp-ktor")`
4. **Feedback**: Shows notification like "Fixed 5 occurrences of 'kmp-ktor' in 3 files"

### Example Scenario

**Before Fix** (multiple files):

File 1: `lib/tool-kmp/network-starter/build.gradle.kts`
```kotlin
plugins {
    id("kmp-ktor")
}
```

File 2: `lib/tool-kmp/api-client/build.gradle.kts`
```kotlin
plugins {
    id("kmp-core")
    id("kmp-ktor")
}
```

File 3: `app/build.gradle.kts`
```kotlin
plugins {
    id("kmp-ktor")
    id("kmp-json")
}
```

**After Fix** (all files updated):

File 1:
```kotlin
plugins {
    id("site.addzero.buildlogic.kmp.libs.kmp-ktor")
}
```

File 2:
```kotlin
plugins {
    id("kmp-core")  // Not changed - different plugin
    id("site.addzero.buildlogic.kmp.libs.kmp-ktor")
}
```

File 3:
```kotlin
plugins {
    id("site.addzero.buildlogic.kmp.libs.kmp-ktor")
    id("kmp-json")  // Not changed - different plugin
}
```

**Notification**: "Fixed 3 occurrences of 'kmp-ktor' in 3 files"

### Implementation Note

The `invoke()` method must:
1. Use `IdReplacementEngine.findReplacementCandidates()` with `GlobalSearchScope.projectScope(project)`
2. Filter candidates to only the specific plugin ID being fixed
3. Apply all replacements in a single write action
4. Show a summary notification with counts

This ensures consistency across the entire project and prevents partial fixes.
