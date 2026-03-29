import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}


dependencies {
    implementation(libs.org.xerial.sqlite.jdbc.v3)

    intellijPlatform {
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
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
//
//tasks.named<Test>("test") {
//    useJUnit()
//}
