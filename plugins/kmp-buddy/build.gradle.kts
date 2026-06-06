import org.gradle.internal.impldep.org.joda.time.LocalDate
import org.gradle.internal.impldep.org.joda.time.format.DateTimeFormatter
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}


dependencies {
    implementation(project(":plugins:kmp-buddy:kmp-buddy-blocks"))
    implementation(project(":plugins:kmp-buddy:kmp-buddy-designer"))
    implementation(project(":plugins:kmp-buddy:kmp-buddy-preview"))
    implementation(project(":plugins:kmp-buddy:kmp-buddy-preview-sandbox"))
    implementation(project(":plugins:kmp-buddy:smart-intentions-koin-redundant-dependency"))
    implementation(project(":plugins:kmp-buddy:smart-intentions-koin-single-binds"))
    implementation(project(":plugins:ide-kit:smart-intentions-core"))

    intellijPlatform {
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("org.jetbrains.kotlin")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
//        name = "Gitee"
    }
}
