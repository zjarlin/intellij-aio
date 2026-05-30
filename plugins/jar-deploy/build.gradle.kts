plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}
val libs = versionCatalogs.named("libs")


dependencies {
    intellijPlatform {
        // Git4Idea for git push listener
        bundledPlugin("Git4Idea")
    }
    // JSch for SSH/SFTP functionality (IntelliJ SSH API is internal/undocumented)
    implementation(libs.findLibrary("com-jcraft-jsch").get())
}
