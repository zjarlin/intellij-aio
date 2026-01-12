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
// Generated at: 2026-01-12T10:59:02.306862
// Loaded: 8, Excluded: 0, Total: 8
include(":lib:lsi-code-analyzer-core")
include(":plugins:autoddl")
include(":plugins:gradle-buddy")
include(":plugins:lsi-code-analyzer")
include(":plugins:lsi-code-generator")
include(":plugins:maven-buddy")
include(":plugins:problem4ai")
include(":plugins:vsc-auto-update")
// <<< Gradle Buddy: End Of Block <<<
