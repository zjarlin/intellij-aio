rootProject.name = rootDir.name
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
    id("site.addzero.gradle.plugin.repo-buddy") version "+"
    id("site.addzero.gradle.plugin.addzero-git-dependency") version "2026.02.02"
    id("site.addzero.gradle.plugin.modules-buddy") version "2026.01.11"
}
//implementationRemoteGit{
//    remoteGits=listOf("lsi")
//}



// >>> Gradle Buddy: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2026-01-21T18:24:51.570029
// Loaded: 3, Excluded: 0, Total: 3
//include(":plugins:gradle-buddy") // excluded by Gradle Buddy
//include(":plugins:gradle-buddy:id-fixer") // excluded by Gradle Buddy
// <<< Gradle Buddy: End Of Block <<<



//include(":plugins:dotfiles") // excluded by Gradle Buddy


// >>> Gradle Module Sleep: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2026-02-14T18:10:37.781066
// Loaded: 1, Excluded: 0, Total: 1
include(":plugins:kotlin-null-fixer")
// <<< Gradle Module Sleep: End Of Block <<<
