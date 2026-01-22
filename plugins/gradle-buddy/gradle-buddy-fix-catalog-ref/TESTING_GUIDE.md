# Testing Guide for Catalog Reference Fix

## What We Built

A new Gradle Buddy module that detects and fixes invalid Gradle version catalog references in `build.gradle.kts` files with **intelligent similarity matching**.

### Features

1. **Detection**: Identifies invalid catalog references like `libs.com.google.devtools.ksp.gradle.plugin`
2. **Two Error Types**:
   - **WrongFormat**: TOML has the declaration but reference format is wrong (e.g., `gradlePlugin.ksp` → `gradle.plugin.ksp`)
   - **NotDeclared**: TOML doesn't have the exact declaration, but may have similar ones
3. **Intelligent Matching**:
   - Tokenizes the invalid reference (e.g., `com.google.devtools.ksp.gradle.plugin` → `[com, google, devtools, ksp, gradle, plugin]`)
   - Searches TOML for aliases containing these tokens
   - Calculates similarity scores using multiple strategies:
     - Exact token matches (50% weight)
     - Jaccard similarity (30% weight)
     - Token order similarity (20% weight)
   - Returns Top 5 most similar aliases
4. **User-Friendly Fixes**:
   - **If similar aliases found**: Shows dialog with Top 5 candidates, user selects the correct one
   - **If no similar aliases found**: Prompts user to add declaration to TOML
5. **Two Mechanisms**:
   - **Inspection**: Automatic yellow squiggly lines on invalid references
   - **Intention**: Alt+Enter quick fix to correct the reference

### Strategy Pattern

The fix logic uses a strategy pattern:
- `CatalogFixStrategy` interface with `support()` method
- `WrongFormatFixStrategy`: Fixes format issues (simple case)
- `NotDeclaredFixStrategy`:
  - If `suggestedAliases` not empty: Shows selection dialog
  - If `suggestedAliases` empty: Shows "add to TOML" message
- `CatalogFixStrategyFactory`: Routes errors to appropriate strategy

### Similarity Matching Algorithm

`AliasSimilarityMatcher` class implements the matching logic:

```kotlin
// Example: libs.com.google.devtools.ksp.gradle.plugin
// Tokens: [com, google, devtools, ksp, gradle, plugin]

// TOML has: gradle-plugin-ksp
// Tokens: [gradle, plugin, ksp]
// Matched tokens: [gradle, plugin, ksp]
// Score: High (3/6 exact matches + good Jaccard + good order)

// Result: gradle.plugin.ksp (Top candidate)
```

## How to Test

### Test Case 1: Wrong Format (Simple Fix)

**File**: `lib/gradle-plugin/project-plugin/conventions/jvm-conventions/koin-convention/build.gradle.kts`
**Line 27**: `implementation(libs.gradlePlugin.ksp)`

**Expected**:
- Yellow squiggly line under `gradlePlugin.ksp`
- Alt+Enter shows: "修复版本目录引用: gradlePlugin.ksp → gradle.plugin.ksp"
- Applying fix changes to `gradle.plugin.ksp`

### Test Case 2: Not Declared with Similar Aliases

**Create a test line**: `implementation(libs.com.google.devtools.ksp.gradle.plugin)`

**Expected**:
- Yellow squiggly line under `com.google.devtools.ksp.gradle.plugin`
- Alt+Enter shows: "选择正确的版本目录引用（找到 X 个相似项）"
- Applying fix shows dialog with candidates:
  ```
  gradle.plugin.ksp (匹配度: 85%, 匹配词: gradle, plugin, ksp)
  ksp.symbol.processing.api (匹配度: 45%, 匹配词: ksp)
  ...
  ```
- User selects `gradle.plugin.ksp`
- Reference is replaced

### Test Case 3: Not Declared with No Similar Aliases

**Create a test line**: `implementation(libs.completely.unknown.library)`

**Expected**:
- Yellow squiggly line
- Alt+Enter shows: "版本目录中未声明 'completely.unknown.library'，需要添加到 TOML"
- Applying fix shows info dialog:
  ```
  版本目录 'libs' 中未找到 'completely.unknown.library' 的声明。

  也没有找到相似的别名。

  请在 TOML 文件中添加对应的声明，例如：

  [libraries]
  completely-unknown-library = { group = "...", name = "...", version = "..." }
  ```

### 6. Check Debug Logs

The code now has extensive debug logging. Check the IDE log for messages starting with:
- `[FixCatalogReferenceIntention]`
- `[CatalogReferenceScanner]`
- `[detectCatalogReferenceError]`

These will show:
- Whether the file is recognized as a gradle.kts file
- What element is being checked
- What TOML files were found
- What aliases were extracted
- Whether an error was detected
- Which strategy was selected

## TOML File Location

The test project's TOML is at:
```
/Users/zjarlin/IdeaProjects/addzero-lib-jvm/checkouts/build-logic/gradle/libs.versions.toml
```

It contains:
```toml
gradle-plugin-ksp = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "..." }
```

Which should be referenced as: `libs.gradle.plugin.ksp`

## Troubleshooting

### Intention Not Showing

If the intention doesn't appear:

1. **Check the logs** - Look for debug output to see where it's failing
2. **Verify TOML scanning** - Logs should show the TOML file was found and parsed
3. **Check alias extraction** - Logs should show `gradle.plugin.ksp` was extracted
4. **Verify error detection** - Logs should show the error was detected as WrongFormat
5. **Check plugin registration** - Verify `plugin.xml` has the intention registered

### Common Issues

1. **TOML not found**: Scanner might not be finding the TOML in subdirectories
2. **Wrong project context**: The intention might be running in the wrong project
3. **Alias conversion**: The TOML key `gradle-plugin-ksp` should convert to `gradle.plugin.ksp`
4. **Plugin not loaded**: The test IDE might not have loaded the plugin

## Next Steps

After testing, if the intention still doesn't show:

1. Review the debug logs to identify the failure point
2. Check if the TOML scanner is finding the file
3. Verify the alias conversion logic is correct
4. Ensure the intention is properly registered in plugin.xml
5. Test with a simpler case (TOML in root gradle/ directory)

## Files Modified

- `FixCatalogReferenceIntention.kt` - Added debug logging
- `CatalogReferenceScanner.kt` - Added debug logging and recursive TOML search
- `plugin.xml` - Registered intention and inspection
- Strategy classes - Implement fix logic

## Debug Logging Added

All key methods now have println statements showing:
- File paths being scanned
- TOML files found
- Aliases extracted
- Errors detected
- Strategies selected
- Fix availability

This should help diagnose exactly where the intention is failing to appear.
