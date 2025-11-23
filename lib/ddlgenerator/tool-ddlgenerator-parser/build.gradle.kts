plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
//    implementation(kotlin("stdlib"))

    // 依赖 core 模块
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-core"))

    // 依赖 LSI 核心
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))

    // 工具依赖
    implementation("cn.hutool:hutool-core:+")
    implementation("site.addzero:tool-database-model:+")
    implementation("site.addzero:tool-jvmstr:+")
    implementation("site.addzero:tool-pinyin:+")
}

description = "DDL Generator Parser - 基于LSI的DDL解析器"
