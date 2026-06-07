package site.addzero.composebuddy.deadcode

import java.nio.file.Path

data class DeadCodeDeclarationKey(
    val filePath: String,
    val name: String,
    val offset: Int,
)

data class DeadCodeFileAnalysis(
    val sourcePath: Path,
    val relativePath: String,
    val declarations: List<DeadCodeDeclarationKey>,
    val liveDeclarations: List<DeadCodeDeclarationKey>,
) {
    val isFullyDead: Boolean
        get() = declarations.isNotEmpty() && liveDeclarations.isEmpty()

    val isMixed: Boolean
        get() = liveDeclarations.isNotEmpty() && liveDeclarations.size < declarations.size

    val deadDeclarations: List<DeadCodeDeclarationKey>
        get() = declarations.filterNot(liveDeclarations.toSet()::contains)
}

data class DeadCodeAnalysisResult(
    val sourceModulePath: Path,
    val files: List<DeadCodeFileAnalysis>,
    val reachableDeclarationCount: Int,
) {
    val movableDeadFiles: List<DeadCodeFileAnalysis>
        get() = files.filter(DeadCodeFileAnalysis::isFullyDead)

    val mixedFiles: List<DeadCodeFileAnalysis>
        get() = files.filter(DeadCodeFileAnalysis::isMixed)
}

data class DeadCodeCullResult(
    val mirrorRoot: Path,
    val manifestPath: Path,
    val reportPath: Path,
    val movedFileCount: Int,
    val mixedFileCount: Int,
)

data class DeadCodeRestoreResult(
    val restoredFileCount: Int,
    val conflictCount: Int,
    val missingMirrorFileCount: Int,
    val conflictReportPath: Path?,
)

data class DeadCodeManifest(
    val analyzerVersion: String = DeadCodeConstants.ANALYZER_VERSION,
    val createdAt: String = "",
    val sourceModulePath: String = "",
    val mirrorRootPath: String = "",
    val sourceBuildFilePath: String = "",
    val mirrorBuildFilePath: String = "",
    val movedFiles: List<DeadCodeMovedFile> = emptyList(),
    val mixedFiles: List<DeadCodeMixedFile> = emptyList(),
    val reachableDeclarationCount: Int = 0,
)

data class DeadCodeMovedFile(
    val relativePath: String = "",
    val sourcePath: String = "",
    val mirrorPath: String = "",
    val sha256: String = "",
)

data class DeadCodeMixedFile(
    val relativePath: String = "",
    val sourcePath: String = "",
    val liveDeclarations: List<String> = emptyList(),
    val deadDeclarations: List<String> = emptyList(),
)

object DeadCodeConstants {
    const val ANALYZER_VERSION: String = "1"
    const val MANIFEST_FILE_NAME: String = "dead-code-manifest.json"
    const val REPORT_FILE_NAME: String = "dead-code-report.md"
    const val CONFLICT_REPORT_FILE_NAME: String = "dead-code-restore-conflicts.md"
    const val DEFAULT_SOURCE_MODULE_RELATIVE_PATH: String = "lib/compose/az-compose"
    const val MIRROR_ROOT_RELATIVE_PATH: String = ".kmp-buddy/dead-code-modules"
}
