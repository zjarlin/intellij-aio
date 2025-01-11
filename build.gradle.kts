import com.addzero.gradle.utils.MarkdownTranslator
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.2.1"
    id("org.jetbrains.changelog") version "latest.release"
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

val type = "IC"
dependencies {
    intellijPlatform {
        create(type, sinceVersion)
        bundledPlugins(
            "com.intellij.java", "org.jetbrains.kotlin"
        )
        testFramework(TestFrameworkType.Platform)
    }
    implementation("com.belerweb:pinyin4j:2.5.1")
    implementation("cn.hutool:hutool-all:5.8.25")
    implementation("com.alibaba:fastjson:2.0.52")
}

// 确保在构建前编译主项目
tasks.named("buildPlugin") {
    dependsOn("classes")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.addzero.AutoDDL"
        name = "AutoDDL"
        vendor {
            name = "zjarlin"
            email = "zjarlin@outlook.com"
        }
        ideaVersion {
            sinceBuild = "223"
            untilBuild = "243.*"
        }

        fun String.ok(): String {
            val that = this
            val run = File(projectDir, this).readText().run {
                val apiKey = System.getenv("DASHSCOPE_API_KEY") ?: throw IllegalStateException("DASHSCOPE_API_KEY environment variable is not set")
                val translatedContent = MarkdownTranslator.translateAndAppend(this, apiKey)
                markdownToHTML(translatedContent)
            }
            return run
        }

        description = "README.md".ok()
        changeNotes = "CHANGELOG.md".ok()
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