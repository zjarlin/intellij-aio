rootProject.name = rootDir.name
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
    id("site.addzero.gradle.plugin.repo-buddy") version "+"
    id("site.addzero.gradle.plugin.addzero-git-dependency") version "2025.12.25"
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
}
implementationRemoteGit{
    remoteGits=listOf("lsi")
}

// >>> Gradle Buddy: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2025-12-23T12:22:58.440756
// Loaded: 7, Excluded: 0, Total: 7
include(":lib:ddlgenerator:tool-ddlgenerator")
include(":lib:lsi-code-analyzer-core")
include(":plugins:autoddl")
include(":plugins:autoddl-jimmer")
include(":plugins:gradle-buddy")
include(":plugins:lsi-code-analyzer")
include(":plugins:problem4ai")
// <<< Gradle Buddy: End Of Block <<<

//include(":lib:tool-template")
