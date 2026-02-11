plugins {
  id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
  implementation(project(":plugins:gradle-buddy:gradle-buddy-core"))
  implementation(project(":plugins:gradle-buddy:id-fixer"))
  implementation(project(":plugins:gradle-buddy:gradle-buddy-search"))
  compileOnly("site.addzero:tool-api-maven:2025.12.04")
  intellijPlatform {
    bundledPlugin("org.jetbrains.plugins.gradle")
  }
}
