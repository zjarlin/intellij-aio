package site.addzero.composebuddy.features.koincollection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

class KoinCollectionInjectionSupportTest : BasePlatformTestCase() {
    fun testFindsOnlyKoinConstructorListInterfaceInjectionTargets() {
        val interfaceFile = myFixture.addFileToProject(
            "src/demo/AppScreen.kt",
            """
            package demo

            interface AppScreen
            interface OtherScreen
            class PlainScreen
            """.trimIndent(),
        ) as KtFile

        val consumerFile = myFixture.addFileToProject(
            "src/demo/ScaffoldScreenViewModel.kt",
            """
            package demo

            import org.koin.core.annotation.Factory
            import org.koin.core.annotation.Scoped
            import org.koin.core.annotation.Single
            import org.koin.core.annotation.Singleton

            @Single
            class ScaffoldScreenViewModel(
                private val appScreens: List<AppScreen>,
                private val mutableScreens: MutableList<AppScreen>,
                private val directScreen: AppScreen,
                private val plainScreens: List<PlainScreen>,
                private val otherScreens: List<OtherScreen>,
            ) {
                private val screens = KoinPlatform.getKoin().getAll<AppScreen>()
                private val routePlugins = getKoin().getAll<AppScreen>()
                private val ignored = repository.getAll<AppScreen>()
            }

            @Single
            class HomeAppScreen : AppScreen

            @Singleton
            class CachedAppScreen : AppScreen

            @Factory
            class FactoryAppScreen : AppScreen

            @Scoped
            class ScopedAppScreen : AppScreen

            @Single([OtherScreen::class])
            class BoundOtherScreen

            class NotInjected(private val appScreens: List<AppScreen>)

            fun render(appScreens: List<AppScreen>) {
            }
            """.trimIndent(),
        ) as KtFile

        val customAnnotationFile = myFixture.addFileToProject(
            "src/other/CustomAnnotated.kt",
            """
            package other

            import demo.AppScreen

            annotation class Single

            @Single
            class CustomAnnotated(private val appScreens: List<AppScreen>) : AppScreen
            """.trimIndent(),
        ) as KtFile

        val appScreen = interfaceFile.findClass("AppScreen")
        val plainScreen = interfaceFile.findClass("PlainScreen")

        val targets = KoinCollectionInjectionSupport.findCollectionInjectionTargets(appScreen)
        val targetTexts = targets.map { target -> target.text }

        assertEquals(3, targets.size)
        assertTrue(targetTexts.contains("List<AppScreen>"))
        assertTrue(targetTexts.contains("KoinPlatform.getKoin().getAll<AppScreen>()"))
        assertTrue(targetTexts.contains("getKoin().getAll<AppScreen>()"))
        assertFalse(targetTexts.contains("repository.getAll<AppScreen>()"))
        assertTrue(KoinCollectionInjectionSupport.findCollectionInjectionTargets(plainScreen).isEmpty())

        assertKoinNavigation(consumerFile.findClass("HomeAppScreen"), "Single", "AppScreen")
        assertKoinNavigation(consumerFile.findClass("CachedAppScreen"), "Singleton", "AppScreen")
        assertKoinNavigation(consumerFile.findClass("FactoryAppScreen"), "Factory", "AppScreen")
        assertKoinNavigation(consumerFile.findClass("BoundOtherScreen"), "Single", "OtherScreen")
        assertNull(KoinCollectionInjectionSupport.findCollectionInjectionNavigation(consumerFile.findClass("ScopedAppScreen")))
        assertNull(KoinCollectionInjectionSupport.findCollectionInjectionNavigation(customAnnotationFile.findClass("CustomAnnotated")))
    }

    private fun assertKoinNavigation(componentClass: KtClass, annotationName: String, interfaceName: String) {
        val navigation = KoinCollectionInjectionSupport.findCollectionInjectionNavigation(componentClass)
        assertNotNull(navigation)
        assertEquals(annotationName, navigation!!.anchor.text)
        assertEquals(listOf(interfaceName), navigation.interfaceNames)
        assertTrue(navigation.targets.isNotEmpty())
    }

    private fun KtFile.findClass(name: String): KtClass {
        return declarations
            .filterIsInstance<KtClass>()
            .single { ktClass -> ktClass.name == name }
    }
}
