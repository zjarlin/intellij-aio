Compose Blocks turns nested Compose layout code into a single-pane block editor for JetBrains IDEs with source-coupled highlighting.

## What It Does

- Parses `@Composable` functions in Kotlin files and extracts layout-oriented structure.
- Renders containers and nested content lambdas as clickable blocks inside a custom editor tab.
- Uses leading doc comments such as `/** Title */` as the visible block title.
- Draws `Row` blocks horizontally and `Column` blocks vertically so the visual layout matches the Compose container.
- Lets you hide shell composables so you can edit only the meaningful block.
- Keeps the real Kotlin source inline under the selected block instead of switching to a split editor.

## Current MVP

- Kotlin Compose file detection
- Custom file editor for Compose Kotlin files
- Block layout rendering for common layout calls and custom content-lambda wrappers
- Source-coupled highlighting with selected block background, parent background, and line-level emphasis
- Source guide bars in the code editor so the selected block path stays visually anchored to the real Kotlin lines
- Double-click block note editing backed by source doc comments
- Low-code skeleton editing for safe `Row` / `Column` / `Box` containers
- Inline template insertion, same-container reordering, wrap, unwrap, and lightweight layout argument editing
- Builder Mode layout sketching that lets you draw named slots and generate lambda-based layout containers

## Why

Large Compose screens often wrap real content inside multiple shell layers such as pages, routes, scaffolds, or hosts. This plugin keeps the source and the visual block model anchored to the same code range, so you can move between structure editing and real Kotlin editing without the view feeling split apart.
