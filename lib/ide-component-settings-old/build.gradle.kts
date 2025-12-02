plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation(libs.tool.ai)
    implementation(project(":lib:ide-component-dynamicform"))
}
