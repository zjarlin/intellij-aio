plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
    id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlin.get()
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.dotfiles"
        name = "Dotfiles"
    }
}

dependencies {
    implementation(libs.tool.toml)
    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.compose.ui:ui-desktop:${libs.versions.composeMultiplatform.get()}")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:${libs.versions.composeMultiplatform.get()}")
    implementation("org.jetbrains.compose.material:material-desktop:${libs.versions.composeMultiplatform.get()}")
}

repositories {
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}
