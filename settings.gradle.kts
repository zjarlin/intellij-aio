rootProject.name = rootDir.name
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
    id("site.addzero.gradle.plugin.repo-buddy") version "+"
    id("site.addzero.gradle.plugin.addzero-git-dependency") version "+"
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
}
implementationRemoteGit{
    remoteGits=listOf("lsi")
}




// >>> Gradle Buddy: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2026-01-13T12:20:07.668305
// Loaded: 11, Excluded: 0, Total: 11
include(":checkouts:lsi:lsi-intellij")
include(":lib:lsi-code-analyzer-core")
include(":plugins:autoddl")
include(":plugins:autoddl-jimmer")
include(":plugins:gradle-buddy")
include(":plugins:gradle-module-sleep")
include(":plugins:lsi-code-analyzer")
include(":plugins:maven-buddy")
include(":plugins:problem4ai")
include(":plugins:split-module")
include(":plugins:vcs-auto-update")
// <<< Gradle Buddy: End Of Block <<<
