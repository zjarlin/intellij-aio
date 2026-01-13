# Changelog

All notable changes to the Split Module plugin will be documented in this file.

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
- Support for Gradle Groovy DSL
- Dependency analysis and optimization
- Batch module splitting
- Undo/redo support improvements
