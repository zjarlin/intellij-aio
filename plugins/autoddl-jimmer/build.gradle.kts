
plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    // LSI 核心依赖
//    implementation(project(":checkouts:lsi:lsi-core"))

//    implementation(project(":checkouts:lsi:lsi-intellij"))
//    implementation(project(":checkouts:lsi:lsi-psi"))
//    implementation(project(":checkouts:lsi:lsi-kt2"))  // K2 Analysis API
//    implementation(project(":checkouts:lsi:lsi-psiandkt"))


    // UI 组件
    implementation(project(":lib:tool-swing"))
    implementation(project(":lib:tool-awt"))
    implementation(project(":lib:ide-component-settings"))

    implementation("site.addzero:tool-database-model:2025.12.04")
    // SQL Executor (关键依赖)
    implementation("site.addzero:tool-sql-executor:2025.11.26")

    // 工具类
    implementation(libs.site.addzero.tool.str.v2025)
    implementation(libs.site.addzero.tool.coll)
    implementation(libs.site.addzero.tool.io.codegen)

    // YAML 解析（用于读取 Spring 配置）
    implementation("org.yaml:snakeyaml:2.2")
}
