import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val sinceVersion by extra("223.7571.182")
val untilVersion by extra("243.*")
plugins {
//    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "latest.release"
//    id("org.jetbrains.intellij.platform") version "2.1.0"
}

//pluginManagement {
//    repositories {
//        maven("https://oss.sonatype.org/content/repositories/snapshots/")
//        gradlePluginPortal()
//    }
//}

group = "com.addzero"
version = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
configurations.all {
    resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.DEPENDENCY_FIRST)
}
repositories {
    mavenCentral()

    mavenLocal()
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    maven { url = uri("https://mirrors.huaweicloud.com/repository/maven/") }
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven { url = uri("https://repo.spring.io/milestone") }
}

intellij {
    plugins.set(
        listOf(
            "com.intellij.java", "org.jetbrains.kotlin", "com.intellij.database"
        )
    )
//    localPath.set("/Applications/IntelliJ IDEA.app/Contents")
//    version.set("2023.2.6")
//    type.set("IU") // Target IDE Platform
    version.set("2023.2.6")
    type.set("IC") // Target IDE Platform

//    type.set("IC") // Target IDE Platform
}
//dependencyManagement {
//    imports {
//        mavenBom("org.springframework.ai:spring-ai-bom:${libs.versions.spring.ai}")
//        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot}")
//    }
//}

dependencies {
//    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains:annotations:26.0.1")
    implementation("com.belerweb:pinyin4j:2.5.1")
    implementation("cn.hutool:hutool-all:5.8.25")
    implementation("com.alibaba:fastjson:2.0.52")
//    implementation(libs.spring.ai.ollama)
//    {
//        exclude(group = "com.fasterxml.jackson.core")
}


//    implementation(libs.spring.ai.openai)
//    {
//        exclude(group = "com.fasterxml.jackson.core")
//    }
//    implementation(libs.spring.ai.zhipuai)
//    {
//        exclude(group = "com.fasterxml.jackson.core")
//    }
//    implementation(libs.spring.ai.moonshot)
//    {
//        exclude(group = "com.fasterxml.jackson.core")
//    }
//    implementation(libs.spring.ai.alibaba)
//    {
//        exclude(group = "com.fasterxml.jackson.core")
//    }
//    implementation(libs.spring.boot)
//    {
//        exclude(group = "com.fasterxml.jackson.core")
//    }

//}


tasks {
// 将依赖打进jar包中
//    jar.configure {
//        duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
//        from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) })
//    }

    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += "-Xuse-k2" // 启用 K2 编译器
        }
    }

    patchPluginXml {
        sinceBuild.set(sinceVersion)
        untilBuild.set(untilVersion)
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }


    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}