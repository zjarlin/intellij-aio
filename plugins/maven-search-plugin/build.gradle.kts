plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.intellij.platform.module)
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.jetbrains.space/addzero/p/addzero/maven")
    }
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(libs.versions.intellij.asProvider().get())
        instrumentationTools()
    }
    
    implementation(kotlin("stdlib"))
    
    // Maven Central API 工具类
    implementation("site.addzero:tool-api-maven:2025.11.27")
}

intellijPlatform {
    buildSearchableOptions = false
}
