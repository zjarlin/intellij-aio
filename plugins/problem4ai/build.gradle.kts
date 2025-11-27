plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    // IntelliJ Platform 依赖由插件自动添加
}

// 禁用 buildSearchableOptions 任务（开发阶段不需要）
tasks.named("buildSearchableOptions") {
    enabled = false
}
