plugins {
    id("site.addzero.gradle.plugin.version-buddy") version "2025.11.32"
//    alias(libs.plugins.addzeroVersionBuddy)
    id("site.addzero.gradle.plugin.publish-buddy") version "2025.12.04"
//    alias(libs.plugins.addzeroPublishBuddyNew)
    alias(libs.plugins.kotlinJvm) apply false
}
//afterEvaluate {
    subprojects {
        if (!path.startsWith(":checkouts:")) return@subprojects
        apply(plugin = "site.addzero.gradle.plugin.publish-buddy")
    }
//}

