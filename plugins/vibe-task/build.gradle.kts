plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.vibetask"
        name = "Vibe Task"
    }
}

dependencies {
    // 无外部依赖，使用平台自带功能
}
