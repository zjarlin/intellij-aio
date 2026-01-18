plugins {
  id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
  implementation(project(":plugins:gradle-buddy:gradle-buddy-core"))
  implementation("site.addzero:tool-api-maven:2025.12.04")

}
