rootProject.name = rootDir.name


enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
//    id("site.addzero.repo-buddy") version "+"
    id("site.addzero.repo-buddy") version "2025.10.07"
    id("site.addzero.gradle.plugin.modules-buddy") version "+"
//    id("io.gitee.zjarlin.auto-modules") version "0.0.608"
    id("site.addzero.gradle.plugin.git-dependency") version "2025.11.32"

}

implementationRemoteGit{
//    buidlogicName="build-logic"
//    auther= "zjarlin"
    remoteGits=listOf("metaprogramming-lsi","ddlgenerator")
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


