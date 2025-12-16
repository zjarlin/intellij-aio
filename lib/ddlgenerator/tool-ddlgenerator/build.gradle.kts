plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-core"))
    implementation(project(":lib-git:metaprogramming-lsi:lsi-database"))
    implementation("site.addzero:tool-database-model:2025.12.04")
    implementation("site.addzero:tool-jdbc:2025.10.07")

}
