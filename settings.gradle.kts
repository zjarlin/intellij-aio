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
// Generated at: 2025-12-07T13:15:27.717538
// Loaded: 10, Excluded: 0, Total: 10
include(":checkouts:metaprogramming-lsi:lsi-apt")
include(":checkouts:metaprogramming-lsi:lsi-core")
include(":checkouts:metaprogramming-lsi:lsi-kt")
include(":checkouts:metaprogramming-lsi:lsi-psi")
include(":checkouts:metaprogramming-lsi:lsi-psiandkt")
include(":checkouts:metaprogramming-lsi:lsi-reflection")
include(":lib:ddlgenerator:tool-ddlgenerator-core")
include(":lib:ddlgenerator:tool-ddlgenerator-parser")
include(":lib:ddlgenerator:tool-ddlgenerator-sql")
include(":plugins:gradle-buddy")
// <<< Gradle Buddy: End Of Block <<<

include(":lib:tool-template")
