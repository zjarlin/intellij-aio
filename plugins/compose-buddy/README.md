Compose Buddy combines Compose code insights, block-based inspection, and visual builder workflows for Compose Multiplatform in JetBrains IDEs.

## Included Features

- Compose refactoring and intention actions for wrappers, state, events, slots, variants, previews, and container extraction.
- Compose Blocks Inspect view with split block browser and live source editor for Compose Kotlin files.
- Builder mode with palette, canvas, sketch regions, named slots, and managed source generation.
- Source-coupled block highlighting, progressive folding, inline remark editing, wrap, and unwrap operations.
- Compose Designer tool window and existing Compose Buddy inspections.

## Module Layout

- `plugins/compose-buddy`: root IntelliJ plugin descriptor and shared registration.
- `plugins/compose-buddy/compose-buddy-designer`: existing designer module.
- `plugins/compose-buddy/compose-buddy-blocks`: merged Compose Blocks editor, parser, builder, and managed-document support.
