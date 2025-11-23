plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {

//    implementation("site.addzero:tool-jvmstr:+")
//    implementation("site.addzero:tool-str:0.0.674")

//    implementation("site.addzero:tool-pinyin:+")
    implementation(libs.hutool.all)


}

description = "语言无关的不完备抽象层"
