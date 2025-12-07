plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-database"))
    implementation("site.addzero:tool-database-model:2025.12.04")
    implementation("site.addzero:tool-jdbc:2025.10.07")

}
