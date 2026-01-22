# Similarity Matching Examples

## Algorithm Overview

The `AliasSimilarityMatcher` uses a multi-factor scoring system to find the most similar aliases in the TOML file.

### Scoring Components

1. **Exact Match Score (50% weight)**
   - Counts how many tokens from the invalid reference exactly match tokens in the alias
   - Formula: `(matched_tokens / total_reference_tokens) * 0.5`

2. **Jaccard Similarity (30% weight)**
   - Measures set similarity between token sets
   - Formula: `(intersection_size / union_size) * 0.3`

3. **Order Similarity (20% weight)**
   - Rewards aliases where matched tokens appear in the same relative order
   - Formula: `(order_matches / (common_tokens - 1)) * 0.2`

## Example 1: Perfect Match

**Invalid Reference**: `com.google.devtools.ksp.gradle.plugin`
**Tokens**: `[com, google, devtools, ksp, gradle, plugin]`

**TOML Alias**: `gradle-plugin-ksp` → `gradle.plugin.ksp`
**Alias Tokens**: `[gradle, plugin, ksp]`

**Scoring**:
- Exact matches: 3/6 = 0.5 → Score: 0.5 * 0.5 = **0.25**
- Jaccard: 3/6 = 0.5 → Score: 0.5 * 0.3 = **0.15**
- Order: All 3 tokens in same order → Score: 1.0 * 0.2 = **0.20**
- **Total Score: 0.60 (60%)**

## Example 2: Partial Match

**Invalid Reference**: `com.google.devtools.ksp.gradle.plugin`
**Tokens**: `[com, google, devtools, ksp, gradle, plugin]`

**TOML Alias**: `ksp-symbol-processing-api` → `ksp.symbol.processing.api`
**Alias Tokens**: `[ksp, symbol, processing, api]`

**Scoring**:
- Exact matches: 1/6 = 0.167 → Score: 0.167 * 0.5 = **0.083**
- Jaccard: 1/9 = 0.111 → Score: 0.111 * 0.3 = **0.033**
- Order: N/A (only 1 common token) → Score: **0.0**
- **Total Score: 0.116 (12%)**

## Example 3: No Match

**Invalid Reference**: `completely.unknown.library`
**Tokens**: `[completely, unknown, library]`

**TOML Alias**: `gradle.plugin.ksp`
**Alias Tokens**: `[gradle, plugin, ksp]`

**Scoring**:
- Exact matches: 0/3 = 0 → Score: **0.0**
- Jaccard: 0/6 = 0 → Score: **0.0**
- Order: N/A → Score: **0.0**
- **Total Score: 0.0 (0%)**

Result: This alias is filtered out (score > 0.0 required)

## Real-World Test Cases

### Case 1: KSP Plugin

```kotlin
// Invalid reference
implementation(libs.com.google.devtools.ksp.gradle.plugin)

// TOML has
gradle-plugin-ksp = { ... }

// Expected Top 5:
1. gradle.plugin.ksp (60% - exact match for gradle, plugin, ksp)
2. ksp.symbol.processing.api (12% - partial match for ksp)
3. ... (other ksp-related dependencies)
```

### Case 2: Kotlin Plugin

```kotlin
// Invalid reference
implementation(libs.org.jetbrains.kotlin.jvm)

// TOML has
kotlin-jvm = { ... }
kotlin-stdlib = { ... }
kotlin-reflect = { ... }

// Expected Top 5:
1. kotlin.jvm (high score - exact match for kotlin, jvm)
2. kotlin.stdlib (medium score - exact match for kotlin)
3. kotlin.reflect (medium score - exact match for kotlin)
```

### Case 3: Spring Boot

```kotlin
// Invalid reference
implementation(libs.org.springframework.boot.starter.web)

// TOML has
spring-boot-starter-web = { ... }
spring-boot-starter-data-jpa = { ... }
spring-core = { ... }

// Expected Top 5:
1. spring.boot.starter.web (very high - exact match for spring, boot, starter, web)
2. spring.boot.starter.data.jpa (high - exact match for spring, boot, starter)
3. spring.core (low - only spring matches)
```

## Edge Cases

### Case 1: Camel Case vs Kebab Case

```kotlin
// Invalid: libs.gradlePlugin.ksp
// TOML: gradle-plugin-ksp
// Result: Should match (both convert to [gradle, plugin, ksp])
```

### Case 2: Different Separators

```kotlin
// Invalid: libs.gradle_plugin_ksp
// TOML: gradle-plugin-ksp
// Result: Should match (both convert to [gradle, plugin, ksp])
```

### Case 3: Partial Package Name

```kotlin
// Invalid: libs.ksp
// TOML: gradle-plugin-ksp
// Result: Should match but with lower score (only 1 token matches)
```

## Implementation Notes

1. **Tokenization**: Splits on `.`, `-`, `_` and converts to lowercase
2. **Filtering**: Only returns results with score > 0.0
3. **Sorting**: Results sorted by score descending
4. **Top N**: Returns at most N results (default: 5)
5. **Dialog Display**: Shows score as percentage and matched tokens for user clarity
