plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.lsi-code-analyzer"
        name = "LSI Code Analyzer"
    }
}

dependencies {
    // LSI 核心依赖
    implementation(project(":checkouts:lsi:lsi-core"))
    implementation(project(":checkouts:lsi:lsi-database"))
    implementation(project(":checkouts:lsi:lsi-intellij"))
    implementation(project(":checkouts:lsi:lsi-psiandkt"))

    // DDL Generator
    implementation(project(":lib:ddlgenerator:tool-ddlgenerator"))
    implementation("site.addzero:tool-database-model:2025.12.04")
    implementation("site.addzero:tool-sql-executor:2025.11.26")
    implementation("site.addzero:tool-jdbc:2025.10.07")
    implementation(libs.gson)
    implementation(libs.tool.jvmstr)

    // JSON to Kotlin Data Class
    implementation(libs.json2kotlin.dataclass)



    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("org.postgresql:postgresql:42.3.3")
//    implementation("com.oracle.database.jdbc:ojdbc8:21.5.0.0")
//    implementation("com.microsoft.sqlserver:mssql-jdbc:10.2.0.jre8")
    implementation("com.h2database:h2:2.2.224")
    implementation("org.xerial:sqlite-jdbc:3.39.3.0")
//    implementation("com.dameng:DmJdbcDriver18:8.1.2.141")
//    implementation("cn.com.kingbase:kingbase8:8.6.0")
//    implementation("com.oceanbase:oceanbase-client:2.4.5")
//    implementation("com.huawei.gauss:gaussdb-jdbc:4.9.0")
//    implementation("com.aliyun:polardb-jdbc:1.0.0")
//    implementation("io.tidb:tidb-jdbc:0.1.0")
//    implementation("com.ibm.db2:jcc:11.5.8.0")
//    compileOnly("com.taosdata:taos-jdbcdriver:3.2.7")

    implementation("com.taosdata.jdbc:taos-jdbcdriver:3.7.8")



}

description = "LSI Code Analyzer IDE Plugin - POJO元数据扫描与代码生成 IDE 工具窗口"
