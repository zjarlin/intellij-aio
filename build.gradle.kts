
import com.addzero.gradle.utils.MarkdownTranslator
import org.jetbrains.changelog.markdownToHTML
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
//    java
    id("org.jetbrains.changelog") version "latest.release"

    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij)
}
group = "com.addzero"
version = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

repositories {
    mavenCentral()
    intellijPlatform {
        releases()
        marketplace()
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
//        create("IC", "2024.2.5")
        intellijIdeaCommunity("2024.2.5")
//        intellijIdeaUltimate("2023.2")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugins(
            "com.intellij.java", "org.jetbrains.kotlin"
//            , "org.intellij.groovy"  // Correct Groovy plugin ID
        )

    }

    implementation(libs.org.tomlj.tomlj)
    implementation(libs.com.belerweb.pinyin4j)
    implementation(libs.cn.hutool.hutool.all)
    implementation(libs.com.alibaba.fastjson)


}

intellijPlatform {
    pluginConfiguration {
        id = "com.addzero.autoddl"
        name = "AutoDDL"
        vendor {
            name = "zjarlin"
            email = "zjarlin@outlook.com"
        }
//        ideaVersion {
//            sinceBuild = "232"
//            untilBuild = "262.*"
//        }

        description = File(projectDir, "README.md").readText().let { markdownToHTML(it) }
        changeNotes = File(projectDir, "CHANGELOG.md").readText().let { markdownToHTML(it) }


    }
//    pluginVerification {
//        ides {
//            ide(type, sinceVersion)
//        }
//    }

//    publishing {
//        token = System.getenv("PUBLISH_TOKEN")
//        channels.add("Stable")
//    }
//    signing {
//        certificateChain = System.getenv("CERTIFICATE_CHAIN")
//        privateKey = System.getenv("PRIVATE_KEY")
//        password = System.getenv("PRIVATE_KEY_PASSWORD")
//    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }



    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("262.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
        doFirst {
            val apiKey = System.getenv("DASHSCOPE_API_KEY") ?: throw IllegalStateException("DASHSCOPE_API_KEY environment variable is not set")
            val readmeContent = File(projectDir, "README.md").readText()
            val translatedReadme = MarkdownTranslator.translateAndAppend(readmeContent, apiKey)
            val changelogContent = File(projectDir, "CHANGELOG.md").readText()
            val translatedChangelog = MarkdownTranslator.translateAndAppend(changelogContent, apiKey)
            
            intellijPlatform.pluginConfiguration {
                description = markdownToHTML(translatedReadme)
                changeNotes = markdownToHTML(translatedChangelog)
            }
        }
    }
}
