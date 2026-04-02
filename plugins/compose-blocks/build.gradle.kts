plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.compose-blocks"
        name = "Compose Blocks"
    }
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
