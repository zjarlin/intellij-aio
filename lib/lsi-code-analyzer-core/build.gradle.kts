plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":checkouts:lsi:lsi-core"))
    implementation(project(":checkouts:lsi:lsi-database"))
    implementation("site.addzero:tool-database-model:2025.11.15")
    implementation(libs.gson)
    // FreeMarker Template Engine
    implementation("org.freemarker:freemarker:2.3.32")
}

description = "LSI Code Analyzer Core - POJO元数据扫描核心库（无IDE依赖）"
