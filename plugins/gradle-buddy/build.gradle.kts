plugins {
  id("site.addzero.buildlogic.intellij.intellij-platform") version "+"
}

val pluginName = project.name
intellijPlatform {
  pluginConfiguration {
    id = "site.addzero.$pluginName"
    name = pluginName
        version = "2026.02.16"
  }
}

dependencies {
  implementation(project(":plugins:gradle-buddy:gradle-buddy-core"))
  implementation(project(":plugins:gradle-buddy:gradle-buddy-intentions"))
  implementation(project(":plugins:gradle-buddy:gradle-buddy-migration"))
  implementation(project(":plugins:gradle-buddy:gradle-buddy-tasks"))
  implementation(project(":plugins:gradle-buddy:id-fixer"))
  implementation(project(":plugins:gradle-buddy:gradle-buddy-fix-catalog-ref"))
  implementation(project(":plugins:gradle-buddy:gradle-buddy-linemarker"))
  implementation(project(":plugins:gradle-buddy:gradle-buddy-wrapper"))
  implementation(project(":plugins:gradle-buddy:gradle-buddy-core"))
  implementation(project(":plugins:gradle-buddy:gradle-buddy-buildlogic"))
}
