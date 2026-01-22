# Changelog

All notable changes to the Gradle Plugin ID Fixer module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial implementation of plugin ID scanner
- Automatic detection of `build-logic` directories
- Package name extraction from Kotlin precompiled script plugins
- Intention action for fixing individual plugin IDs (Alt+Enter)
- Bulk action for fixing all plugin IDs in the project
- Thread-safe PSI access with proper read actions
- Progress indicators for long-running operations
- Notification system for operation results
- Support for nested `build-logic` directory structures

### Features
- **PluginIdScanner**: Recursively scans project for `build-logic` directories and extracts plugin metadata
- **IdReplacementEngine**: Finds and replaces short plugin IDs with fully qualified names
- **FixPluginIdIntention**: Quick fix available via Alt+Enter on plugin ID references
- **FixAllPluginIdsAction**: Bulk action to fix all plugin IDs across the entire project

### Technical Details
- Proper read action wrapping for all PSI operations
- Background task execution with cancellation support
- Atomic file modifications with write actions
- Comprehensive error handling and reporting

### Fixed
- Thread safety issue in `extractPackageName` method
- PSI access violations when running in background threads

## [1.0.0] - 2025-01-22

### Added
- Initial release of Gradle Plugin ID Fixer module
- Core functionality for detecting and fixing plugin ID references
- Integration with gradle-buddy plugin

---

## Development Notes

### Known Limitations
- Only supports Kotlin DSL (`.gradle.kts`) files
- Assumes `build-logic/src/main/kotlin` directory structure
- Does not handle dynamic plugin ID construction

### Future Enhancements
- [ ] Support for Groovy DSL (`.gradle`) files
- [ ] Configuration options for custom `build-logic` paths
- [ ] Batch preview before applying changes
- [ ] Undo/redo support for bulk operations
- [ ] Detection of unused plugin declarations
- [ ] Validation of plugin ID references at compile time

### Contributing
When making changes to this module:
1. Ensure all PSI access is wrapped in read actions
2. Use background tasks for long-running operations
3. Provide clear notifications to users
4. Add tests for new functionality
5. Update this CHANGELOG with your changes
