# DDL 路径模板功能说明

## 概述

DDL 保存路径现在支持模板变量，可以更灵活地定义保存目录结构。

## 路径模板变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `{projectDir}` | 项目根目录的绝对路径 | `/Users/username/my-project` |
| `{entityName}` | 实体名称（取自第一个 POJO 的简单类名） | `User`、`Order`、`Product` |

## 默认模板

```
{projectDir}/.autoddl/{entityName}
```

这个默认值会创建如下目录结构：
- `{projectDir}/.autoddl/User/`
- `{projectDir}/.autoddl/Order/`
- `{projectDir}/.autoddl/Product/`

## 自定义模板示例

### 1. 按模块组织
```
{projectDir}/src/main/resources/db/migration/{entityName}
```

### 2. 按日期组织
```
{projectDir}/ddl/{entityName}/2025-12
```

### 3. 自定义前缀
```
{projectDir}/autoddl-v2/{entityName}
```

### 4. 使用固定路径（不使用变量）
```
/Users/username/ddl
```

## 注意事项

1. **目录选择器限制**：通过目录选择器选择的路径不支持变量替换
2. **自动创建**：如果目录不存在，系统会自动创建
3. **混合使用**：可以混合使用变量和固定路径，例如：`{projectDir}/custom-ddl/{entityName}`

## 使用建议

1. **使用默认模板**：默认的 `{projectDir}/.autoddl/{entityName}` 已经满足大部分需求
2. **保持简洁**：避免过于复杂的路径结构
3. **版本控制**：建议将 `.autoddl` 目录添加到 `.gitignore`
4. **团队协作**：如果是团队项目，建议统一使用相同的模板