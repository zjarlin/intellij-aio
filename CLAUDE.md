# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AutoDDL is an IntelliJ IDEA plugin that extends metadata operations for Java/Kotlin entity classes. It generates DDL (CREATE TABLE and ALTER TABLE statements) from entity classes, with optional AI assistance. The plugin also provides intention actions for adding annotations (Swagger, Excel properties, custom annotations) and generating enums from field comments.

## Build System

This project uses a custom Gradle build system with:
- Custom build logic in `checkouts/build-logic` (checked out via git from a remote repository)
- Auto-generated module configuration via `io.gitee.zjarlin.auto-modules` plugin
- IntelliJ Platform Gradle Plugin for plugin development

**Note**: The build requires Java 24+ JVM due to custom plugins (`site.addzero.repo-buddy` and `io.gitee.zjarlin.auto-modules`). If you encounter JVM version errors, this is a known limitation.

### Common Build Commands

```bash
# Build the plugin
./gradlew build

# Run the plugin in a test IDE instance
./gradlew runIde

# Build plugin distribution
./gradlew buildPlugin

# Verify plugin
./gradlew verifyPlugin
```

## Project Architecture

### Module Structure

```
autoddl-idea-plugin/
├── lib/
│   ├── tool-common/    # Language-agnostic utilities (LSI abstraction layer)
│   └── tool-psi/       # PSI-specific utilities
└── src/main/kotlin/    # Main plugin code
```

### Key Architectural Concepts

#### 1. LSI (Language Structure Interface) Abstraction Layer

Located in `lib/tool-common/src/main/kotlin/com/addzero/util/lsi/`, the LSI layer provides language-agnostic abstractions for working with class structures:

- **`LsiClass`**: Language-independent interface for class representation
- **`LsiType`**: Language-independent interface for type information
- **`LsiField`**: Language-independent interface for field information
- **`LsiAnnotation`**: Language-independent interface for annotations

The LSI layer has three implementations:
- **`psi`**: PSI-based implementation for Java classes (uses IntelliJ's PSI API)
- **`kt`**: Kotlin-specific implementation for KtClass
- **`clazz`**: Reflection-based implementation using `java.lang.Class`

This abstraction allows the plugin to handle both Java and Kotlin classes uniformly throughout the codebase. When working with entity classes, always use LSI interfaces rather than directly working with PsiClass or KtClass.

#### 2. DDL Generation Pipeline

The DDL generation follows this flow:
1. Extract metadata from entity class (via LSI layer)
2. Create `DDLContext` object (contains table name, fields, database type)
3. Select appropriate database generator (`MysqlDDLGenerator`, `PostgreSQLDDLGenerator`, `OracleDDLGenerator`, `H2SQLDDLGenerator`, `DMSQLDDLGenerator`, or `TaosSQLDDLGenerator`)
4. Generate SQL statements (CREATE TABLE or ALTER TABLE ADD COLUMN)
5. Open generated SQL in editor (saved to `.autoddl` folder in project root)

Entry points:
- `GenDDL` action: Main action for generating DDL from entity classes
- `DDLContextFactory4JavaMetaInfo`: Factory for creating DDLContext from Java/Kotlin classes
- `IDatabaseGenerator`: Interface for database-specific SQL generation

#### 3. Intention Actions System

Intention actions (Alt+Enter) are dynamically registered at startup via `AddActionAndIntentions`:

- **Swagger annotations**: `AddSwaggerAnnotationAction` / `AddSwaggerAnnotationJavaAction`
- **Excel annotations**: `AddExcelPropertyAnnotationAction` / `AddExcelPropertyAnnotationJavaAction`
- **Custom annotations**: `AddCusTomAnnotationAction` / `AddCusTomAnnotationJavaAction`
- **Enum generation**: Generate Java/Kotlin enums from field comments with enum notation (e.g., `1=男 2:女 3-其他`)

All annotation intention actions extend `AbstractDocCommentAnnotationAction`, which provides a priority system for inferring missing documentation:
1. Existing Swagger annotations
2. Existing Excel property annotations
3. Javadoc comments
4. AI-based guessing (as last resort)

#### 4. Code Generation System

Located in `src/main/kotlin/com/addzero/addl/action/anycodegen/`, provides generators for:
- **Jimmer DTOs**: Generate Jimmer framework DTO classes
- **Excel DTOs**: Generate FastExcel framework entities
- **Controllers**: Generate controller code
- **Jimmer All**: Generate complete Jimmer-based CRUD scaffolding

All generators extend `AbsGen` base class.

#### 5. Settings and Configuration

- **Settings service**: `MyPluginSettingsService` (application-level service)
- **Configurable UI**: `MyPluginConfigurable`
- Settings include: database type, API keys for AI, enum generation path, annotation targets

### Important File Locations

- **Plugin descriptor**: `src/main/resources/META-INF/plugin.xml`
- **LSI interfaces**: `lib/tool-common/src/main/kotlin/com/addzero/util/lsi/`
- **PSI utilities**: `lib/tool-common/src/main/kotlin/com/addzero/util/psi/`
- **DDL generators**: `src/main/kotlin/com/addzero/addl/autoddlstarter/generator/`
- **Action classes**: `src/main/kotlin/com/addzero/addl/action/`
- **Intention actions**: `src/main/kotlin/com/addzero/addl/intention/`

## Development Guidelines

### Working with Entity Classes

When adding features that process entity classes:
1. Use the LSI abstraction layer (`LsiClass`, `LsiType`, `LsiField`) instead of directly accessing PSI or KtClass
2. Check `PsiValidateUtil.isValidTarget()` to verify if a class is a valid entity (POJO or Jimmer entity)
3. Use `psiCtx()` utility to get current editor context

### Adding New Database Support

To add support for a new database:
1. Create a new class extending `IDatabaseGenerator` in `src/main/kotlin/com/addzero/addl/autoddlstarter/generator/ex/`
2. Implement type mapping in the `getColumnType()` method
3. Register the generator in `IDatabaseGenerator.Companion.getDatabaseDDLGenerator()`
4. Update settings UI to include the new database type

### Adding New Intention Actions

1. Create action class extending `AbstractDocCommentAnnotationAction` (for annotation actions) or `IntentionAction`
2. Register in `AddActionAndIntentions.kt` startup activity
3. Add description resources in `src/main/resources/intentionDescriptions/`

### Tool Windows

The plugin provides two tool windows:
- **AutoDDL**: Main tool window for DDL operations (right sidebar)
- **ShitCode**: Tracks code marked with `@Shit` annotation for cleanup

### AI Integration

The plugin integrates with LLM APIs (default: Alibaba's `qwen2.5-coder-1.5b-instruct`) for:
- Generating table metadata from natural language
- Guessing field comments when other methods fail

AI configuration is in plugin settings (Tools → AutoDDL Settings).

## Testing

When making changes:
1. Build the plugin: `./gradlew build`
2. Run in test IDE: `./gradlew runIde`
3. Test with both Java and Kotlin entity classes
4. Verify generated SQL syntax for target database types

## Important Notes

- Generated SQL files are saved to `.autoddl/` folder (hidden folder) - ensure this is in `.gitignore`
- The plugin supports Jimmer framework entities (checks for `@Entity` and other Jimmer annotations)
- Enum generation from comments uses patterns: `1=Male`, `2:Female`, `3-Other` (separators: `=`, `:`, `-`)
- The build system uses configuration cache for faster builds
