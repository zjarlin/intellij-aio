plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
        name = pluginName
//        version = "2026.01.31"
    }
}

dependencies {
    implementation(project(":plugins:gradle-buddy:gradle-buddy-core"))
    implementation(project(":plugins:gradle-buddy:gradle-buddy-intentions"))
    implementation(project(":plugins:gradle-buddy:gradle-buddy-migration"))
    implementation(project(":plugins:gradle-buddy:gradle-buddy-tasks"))
    implementation(project(":plugins:gradle-buddy:id-fixer"))
    implementation(project(":plugins:gradle-buddy:gradle-buddy-fix-catalog-ref"))
}
