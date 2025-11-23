plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
dependencies {

    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-intellij"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-psi"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-kt"))
    implementation(project(":lib:tool-swing"))
    implementation(project(":lib:tool-awt"))
    implementation(project(":lib:tool-psi-toml"))
    implementation(project(":lib:ide-component-settings-old"))


    implementation("site.addzero:tool-ai:+")
    implementation("site.addzero:tool-toml:+")
    implementation("site.addzero:tool-io-codegen:+")
    implementation("site.addzero:tool-str:2025.11.27")
    implementation("site.addzero:tool-jvmstr:2025.11.27")
    implementation("site.addzero:tool-coll:+")
    implementation("site.addzero:tool-reflection:+")

}
