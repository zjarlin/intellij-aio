plugins {
  id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
  implementation(project(":plugins:gradle-buddy:gradle-buddy-core"))
  implementation(project(":plugins:gradle-buddy:id-fixer"))
  compileOnly(project(":plugins:maven-buddy-core"))
  compileOnly("site.addzero:tool-api-maven:2025.12.04")
  intellijPlatform {
    bundledPlugin("org.jetbrains.plugins.gradle")
  }
}
