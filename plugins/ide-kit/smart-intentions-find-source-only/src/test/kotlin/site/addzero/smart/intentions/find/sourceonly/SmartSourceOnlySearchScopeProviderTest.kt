package site.addzero.smart.intentions.find.sourceonly

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartSourceOnlySearchScopeProviderTest : BasePlatformTestCase() {
    fun testProvidesSourceOnlyScope() {
        val sourceRoot = myFixture.tempDirFixture.findOrCreateDir("src")
        val generatedRoot = myFixture.tempDirFixture.findOrCreateDir("build/generated")
        PsiTestUtil.addSourceRoot(module, sourceRoot)

        val sourceFile = myFixture.tempDirFixture.createFile("src/App.kt", "class App")
        val generatedFile = myFixture.tempDirFixture.createFile("build/generated/AppGenerated.kt", "class AppGenerated")

        val scopes = SmartSourceOnlySearchScopeProvider().getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT)

        assertEquals(1, scopes.size)
        val scope = scopes.single()
        assertEquals("源码目录", scope.displayName)
        assertTrue(scope.contains(sourceFile))
        assertFalse(scope.contains(generatedFile))
        assertFalse(scope.contains(generatedRoot))
    }
}
