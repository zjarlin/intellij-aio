# Changelog

## Unreleased

- Added the initial `ide-kit` plugin structure.
- Added Kotlin redundant explicit type cleanup inspection and intention.
- Added a Kotlin intention to convert simple `class` / `data class` declarations into `interface`.
- Added a source-only Find in Files scope that skips generated directories.
- Excluded common log files and `log` / `logs` directories from the source-only Find in Files scope.
- Excluded module-local `.gradle` and `.kotlin` generated script directories from project indexing and global search.
- Added `Module Lock` to temporarily hide selected modules from the Project view without affecting Gradle builds.
