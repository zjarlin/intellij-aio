plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(project(":lib:lsi-code-analyzer-core"))
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-core"))
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-sql"))
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-parser"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))

    implementation(libs.gson)
    implementation(libs.tool.jvmstr)

    // JTE Template Engine
    implementation(libs.jte)
    implementation(libs.jte.kotlin)
}

description = "LSI Code Generator - 代码生成器 (DDL等)"
