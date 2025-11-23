plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    
    intellijPlatform {
        bundledPlugin("com.intellij.database")
    }
}

description = "IntelliJ Database插件工具类封装"
