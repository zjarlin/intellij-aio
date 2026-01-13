# Changelog

All notable changes to the Vcs auto update plugin will be documented in this file.

## [1.1.0] - 2026-01-13

### Added
- **New Detection Mechanism**: Switched from unreliable VFS monitoring to robust `AnActionListener` for detecting push operations.
- **Automatic Refresh**: Added automatic VFS and project refresh after a successful pull to ensure changes are immediately visible.
- **SVG Plugin Icon**: Added a modern, high-quality SVG icon for the plugin.
- **Detailed Notifications**: Improved notification messages for pull results.

### Fixed
- **API Compatibility**: Fixed several deprecation warnings and "removed in future release" API calls for IntelliJ IDEA 2024.2+.
- **Trigger Failure**: Resolved the issue where the pull operation was not being triggered correctly, including support for "Commit and Push".
- **Code Style**: Standardized casing (Vcs auto update) and fixed grammatical issues in comments and UI.
- **Import Cleanup**: Removed unused imports and cleaned up internal services.

### Changed
- Refactored `GitPushDetectorService` to use `MessageBus` subscription for actions.
- Consolidated Git pull execution logic.

## [1.0.0] - Initial Release

### Added
- Basic auto-pull before push functionality.
- Settings page for configuration.
- Manual pull action in VCS menu.
