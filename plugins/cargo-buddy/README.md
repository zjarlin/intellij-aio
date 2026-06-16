# Cargo Buddy adds a floating Cargo toolbar for Rust crates in JetBrains IDEs.

Cargo Buddy detects the crate for the file currently open in the editor and lets you run Cargo commands without opening the project tree.

## Features

- Shows a floating editor toolbar for files inside a Cargo crate.
- Runs Cargo commands against the nearest `Cargo.toml` for the current editor file.
- Supports `build`, `check`, `test`, `clippy`, `clean`, `publish`, and dependency-ordered publish.
- Resolves local Cargo dependencies with `cargo metadata` and publishes them before the current crate.
- Keeps the Cargo tool window focused on the crate for the active editor tab.
- Falls back to the system `cargo` executable and keeps command output in the IDE Run tool window.
