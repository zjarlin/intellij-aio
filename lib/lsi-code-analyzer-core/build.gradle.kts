plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(kotlin("stdlib"))
    
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))

    implementation("com.google.code.gson:gson:2.10.1")

    // JTE Template Engine
    implementation("gg.jte:jte:3.1.12")
    implementation("gg.jte:jte-kotlin:3.1.12")
}

description = "LSI Code Analyzer Core - POJO元数据扫描核心库（无IDE依赖）"
