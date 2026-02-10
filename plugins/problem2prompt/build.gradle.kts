plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform") version "+"
}

dependencies {
    // MCP Kotlin SDK - Server module for MCP protocol implementation
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.8.1")

    // Ktor CIO engine for embedded HTTP server (SSE transport)
    implementation("io.ktor:ktor-server-cio:${libs.versions.ktor.get()}")

    // kotlinx-serialization for JSON serialization of MCP responses
    implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.json)
}

// 禁用 buildSearchableOptions 任务（开发阶段不需要）
tasks.named("buildSearchableOptions") {
    enabled = false
}
