# 资源文件位置修复

## 问题

意图操作没有显示，原因是资源文件放在了错误的位置。

## 原因

IntelliJ 插件的资源文件（intentionDescriptions）需要放在**主模块**的 `src/main/resources` 目录下，而不是子模块中。

## 解决方案

将所有 intentionDescriptions 从子模块复制到主模块：

```bash
# 从
plugins/gradle-buddy/gradle-buddy-fix-catalog-ref/src/main/resources/intentionDescriptions/

# 到
plugins/gradle-buddy/src/main/resources/intentionDescriptions/
```

## 已创建的资源文件

### 1. SelectCatalogReferenceIntentionGroup
- `description.html` - 意图操作描述
- `before.gradle.kts.template` - 修复前的代码示例
- `after.gradle.kts.template` - 修复后的代码示例

### 2. BrowseCatalogAlternativesIntention
- `description.html` - 意图操作描述
- `before.gradle.kts.template` - 使用前的代码示例
- `after.gradle.kts.template` - 使用后的代码示例

### 3. FixCatalogReferenceIntention (已存在)
- `description.html`
- `before.gradle.kts.template`
- `after.gradle.kts.template`

## 验证

运行以下命令验证资源文件位置：

```bash
ls -la plugins/gradle-buddy/src/main/resources/intentionDescriptions/
```

应该看到：
- BrowseCatalogAlternativesIntention/
- FixCatalogReferenceIntention/
- SelectCatalogReferenceIntentionGroup/

## 测试

1. 重新构建插件：`./gradlew build`
2. 运行测试 IDE：`./gradlew runIde`
3. 在 `.gradle.kts` 文件中测试意图操作：
   - 无效引用：按 `Alt+Enter` 应该看到 "选择正确的版本目录引用"
   - 有效引用：按 `Alt+Enter` 应该看到 "浏览其他版本目录引用"

## 注意事项

- 子模块中的资源文件可以保留（作为备份），但不会被使用
- 主模块的资源文件才会被打包到最终的插件中
- 如果修改了子模块的资源文件，记得同步到主模块
