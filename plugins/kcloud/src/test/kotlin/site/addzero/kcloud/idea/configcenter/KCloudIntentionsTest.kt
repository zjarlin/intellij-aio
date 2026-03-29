package site.addzero.kcloud.idea.configcenter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KCloudIntentionsTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String {
        return ""
    }

    fun testReplacesConstLocalhostUrlWithLocalEndpointAccessor() {
        myFixture.configureByText(
            "McuConsoleApiClient.jvm.kt",
            """
            package sample
            
            private const val defaultBaseUrl = "<caret>http://localhost:18080/"
            """.trimIndent(),
        )

        myFixture.launchAction(myFixture.findSingleIntention("替换为 KCloud 本地端点"))

        myFixture.checkResult(
            """
            package sample
            
            import site.addzero.kcloud.KCloudLocalServerEndpoint
            
            private val defaultBaseUrl = KCloudLocalServerEndpoint.currentBaseUrl()
            """.trimIndent(),
        )
    }

    fun testReplacesLoopbackUrlWithLocalEndpointAccessor() {
        myFixture.configureByText(
            "LoopbackUrl.kt",
            """
            package sample
            
            private val defaultBaseUrl = "<caret>http://127.0.0.1:18080/"
            """.trimIndent(),
        )

        myFixture.launchAction(myFixture.findSingleIntention("替换为 KCloud 本地端点"))

        myFixture.checkResult(
            """
            package sample
            
            import site.addzero.kcloud.KCloudLocalServerEndpoint
            
            private val defaultBaseUrl = KCloudLocalServerEndpoint.currentBaseUrl()
            """.trimIndent(),
        )
    }

    fun testDoesNotOfferLocalEndpointIntentionForUnrelatedUrl() {
        myFixture.configureByText(
            "ExternalUrl.kt",
            """
            package sample
            
            private val defaultBaseUrl = "<caret>https://example.com/"
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("替换为 KCloud 本地端点")
        assertEmpty(actions)
    }

    fun testExistingConfigCenterExtractionStillAvailableForRegularLiteral() {
        myFixture.configureByText(
            "RegularLiteral.kt",
            """
            package sample
            
            private val greeting = "<caret>Hello KCloud"
            """.trimIndent(),
        )

        val actions = myFixture.filterAvailableIntentions("提取到 KCloud 配置中心")
        assertSize(1, actions)
    }
}
