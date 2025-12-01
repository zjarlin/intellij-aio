plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    intellijPlatform {
        plugin("com.intellij.ssh")
        plugin("com.intellij.remote")
    }
}
