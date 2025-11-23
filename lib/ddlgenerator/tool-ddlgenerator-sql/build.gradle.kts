plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(kotlin("stdlib"))

    // 依赖 core 模块
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator-core"))

    // 工具依赖
    implementation("cn.hutool:hutool-core:+")
    implementation("site.addzero:tool-jvmstr:+")

    // 测试依赖
//    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
//    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

//tasks.test {
//    useJUnitPlatform()
//}

description = "DDL Generator SQL - SQL方言和生成器"
