plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(project(":plugins:lsi-code-analyzer"))
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-core"))
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-sql"))
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-parser"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("site.addzero:tool-jvmstr:+")
    
    // JTE Template Engine
    implementation("gg.jte:jte:3.1.12")
    implementation("gg.jte:jte-kotlin:3.1.12")
}

description = "LSI Code Generator - 代码生成器 (DDL等)"
