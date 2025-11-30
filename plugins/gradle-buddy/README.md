# Gradle Buddy

A plugin for IntelliJ IDEA that helps you manage Gradle projects by showing an indicator when a project is not loaded.

## Features

- Shows an indicator in the status bar when you open a file in a Gradle project that hasn't been loaded
- Click the indicator to trigger Gradle project loading
- Automatically detects Gradle projects by looking for `build.gradle`, `build.gradle.kts`, `settings.gradle`, or `settings.gradle.kts` files

## Usage

1. Open a Gradle project in IntelliJ IDEA
2. If the Gradle project is not loaded, you'll see "Gradle: Load Required" in the status bar
3. Click on the indicator to load the Gradle project

## Installation

The plugin can be built and installed from source:

```bash
./gradlew :plugins:gradle-buddy:buildPlugin
```

Then install the generated plugin zip file from `plugins/gradle-buddy/build/libs/` in IntelliJ IDEA.

## How it works

The plugin monitors file opening events in your project. When you open a file, it checks if:

1. The project contains Gradle build files (`build.gradle`, `build.gradle.kts`, `settings.gradle`, or `settings.gradle.kts`)
2. The Gradle project has been loaded in IntelliJ IDEA

If the project is a Gradle project but hasn't been loaded, the plugin displays an indicator in the status bar prompting you to load the project.

## Future improvements

- Implement actual Gradle project loading functionality
- Add support for Gradle Kotlin DSL files
- Improve detection of Gradle projects in subdirectories
- Add configuration options for the indicator appearance