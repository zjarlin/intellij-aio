plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation(project(":lib:ide-component-dynamicform"))
}

// intellijPlatform {
//    pluginConfiguration {
//        id = "site.addzero.ide.component.settings"
//        name = "IDE Component Settings"
//    }
//}
