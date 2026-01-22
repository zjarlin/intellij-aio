# Gradle Plugin ID Fixer

A module for fixing plugin ID references in Gradle build scripts when using precompiled script plugins from `build-logic`.

## Problem

When you define a precompiled script plugin in `build-logic/src/main/kotlin/com/example/my-plugin.gradle.kts`, Gradle requires you to reference it using the fully qualified ID:

```kotlin
plugins {
    id("com.example.my-plugin")  // ✅ Correct
}
```

However, developers often use the short ID by mistake:

```kotlin
plugins {
    id("my-plugin")  // ❌ Wrong - will fail at runtime
}
```

This causes build failures that are hard to debug.

## Solution

This module provides automated tools to detect and fix these issues:

### 1. Intention Action (Alt+Enter)

When your cursor is on a short plugin ID, press `Alt+Enter` to see the quick fix:

- **"Fix build-logic qualified name"** - Replaces all occurrences of the short ID throughout the entire project

### 2. Bulk Action

Use the action **"Fix All Plugin IDs in Project"** to:

1. Scan all `build-logic` directories
2. Find all precompiled script plugins
3. Detect all short ID references in the project
4. Replace them with fully qualified IDs

## Features

- ✅ Automatically scans `build-logic` directories
- ✅ Extracts package names from Kotlin files
- ✅ Detects plugin ID references in `plugins {}` blocks
- ✅ Project-wide replacement with a single action
- ✅ Thread-safe PSI access with proper read actions
- ✅ Progress indicators for long-running operations
- ✅ Notifications with detailed results

## How It Works

1. **Scanner** - Recursively scans `build-logic/src/main/kotlin` for `.gradle.kts` files
2. **Parser** - Extracts package declarations to build fully qualified IDs
3. **Detector** - Finds all `id("...")` calls in `plugins {}` blocks
4. **Replacer** - Performs safe, atomic replacements across all files

## Architecture

```
id-fixer/
├── PluginIdScanner.kt          # Scans build-logic for plugins
├── PluginIdInfo.kt             # Data class for plugin metadata
├── IdReplacementEngine.kt      # Finds and replaces plugin IDs
├── ReplacementCandidate.kt     # Represents a replacement location
├── ReplacementResult.kt        # Result of replacement operation
├── FixPluginIdIntention.kt     # Alt+Enter quick fix
└── FixAllPluginIdsAction.kt    # Bulk action for entire project
```

## Usage Example

### Before

```kotlin
// build-logic/src/main/kotlin/com/example/conventions/java-library.gradle.kts
plugins {
    `java-library`
}

// build.gradle.kts (in your project)
plugins {
    id("java-library")  // ❌ Short ID - will fail
}
```

### After

```kotlin
// build.gradle.kts (in your project)
plugins {
    id("com.example.conventions.java-library")  // ✅ Fully qualified
}
```

## Thread Safety

All PSI access is properly wrapped in `ReadAction.compute` to ensure thread safety when running in background tasks.

## Notifications

The module uses the notification group **"Gradle Plugin ID Fixer"** to report:

- Number of files scanned
- Number of replacements made
- Any errors encountered

## Requirements

- IntelliJ IDEA 2024.1+
- Kotlin plugin
- Gradle projects with `build-logic` convention plugins

## Integration

This module is part of the `gradle-buddy` plugin and is automatically available when the plugin is installed.
