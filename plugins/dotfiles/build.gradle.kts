plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.dotfiles"
        name = "Dotfiles"
    }
}

dependencies {
    implementation(libs.tool.toml)
    implementation(libs.gson)
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:${libs.versions.kotlin.get()}")
}
