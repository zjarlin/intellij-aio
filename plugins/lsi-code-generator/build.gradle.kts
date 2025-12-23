plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(project(":lib:lsi-code-analyzer-core"))
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-core"))
//    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-parser"))
    implementation(project(":checkouts:lsi:lsi-core"))
    implementation(project(":checkouts:lsi:lsi-database"))
    implementation(project(":lib:tool-template"))

    implementation(libs.gson)
    implementation(libs.tool.jvmstr)
}

description = "LSI Code Generator - 代码生成器 (DDL等)"
