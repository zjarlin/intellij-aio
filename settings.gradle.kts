rootProject.name = rootDir.name
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
    id("site.addzero.gradle.plugin.repo-buddy") version "+"
    id("site.addzero.gradle.plugin.addzero-git-dependency") version "+"
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
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


// >>> Gradle Module Sleep: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2026-01-29T18:50:43.549048
// Loaded: 10, Excluded: 0, Total: 10
include(":plugins:dotfiles")
include(":plugins:gradle-buddy")
include(":plugins:gradle-buddy:gradle-buddy-core")
include(":plugins:gradle-buddy:gradle-buddy-fix-catalog-ref")
include(":plugins:gradle-buddy:gradle-buddy-intentions")
include(":plugins:gradle-module-sleep")
include(":plugins:maven-buddy-core")
include(":plugins:package-fixer")
include(":plugins:problem4ai")
include(":plugins:split-module")
// <<< Gradle Module Sleep: End Of Block <<<

include(":plugins:dotfiles")
