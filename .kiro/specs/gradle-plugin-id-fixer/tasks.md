# Implementation Plan: Gradle Plugin ID Fixer

## Overview

This implementation plan breaks down the Gradle Plugin ID Fixer feature into discrete coding tasks. The feature automatically fixes plugin ID references in Gradle build scripts when precompiled script plugins have package declarations. The implementation follows a bottom-up approach: core data structures → scanning logic → replacement engine → UI layer.

## Tasks

- [-] 1. Set up core data structures and utilities
  - Create `PluginIdInfo` data class to represent plugin metadata
  - Create `ReplacementCandidate` data class for tracking replacement operations
  - Create `ReplacementResult` data class for operation results
  - Set up project structure in appropriate package (e.g., `com.addzero.gradlebuddy.pluginid`)
  - _Requirements: 3.1, 3.2, 3.3, 3.4_

- [ ] 2. Implement Plugin ID Scanner
  - [~] 2.1 Create `PluginIdScanner` class with build-logic directory discovery
    - Implement `findBuildLogicDirectories()` to locate all `build-logic` directories in project
    - Handle both root-level and nested `checkouts/build-logic` locations
    - _Requirements: 3.1_

  - [~] 2.2 Implement plugin file scanning logic
    - Implement `scanBuildLogic(buildLogicDir: VirtualFile)` to find all `.gradle.kts` files
    - Recursively scan `src/main/kotlin/**/*.gradle.kts` paths
    - Filter out non-plugin files
    - _Requirements: 3.1_

  - [~] 2.3 Implement plugin metadata extraction
    - Implement `extractPluginInfo(file: VirtualFile)` to parse plugin files
    - Extract short ID from filename (remove `.gradle.kts` extension)
    - Implement `extractPackageName(psiFile: KtFile)` using PSI API
    - Construct fully qualified ID: `packageName.shortId`
    - Return `PluginIdInfo` object with all metadata
    - _Requirements: 3.2, 3.3, 3.4_

  - [ ]* 2.4 Write property test for plugin ID construction
    - **Property 1: ID Mapping Correctness**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4**

- [ ] 3. Implement Plugin ID Cache
  - [~] 3.1 Create `PluginIdCache` class with caching logic
    - Implement cache storage using `MutableMap<VirtualFile, List<PluginIdInfo>>`
    - Implement `get()`, `put()`, `invalidate()`, and `clear()` methods
    - Make it a project-level service for singleton behavior
    - _Requirements: 3.5_

  - [~] 3.2 Add cache invalidation on file changes
    - Register `VirtualFileListener` to detect build-logic file modifications
    - Invalidate cache entries when `.gradle.kts` files change
    - _Requirements: 3.6_

  - [ ]* 3.3 Write property test for cache consistency
    - **Property 4: Cache Consistency**
    - **Validates: Requirements 3.6**

- [~] 4. Checkpoint - Verify scanning and caching
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Implement ID Replacement Engine
  - [~] 5.1 Create `IdReplacementEngine` class structure
    - Set up constructor accepting `project` and `pluginIdMapping`
    - Create helper methods for PSI navigation
    - _Requirements: 1.5, 1.6_

  - [~] 5.2 Implement plugin ID reference detection
    - Implement `isPluginIdReference(element: PsiElement)` to detect `id("...")` calls
    - Implement `isInPluginsBlock(element: PsiElement)` to verify context
    - Use PSI tree traversal to check parent elements
    - _Requirements: 1.5_

  - [~] 5.3 Implement replacement candidate discovery
    - Implement `findReplacementCandidates(scope: SearchScope)` method
    - Search for all `id("...")` string literals in the given scope
    - Filter to only those in `plugins {}` blocks
    - Match against local plugin short IDs
    - Create `ReplacementCandidate` objects for matches
    - Skip external library plugins (e.g., `org.jetbrains.kotlin.*`)
    - _Requirements: 1.5, 1.8, 2.6_

  - [~] 5.4 Implement safe string replacement
    - Implement `applyReplacements(candidates: List<ReplacementCandidate>)` method
    - Use `KtPsiFactory` to create new string templates
    - Replace plugin ID strings while preserving formatting
    - Wrap in write action for thread safety
    - Track success/failure for each replacement
    - Return `ReplacementResult` with statistics
    - _Requirements: 1.6, 2.4_

  - [ ]* 5.5 Write property test for replacement accuracy
    - **Property 2: Replacement Accuracy**
    - **Validates: Requirements 1.8, 2.6**

  - [ ]* 5.6 Write property test for scope correctness
    - **Property 3: Scope Correctness**
    - **Validates: Requirements 1.6, 2.4**

  - [ ]* 5.7 Write unit tests for edge cases
    - Test multi-line plugin declarations
    - Test plugins with comments
    - Test already qualified IDs (should skip)
    - Test external plugins (should skip)
    - _Requirements: 1.8, 2.6_

