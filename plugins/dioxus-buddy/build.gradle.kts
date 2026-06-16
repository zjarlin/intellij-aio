plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

val pluginName = project.name
intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.$pluginName"
//        name = "Dioxus Buddy"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}
