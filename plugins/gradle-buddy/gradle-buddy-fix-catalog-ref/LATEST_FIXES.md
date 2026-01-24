# Latest Bug Fixes

## Date: 2026-01-23

### Fix 1: Version Catalog Dependency Upgrade Not Working in TOML Files

**Problem**: The "Update dependency to latest version" intention was not appearing in `libs.versions.toml` files when pressing Alt+Enter on dependencies.

**Root Cause**: Two issues:
1. The intention was registered with `<language>kotlin</language>` in plugin.xml, but TOML files use the language ID "TOML", not "kotlin".
2. The `findVersionRef()` method only searched in the current line, so when cursor was on `version.ref = "kotlin"`, it couldn't find the actual version definition in the `[versions]` section.

**Solution**:
1. Changed the language registration in `plugins/gradle-buddy/src/main/resources/META-INF/plugin.xml`:

```xml
<!-- BEFORE -->
<intentionAction order="FIRST">
  <language>kotlin</language>
  <className>site.addzero.gradle.buddy.intentions.catalog.VersionCatalogUpdateDependencyIntention</className>
  <category>Gradle Buddy</category>
</intentionAction>

<!-- AFTER -->
<intentionAction order="FIRST">
  <language>TOML</language>
  <className>site.addzero.gradle.buddy.intentions.catalog.VersionCatalogUpdateDependencyIntention</className>
  <category>Gradle Buddy</category>
</intentionAction>
```

2. Modified `detectCatalogDependency()` and `findVersionRef()` to search in the full file text instead of just the current line:

```kotlin
// BEFORE
private fun detectCatalogDependency(element: PsiElement): CatalogDependencyInfo? {
    val text = getLineText(element)
    // ... uses 'text' for both pattern matching and findVersionRef()
}

private fun findVersionRef(text: String, versionKey: String): String? {
    // searches only in 'text' (current line)
}

// AFTER
private fun detectCatalogDependency(element: PsiElement): CatalogDependencyInfo? {
    val lineText = getLineText(element)
    val fullText = element.containingFile?.text ?: lineText
    // ... uses 'lineText' for pattern matching, 'fullText' for findVersionRef()
}

private fun findVersionRef(fullText: String, versionKey: String): String? {
    // searches in entire file
}
```

**Testing**: After rebuilding, the intention should now appear when you:
1. Open `gradle/libs.versions.toml`
2. Place cursor on any dependency line, including on the word "version":
   - `serialization = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }`
   - Cursor can be on "serialization", "module", "version", "kotlin", etc.
3. Press Alt+Enter
4. See "(Gradle Buddy) Update dependency to latest version"

**Example**:
```toml
[versions]
kotlin = "1.9.0"

[libraries]
serialization = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }
```
Now works when cursor is anywhere on the `serialization` line, including on the word "version".

---

### Fix 2: Token Extraction Bug in Catalog Reference Suggestions

**Problem**: When cursor was on `jdk15to18` in `libs.bcprov.jdk15to18`, the similarity matcher only searched with `jdk15to18` token, missing `bcprov`. This resulted in poor or missing suggestions.

**Root Cause**: The `extractCatalogReference()` method was using the current `KtDotQualifiedExpression` directly, which might be a sub-expression if the cursor is in the middle of a longer reference.

**Solution**: Modified `extractCatalogReference()` in `SelectCatalogReferenceIntentionGroup.kt` to always find the top-level expression:

```kotlin
// BEFORE
private fun extractCatalogReference(expression: KtDotQualifiedExpression): Pair<String, String>? {
    val fullText = expression.text
    val parts = fullText.split(".")
    // ...
}

// AFTER
private fun extractCatalogReference(expression: KtDotQualifiedExpression): Pair<String, String>? {
    // 找到最顶层的 KtDotQualifiedExpression（包含完整的 catalog 引用）
    var topExpression = expression
    while (topExpression.parent is KtDotQualifiedExpression) {
        topExpression = topExpression.parent as KtDotQualifiedExpression
    }

    val fullText = topExpression.text
    val parts = fullText.split(".")
    // ...
}
```

**Impact**: Now when cursor is anywhere in `libs.bcprov.jdk15to18`, the system will:
1. Extract the full reference: `bcprov.jdk15to18`
2. Tokenize it: `["bcprov", "jdk15to18"]`
3. Search TOML with ALL tokens for better similarity matching
4. Show Top 10 most relevant candidates

---

## Files Modified

1. `plugins/gradle-buddy/src/main/resources/META-INF/plugin.xml`
   - Changed language from "kotlin" to "TOML" for VersionCatalogUpdateDependencyIntention

2. `plugins/gradle-buddy/gradle-buddy-intentions/src/main/kotlin/site/addzero/gradle/buddy/intentions/catalog/VersionCatalogUpdateDependencyIntention.kt`
   - Modified `detectCatalogDependency()` to use full file text for version reference lookup
   - Modified `findVersionRef()` to search in entire file instead of just current line

3. `plugins/gradle-buddy/gradle-buddy-fix-catalog-ref/src/main/kotlin/site/addzero/gradle/catalog/SelectCatalogReferenceIntentionGroup.kt`
   - Fixed `extractCatalogReference()` to always use top-level expression

---

## Testing Checklist

### Test 1: TOML Dependency Upgrade
- [ ] Open `gradle/libs.versions.toml`
- [ ] Test with version reference format:
  ```toml
  [versions]
  kotlin = "1.9.0"

  [libraries]
  serialization = { module = "org.jetbrains.kotlin:kotlin-serialization", version.ref = "kotlin" }
  ```
- [ ] Place cursor on different positions: "serialization", "module", "version", "kotlin"
- [ ] Press Alt+Enter on each position
- [ ] Verify "(Gradle Buddy) Update dependency to latest version" appears
- [ ] Select it and verify it fetches latest version from Maven Central
- [ ] Verify the version in `[versions]` section gets updated

- [ ] Test with direct version format:
  ```toml
  [libraries]
  guava = { module = "com.google.guava:guava", version = "32.1.3" }
  ```
- [ ] Place cursor anywhere on the line
- [ ] Press Alt+Enter
- [ ] Verify intention appears and works

### Test 2: Token Extraction
- [ ] Open a `.gradle.kts` file
- [ ] Write: `implementation(libs.bcprov.jdk15to18)`
- [ ] Place cursor on `jdk15to18` (middle of reference)
- [ ] Press Alt+Enter
- [ ] Verify suggestions include candidates matching BOTH "bcprov" AND "jdk15to18"
- [ ] Verify Top 10 candidates are shown

---

## Next Steps

1. **Build the plugin**: `./gradlew build`
2. **Test in IDE**: `./gradlew runIde`
3. **Verify both fixes work as expected**
4. **Update CHANGELOG if needed**
