import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.compose-buddy"
        name = "compose-buddy"
        version = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

dependencies {
    implementation(project(":plugins:compose-buddy:compose-buddy-blocks"))
    implementation(project(":plugins:compose-buddy:compose-buddy-designer"))
    implementation(project(":plugins:ide-kit:smart-intentions-core"))

    intellijPlatform {
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
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
    archiveBaseName.set("compose-buddy")
}
