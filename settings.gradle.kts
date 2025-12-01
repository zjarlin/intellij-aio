rootProject.name = rootDir.name
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
    id("site.addzero.gradle.plugin.repo-buddy") version "+"
    id("site.addzero.gradle.plugin.git-dependency") version "+"
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
}
implementationRemoteGit{
    remoteGits=listOf("metaprogramming-lsi")
}

// >>> Gradle Buddy: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2025-12-01T20:59:41.834955
// Loaded: 7, Excluded: 0, Total: 7
include(":checkouts:metaprogramming-lsi:lsi-intellij")
include(":lib:lsi-code-analyzer-core")
include(":lib:tool-swing")
include(":plugins:autoddl")
include(":plugins:jar-deploy")
include(":plugins:lsi-code-analyzer")
include(":plugins:lsi-code-generator")
// <<< Gradle Buddy: End Of Block <<<
