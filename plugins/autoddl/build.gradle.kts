
plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
intellijPlatform {
    pluginConfiguration {
        id = "com.addzero.autoddl"
        name = "AutoDDL"
    }
}

dependencies {

//    implementation(project(":checkouts:lsi:lsi-core"))

//    implementation(project(":checkouts:lsi:lsi-intellij"))
//    implementation(project(":checkouts:lsi:lsi-psi"))
//    implementation(project(":checkouts:lsi:lsi-kt"))
    implementation(project(":lib:tool-swing"))
    implementation(project(":lib:tool-awt"))
    implementation(project(":lib:tool-psi-toml"))
    implementation(project(":lib:ide-component-settings-old"))


    implementation(libs.site.addzero.tool.ai)
    implementation(libs.site.addzero.tool.toml)
    implementation(libs.site.addzero.tool.io.codegen)
    implementation(libs.site.addzero.tool.str.v2026)
    implementation(libs.site.addzero.tool.jvmstr)
    implementation(libs.site.addzero.tool.coll)
    implementation(libs.site.addzero.tool.reflection)

}
