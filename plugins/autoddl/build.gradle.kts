plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
dependencies {
    implementation(project(":lib:tool-psi"))
    implementation(project(":lib:tool-swing"))
    implementation(project(":lib:tool-awt"))
    implementation(project(":lib:tool-psi-toml"))

    implementation("site.addzero:tool-ai:+")
    implementation("site.addzero:tool-io-codegen:+")
    implementation("site.addzero:tool-str:2025.11.27")
    implementation("site.addzero:tool-coll:+")
    implementation("site.addzero:tool-reflection:+")
}
