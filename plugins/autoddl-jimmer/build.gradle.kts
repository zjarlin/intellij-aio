
plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    // LSI 核心依赖
    implementation(project(":lib-git:metaprogramming-lsi:lsi-core"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-database"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-intellij"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-psi"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-kt2"))  // K2 Analysis API
    implementation(project(":lib-git:metaprogramming-lsi:lsi-psiandkt"))

    // DDL Generator
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator"))

    // UI 组件
    implementation(project(":lib:tool-swing"))
    implementation(project(":lib:tool-awt"))
    implementation(project(":lib:ide-component-settings"))

    implementation("site.addzero:tool-database-model:2025.12.04")
    // SQL Executor (关键依赖)
    implementation("site.addzero:tool-sql-executor:2025.11.26")

    // 工具类
    implementation(libs.tool.str)
    implementation(libs.tool.coll)
    implementation(libs.tool.io.codegen)

    // YAML 解析（用于读取 Spring 配置）
    implementation("org.yaml:snakeyaml:2.2")
}
