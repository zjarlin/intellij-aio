# Changelog

All notable changes to the Split Module plugin will be documented in this file.

## [1.1.0] - 2026-01-13

### Added
- **Multi-Build System Support**: Added support for Maven (`pom.xml`) and Gradle Groovy DSL (`build.gradle`) in addition to Kotlin DSL.
- **Automatic Detection**: The plugin now automatically detects the build system in the source module.
- **Smart Dependency Injection**: Refactored dependency adding logic to use specific syntax for each build system (XML for Maven, Groovy/Kotlin DSL for Gradle).

## [2026.01.12] - 2026-01-12

### Added
- Initial release of Split Module plugin
- Right-click action to split files into a new sibling module
- Automatic module name input dialog with validation
- Automatic `build.gradle.kts` copy from source module
- Automatic dependency injection in source module
- File structure preservation during migration
- Support for Kotlin and Java source directories
- Support for resource and test directories
- Background thread execution for better IDE performance

### Fixed
- Threading issue with `ActionUpdateThread` for IntelliJ IDEA 2024.2+

### Changed
- Removed automatic `settings.gradle.kts` registration (delegated to external module discovery plugins)

## [Unreleased]

### Planned
- Dependency analysis and optimization
- Batch module splitting
- Undo/redo support improvements
