import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.gradle.api.tasks.bundling.Zip

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform") version "+"
}

val pluginId = "site.addzero.smart-intentions"
val pluginName = "ide-kit"

intellijPlatform {
    pluginConfiguration {
        id = pluginId
        name = pluginName
        version = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("252")
        untilBuild.set("262.*")
    }
}

dependencies {
    implementation(project(":plugins:ide-kit:smart-intentions-core"))
    implementation(project(":plugins:ide-kit:smart-intentions-find-source-only"))
    implementation(project(":plugins:ide-kit:smart-intentions-hidden-files"))
    implementation(project(":plugins:ide-kit:smart-intentions-kotlin-redundant-explicit-type"))
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

tasks.named<Zip>("buildPlugin") {
    archiveBaseName.set(pluginName)
}
