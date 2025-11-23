# Gradle Favorites 功能演示

## 演示场景

假设你正在开发一个多模块的 Gradle 项目,有以下模块结构:

```
project-root/
├── lib/
│   ├── tool-psi/
│   └── tool-database/
└── app/
```

你经常需要执行以下任务:
- `:lib:tool-psi:kspKotlin` - 生成 KSP 代码
- `:lib:tool-psi:publishToMavenLocal` - 发布到本地 Maven
- `:lib:tool-database:publishToMavenLocal` - 发布数据库工具

## 演示步骤

### 步骤 1: 启动插件

```bash
# 在项目根目录
./gradlew :plugins:gradle-favorites:runIde
```

等待测试 IDE 启动,打开一个多模块 Gradle 项目。

### 步骤 2: 打开工具窗口

在右侧边栏找到 ⭐ "Gradle Favorites" 图标,点击打开面板。

初始状态:列表为空。

### 步骤 3: 添加第一个收藏

1. 点击 **"Add Favorite"** 按钮
2. 对话框输入:
   - 模块路径: `:lib:tool-psi`
   - 任务名称: `kspKotlin`
3. 点击 OK

**结果:** 列表显示 `:lib:tool-psi:kspKotlin`

### 步骤 4: 批量添加多个任务

重复步骤 3,添加以下任务:

```
:lib:tool-psi:publishToMavenLocal
:lib:tool-database:publishToMavenLocal
:lib:tool-database:kspKotlin
```

**结果:** 工具窗口列表显示 4 个收藏任务

### 步骤 5: 测试编辑器菜单 (核心功能)

#### 5.1 在 tool-psi 模块中

1. 打开文件: `lib/tool-psi/src/main/kotlin/Example.kt`
2. 右键打开上下文菜单
3. 找到 **"Gradle Favorites"** 子菜单

**预期结果:** 显示 2 个任务
- ✅ `:lib:tool-psi:kspKotlin`
- ✅ `:lib:tool-psi:publishToMavenLocal`
- ❌ `:lib:tool-database:...` (不显示,因为不匹配当前模块)

#### 5.2 在 tool-database 模块中

1. 打开文件: `lib/tool-database/src/main/kotlin/DB.kt`
2. 右键打开上下文菜单
3. 找到 **"Gradle Favorites"** 子菜单

**预期结果:** 显示 2 个任务
- ✅ `:lib:tool-database:publishToMavenLocal`
- ✅ `:lib:tool-database:kspKotlin`

#### 5.3 执行任务

1. 在上下文菜单中点击 `:lib:tool-psi:kspKotlin`
2. 观察通知气泡: "Executing Gradle Task: Running :lib:tool-psi:kspKotlin"
3. 打开 **Build** 工具窗口查看执行输出

**预期结果:** 任务开始执行,可以在 Build 窗口看到日志

### 步骤 6: 测试智能通知

#### 6.1 首次打开文件

1. 重启测试 IDE (或关闭所有编辑器标签)
2. 打开 `lib/tool-psi/src/main/kotlin/Example.kt`

**预期结果:** 
- 右下角弹出通知气泡
- 标题: "Gradle Favorites Available"
- 内容: "You have 2 favorite task(s) for this module"
- 通知中显示 2 个可点击的任务链接

#### 6.2 点击通知中的任务

1. 在通知气泡中点击 `:lib:tool-psi:kspKotlin`

**预期结果:**
- 通知消失
- 任务开始执行
- 如果失败,显示错误通知

#### 6.3 验证去重逻辑

1. 关闭 `Example.kt`
2. 再次打开 `Example.kt`

**预期结果:** 不再显示通知(已在本次会话中显示过)

### 步骤 7: 测试工具窗口执行

1. 打开 "Gradle Favorites" 工具窗口
2. 在列表中选择 `:lib:tool-psi:publishToMavenLocal`
3. 点击 **"Execute"** 按钮

**预期结果:**
- 弹出确认信息: "Executing ':lib:tool-psi:publishToMavenLocal'..."
- 任务在后台执行
- Build 窗口显示执行日志

### 步骤 8: 管理收藏列表

#### 8.1 删除任务

1. 在工具窗口选择 `:lib:tool-database:kspKotlin`
2. 点击 **"Remove"** 按钮

