KMP Buddy combines Compose refactors, Koin cleanups, and block-based design tools for JetBrains IDEs.

## Included Features

- Compose refactoring and intention actions for wrappers, state, events, slots, variants, previews, and container extraction.
- Koin constructor cleanup that removes redundant injected parameters when another dependency already exposes them.
- Project-wide Koin `@Single(binds = [...])` cleanup from a single `Alt+Enter` entry.
- Compose Blocks Inspect view with split block browser and live source editor for Compose Kotlin files.
- Builder mode with palette, canvas, sketch regions, named slots, and managed source generation.
- Source-coupled block highlighting, progressive folding, inline remark editing, wrap, and unwrap operations.
- Compose Designer tool window and existing KMP Buddy inspections.

## Module Layout

- `plugins/kmp-buddy`: root IntelliJ plugin descriptor and shared registration.
- `plugins/kmp-buddy/kmp-buddy-designer`: existing designer module.
- `plugins/kmp-buddy/kmp-buddy-blocks`: merged Compose Blocks editor, parser, builder, and managed-document support.
- `plugins/kmp-buddy/smart-intentions-koin-redundant-dependency`: Koin constructor dependency cleanup.
- `plugins/kmp-buddy/smart-intentions-koin-single-binds`: project-wide `@Single binds` cleanup.
