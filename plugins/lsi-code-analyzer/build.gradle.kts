plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.lsi-code-analyzer"
        name = "LSI Code Analyzer"
    }
}

dependencies {
    // 核心库（无 IDE 依赖）
    implementation(project(":lib:lsi-code-analyzer-core"))
    // IDE 相关依赖
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-intellij"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-psi"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-kt"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-psiandkt"))

    implementation(libs.gson)
    implementation(libs.tool.jvmstr)

    // JSON to Kotlin Data Class
    implementation(libs.json2kotlin.dataclass)
}

description = "LSI Code Analyzer IDE Plugin - POJO元数据扫描与代码生成 IDE 工具窗口"
