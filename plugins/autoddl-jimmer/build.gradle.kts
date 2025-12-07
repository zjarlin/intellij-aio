
plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}


dependencies {
    intellijPlatform {
        bundledPlugins(
            "com.intellij.database",
        )
    }
}



dependencies {
    // LSI 核心依赖
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-database"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-intellij"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-psi"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-kt"))

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
}
