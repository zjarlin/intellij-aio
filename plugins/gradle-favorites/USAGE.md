# Gradle Favorites 使用指南

## 快速开始

### 1. 安装插件

构建并安装插件后,右侧边栏会出现 "Gradle Favorites" 工具窗口(星形图标)。

### 2. 添加收藏任务

#### 方法一:通过工具窗口手动添加

1. 点击右侧边栏的 "Gradle Favorites" 工具窗口
2. 点击 "Add Favorite" 按钮
3. 输入模块路径,例如: `:lib:tool-psi`
4. 输入任务名称,例如: `kspKotlin`
5. 点击确定

#### 方法二:从 Gradle 面板添加(待实现)

1. 打开 Gradle 工具窗口
2. 找到目标任务
3. 右键选择 "Add to Favorites"

### 3. 执行收藏的任务

#### 方法一:在编辑器中通过上下文菜单

1. 打开目标模块下的任意文件 (如 `lib/tool-psi/src/...`)
2. 右键打开上下文菜单
3. 选择 "Gradle Favorites" 子菜单
4. 点击要执行的任务 (如 `kspKotlin`)

#### 方法二:通过工具窗口

1. 打开 "Gradle Favorites" 工具窗口
2. 选择要执行的任务
3. 点击 "Execute" 按钮

#### 方法三:通过通知气泡

1. 打开模块下的文件时会自动弹出通知
2. 如果该模块有收藏的任务,通知中会显示
3. 直接点击通知中的任务名执行

### 4. 管理收藏

- **删除收藏**: 在工具窗口选中任务,点击 "Remove" 按钮
- **查看所有收藏**: 打开 "Gradle Favorites" 工具窗口查看完整列表

## 示例场景

### 场景 1: KSP 代码生成

假设你正在开发 `lib/tool-psi` 模块,经常需要运行 KSP 生成代码:

1. 添加收藏: 模块路径 `:lib:tool-psi`, 任务 `kspKotlin`
2. 编辑代码时,右键菜单 → Gradle Favorites → kspKotlin
3. 代码生成完成

### 场景 2: 发布到 Maven

需要发布多个模块到 Maven:

1. 添加收藏: `:lib:tool-psi` → `publishToMavenLocal`
2. 添加收藏: `:lib:tool-database` → `publishToMavenLocal`
3. 在各模块文件中通过右键菜单快速发布

### 场景 3: 清理构建

添加常用的清理任务:

- `:` → `clean` (根项目清理)
- `:lib:tool-psi` → `cleanBuildCache`

## 模块路径格式

### 单模块项目
- 根项目: `:`

### 多模块项目
- 子模块: `:子模块名` (如 `:app`)
- 嵌套模块: `:父模块:子模块` (如 `:lib:tool-psi`)
- 深层嵌套: `:a:b:c:d`

### 自动匹配规则

当你在某个模块的文件中打开上下文菜单时,插件会:

1. 检测当前文件所属的模块
2. 查找该模块及其父模块的收藏任务
3. 只显示匹配的任务

例如:
- 文件位置: `lib/tool-psi/src/Main.kt`
- 检测到模块: `:lib:tool-psi`
- 显示任务:
  - `:lib:tool-psi:kspKotlin` ✅
  - `:lib:tool-psi:build` ✅
  - `:lib:tool-database:build` ❌ (不匹配)

## 通知提醒

插件会在以下情况显示通知气泡:

1. **首次打开模块文件**: 如果该模块有收藏任务,显示提醒
2. **每个模块仅提醒一次**: 避免频繁打扰
3. **通知内容**: 显示可用任务数量和任务列表
4. **交互式通知**: 可直接在通知中点击执行任务

## 数据持久化

收藏的任务保存在项目级别的配置文件中:

- 位置: `.idea/gradleFavorites.xml`
- 格式: XML
- 团队共享: 可以通过版本控制共享给团队成员

## 常见问题

### Q: 任务执行失败怎么办?

A: 检查以下几点:
1. 模块路径是否正确 (注意冒号 `:`)
2. 任务名称是否拼写正确
3. Gradle 项目是否已同步
4. 查看 IDE 的 Build 工具窗口获取详细错误信息

### Q: 编辑器菜单中看不到 "Gradle Favorites"?

A: 确认以下几点:
1. 当前模块是否有收藏的任务
2. 当前文件是否属于 Gradle 模块
3. 插件是否正确安装和启用

### Q: 通知气泡不显示?

A: 可能原因:
1. 该模块没有收藏任务
2. 已经在本次会话中显示过
3. 通知被系统禁用 (检查 IDE 通知设置)

### Q: 如何备份收藏列表?

A: 收藏列表保存在 `.idea/gradleFavorites.xml`,可以:
1. 复制该文件到其他项目
2. 提交到 Git 与团队共享
3. 手动编辑 XML 批量添加任务

## 高级用法

### 批量添加任务

可以直接编辑 `.idea/gradleFavorites.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleFavoritesService">
    <option name="favorites">
      <list>
        <FavoriteTaskData>
          <option name="projectPath" value=":lib:tool-psi" />
          <option name="taskName" value="kspKotlin" />
          <option name="displayName" value=":lib:tool-psi:kspKotlin" />
        </FavoriteTaskData>
        <FavoriteTaskData>
          <option name="projectPath" value=":lib:tool-psi" />
          <option name="taskName" value="publishToMavenLocal" />
          <option name="displayName" value=":lib:tool-psi:publishToMavenLocal" />
        </FavoriteTaskData>
      </list>
    </option>
  </component>
</project>
```

### 团队协作

将 `.idea/gradleFavorites.xml` 加入版本控制:

```bash
# .gitignore 中确保不排除该文件
# 如果 .idea/ 被忽略,可以添加例外:
!.idea/gradleFavorites.xml
```

团队成员拉取代码后会自动获得相同的收藏列表。

## 反馈与支持

如有问题或建议,请联系: zjarlin@outlook.com
