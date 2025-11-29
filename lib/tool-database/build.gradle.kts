plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}


dependencies {
    implementation(kotlin("stdlib"))

    // 添加您指定的SQL执行器依赖
    implementation("site.addzero:tool-sql-executor:2025.11.26")

    intellijPlatform {
        bundledPlugin("com.intellij.database")
    }
}

description = "IntelliJ Database插件工具类封装"
