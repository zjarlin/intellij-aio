# Gradle Module Sleep: Smart On-Demand Module Loading for Large Projects

Gradle Module Sleep supercharges your IDE performance by intelligently loading only the Gradle modules you are actively working on. Say goodbye to long sync times and high memory usage in large multi-module projects.


---

### ？ (Do you face these problems?)

1.  **Gradle Sync is painfully slow** 🐌
    -   A project with 50+ modules takes 5-10 minutes to sync.
    -   Changing one line of code means a long wait for Gradle to re-index.

2.  **IDE memory usage is through the roof** 💥
    -   IntelliJ IDEA consumes 8GB+ of memory, causing your computer's fan to spin wildly.
    -   Other applications become sluggish after opening a large project.

3.  **Most modules are not even used** 😤
    -   Out of 100 modules, you might only be working on 3-5 at any given time.
    -   But the IDE loads all of them, wasting resources.

4.  **Manually managing `settings.gradle.kts` is a nightmare** 😩
    -   Commenting out unused modules? That leads to merge conflicts with your team.
    -   Different team members need different sets of active modules.

## (The Solution)

**Gradle Module Sleep** solves these problems with an on-demand loading strategy:

| Traditional Approach | With Gradle Module Sleep |
|---------------------|--------------------------|
| Load all 100 modules | Load only the 5 you use |
| Sync takes 10 minutes | Sync takes 30 seconds |
| Memory usage: 8GB | Memory usage: 2GB |
| Manual `settings.gradle.kts` edits | Fully automatic, based on open files |

**The principle is simple**: The plugin loads only the modules for the files you have open, including their recursive dependencies.

## (Features)

-   **On-Demand Loading**: Automatically loads modules corresponding to your open editor tabs. Unused modules are not loaded.
-   **Recursive Dependency Detection**: Intelligently analyzes and loads all transitive project dependencies (`implementation(project(":..."))`) to ensure your project compiles successfully.
-   **Editor-based Controls**: A convenient notification banner appears on `settings.gradle.kts` and `build.gradle.kts` files for quick access to sleep/wake actions.
-   **Smart Exclusion**: Build-related modules like `build-logic` and `buildSrc` are automatically ignored to prevent build issues.
-   **(Experimental) Auto-Sleep**: Automatically unloads modules that have been idle to conserve memory.
-   **Smart Toggle**: Module sleeping is enabled by default for projects with 30+ modules, but you can configure this.

## (How to Use)

Once the plugin is installed, it works automatically. For manual control, open a `settings.gradle.kts` or `build.gradle.kts` file to see the control banner at the top of the editor.

A control banner will appear at the top of your `settings.gradle.kts` or `build.gradle.kts` file with the following actions:

-   **Sleep other modules**: Unloads all modules except those related to your currently open files.
-   **Sleep other modules (keep this tab module only)**: Closes all other editor tabs and then unloads all modules except for the one related to your currently active file. This is perfect for focusing on a single task.
-   **Restore modules**: Reloads all modules in the project.
-   **Close**: Hides the banner for the current file until the project is reopened.

## (Recursive Dependency Detection)

A key feature is the intelligent analysis of your module graph. When you open a file, the plugin:

1.  Detects the module the file belongs to (e.g., `:plugins:autoddl`).
2.  Parses that module's `build.gradle.kts` file.
3.  Extracts all `project` dependencies.
4.  Recursively processes each dependency to build a complete dependency tree.
5.  Applies this list of active modules to your `settings.gradle.kts`.

This ensures that even with only a few files open, your project remains fully compilable and navigable.

### Supported Dependency Formats

#### 1. Standard `project()` syntax
```kotlin
dependencies {
    implementation(project(":lib:tool-swing"))
}
```

#### 2. Type-safe Project Accessors syntax
```kotlin
dependencies {
    implementation(projects.lib.toolSwing)
}
```


## Tips

For small projects where you want all modules loaded, you can use the author's other plugin
```kotlin
// add in your settings.gradle.kts
plugins {
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
}
autoModules {
    excludeModules = arrayOf("jackson", "script","pp", "lsi-ksp")
}

```
