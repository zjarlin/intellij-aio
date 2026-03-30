package site.addzero.smart.intentions.find.sourceonly

import com.intellij.find.FindModel
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SmartSourceOnlyFindInProjectExtensionTest : BasePlatformTestCase() {
    fun testUsesSourceOnlyScopeForProjectSearch() {
        val sourceRoot = myFixture.tempDirFixture.findOrCreateDir("src")
        val generatedRoot = myFixture.tempDirFixture.findOrCreateDir("build/generated")
        PsiTestUtil.addSourceRoot(module, sourceRoot)

        val sourceFile = myFixture.tempDirFixture.createFile("src/App.kt", "class App")
        val generatedFile = myFixture.tempDirFixture.createFile("build/generated/AppGenerated.kt", "class AppGenerated")
        val model = FindModel().apply {
            isProjectScope = true
        }

        val changed = SmartSourceOnlyFindInProjectExtension().initModelFromContext(
            model,
            SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .build(),
        )

        assertTrue(changed)
        assertFalse(model.isProjectScope)
        assertTrue(model.isCustomScope)
        val scope = model.customScope
        assertNotNull(scope)
        assertTrue(scope!!.contains(sourceFile))
        assertFalse(scope.contains(generatedFile))
        assertFalse(scope.contains(generatedRoot))
    }

    fun testKeepsExplicitDirectoryScopeUntouched() {
        val model = FindModel().apply {
            isProjectScope = true
            directoryName = project.basePath
        }

        val changed = SmartSourceOnlyFindInProjectExtension().initModelFromContext(
            model,
            SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .build(),
        )

        assertFalse(changed)
        assertTrue(model.isProjectScope)
        assertFalse(model.isCustomScope)
    }
}
