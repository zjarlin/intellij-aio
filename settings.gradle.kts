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


// >>> Gradle Module Sleep: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2026-02-10T19:26:54.114840
// Loaded: 16, Excluded: 0, Total: 16
include(":lib:ide-component-dynamicform")
include(":lib:ide-component-settings-old")
include(":lib:tool-psi-toml")
include(":lib:tool-swing")
include(":plugins:gradle-buddy")
include(":plugins:gradle-buddy:gradle-buddy-buildlogic")
include(":plugins:gradle-buddy:gradle-buddy-core")
include(":plugins:gradle-buddy:gradle-buddy-fix-catalog-ref")
include(":plugins:gradle-buddy:gradle-buddy-intentions")
include(":plugins:gradle-buddy:gradle-buddy-linemarker")
include(":plugins:gradle-buddy:gradle-buddy-migration")
include(":plugins:gradle-buddy:gradle-buddy-tasks")
include(":plugins:gradle-buddy:id-fixer")
include(":plugins:gradle-module-sleep")
include(":plugins:i18n-buddy")
include(":plugins:maven-buddy-core")
// <<< Gradle Module Sleep: End Of Block <<<

include(":plugins:dotfiles")
