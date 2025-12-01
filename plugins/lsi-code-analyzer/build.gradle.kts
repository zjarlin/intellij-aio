plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-intellij"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-psi"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-kt"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-psiandkt"))

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("site.addzero:tool-jvmstr:+")
    
    // JSON to Kotlin Data Class
    implementation("site.addzero:json2kotlin-dataclass:2025.11.33")
    
    // JTE Template Engine
    implementation("gg.jte:jte:3.1.12")
    implementation("gg.jte:jte-kotlin:3.1.12")
}

description = "LSI Code Analyzer - POJO元数据扫描与代码生成"
