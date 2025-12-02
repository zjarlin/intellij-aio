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
// Generated at: 2025-12-02T10:15:41.607938
// Loaded: 4, Excluded: 0, Total: 4
include(":plugins:autoddl")
include(":plugins:gradle-buddy")
include(":plugins:jar-deploy")
include(":plugins:maven-buddy")
// <<< Gradle Buddy: End Of Block <<<
