# ShitCode Plugin 迁移说明

## 迁移概览

ShitCode 插件已从 AutoDDL 插件中独立出来，成为一个独立的 IntelliJ IDEA 插件模块。

## 迁移内容

### 1. 代码迁移

- **原路径**: `plugins/autoddl/src/main/kotlin/site/addzero/addl/toolwindow/ShitCodeToolWindow.kt`
- **新路径**: `plugins/shitcode/src/main/kotlin/site/addzero/shitcode/toolwindow/ShitCodeToolWindow.kt`

### 2. 设置系统重构

创建了独立的设置系统：

- `ShitCodeSettings.kt` - 设置数据类
- `ShitCodeSettingsService.kt` - 设置服务
- `ShitCodeConfigurable.kt` - 设置 UI 配置

**原依赖**: 依赖 `lib/ide-component-settings-old` 模块中的 `SettingContext.settings.shitAnnotation`  
**新实现**: 使用独立的 `ShitCodeSettingsService.getInstance().state.shitAnnotation`

### 3. Plugin.xml 配置

从 AutoDDL 的 plugin.xml 中移除了以下内容：

```xml
<!-- 移除的配置 -->
<toolWindow id="ShitCode"
            anchor="right"
            factoryClass="site.addzero.addl.toolwindow.ShitCodeToolWindow"
            icon="AllIcons.General.Remove"
            secondary="false"
            order="last"/>
```

在新插件中添加了完整的配置：

```xml
<!-- 新插件配置 -->
<id>site.addzero.shitcode</id>
<name>ShitCode</name>
<applicationService serviceImplementation="site.addzero.shitcode.settings.ShitCodeSettingsService"/>
<projectConfigurable instance="site.addzero.shitcode.settings.ShitCodeConfigurable" displayName="ShitCode" parentId="tools"/>
<toolWindow id="ShitCode" ... />
```

### 4. 构建配置

创建了独立的 `build.gradle.kts`：

```kotlin
plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    // IntelliJ Platform 依赖由插件自动添加
}
```

### 5. 包结构变化

| 组件 | 原包名 | 新包名 |
|------|--------|--------|
| 工具窗口 | `site.addzero.addl.toolwindow` | `site.addzero.shitcode.toolwindow` |
| 设置 | `site.addzero.addl.settings` (复用) | `site.addzero.shitcode.settings` |

## 代码变更

### ShitCodeToolWindow 主要变更

1. **包名更新**
   ```kotlin
   // 原包名
   package site.addzero.addl.toolwindow
   
   // 新包名
   package site.addzero.shitcode.toolwindow
   ```

2. **设置获取方式更新**
   ```kotlin
   // 原方式
   import site.addzero.addl.settings.SettingContext
   val annotationName = SettingContext.settings.shitAnnotation
   
   // 新方式
   import site.addzero.shitcode.settings.ShitCodeSettingsService
   val annotationName = ShitCodeSettingsService.getInstance().state.shitAnnotation
   ```

3. **增强的 ElementInfo**
   - 添加了对 Java 元素的支持（PsiClass、PsiMethod、PsiField）
   - 改进了 toString() 方法，更好地显示元素信息

## 使用影响

### 对现有用户

如果您之前使用的是 AutoDDL 插件中的 ShitCode 功能：

1. **数据迁移**: 需要在新插件的设置中重新配置注解名称（默认仍为 "Shit"）
2. **配置位置**: 设置位置从 `Settings → AutoDDL设置` 变更为 `Settings → Tools → ShitCode`
3. **功能不变**: 工具窗口的所有功能保持不变

### 独立安装

现在可以单独安装 ShitCode 插件，无需安装 AutoDDL 插件：

```bash
# 构建插件
./gradlew :plugins:shitcode:buildPlugin

# 运行测试
./gradlew :plugins:shitcode:runIde
```

## 优势

1. **独立性**: 可以单独使用和分发
2. **轻量级**: 不依赖 AutoDDL 的其他功能
3. **易维护**: 代码结构更清晰，职责单一
4. **可复用**: 可以在其他项目中独立使用

## 向后兼容

⚠️ **注意**: 此迁移涉及包名变更，不提供向后兼容。

如果您的代码中有对原 ShitCodeToolWindow 的引用，需要更新导入路径。

## 构建和测试

```bash
# 构建 shitcode 插件
./gradlew :plugins:shitcode:build

# 运行 shitcode 插件测试 IDE
./gradlew :plugins:shitcode:runIde

# 构建发布包
./gradlew :plugins:shitcode:buildPlugin
```

插件发布包位于：`plugins/shitcode/build/distributions/shitcode-*.zip`

## 未来计划

- [ ] 添加更多代码质量检查规则
- [ ] 支持自定义代码扫描规则
- [ ] 集成代码静态分析工具
- [ ] 提供重构建议

## 问题反馈

如遇到问题，请提交 Issue 到项目仓库：  
https://gitee.com/zjarlin/autoddl-idea-plugin/issues
