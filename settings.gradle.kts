rootProject.name = rootDir.name
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
    id("site.addzero.gradle.plugin.repo-buddy") version "+"
    id("site.addzero.gradle.plugin.addzero-git-dependency") version "+"
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
}
implementationRemoteGit{
    remoteGits=listOf("lsi")
}

// >>> Gradle Buddy: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2025-12-22T22:28:25.045714
// Loaded: 5, Excluded: 0, Total: 5
include(":checkouts:lsi:lsi-core")
include(":lib:ddlgenerator:tool-ddlgenerator")
include(":plugins:autoddl")
include(":plugins:lsi-code-analyzer")
include(":plugins:problem4ai")
// <<< Gradle Buddy: End Of Block <<<

//include(":lib:tool-template")
