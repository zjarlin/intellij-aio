plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-intellij"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-psi"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-kt"))

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("site.addzero:tool-jvmstr:+")
}

description = "LSI Code Analyzer - 元数据扫描与缓存"
