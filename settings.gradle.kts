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
// Generated at: 2025-12-01T22:01:45.064390
// Loaded: 6, Excluded: 0, Total: 6
include(":checkouts:metaprogramming-lsi:lsi-core")
include(":checkouts:metaprogramming-lsi:lsi-psiandkt")
include(":lib:lsi-code-analyzer-core")
include(":plugins:gradle-buddy")
include(":plugins:jar-deploy")
include(":plugins:lsi-code-analyzer")
// <<< Gradle Buddy: End Of Block <<<
