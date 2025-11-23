plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation("site.addzero:tool-ai:+")
    implementation(project(":lib:ide-component-dynamicform"))
}
