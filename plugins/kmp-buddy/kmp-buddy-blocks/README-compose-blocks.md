Compose Blocks adds a split block view and a managed low-code builder for Compose files in JetBrains IDEs.

## What It Does

- Parses `@Composable` functions in Kotlin files and extracts layout-oriented structure.
- Opens a dedicated Compose Blocks view with a block browser on one side and the real source editor on the other.
- Keeps Inspect Mode focused on structure browsing, comment editing, and wrap operations instead of mixing in full layout controls.
- Provides a managed Builder Mode with a built-in component palette, visual canvas, and layout sketch workflow for named slots.
- Uses leading doc comments such as `/** Title */` as the visible block title.
- Draws `Row` blocks horizontally and `Column` blocks vertically so the visual layout matches the Compose container.
- Lets you hide shell composables so you can edit only the meaningful block.

## Current MVP

- Kotlin Compose file detection
- Split Compose Blocks editor for normal Compose Kotlin files
- Block layout rendering for common layout calls and custom content-lambda wrappers
- Source-coupled highlighting with selected block background, parent background, and line-level emphasis in the live source pane
- Double-click block note editing backed by source doc comments
- Empty blocks keep only the Compose call name visible; double-click any block to add or edit its remark
- Managed Builder palette and canvas for low-code layout work
- Builder sketching that lets you draw named slots and generate lambda-based layout containers
- Slot filling through palette-driven default content snippets for managed files

## Why

Large Compose screens often wrap real content inside multiple shell layers such as pages, routes, scaffolds, or hosts. This plugin keeps the browsing experience separate from source editing in Inspect Mode, while Builder Mode handles layout sketching, named slots, and low-code palette interactions for managed Compose documents.
