plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation("site.addzero:tool-jvmstr:+")
    implementation("site.addzero:tool-database-model:+")
    implementation("site.addzero:tool-pinyin:+")
    implementation("cn.hutool:hutool-core:+")
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))

}

description = "ddl生成工具类"
