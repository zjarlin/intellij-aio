# `kcloud` is the initial IDEA plugin version for KCloud.



Current scope in this first IDEA plugin version:

- adds a gutter icon on generated Kotlin API declarations
- reads the KDoc source hint like `原始文件: site.addzero.kcloud.vibepocket.routes.Favorite.kt`
- jumps from the generated declaration back to the original Kotlin source file
- provides a local KCloud config-center tool window backed by SQLite
- provides IDE settings for the shared SQLite storage path
- provides a Kotlin/JVM Alt+Enter intention that extracts property initializer literals into the KCloud config center
- provides a dedicated Kotlin/JVM Alt+Enter intention that rewrites hardcoded KCloud local endpoint URLs to `KCloudLocalServerEndpoint.currentBaseUrl()`

This is still the initial IDEA plugin release. The scope is intentionally focused and not the full KCloud IDE toolset yet.

## Current behavior

When a Kotlin declaration contains a KDoc source reference, `kcloud` shows a gutter icon next to the declaration name. Clicking the icon navigates to the referenced source file inside the current IntelliJ project.

Open `View -> Tool Windows -> KCloud Config` to manage key/value entries by project namespace and profile. The storage schema follows the same entry/profile/namespace/storage-mode model used by the KCloud config-center runtime.

In Kotlin/JVM source files, place the caret on a property initializer literal and use `Alt+Enter -> 提取到 KCloud 配置中心`. The plugin stores the value in SQLite and replaces the literal with a JVM-safe `System.getProperty(...) ?: default` expression.

For KCloud desktop local endpoint constants such as `http://localhost:18080/`, use `Alt+Enter -> 替换为 KCloud 本地端点`. The plugin rewrites the literal to `KCloudLocalServerEndpoint.currentBaseUrl()` and removes `const` when required so the declaration stays valid.

## Current limitations

- target resolution is file-based, not symbol-based
- the source hint must follow the `原始文件: <qualified.file.Name>.kt` format
- only the current project scope is searched
- literal extraction is currently limited to Kotlin/JVM property initializers
- local endpoint replacement is intentionally limited to the KCloud desktop loopback URLs on Kotlin/JVM property initializers
- the GUI currently focuses on config entries and does not yet expose render targets

## Development

Build the plugin from the IntelliJ workspace root:

```bash
./gradlew :plugins:kcloud:buildPlugin
```
