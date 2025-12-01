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
// Generated at: 2025-12-01T20:24:22.228557
// Loaded: 5, Excluded: 0, Total: 5
include(":lib:tool-swing")
include(":plugins:autoddl")
include(":plugins:jar-deploy")
include(":plugins:lsi-code-analyzer")
include(":plugins:lsi-code-generator")
// <<< Gradle Buddy: End Of Block <<<
