plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(kotlin("stdlib"))
    
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))

    implementation(libs.gson)

    // JTE Template Engine
    implementation(libs.jte)
    implementation(libs.jte.kotlin)
}

description = "LSI Code Analyzer Core - POJO元数据扫描核心库（无IDE依赖）"
