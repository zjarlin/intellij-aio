plugins {
  id("site.addzero.buildlogic.intellij.intellij-platform")
}
dependencies {
  implementation(libs.site.addzero.tool.api.maven)

  implementation(project(":plugins:maven-buddy-core"))
}

