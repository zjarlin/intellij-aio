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
// Generated at: 2025-12-02T21:53:41.447983
// Loaded: 12, Excluded: 0, Total: 12
include(":checkouts:metaprogramming-lsi:lsi-core")
include(":checkouts:metaprogramming-lsi:lsi-kt")
include(":checkouts:metaprogramming-lsi:lsi-psi")
include(":checkouts:metaprogramming-lsi:lsi-psiandkt")
include(":checkouts:metaprogramming-lsi:lsi-reflection")
include(":lib:lsi-code-analyzer-core")
include(":lib:tool-awt")
include(":lib:tool-swing")
include(":plugins:gradle-buddy")
include(":plugins:jar-deploy")
include(":plugins:lsi-code-analyzer")
include(":plugins:lsi-code-generator")
// <<< Gradle Buddy: End Of Block <<<
