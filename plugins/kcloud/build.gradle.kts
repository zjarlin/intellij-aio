import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

group = "site.addzero"

base {
    archivesName.set("kcloud")
}

dependencies {
    implementation(libs.org.xerial.sqlite.jdbc.v3)

    intellijPlatform {
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}

//intellijPlatform {
//    pluginConfiguration {
//        id = "site.addzero.kcloud"
//        name = "kcloud"
//    }
//}

//tasks.named<Zip>("buildPlugin") {
//    archiveBaseName.set("kcloud")
//}

//listOf(
//    "buildSearchableOptions",
//    "prepareJarSearchableOptions",
//    "jarSearchableOptions",
//).forEach { taskName ->
//    tasks.named(taskName) {
//        enabled = false
//    }
//}
