import me.champeau.gradle.igp.GitIncludeExtension

rootProject.name = rootDir.name


enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "+"
//    id("site.addzero.repo-buddy") version "+"
    id("site.addzero.repo-buddy") version "2025.10.07"

   id("site.addzero.modules-buddy") version "+"
//    id("io.gitee.zjarlin.auto-modules") version "0.0.608"
    id("me.champeau.includegit") version "+"
}
val bdlogic = "build-logic"

//autoModules {
//    excludeModules = listOf(bdlogic, "buildSrc")
//}
//includeBuild("checkouts/$bdlogic")

fun GitIncludeExtension.includeAddzeroProject(projectName: String) {
    include(projectName) {
        uri.set("https://gitee.com/zjarlin/$projectName.git")
        branch.set("master")
    }
}
gitRepositories {
    listOf(bdlogic, "metaprogramming-lsi","compose-component").forEach {
        includeAddzeroProject(it)
    }
}
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("./checkouts/build-logic/gradle/libs.versions.toml"))
        }
    }
}
