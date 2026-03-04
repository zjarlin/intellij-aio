import org.gradle.kotlin.dsl.dependencies

plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "site.addzero.cloudfile"
        name = "Cloud File Hosting"
    }
}

dependencies {
    // S3 Storage Client (includes AWS SDK)
    implementation("site.addzero:tool-s3:+")

    // Aliyun OSS
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.17.4")

    // Git integration
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")

    // Encryption - use existing from libs
    implementation(libs.org.bouncycastle.bcprov.jdk15to18)

    // JSON - use existing from libs
    implementation(libs.com.google.code.gson.gson)
}
