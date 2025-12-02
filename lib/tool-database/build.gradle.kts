plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

repositories {
    mavenCentral()
    // 添加本地仓库以解析自定义依赖
    maven {
        url = uri("file:///Users/zjarlin/IdeaProjects/addzero-lib-jvm/repository")
    }
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    
    // 添加您指定的SQL执行器依赖
    implementation(libs.tool.sql.executor)
    
    intellijPlatform {
        bundledPlugin("com.intellij.database")
    }
}

description = "IntelliJ Database插件工具类封装"