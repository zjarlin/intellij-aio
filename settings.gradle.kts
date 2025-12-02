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
// Generated at: 2025-12-02T13:29:37.899513
// Loaded: 9, Excluded: 0, Total: 9
include(":checkouts:metaprogramming-lsi:lsi-kt")
include(":checkouts:metaprogramming-lsi:lsi-psi")
include(":checkouts:metaprogramming-lsi:lsi-psiandkt")
include(":lib:lsi-code-analyzer-core")
include(":plugins:autoddl")
include(":plugins:gradle-buddy")
include(":plugins:jar-deploy")
include(":plugins:lsi-code-analyzer")
include(":plugins:maven-buddy")
// <<< Gradle Buddy: End Of Block <<<
