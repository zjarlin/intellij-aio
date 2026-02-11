plugins {
  id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
  implementation(libs.site.addzero.tool.api.maven)
  implementation(libs.org.xerial.sqlite.jdbc)
}
