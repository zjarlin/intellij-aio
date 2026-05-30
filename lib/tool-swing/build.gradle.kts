plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}
val libs = versionCatalogs.named("libs")

dependencies {
    implementation(libs.findLibrary("site-addzero-tool-jvmstr").get())
    implementation(libs.findLibrary("cn-hutool-hutool-core").get())
//    implementation(tool-str)
//找到家目录

}
