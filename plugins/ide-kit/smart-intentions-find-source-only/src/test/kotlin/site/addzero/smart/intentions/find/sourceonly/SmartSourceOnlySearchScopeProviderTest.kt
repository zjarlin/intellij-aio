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

    fun testExcludesLogFilesInsideSourceRoot() {
        val sourceRoot = myFixture.tempDirFixture.findOrCreateDir("src")
        PsiTestUtil.addSourceRoot(module, sourceRoot)

        val kotlinFile = myFixture.tempDirFixture.createFile("src/App.kt", "class App")
        val logFile = myFixture.tempDirFixture.createFile("src/kcloud-compile.log", "Task :demo")
        val rotatedLogFile = myFixture.tempDirFixture.createFile("src/kcloud-compile.log.1", "Task :demo")

        val scope = SmartSourceOnlySearchScopeProvider()
            .getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT)
            .single()

        assertTrue(scope.contains(kotlinFile))
        assertFalse(scope.contains(logFile))
        assertFalse(scope.contains(rotatedLogFile))
    }

    fun testExcludesFilesUnderLogsDirectoryInsideSourceRoot() {
        val sourceRoot = myFixture.tempDirFixture.findOrCreateDir("src")
        val logsDir = myFixture.tempDirFixture.findOrCreateDir("src/logs")
        PsiTestUtil.addSourceRoot(module, sourceRoot)

        val kotlinFile = myFixture.tempDirFixture.createFile("src/service/UserService.kt", "class UserService")
        val logFile = myFixture.tempDirFixture.createFile("src/logs/build.txt", "Task :demo")

        val scope = SmartSourceOnlySearchScopeProvider()
            .getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT)
            .single()

        assertTrue(scope.contains(kotlinFile))
        assertFalse(scope.contains(logFile))
        assertFalse(scope.contains(logsDir))
    }
}
