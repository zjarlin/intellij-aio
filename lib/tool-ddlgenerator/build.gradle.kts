plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation("site.addzero:tool-jvmstr:+")
    implementation("site.addzero:tool-database-model:+")
    implementation("site.addzero:tool-pinyin:+")
}

description = "ddl生成工具类"
