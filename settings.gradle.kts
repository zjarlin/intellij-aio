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
// Generated at: 2025-12-07T22:43:35.707912
// Loaded: 12, Excluded: 0, Total: 12
include(":checkouts:metaprogramming-lsi:lsi-core")
include(":checkouts:metaprogramming-lsi:lsi-database")
include(":checkouts:metaprogramming-lsi:lsi-intellij")
include(":checkouts:metaprogramming-lsi:lsi-kt2")
include(":checkouts:metaprogramming-lsi:lsi-psi")
include(":checkouts:metaprogramming-lsi:lsi-psiandkt")
include(":lib:ddlgenerator:tool-ddlgenerator")
include(":lib:tool-swing")
include(":plugins:autoddl")
include(":plugins:autoddl-jimmer")
include(":plugins:jar-deploy")
include(":plugins:lsi-code-analyzer")
// <<< Gradle Buddy: End Of Block <<<

include(":lib:tool-template")
