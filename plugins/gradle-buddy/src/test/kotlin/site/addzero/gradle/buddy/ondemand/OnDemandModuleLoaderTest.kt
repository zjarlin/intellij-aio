package site.addzero.gradle.buddy.ondemand

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * OnDemandModuleLoader 的单元测试
 * 主要测试依赖解析逻辑
 */
class OnDemandModuleLoaderTest {

    @Test
    fun `test parseProjectDependencies with standard format`() {
        val buildContent = """
            dependencies {
                implementation(project(":lib:tool-swing"))
                implementation(project(":lib:tool-awt"))
                api(project(":checkouts:metaprogramming-lsi:lsi-core"))
                testImplementation(project(":lib:test-utils"))
            }
        """.trimIndent()

        val dependencies = invokeParseProjectDependencies(buildContent)

        assertEquals(4, dependencies.size)
        assertTrue(dependencies.contains(":lib:tool-swing"))
        assertTrue(dependencies.contains(":lib:tool-awt"))
        assertTrue(dependencies.contains(":checkouts:metaprogramming-lsi:lsi-core"))
        assertTrue(dependencies.contains(":lib:test-utils"))
    }

    @Test
    fun `test parseProjectDependencies ignores commented dependencies`() {
        val buildContent = """
            dependencies {
                implementation(project(":lib:tool-swing"))
                // implementation(project(":lib:tool-awt"))
                //    implementation(project(":lib:ignored"))
            }
        """.trimIndent()

        val dependencies = invokeParseProjectDependencies(buildContent)

        assertEquals(1, dependencies.size)
        assertTrue(dependencies.contains(":lib:tool-swing"))
        assertFalse(dependencies.contains(":lib:tool-awt"))
        assertFalse(dependencies.contains(":lib:ignored"))
    }

    @Test
    fun `test parseProjectDependencies with projects accessor format`() {
        val buildContent = """
            dependencies {
                implementation(projects.lib.toolSwing)
                api(projects.checkouts.metaprogrammingLsi.lsiCore)
            }
        """.trimIndent()

        val dependencies = invokeParseProjectDependencies(buildContent)

        assertEquals(2, dependencies.size)
        assertTrue(dependencies.contains(":lib:toolSwing"))
        assertTrue(dependencies.contains(":checkouts:metaprogrammingLsi:lsiCore"))
    }

    @Test
    fun `test parseProjectDependencies with mixed formats`() {
        val buildContent = """
            dependencies {
                implementation(project(":lib:tool-swing"))
                implementation(projects.lib.toolAwt)
                api(project(":checkouts:metaprogramming-lsi:lsi-core"))
                // implementation(project(":lib:ignored"))
            }
        """.trimIndent()

        val dependencies = invokeParseProjectDependencies(buildContent)

        assertEquals(3, dependencies.size)
        assertTrue(dependencies.contains(":lib:tool-swing"))
        assertTrue(dependencies.contains(":lib:toolAwt"))
        assertTrue(dependencies.contains(":checkouts:metaprogramming-lsi:lsi-core"))
    }

    @Test
    fun `test parseProjectDependencies with various dependency configurations`() {
        val buildContent = """
            dependencies {
                implementation(project(":lib:impl"))
                api(project(":lib:api"))
                compileOnly(project(":lib:compile-only"))
                runtimeOnly(project(":lib:runtime-only"))
                testImplementation(project(":lib:test-impl"))
                testCompileOnly(project(":lib:test-compile"))
                kapt(project(":lib:kapt-processor"))
            }
        """.trimIndent()

        val dependencies = invokeParseProjectDependencies(buildContent)

        assertEquals(7, dependencies.size)
        assertTrue(dependencies.contains(":lib:impl"))
        assertTrue(dependencies.contains(":lib:api"))
        assertTrue(dependencies.contains(":lib:compile-only"))
        assertTrue(dependencies.contains(":lib:runtime-only"))
        assertTrue(dependencies.contains(":lib:test-impl"))
        assertTrue(dependencies.contains(":lib:test-compile"))
        assertTrue(dependencies.contains(":lib:kapt-processor"))
    }

    @Test
    fun `test parseProjectDependencies with empty content`() {
        val buildContent = ""

        val dependencies = invokeParseProjectDependencies(buildContent)

        assertTrue(dependencies.isEmpty())
    }

    @Test
    fun `test parseProjectDependencies with no project dependencies`() {
        val buildContent = """
            dependencies {
                implementation("com.google.code.gson:gson:2.10.1")
                testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
            }
        """.trimIndent()

        val dependencies = invokeParseProjectDependencies(buildContent)

        assertTrue(dependencies.isEmpty())
    }

    /**
     * 使用反射调用私有方法 parseProjectDependencies
     */
    private fun invokeParseProjectDependencies(content: String): Set<String> {
        val method: Method = OnDemandModuleLoader::class.java
            .getDeclaredMethod("parseProjectDependencies", String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(OnDemandModuleLoader, content) as Set<String>
    }
}
