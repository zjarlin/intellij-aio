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
    implementation(libs.hutool.core)
    implementation(libs.tool.database.model)
    implementation(libs.tool.jvmstr)
    implementation(libs.tool.pinyin)

    // 测试依赖
//    testImplementation(libs.junit.jupiter)
//    testImplementation(libs.mockito.kotlin)
//    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

//tasks.test {
//    useJUnitPlatform()
//}

description = "DDL Generator Parser - 基于LSI的DDL解析器"
