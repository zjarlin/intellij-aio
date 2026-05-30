plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}
val libs = versionCatalogs.named("libs")

dependencies {
       implementation(libs.findLibrary("site-addzero-tool-jvmstr").get())




}
