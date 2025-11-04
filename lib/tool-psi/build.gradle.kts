plugins {
    id("site.addzero.buildlogic.intellij.intellij-core")
}

dependencies {
    implementation("site.addzero:tool-str:+")
    implementation(project(":lib:tool-common"))
    
    // 添加 Kotlin 编译器依赖
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:+")
}