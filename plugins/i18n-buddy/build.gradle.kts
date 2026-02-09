plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform") version "+"
}

dependencies {
    implementation("site.addzero:tool-pinyin:2025.10.07")
    intellijPlatform {
        bundledPlugin("org.jetbrains.kotlin")
    }
}
