# Provides basic Gitee integration functions for IntelliJ IDEA / JetBrains IDE: share projects to Gitee within the IDE, create/view Pull Requests, sync Forks, clone repositories from Gitee, and provide an entry for configuring Access Tokens.

为 IntelliJ IDEA / JetBrains IDE 提供基础的 Gitee 集成功能：在 IDE 内分享项目到 Gitee、创建/查看 Pull Request、同步 Fork、从 Gitee 克隆仓库，并提供访问令牌（Access Token）配置入口。

## 功能

- Share Project on Gitee：将当前本地项目推送到 Gitee 并创建仓库
- Create Pull Request：从 IDE 发起 Gitee Pull Request
- View Pull Requests：打开当前仓库的 PR 列表页
- Sync Fork：将 fork 仓库与 upstream 同步
- Clone from Gitee：从 Gitee 克隆仓库
- Account Management：在 Settings 中管理 Access Token/默认可见性等

## 使用方法

1. 生成 Access Token：打开 `https://gitee.com/profile/personal_access_tokens`
2. 在 IDE 中配置：`Settings/Preferences → Tools → Gitee`
3. 在菜单中使用：`Tools → Gitee`（或在对应动作搜索里输入 Gitee）

## 配置项说明

位置：`Settings/Preferences → Tools → Gitee`

- `Access Token`：访问令牌（建议仅授予必要权限；仓库相关操作通常需要 `repos` scope）
- `Username`：你的 Gitee 用户名（可选）
- `Default Visibility`：分享项目时新建仓库的默认可见性（`private` / `public`）

## 开发与构建（本仓库）

该插件是本仓库的一个子模块，路径：`plugins/gitee`。

- 入口描述：`plugins/gitee/src/main/resources/META-INF/plugin.xml`
- 主要代码：`plugins/gitee/src/main/kotlin/site/addzero/gitee`
- 依赖：使用 `OkHttp` 调用 Gitee API，依赖 IDE 的 `Git4Idea` 插件能力

一般情况下直接在项目根目录用 Gradle 运行/调试 IntelliJ 插件即可（以本仓库既有的 Gradle 配置为准）。

## 常见问题

- 提示未配置 Token：先到 `Settings/Preferences → Tools → Gitee` 填写 `Access Token`
- 找不到 Gitee 相关菜单：检查是否启用插件；或用 `Find Action` 搜索 `Gitee`
- “No Gitee remote found”：当前 Git 仓库没有配置 `gitee.com` 的 remote（例如只配了 GitHub）

## 安全提示

Access Token 等同于账号授权凭证，请勿提交到仓库、截图公开或发给不可信的人；建议定期轮换 Token，并尽量只授予必需权限。
