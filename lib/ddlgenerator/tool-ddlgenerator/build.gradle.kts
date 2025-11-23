plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    // 依赖 core 模块
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-core"))
    
    implementation("site.addzero:tool-jvmstr:+")
    implementation("site.addzero:tool-database-model:+")
    implementation("site.addzero:tool-pinyin:+")
    implementation("cn.hutool:hutool-core:+")
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
}

description = "ddl生成工具类"
