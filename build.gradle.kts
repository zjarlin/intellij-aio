import com.addzero.gradle.utils.MarkdownTranslator
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
//    java
//    id("org.jetbrains.changelog") version "latest.release"
    id("org.jetbrains.changelog") version "+"
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

        fun String.ok(): String {
            val run = File(projectDir, this).readText().run {
                val translatedContent = MarkdownTranslator.translateAndAppend(this)
                markdownToHTML(translatedContent)
            }
            return run
        }

        description = "README.md".ok()
        changeNotes = "CHANGELOG.md".ok()


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
tasks.named<RunIdeTask>("runIde") {
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Didea.kotlin.plugin.use.k2=true")
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }



    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("262.*")
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