- [~] 6. Checkpoint - Verify replacement engine
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. Implement Context Menu Action (Bulk Fix)
  - [~] 7.1 Create `FixAllPluginIdsAction` class
    - Extend `AnAction` from IntelliJ Platform
    - Implement `actionPerformed(e: AnActionEvent)` method
    - _Requirements: 1.1_

  - [~] 7.2 Implement bulk fix workflow
    - Get selected `build-logic` directory from action event
    - Instantiate `PluginIdScanner` and scan for plugins
    - Build short ID → full ID mapping
    - Instantiate `IdReplacementEngine` with mapping
    - Find all replacement candidates in project scope
    - _Requirements: 1.2, 1.3, 1.4, 1.5_

  - [~] 7.3 Create replacement preview dialog
    - Create `ReplacementPreviewDialog` UI component
    - Display list of files and replacements to be made
    - Show before/after preview for each change
    - Allow user to confirm or cancel
    - _Requirements: 1.7_

  - [~] 7.4 Implement replacement execution and feedback
    - Apply replacements when user confirms dialog
    - Show progress indicator during bulk operation
    - Display result notification with summary (files modified, replacement count)
    - Handle errors gracefully with user-friendly messages
    - _Requirements: 1.6, 1.7_

  - [~] 7.5 Implement action visibility logic
    - Override `update(e: AnActionEvent)` method
    - Show action only when right-clicking on `build-logic` directory
    - _Requirements: 1.1_

  - [ ]* 7.6 Write integration test for bulk fix
    - Create test project with sample build-logic plugins
    - Verify end-to-end bulk fix operation
    - Check that all occurrences are replaced correctly
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

- [ ] 8. Implement Intention Action (Single Fix)
  - [~] 8.1 Create `FixPluginIdIntention` class
    - Implement `IntentionAction` interface
    - Implement `getText()` to return "Fix build-logic qualified name"
    - _Requirements: 2.1_

  - [~] 8.2 Implement availability detection
    - Implement `isAvailable(project, editor, file)` method
    - Check if cursor is on a plugin ID string
    - Verify the element is in a `plugins {}` block
    - Check if the plugin is a local precompiled script plugin
    - Check if the plugin needs qualification (has package declaration)
    - Return false for external library plugins
    - _Requirements: 2.1, 2.2, 2.3, 2.6_

  - [~] 8.3 Implement project-wide fix logic
    - Implement `invoke(project, editor, file)` method
    - Extract plugin ID from cursor position
    - Find the fully qualified ID using `PluginIdScanner`
    - Use `IdReplacementEngine` to find ALL occurrences in project scope
    - Apply replacements to all files (not just current file)
    - Show notification with summary: "Fixed N occurrences of 'plugin-id' in M files"
    - _Requirements: 2.4, 2.5_

  - [ ]* 8.4 Write unit tests for intention action
    - Test availability detection for various cursor positions
    - Test that external plugins don't trigger the intention
    - Test that already qualified IDs don't trigger the intention
    - _Requirements: 2.1, 2.2, 2.3, 2.6_

  - [ ]* 8.5 Write integration test for project-wide replacement
    - Create test project with multiple files using same plugin ID
    - Trigger intention on one occurrence
    - Verify all occurrences across project are fixed
    - _Requirements: 2.4_

- [~] 9. Register actions in plugin.xml
  - Add `FixAllPluginIdsAction` to context menu for directories
  - Register `FixPluginIdIntention` as intention action
  - Add action descriptions and icons
  - _Requirements: 1.1, 2.1_

- [ ] 10. Add warning indicators for unqualified IDs
  - [~] 10.1 Create inspection class for plugin ID warnings
    - Extend `LocalInspectionTool` from IntelliJ Platform
    - Detect unqualified local plugin IDs in `plugins {}` blocks
    - Show warning indicator in editor gutter
    - _Requirements: 2.5_

  - [~] 10.2 Link inspection to intention action
    - Register quick fix that triggers `FixPluginIdIntention`
    - Ensure warning disappears after fix is applied
    - _Requirements: 2.5_

- [ ] 11. Final checkpoint and polish
  - [~] 11.1 Add error handling and logging
    - Handle file access errors gracefully
    - Log errors for debugging
    - Show user-friendly error messages
    - _Requirements: All_

  - [~] 11.2 Add performance optimizations
    - Ensure caching is working correctly
    - Run operations in background threads where possible
    - Add progress indicators for long operations
    - _Requirements: All_

  - [ ]* 11.3 Write end-to-end integration tests
    - Test complete workflow from action trigger to result
    - Test with various project structures
    - Test error scenarios
    - _Requirements: All_

- [~] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- The implementation follows a bottom-up approach: data structures → core logic → UI
- Property tests validate universal correctness properties across all inputs
- Unit tests validate specific examples and edge cases
- Integration tests verify end-to-end workflows
- The intention action performs project-wide replacement, not single-file fixes
