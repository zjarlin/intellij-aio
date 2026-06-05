package site.addzero.composebuddy.previewsandbox

import java.nio.file.Path

data class PreviewSandboxSnapshot(
    val sandboxId: String,
    val previewName: String,
    val previewPackage: String,
    val originalPreviewPath: String,
    val entryFileKey: String,
    val files: List<PreviewSandboxSourceFile>,
    val dependencyClassPath: List<String> = emptyList(),
    val externalMavenDependencies: List<String> = emptyList(),
    val jvmTarget: String = PreviewSandboxJvmTarget.currentTarget(),
    val gradleJavaHome: String = PreviewSandboxGradleJvm.currentJavaHome(),
) {
    val declarationCount: Int = files.sumOf { file -> file.declarations.size }

    val previewDisplayName: String =
        listOf(previewPackage, previewName)
            .filter(String::isNotBlank)
            .joinToString(".")
}

data class PreviewSandboxSourceFile(
    val key: String,
    val packageName: String,
    val originalPath: String,
    val outputFileName: String,
    val imports: List<String>,
    val declarations: List<String>,
)

data class PreviewSandboxWriteResult(
    val rootDirectory: Path,
    val entryFile: Path,
    val buildFile: Path,
    val gradlePropertiesFile: Path,
    val runnerFile: Path,
    val classpathFile: Path,
    val runnerMainClass: String,
    val entryComposableName: String,
    val generatedFiles: List<PreviewSandboxGeneratedFile>,
    val generatedFileCount: Int,
    val declarationCount: Int,
)

data class PreviewSandboxGeneratedFile(
    val sourceFileKey: String,
    val path: Path,
)
