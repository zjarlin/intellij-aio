import gradle.kotlin.dsl.accessors._32f2ece88069c76c138c610e06db24a1.intellijPlatform

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
       intellijPlatform {
//        intellijIdeaUltimate("2025.2.3")
        bundledPlugins(
            "git4idea"
        )
        // 添加 SSH 相关插件依赖
        plugin("com.intellij.ssh")
        plugin("com.intellij.remote")
    }

    // SSH 依赖由 IDE 提供
}
