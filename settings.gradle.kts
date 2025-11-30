rootProject.name = rootDir.name


enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
//    id("site.addzero.repo-buddy") version "+"
    id("site.addzero.repo-buddy") version "2025.10.07"
    id("site.addzero.modules-buddy") version "+"
//    id("io.gitee.zjarlin.auto-modules") version "0.0.608"
    id("site.addzero.gradle.plugin.git-dependency") version "2025.11.32"

}

implementationRemoteGit{
    remoteGits=listOf("compose-component","metaprogramming-lsi")
}


//autoModules {
//    excludeModules = listOf(bdlogic, "buildSrc")
//}
//includeBuild("checkouts/$bdlogic")

//fun GitIncludeExtension.includeAddzeroProject(projectName: String) {
//    include(projectName) {
//        uri.set("https://gitee.com/zjarlin/$projectName.git")
//        branch.set("master")
//    }
//}
//gitRepositories {
//    listOf("build-logic", "metaprogramming-lsi", "compose-component").forEach {
//        includeAddzeroProject(it)
//    }
//}
//dependencyResolutionManagement {
//    versionCatalogs {
//        create("libs") {
//            from(files("./checkouts/build-logic/gradle/libs.versions.toml"))
//        }
//    }
//}


// >>> Gradle Buddy: On-Demand Modules (DO NOT EDIT THIS BLOCK) >>>
// Generated at: 2025-11-30T18:25:07.962988
// Only these modules will be loaded:
include(":lib:ddlgenerator:tool-ddlgenerator-core")
include(":lib:ddlgenerator:tool-ddlgenerator-sql")
include(":plugins:autoddl")
include(":plugins:gradle-buddy")
include(":plugins:maven-buddy")
// <<< Gradle Buddy: End Of Block <<<
