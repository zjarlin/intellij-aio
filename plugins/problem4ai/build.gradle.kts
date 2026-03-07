import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

dependencies {
    // IntelliJ Platform 依赖由插件自动添加
}

intellijPlatform {
    pluginConfiguration {
        version = LocalDate.now().plusDays(2).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    }
}

//intellijPlatform {
//    pluginVerification {
//        ides {
//            create("IU", "2025.2.3")
//        }
//    }
//}
//
//tasks.named<JavaExec>("verifyPlugin") {
//    javaLauncher.set(
//        javaToolchains.launcherFor {
//            languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
//        }
//    )
//}

// 禁用 buildSearchableOptions 任务（开发阶段不需要）
tasks.named("buildSearchableOptions") {
    enabled = false
}
