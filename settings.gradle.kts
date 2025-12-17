rootProject.name = rootDir.name
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
    id("site.addzero.gradle.plugin.repo-buddy") version "+"
    id("site.addzero.gradle.plugin.git-dependency") version "2025.12.22"
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
}
implementationRemoteGit{
    remoteGits=listOf("metaprogramming-lsi")
}

// >>> Gradle Buddy: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2025-12-17T12:54:12.144693
// Loaded: 9, Excluded: 0, Total: 9
include(":lib-git:metaprogramming-lsi:lsi-core")
include(":lib-git:metaprogramming-lsi:lsi-ksp")
include(":lib-git:metaprogramming-lsi:lsi-kt2")
include(":lib-git:metaprogramming-lsi:lsi-psi")
include(":lib-git:metaprogramming-lsi:lsi-reflection")
include(":lib:ddlgenerator:tool-ddlgenerator")
include(":lib:tool-swing")
include(":plugins:autoddl")
include(":plugins:lsi-code-analyzer")
// <<< Gradle Buddy: End Of Block <<<

include(":lib:tool-template")
