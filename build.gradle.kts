plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    implementation(project(":lib:tool-psi"))
    implementation(project(":lib:tool-psi-toml"))
    implementation(project(":lib:tool-common"))
    implementation("site.addzero:tool-jvmstr:+")
    implementation("site.addzero:tool-reflection:+")

}
