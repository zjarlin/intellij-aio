Compose Blocks turns nested Compose layout code into a focused block editor for JetBrains IDEs.

## What It Does

- Parses `@Composable` functions in Kotlin files and extracts layout-oriented structure.
- Renders containers and nested content lambdas as clickable blocks.
- Uses leading `/* block comment */` text as the visible block title.
- Draws `Row` blocks horizontally and `Column` blocks vertically so the visual layout matches the Compose container.
- Lets you hide shell composables so you can edit only the meaningful block.
- Opens as an editor-tab view instead of forcing a separate tool window.

## Current MVP

- Kotlin Compose file detection
- Custom file editor for Compose Kotlin files
- Block layout rendering for common layout calls and custom content-lambda wrappers
- Comment editing that syncs `/* ... */` block labels back into source
- Focused source editing by folding everything outside the selected block

## Why

Large Compose screens often wrap real content inside multiple shell layers such as pages, routes, scaffolds, or hosts. This plugin makes the structural layout visible and keeps editing focused on the block that matters.
