plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.freemarker:freemarker:2.3.32")
}

description = "Shared template utilities (FreeMarker wrappers)"
