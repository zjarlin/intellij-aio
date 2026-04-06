import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform") version "+"
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
        name = pluginName
        version = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("252")
        untilBuild.set("262.*")
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
    implementation(project(":plugins:gradle-buddy:gradle-buddy-search"))
    implementation(project(":plugins:gradle-buddy:gradle-buddy-core"))
    implementation(project(":plugins:gradle-buddy:gradle-buddy-buildlogic"))
    implementation(project(":plugins:gradle-buddy:git-fixer"))
}
