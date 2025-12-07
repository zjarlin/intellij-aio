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
    // LSI 核心依赖
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-database"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-intellij"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-psiandkt"))

    // DDL Generator
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator"))
    implementation("site.addzero:tool-database-model:2025.12.04")

    implementation(libs.gson)
    implementation(libs.tool.jvmstr)

    // JSON to Kotlin Data Class
    implementation(libs.json2kotlin.dataclass)
}

description = "LSI Code Analyzer IDE Plugin - POJO元数据扫描与代码生成 IDE 工具窗口"
