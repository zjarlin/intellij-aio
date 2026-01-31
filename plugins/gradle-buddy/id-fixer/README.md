# Gradle Plugin ID Fixer

A module for fixing plugin ID references in Gradle Kotlin scripts with a project-wide candidate picker for local precompiled script plugins.

## Problem

When you define a precompiled script plugin in `src/main/kotlin/com/example/my-plugin.gradle.kts`, Gradle requires you to reference it using the fully qualified ID:

```kotlin
plugins {
    id("com.example.my-plugin")  // ✅ Correct
}
```

In monorepos or composite builds, developers often use the short ID or a partially-qualified ID by mistake:

```kotlin
plugins {
    id("my-plugin")  // ❌ Wrong - will fail at runtime
}
```

This causes build failures or mismatched plugin resolution that are hard to debug.

## Solution

This module provides automated tools to detect and fix these issues:

### 1. Intention Action (Alt+Enter)

When your cursor is on a short plugin ID, press `Alt+Enter` to see the quick fix:

- **"Fix build script reference"** - Opens a candidate list (when multiple matches exist), sorted by similarity, then replaces all occurrences of the selected ID throughout the entire project

### 2. Bulk Action

Use the action **"Fix All Plugin IDs in Project"** to:

1. Scan all `build-logic` directories
2. Find all precompiled script plugins
3. Detect all short ID references in the project
4. Replace them with fully qualified IDs

## Features

- ✅ Candidate picker with similarity ranking across project `*.gradle.kts` files
- ✅ Keyword extraction from plugin IDs (package stripped) for matching
- ✅ Weak keyword filtering (for example, "convention" alone does not qualify a candidate)
- ✅ Project-wide replacement with a single action
- ✅ Thread-safe PSI access with proper read actions
- ✅ Progress indicators for long-running operations
- ✅ Notifications with detailed results

## How It Works

1. **Scanner** - Collects all `*.gradle.kts` files in the project to build candidate plugin IDs
2. **Parser** - Extracts package declarations to build fully qualified IDs
3. **Matcher** - Strips package from the current ID, extracts keywords, and ranks candidates by similarity
4. **Detector** - Finds all `id("...")` calls in `plugins {}` blocks
5. **Replacer** - Performs safe, atomic replacements across all files

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
// src/main/kotlin/com/example/conventions/java-library.gradle.kts
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
- Gradle projects with precompiled script plugins (`*.gradle.kts`)
- Note: the bulk action still targets `build-logic` directories

## Integration

This module is part of the `gradle-buddy` plugin and is automatically available when the plugin is installed.
