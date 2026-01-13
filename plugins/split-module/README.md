# Split Module

> Quickly split files from a Gradle module into a new sibling module

## Features

- **Right-click to split**: Select files in Project View, right-click, and choose "Split Module"
- **Automatic sibling module creation**: New module is created at the same level as the source module
- **Configuration copy**: Automatically copies `build.gradle.kts` from source module
- **Dependency injection**: Automatically adds the new module as a dependency in the source module
- **Structure preservation**: Maintains file paths and package hierarchy

## Usage

1. **Select files** in the Project View that you want to split into a new module
2. **Right-click** and select **"Split Module"**
3. **Enter a name** for the new module (default: `{source-module}1`)
4. **Click OK** - the plugin will:
   - Create a new sibling module directory
   - Copy `build.gradle.kts` from the source module
   - Move selected files to the new module
   - Add `implementation(project(":new-module"))` to source module

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
- Gradle project with Kotlin DSL (`build.gradle.kts`)
- All selected files must be from the same Gradle module

## Limitations

- Only supports Gradle Kotlin DSL (not Groovy DSL or Maven)
- Copies all dependencies from source module (manual cleanup required)
- Module registration in `settings.gradle.kts` is handled by external plugins

## License

MIT
