import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.addzero"
version = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

val sinceVersion by extra("2022.3")
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    mavenLocal()
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    maven { url = uri("https://mirrors.huaweicloud.com/repository/maven/") }
}

//kotlin {
//    jvmToolchain(17)
//    compilerOptions {
//        freeCompilerArgs.add("-Xjvm-default=all")
//        freeCompilerArgs.add("-Xcontext-receivers")
//        freeCompilerArgs.add("-Xkotlin-version=1.9")
//        freeCompilerArgs.add("-Xuse-k2")
//    }
//}
val type = "IC"
dependencies {
    intellijPlatform {
        create(type, sinceVersion)
        bundledPlugins(
            "com.intellij.java", "org.jetbrains.kotlin"
//            , "com.intellij.database"
        )

        testFramework(TestFrameworkType.Platform)
    }
//    implementation("org.jetbrains.kotlin:kotlin-reflect")
//    implementation("org.jetbrains:annotations:26.0.1")
    implementation("com.belerweb:pinyin4j:2.5.1")
    implementation("cn.hutool:hutool-all:5.8.25")
    implementation("com.alibaba:fastjson:2.0.52")
//    implementation(libs.spring.ai.ollama)
//    {
//        exclude(group = "com.fasterxml.jackson.core")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.addzero.AutoDDL"
        name = "AutoDDL"
        vendor {
            name = "zjarlin"
            email = "zjarlin@outlook.comk"
        }
        ideaVersion {
            sinceBuild = "223"
            untilBuild = "243.*"
        }


    }
    pluginVerification {
        ides {
            ide(type, sinceVersion)

        }
    }

    publishing {
        token = System.getenv("PUBLISH_TOKEN")
        channels.add("Stable")
    }
    signing {
        certificateChain = System.getenv("CERTIFICATE_CHAIN")
        privateKey = System.getenv("PRIVATE_KEY")
        password = System.getenv("PRIVATE_KEY_PASSWORD")
    }


}
tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
        }
    }

    test {
        systemProperty("idea.home.path", intellijPlatform.sandboxContainer.get().toString())
    }

}