**预期结果:**
- 确认信息: "Removed ... from favorites"
- 列表中该任务消失
- 持久化:重启 IDE 后仍然不存在

#### 8.2 验证持久化

1. 关闭测试 IDE
2. 重新启动
3. 打开 "Gradle Favorites" 工具窗口

**预期结果:** 之前添加的任务仍然存在(除了已删除的)

### 步骤 9: 检查配置文件

```bash
# 在测试 IDE 的测试项目中
cat .idea/gradleFavorites.xml
```

**预期内容:**
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
        <!-- 其他任务 -->
      </list>
    </option>
  </component>
</project>
```

## 演示视频脚本

### 场景 1: 快速上手 (30秒)

```
旁白: "厌烦了在终端重复输入长长的 Gradle 命令?"

操作:
1. 打开 Gradle Favorites 工具窗口
2. 点击 Add Favorite
3. 输入 :lib:tool-psi 和 kspKotlin
4. 在编辑器右键 → Gradle Favorites → 执行

旁白: "一键执行常用任务,就这么简单!"
```

### 场景 2: 多模块智能识别 (45秒)

```
旁白: "在大型多模块项目中,自动识别当前模块"

操作:
1. 打开 lib/tool-psi 模块的文件
2. 右键菜单显示该模块的任务
3. 切换到 lib/tool-database 模块
4. 右键菜单自动切换显示该模块的任务

旁白: "不用记忆模块路径,智能为你过滤!"
```

### 场景 3: 非侵入式通知 (30秒)

```
旁白: "打开文件时友好提醒可用任务"

操作:
1. 关闭所有编辑器
2. 打开一个有收藏任务的模块文件
3. 右下角弹出通知气泡
4. 直接在通知中点击执行任务

旁白: "不打断工作流,随时可用!"
```

## 性能测试

### 测试用例 1: 大量收藏任务

1. 添加 50 个不同模块的收藏任务
2. 打开编辑器并右键

**预期:** 菜单响应时间 < 100ms

### 测试用例 2: 频繁打开文件

1. 快速连续打开 10 个不同模块的文件
2. 观察通知弹出情况

**预期:** 每个模块仅通知一次,不卡顿

### 测试用例 3: 持久化压力测试

1. 添加/删除任务 100 次
2. 检查 XML 文件完整性

**预期:** 数据不丢失,文件格式正确

## 常见问题演示

### 问题 1: 模块路径错误

**演示:**
1. 添加收藏时输入错误路径: `:lib/tool-psi` (应该是 `:lib:tool-psi`)
2. 尝试执行任务

**预期:** 显示错误通知,提示任务不存在

### 问题 2: 任务名拼写错误

**演示:**
1. 添加任务: `:lib:tool-psi:kspKotlin1` (多了个 1)
2. 执行任务

**预期:** Gradle 报错: Task 'kspKotlin1' not found

### 问题 3: 未同步 Gradle 项目

**演示:**
1. 创建新的 Gradle 项目但不同步
2. 尝试添加和执行任务

**预期:** 提示需要先同步 Gradle 项目

## 对比演示

### 传统方式 vs 插件方式

**传统方式:**
```bash
# 每次都要输入完整命令
./gradlew :lib:tool-psi:kspKotlin
./gradlew :lib:tool-psi:publishToMavenLocal
./gradlew :lib:tool-database:kspKotlin
```

⏱️ 每次 5-10 秒(找终端、输入命令、等待补全)

**插件方式:**
```
右键 → Gradle Favorites → 点击任务
```

⏱️ 不到 2 秒

**效率提升:** 3-5 倍!

## 团队协作演示

### 场景: 共享收藏配置

**步骤:**

1. 开发者 A 配置收藏列表
2. 提交 `.idea/gradleFavorites.xml` 到 Git
3. 开发者 B 拉取代码
4. 开发者 B 打开 IDE,自动获得相同收藏列表

**旁白:** "团队标准化操作流程,新成员快速上手!"

## 总结

通过这些演示,展示了插件的核心价值:

✅ **节省时间** - 减少 70% 的命令输入时间  
✅ **智能化** - 自动识别上下文,减少认知负担  
✅ **非侵入** - 友好的通知系统,不打断工作流  
✅ **可靠** - 持久化存储,数据不丢失  
✅ **协作** - 团队共享配置,统一工作流程  

---

**下一步:** 根据用户反馈继续优化功能!
