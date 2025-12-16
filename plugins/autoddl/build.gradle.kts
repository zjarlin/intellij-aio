
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

    implementation(project(":lib-git:metaprogramming-lsi:lsi-core"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-database"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-intellij"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-psi"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-kt"))
    implementation(project(":lib:tool-swing"))
    implementation(project(":lib:tool-awt"))
    implementation(project(":lib:tool-psi-toml"))
    implementation(project(":lib:ide-component-settings-old"))


    implementation(libs.tool.ai)
    implementation(libs.tool.toml)
    implementation(libs.tool.io.codegen)
    implementation(libs.tool.str)
    implementation(libs.tool.jvmstr)
    implementation(libs.tool.coll)
    implementation(libs.tool.reflection)

}
