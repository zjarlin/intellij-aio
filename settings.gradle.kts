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
// Generated at: 2025-12-07T18:41:00.763469
// Loaded: 17, Excluded: 0, Total: 17
include(":checkouts:metaprogramming-lsi:lsi-core")
include(":checkouts:metaprogramming-lsi:lsi-database")
include(":checkouts:metaprogramming-lsi:lsi-kt")
include(":checkouts:metaprogramming-lsi:lsi-reflection")
include(":lib:ddlgenerator:tool-ddlgenerator")
include(":lib:ddlgenerator:tool-ddlgenerator-core")
include(":lib:ddlgenerator:tool-ddlgenerator-koin")
include(":lib:ide-component-dynamicform")
include(":lib:lsi-code-analyzer-core")
include(":lib:tool-database")
include(":plugins:autoddl")
include(":plugins:autoddl-jimmer")
include(":plugins:gradle-buddy")
include(":plugins:jar-deploy")
include(":plugins:lsi-code-generator")
include(":plugins:maven-buddy")
include(":plugins:problem4ai")
// <<< Gradle Buddy: End Of Block <<<

include(":lib:tool-template")
