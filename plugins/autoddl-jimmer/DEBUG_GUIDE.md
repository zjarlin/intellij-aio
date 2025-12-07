# AutoDDL Jimmer 调试指南

## 问题1: 未找到 Jimmer 实体类

### 原因分析

1. **扫描包路径配置问题**
   - 默认扫描整个项目（scanPackages 为空）
   - 如果配置了包路径，确保实体类在配置的包下

2. **依赖缺失**
   - 项目中未添加 jimmer-sql 依赖
   - @Entity 注解无法被识别

3. **注解类型不匹配**
   - 支持的注解：
     - `org.babyfish.jimmer.sql.Entity` (Jimmer)
     - `javax.persistence.Entity` (JPA)

### 解决方案

#### 1. 检查项目依赖

确保 `build.gradle.kts` 或 `pom.xml` 中包含：

**Gradle:**
```kotlin
dependencies {
    implementation("org.babyfish.jimmer:jimmer-sql:0.8.x")
    // 或 JPA 依赖
    implementation("javax.persistence:javax.persistence-api:2.2")
}
```

**Maven:**
```xml
<dependency>
    <groupId>org.babyfish.jimmer</groupId>
    <artifactId>jimmer-sql</artifactId>
    <version>0.8.x</version>
</dependency>
```

#### 2. 确认实体类注解

**Jimmer 实体示例：**
```kotlin
import org.babyfish.jimmer.sql.*

@Entity
interface Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long
    
    val name: String
    val price: BigDecimal
}
```

**JPA 实体示例：**
```java
import javax.persistence.*;

@Entity
@Table(name = "books")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private BigDecimal price;
}
```

#### 3. 配置扫描包路径

**Settings → Tools → AutoDDL Jimmer → 扫描包路径**

选项：
- **留空**（推荐）：扫描整个项目
- **指定包路径**：`com.example.entity,com.example.domain`

#### 4. 查看日志

执行 Generate Delta DDL 后，查看 **Jimmer DDL** 工具窗口：

```
[2023-12-07 18:00:00] [INFO] [SUCCESS] 开始扫描项目中的 Jimmer 实体类...
[2023-12-07 18:00:00] [INFO] [SUCCESS] 扫描包路径：
[2023-12-07 18:00:01] [INFO] [SUCCESS] 扫描完成，找到 0 个实体类
[2023-12-07 18:00:01] [ERROR] [FAILED] 未找到 Jimmer 实体类
```

如果实体数为 0，检查：
1. 项目是否已编译
2. 索引是否已完成（File → Invalidate Caches / Restart）
3. 依赖是否正确添加

## 问题2: 日志面板不起作用

### 原因分析

1. **工具窗口未打开**
   - 首次使用需要手动打开工具窗口

2. **工具窗口未初始化**
   - plugin.xml 配置问题
   - 服务未正确注册

### 解决方案

#### 1. 手动打开工具窗口

**View → Tool Windows → Jimmer DDL**

或点击 IDE 底部的 **Jimmer DDL** 标签。

#### 2. 自动激活

现在执行 Generate Delta DDL 时会自动激活工具窗口。

#### 3. 验证工具窗口

打开后应该看到：

```
┌─────────────────────────────────────────────────────────┐
│ [清空日志] [导出日志]  总计: 0  成功: 0  失败: 0        │
├───────────┬────────┬────────┬─────────────┬────────────┤
│ 时间      │ 类型   │ 状态   │ SQL/消息    │ 详情       │
├───────────┴────────┴────────┴─────────────┴────────────┤
│ (日志行将显示在这里)                                    │
└─────────────────────────────────────────────────────────┘
```

## 完整调试流程

### 步骤 1: 打开工具窗口

**View → Tool Windows → Jimmer DDL**

### 步骤 2: 配置设置（可选）

**Settings → Tools → AutoDDL Jimmer**

- DDL输出目录：`.autoddl/jimmer`
- 扫描包路径：留空或指定包路径
- 包含索引：✓
- 包含外键：✓

### 步骤 3: 执行生成

**右键项目 → Generate Delta DDL**

### 步骤 4: 查看日志

在 **Jimmer DDL** 工具窗口查看执行日志：

**成功示例：**
```
[18:00:00] [INFO] [SUCCESS] 开始扫描项目中的 Jimmer 实体类...
[18:00:00] [INFO] [SUCCESS] 扫描包路径：
[18:00:01] [INFO] [SUCCESS] 扫描完成，找到 5 个实体类
[18:00:01] [INFO] [SUCCESS]   - com.example.entity.Book
[18:00:01] [INFO] [SUCCESS]   - com.example.entity.Author
[18:00:01] [INFO] [SUCCESS]   - com.example.entity.Publisher
[18:00:01] [INFO] [SUCCESS]   - com.example.entity.Category
[18:00:01] [INFO] [SUCCESS]   - com.example.entity.Review
[18:00:01] [GENERATE] [RUNNING] 开始生成差量DDL，共 5 个实体
[18:00:02] [GENERATE] [SUCCESS] DDL生成完成，共 12 条语句
                                 /path/to/project/.autoddl/jimmer/delta_20231207_180002.sql
```

### 步骤 5: 查看生成的 DDL

打开 `.autoddl/jimmer/delta_yyyyMMdd_HHmmss.sql` 文件。

## 常见问题

### Q1: 扫描完成但找到 0 个实体

**检查清单：**
- [ ] 项目已编译
- [ ] 依赖已添加
- [ ] @Entity 注解包路径正确
- [ ] 索引已完成（File → Invalidate Caches）

### Q2: 工具窗口不显示

**解决方案：**
1. 重启 IDE
2. File → Invalidate Caches / Restart
3. 检查 plugin.xml 配置

### Q3: 生成的 DDL 为空

**可能原因：**
- 实体类没有字段
- 所有字段都标记为 @Transient
- toCompleteSchemaDDL() 扩展函数异常

### Q4: SQL 执行失败

**检查：**
- Database Tools 插件已安装
- 数据源已配置
- 数据源名称正确
- 数据库连接正常

## 调试技巧

### 1. 启用详细日志

在 Action 执行前添加：

```kotlin
logPanel.logInfo("开始扫描...")
logPanel.logInfo("配置信息：scanPackages = ${settings.scanPackages}")
```

### 2. 检查 PSI 索引

确保项目已完成索引：

```
File → Project Structure → Project Settings → Modules
```

验证源码目录正确标记。

### 3. 手动测试扫描

在 Kotlin REPL 或测试代码中：

```kotlin
val project = ...
val scope = GlobalSearchScope.projectScope(project)
val javaPsiFacade = JavaPsiFacade.getInstance(project)
val annotationClass = javaPsiFacade.findClass("org.babyfish.jimmer.sql.Entity", scope)
println("Found annotation class: $annotationClass")

val classes = AnnotatedElementsSearch.searchPsiClasses(annotationClass, scope).findAll()
println("Found ${classes.size} entities")
classes.forEach { println("  - ${it.qualifiedName}") }
```

## 获取帮助

如果问题仍然存在：

1. **收集信息：**
   - IDE 版本
   - 插件版本
   - 项目类型（Java/Kotlin/Mixed）
   - 依赖版本（jimmer-sql 版本）
   - 完整错误日志

2. **导出日志：**
   - 点击工具窗口的 **[导出日志]** 按钮
   - 保存日志文件

3. **提交 Issue：**
   - GitHub/Gitee Issue
   - 附上日志文件和项目配置
