rootProject.name = rootDir.name
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
    id("site.addzero.gradle.plugin.repo-buddy") version "+"
    id("site.addzero.gradle.plugin.addzero-git-dependency") version "+"
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
}
implementationRemoteGit{
    remoteGits=listOf("lsi")
}



// >>> Gradle Buddy: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2026-01-20T16:25:00.496727
// Loaded: 9, Excluded: 0, Total: 9
include(":plugins:gradle-buddy")
include(":plugins:gradle-buddy:gradle-buddy-core")
include(":plugins:gradle-buddy:gradle-buddy-intentions")
include(":plugins:gradle-buddy:gradle-buddy-migration")
include(":plugins:gradle-module-sleep")
include(":plugins:maven-buddy")
include(":plugins:maven-buddy-core")
include(":plugins:split-module")
include(":plugins:vcs-auto-update")
// <<< Gradle Buddy: End Of Block <<<
