# Recent Project Cleaner removes missing recent projects from the Welcome screen.

## Features

- Adds a `Clear Invalid Projects` action to the Welcome screen project area.
- Shows the current invalid-entry count directly in the action label when stale entries exist.
- Removes only local recent-project entries whose paths no longer exist on the current machine.
- Leaves remote URIs and foreign operating-system path formats alone to avoid accidental cleanup.

## Behavior

The plugin reads the IDE recent-project list from the public `RecentProjectsManager` API surface and removes entries that are clearly stale on the current OS.

It treats the following entries as removable:

- Blank recent-project paths
- Absolute local paths that no longer exist
- Paths that cannot be parsed as valid local paths

It intentionally does not remove:

- Remote project URIs such as `ssh://...`
- Windows-style paths while running on macOS or Linux
- POSIX-style absolute paths while running on Windows

## Usage

1. Open the IDE Welcome screen.
2. Look for `Clear Invalid Projects` in the project actions area.
3. Click it to remove stale recent-project entries in one step.

When no invalid entries exist, the action stays visible but disabled.

## Development

- Module path: `:plugins:recent-project-cleaner`
- Verification: `./gradlew :plugins:recent-project-cleaner:test`
- Descriptor preview: `plugins/recent-project-cleaner/build/tmp/patchPluginXml/plugin.xml`
