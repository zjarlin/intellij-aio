import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation(project(":plugins:ide-kit:smart-intentions-core"))

    intellijPlatform {
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}
