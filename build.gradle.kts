
plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    implementation(project(":lib:tool-psi"))
    implementation(project(":lib:tool-common"))
}
