# Cloud File Hosting

IntelliJ IDEA 插件，用于将项目文件托管到云存储（S3/OSS），而非提交到 Git。

## 功能特性

### 三种托管模式

1. **全局托管**：对所有 IDEA 打开的项目生效
   - 在全局设置中配置
   - 适用于跨项目的共享配置（如 `build-logic/`, `.idea/` 等）
   - 无条件覆盖到以项目根路径为基准的相对路径

2. **项目托管**：以项目名为命名空间
   - 命名空间与项目名称一致
   - 同步内容为「全局托管」+「项目托管」的合并
   - 每个项目独立管理

3. **自定义托管**：基于 Git 作者和项目名称规则
   - 支持 Git 作者匹配（如包含 `zjarlin`）
   - 支持项目名称包含匹配
   - 自动应用到符合条件的项目

### 存储支持

- **S3 协议**：AWS S3、MinIO、Cloudflare R2 等
- **阿里云 OSS**

### 同步策略

- **远程为准**：S3/OSS 上的内容不允许手动修改，所有内容以远程为准
- **自动同步**：文件变更后自动同步到云端（可配置）
- **后台同步**：定期后台同步（可配置间隔）
- **启动时同步**：项目打开时自动从云端拉取最新内容

## 使用方法

### 1. 配置存储

Settings → Cloud File Hosting → Storage

配置 S3 或 OSS 的连接信息，支持：
- Endpoint、Region、Bucket
- Access Key / Secret Key（安全存储）
- 连接测试

### 2. 配置托管规则

#### 全局规则
Settings → Cloud File Hosting → Global Rules

添加需要托管的文件/目录模式：
- `build-logic/` - 目录模式
- `.idea/inspectionProfiles/` - 目录模式
- `*.local.properties` - 通配符模式

#### 项目规则
在项目设置中配置，或右键项目文件 → "Add to Cloud Hosting"

#### 自定义规则
Settings → Cloud File Hosting → Custom Rules

基于 Git 作者和项目名称的条件规则：
- Git Author: `zjarlin`
- Project Name: `vibepocket`

### 3. 手动同步

- 右键项目视图 → Sync to Cloud / Sync from Cloud
- 工具窗口 Cloud Hosting → 同步按钮

## 防御性编程特性

- **凭证安全**：所有密钥使用 IntelliJ PasswordSafe 加密存储
- **本地备份**：从云端下载前自动备份本地文件
- **Git 集成**：自动识别 Git 追踪的文件，避免冲突
- **变更检测**：使用 CRC32/SHA-256 检测文件变更
- **日志审计**：详细的同步日志记录

## 文件结构

```
cloud://bucket/
├── cloudfile/
│   ├── global/          # 全局托管文件
│   │   └── build-logic/
│   └── {project-name}/  # 项目托管文件
│       └── .idea/
```

## 注意事项

1. 远程存储为**只读**（从 IDE 视角），所有修改应通过插件进行
2. 首次启用时会自动从云端拉取文件覆盖本地
3. `.gitignore` 建议添加 `.cloud-file-hosting/backups/`

## 快捷键

- 暂无默认快捷键，可在 Keymap 中自定义
