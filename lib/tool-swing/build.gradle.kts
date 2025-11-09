plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
       implementation("site.addzero:tool-jvmstr:+")

    implementation(project(":lib:tool-common"))


}
