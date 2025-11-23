plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation("site.addzero:tool-jvmstr:+")
    implementation("site.addzero:tool-str:0.0.674")

    implementation("site.addzero:tool-pinyin:+")
    implementation(libs.hutool.all)

    implementation(project(":lib:lsi-core"))


}
