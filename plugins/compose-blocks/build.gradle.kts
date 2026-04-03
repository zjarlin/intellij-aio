import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.compose-blocks"
        name = "Compose Blocks"
    }
}

dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}

tasks {
    patchPluginXml {
        sinceBuild.set("252")
        untilBuild.set(provider { null })
    }
}

listOf(
    "buildSearchableOptions",
    "prepareJarSearchableOptions",
    "jarSearchableOptions",
).forEach { taskName ->
    tasks.named(taskName) {
        enabled = false
    }
}
