plugins {
  id("site.addzero.buildlogic.intellij.intellij-platform")
}
dependencies {
  implementation(libs.tool.api.maven)
  implementation(libs.sqlite.jdbc)
}

