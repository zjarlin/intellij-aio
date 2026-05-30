# Split Module

> Refactor JVM modules by splitting selected files out or merging selected module roots back together.

## Features

- **Right-click to split**: Select files in Project View, right-click, and choose "Split Module"
- **Right-click to merge**: Select module root directories in Project View, right-click, and choose "Merge Modules"
- **Automatic sibling module creation**: New module is created at the same level as the source module
- **Existing module merge**: If the target sibling module already exists, the plugin merges selected files into it instead of failing immediately
- **Main module selection**: Choose one selected module as the merge target, with `az-compose` preferred when present
- **Package relocation**: Confirm the common base package, then move source files under `{basePackage}.{module_package_segment}`
- **Dependency fusion**: Merge Gradle dependency statements into the main module, remove dependencies to merged modules, and deduplicate project references
- **Background merge task**: Long-running package scans, moves, and reference rewrites run with IDE task progress instead of blocking the UI
- **Root README preservation**: Source module root `README*` files stay in their original module directory instead of being moved into `merged-modules`
- **Multi-build system support**: Supports **Maven** (`pom.xml`), **Gradle Kotlin DSL** (`build.gradle.kts`), and **Gradle Groovy DSL** (`build.gradle`)
- **Smart dependency injection**: Automatically adds the new module as a dependency in the source module using the appropriate syntax
- **Structure preservation**: Maintains file paths and package hierarchy (e.g., `src/main/kotlin/...`)
- **Conflict prompts**: During merge, conflicting target items are handled one by one with overwrite or skip choices

## Usage

1. **Select files** in the Project View that you want to split into a new module
2. **Right-click** and select **"Split Module"**
3. **Enter a name** for the new module
   - If a leaf package is selected, the default becomes `{source-module}-{leaf-package}`
   - Otherwise it falls back to `{source-module}1`
4. **Click OK** - the plugin will:
   - Create a new sibling module directory, or merge into an existing sibling module with the same name
   - Copy `build.gradle.kts` from the source module
   - Move selected files to the new module
   - Add `implementation(project(":new-module"))` to source module

### Merge modules

1. **Select module root directories** in the Project View, such as `az-compose`, `compose-native-component-button`, and `compose-native-component-card`
2. **Right-click** and select **"Merge Modules"**
3. **Choose the main module** and confirm the inferred common base package
4. **Click OK** - the plugin will:
   - Move every source module into the selected main module
   - Rewrite package declarations to `{basePackage}.{sanitized-module-name}[.{old-package-suffix}]`
   - Remove the shared prefix between the old package and confirmed base package before appending the old package suffix
   - Update imports and fully qualified references for detected top-level Kotlin/Java symbols
   - Merge Gradle dependencies into the main module and remove direct dependencies to merged modules
   - Delete fully merged source module contents after the move, while keeping root `README*` files in place

## Example

**Before:**
```
project/
├── module-a/
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── FeatureA.kt
│       └── FeatureB.kt
```

**After splitting `FeatureB.kt` into `module-b`:**
```
project/
├── module-a/
│   ├── build.gradle.kts  (now depends on module-b)
│   └── src/main/kotlin/
│       └── FeatureA.kt
├── module-b/
│   ├── build.gradle.kts  (copied from module-a)
│   └── src/main/kotlin/
│       └── FeatureB.kt
```

## Requirements

- IntelliJ IDEA 2024.2+
- Maven project OR Gradle project (Kotlin/Groovy DSL)
- Split action: all selected files must be from the same module
- Merge action: all selected items must be module root directories or module build files

## Limitations

- Split copies all dependencies from source module (manual cleanup may still be required)
- Merge dependency fusion is text-based for Gradle build files; complex custom build logic may still need review
- Merge updates direct imports and fully qualified references for detected top-level JVM symbols; ambiguous duplicate symbols are reported for manual checking
- Module registration in `settings.gradle.kts` is handled by external plugins

## License

MIT
