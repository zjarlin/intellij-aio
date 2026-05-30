plugins {
  id("site.addzero.buildlogic.intellij.intellij-core")
}
val libs = versionCatalogs.named("libs")

dependencies {
  implementation(libs.findLibrary("site-addzero-tool-api-maven").get())
   implementation(libs.findLibrary("org-xerial-sqlite-jdbc-v3").get())

}

