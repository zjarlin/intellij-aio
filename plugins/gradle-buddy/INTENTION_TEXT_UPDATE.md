# Intention Text Update Summary

## Changes Made

All intention actions in the Gradle Buddy plugin now have:
1. `(Gradle Buddy)` prefix to identify the plugin source
2. English descriptions for consistency

## Updated Intentions

### 1. SelectCatalogReferenceIntentionGroup
- **Before**: `选择正确的版本目录引用（N 个候选项）`
- **After**: `(Gradle Buddy) Select correct catalog reference (N candidates)`
- **File**: `gradle-buddy-fix-catalog-ref/src/main/kotlin/site/addzero/gradle/catalog/SelectCatalogReferenceIntentionGroup.kt`

### 2. BrowseCatalogAlternativesIntention
- **Before**: `浏览其他版本目录引用（N 个候选项）`
- **After**: `(Gradle Buddy) Browse catalog alternatives (N candidates)`
- **File**: `gradle-buddy-fix-catalog-ref/src/main/kotlin/site/addzero/gradle/catalog/BrowseCatalogAlternativesIntention.kt`

### 3. FixPluginIdIntention
- **Before**: `Fix build-logic qualified name`
- **After**: `(Gradle Buddy) Fix build-logic qualified name`
- **File**: `id-fixer/src/main/java/site/addzero/idfixer/FixPluginIdIntention.kt`

### 4. GradleKtsPluginToTomlIntention
- **Before**: `将插件转换为版本目录格式 (TOML)`
- **After**: `(Gradle Buddy) Convert plugin to version catalog format (TOML)`
- **File**: `gradle-buddy-intentions/src/main/kotlin/site/addzero/gradle/buddy/intentions/convert/GradleKtsPluginToTomlIntention.kt`

### 5. GradleKtsHardcodedDependencyToTomlIntention
- **Before**: `(gradle-buddy)将依赖转换为版本目录格式 (TOML)`
- **After**: `(Gradle Buddy) Convert dependency to version catalog format (TOML)`
- **File**: `gradle-buddy-intentions/src/main/kotlin/site/addzero/gradle/buddy/intentions/convert/GradleKtsHardcodedDependencyToTomlIntention.kt`

### 6. VersionCatalogUpdateDependencyIntention
- **Before**: `Update dependency to latest version`
- **After**: `(Gradle Buddy) Update dependency to latest version`
- **File**: `gradle-buddy-intentions/src/main/kotlin/site/addzero/gradle/buddy/intentions/catalog/VersionCatalogUpdateDependencyIntention.kt`

### 7. GradleKtsUpdateDependencyIntention
- **Before**: `update dependency to the latest version`
- **After**: `(Gradle Buddy) Update dependency to latest version`
- **File**: `gradle-buddy-intentions/src/main/kotlin/site/addzero/gradle/buddy/intentions/update/GradleKtsUpdateDependencyIntention.kt`

## Benefits

1. **Clear Plugin Identification**: Users can easily identify which intentions come from Gradle Buddy
2. **Consistency**: All intentions now follow the same naming pattern
3. **Professional**: English descriptions make the plugin more accessible to international users
4. **Branding**: The `(Gradle Buddy)` prefix helps with plugin recognition

## Testing

After rebuilding the plugin, all intentions should display with the new format:
- Press `Alt+Enter` in any Gradle file
- Look for intentions starting with `(Gradle Buddy)`
- Verify the English descriptions are clear and accurate
