# Dioxus Buddy provides local preview sandbox actions for Dioxus Rust components.

Dioxus Buddy adds editor actions for Rust files that contain Dioxus preview targets.

## Features

- Detects Rust functions marked with `#[component]`, `#[dioxus_preview]`, or `#[preview]`.
- Adds a gutter icon on detected preview targets.
- Adds editor and Tools menu actions for launching the current preview target.
- Generates a local `.dioxus-buddy/preview-sandbox/<function>` crate.
- Copies crate source modules for `src` previews and depends on the original crate for `examples` previews.
- Runs `dx serve --platform web` in the IDE Run tool window.

## Preview Model

Dioxus component functions are Rust code, so the plugin does not try to execute them inside the IDE process. It creates a small temporary Dioxus web crate and delegates rendering to the official Dioxus CLI.

The first supported path is intentionally narrow: a preview target should be constructible without required props. For prop-heavy components, create a nearby `#[dioxus_preview]` wrapper function that supplies sample data and calls the real component.
