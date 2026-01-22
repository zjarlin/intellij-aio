# Testing Guide for Catalog Reference Fix

## What We Built

A new Gradle Buddy module that detects and fixes invalid Gradle version catalog references in `build.gradle.kts` files.

### Features

1. **Detection**: Identifies invalid catalog references like `libs.gradlePlugin.ksp`
2. **Two Error Types**:
   - **WrongFormat**: TOML has the declaration but reference format is wrong (e.g., `gradlePlugin.ksp` → `gradle.plugin.ksp`)
   - **NotDeclared**: TOML doesn't have the declaration at all
3. **Two Mechanisms**:
   - **Inspection**: Automatic yellow squiggly lines on invalid references
   - **Intention**: Alt+Enter quick fix to correct the reference

### Strategy Pattern

The fix logic uses a strategy pattern:
- `CatalogFixStrategy` interface with `support()` method
- `WrongFormatFixStrategy`: Fixes format issues
- `NotDeclaredFixStrategy`: Suggests adding to TOML or shows available aliases
- `CatalogFixStrategyFactory`: Routes errors to appropriate strategy

## How to Test

### 1. Build the Plugin

```bash
./gradlew :plugins:gradle-buddy:build -x test
```

### 2. Run Test IDE

```bash
./gradlew :plugins:gradle-buddy:runIde
```

### 3. Open Test Project

Open the project at: `/Users/zjarlin/IdeaProjects/addzero-lib-jvm/`

### 4. Navigate to Test File

Open: `lib/gradle-plugin/project-plugin/conventions/jvm-conventions/koin-convention/build.gradle.kts`

Line 27 has: `implementation(libs.gradlePlugin.ksp)`

### 5. Expected Behavior

**What Should Happen:**
1. Yellow squiggly line under `gradlePlugin.ksp`
2. Alt+Enter shows intention: "修复版本目录引用: gradlePlugin.ksp → gradle.plugin.ksp"
3. Applying the fix changes `gradlePlugin.ksp` to `gradle.plugin.ksp`

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
