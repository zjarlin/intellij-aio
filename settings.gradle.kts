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
// Generated at: 2025-12-16T22:32:29.853479
// Loaded: 5, Excluded: 0, Total: 5
include(":lib-git:metaprogramming-lsi:lsi-apt")
include(":lib:ddlgenerator:tool-ddlgenerator")
include(":lib:lsi-code-analyzer-core")
include(":lib:tool-swing")
include(":plugins:gradle-buddy")
// <<< Gradle Buddy: End Of Block <<<

include(":lib:tool-template")
