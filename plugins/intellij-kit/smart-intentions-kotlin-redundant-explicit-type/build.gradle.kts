import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation(project(":plugins:intellij-kit:smart-intentions-core"))

    intellijPlatform {
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}
