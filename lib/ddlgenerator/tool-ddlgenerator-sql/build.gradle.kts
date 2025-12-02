plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(kotlin("stdlib"))

    // 依赖 core 模块
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-core"))

    // 工具依赖
    implementation(libs.hutool.core)
    implementation(libs.tool.jvmstr)

    // 测试依赖
//    testImplementation(libs.junit.jupiter)
//    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

//tasks.test {
//    useJUnitPlatform()
//}

description = "DDL Generator SQL - SQL方言和生成器"
