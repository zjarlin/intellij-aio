
plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation(libs.site.addzero.tool.jvmstr)
    implementation(libs.cn.hutool.hutool.core)

//    implementation(tool-str)

    implementation(libs.site.addzero.tool.str)

}
