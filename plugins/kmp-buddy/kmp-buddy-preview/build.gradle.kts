plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation(project(":plugins:kmp-buddy:kmp-buddy-designer"))
    implementation(project(":plugins:ide-kit:smart-intentions-core"))

    intellijPlatform {
        bundledPlugin("org.jetbrains.kotlin")
    }
}
