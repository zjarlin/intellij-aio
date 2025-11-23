# Repository Guidelines

## Project Structure & Module Organization
The Gradle root hosts helper tooling under `lib/` (Swing/AWT widgets, PSI helpers, database utilities) and shared checkouts in `checkouts/` (build logic and LSI APIs). The IntelliJ plugin you will edit lives in `plugins/autoddl`, with Kotlin sources in `src/main/kotlin`, UI resources under `src/main/resources`, and plugin-specific tests in `src/test`. Keep generated artifacts confined to each module’s `build/` directory so the root stays clean.

## Build, Test, and Development Commands
Use Gradle Wrapper commands from the repo root: `./gradlew :plugins:autoddl:build` compiles and runs static checks, `./gradlew :plugins:autoddl:test` executes the module test suite, and `./gradlew :plugins:autoddl:runIde` launches the sandboxed IDE for manual verification. Before publishing, run `./gradlew :plugins:autoddl:verifyPlugin` to execute IntelliJ inspections and metadata checks. Spotless formatting can be enforced with `./gradlew :plugins:autoddl:spotlessApply`.

## Coding Style & Naming Conventions
Kotlin sources follow Spotless + ktfmt (Google style, 2-space indent, 160-column width). Favor top-level functions and data classes where practical, and name packages with the existing `site.addzero.autoddl.*` convention. Keep IntelliJ action IDs and settings keys lowercase with dot separators (e.g., `autoddl.schema.wizard`). UI resources should mirror action or service names to simplify navigation.

## Testing Guidelines
Tests rely on JUnit 5; keep suites colocated under `plugins/autoddl/src/test/kotlin` and mirror the production package path. Name test classes with the `*Test` suffix (e.g., `MetadataMapperTest`) and prefer expressive, single-behavior test methods. Run `./gradlew :plugins:autoddl:test` before every PR; new features should cover both generator logic and PSI helpers. Include fixture files in `src/test/resources` when mocking IntelliJ PSI trees rather than embedding raw strings.

## Commit & Pull Request Guidelines
The history uses conventional commits such as `refactor(autoddl): 调整字段映射`. Keep the `type(scope): summary` pattern, use English for the summary when possible, and include concise Chinese context if it aids reviewers. Every PR should describe behavior changes, list key commands executed, and attach screenshots or GIFs when UI flows change. Link tracking issues via `Fixes #123` and call out any follow-up work in a checklist.

## Security & Configuration Tips
Do not hardcode AI provider keys; rely on the IDE Settings > AutoDDL panel described in `plugins/autoddl/README.md`. When adding new services, read secrets from IntelliJ’s secure storage APIs and gate network calls behind the existing settings checks so the plugin remains compliant with JetBrains Marketplace policies.
