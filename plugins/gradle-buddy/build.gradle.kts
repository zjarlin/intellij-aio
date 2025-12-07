plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
        name = pluginName
    }
}

dependencies {
    intellijPlatform {
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("com.intellij.java")
    }
    implementation(libs.tool.api.maven)
    
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}
