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

    val isFullyLive: Boolean
        get() = declarations.isNotEmpty() && liveDeclarations.size == declarations.size

    val isMixed: Boolean
        get() = liveDeclarations.isNotEmpty() && liveDeclarations.size < declarations.size

    val deadDeclarations: List<DeadCodeDeclarationKey>
        get() = declarations.filterNot(liveDeclarations.toSet()::contains)
}

enum class DeadCodeTransferMode(
    val manifestValue: String,
    val dialogLabel: String,
    val mirrorRootRelativePath: String,
    val taskTitle: String,
    val commandName: String,
    val reportTitle: String,
    val movedFilesLabel: String,
    val emptyMovedFilesMessage: String,
    val noCandidateMessage: String,
    val notificationFileLabel: String,
) {
    DEAD_CODE(
        manifestValue = "dead-code",
        dialogLabel = "Move dead code to an isolated mirror module",
        mirrorRootRelativePath = DeadCodeConstants.DEAD_CODE_MIRROR_ROOT_RELATIVE_PATH,
        taskTitle = "Culling KMP Buddy dead code",
        commandName = "Cull KMP Buddy dead code",
        reportTitle = "KMP Buddy Dead Code Report",
        movedFilesLabel = "Moved whole-file dead code",
        emptyMovedFilesMessage = "No whole-file dead code was moved.",
        noCandidateMessage = "No dead-code candidates were found.",
        notificationFileLabel = "dead",
    ),
    LIVE_CODE(
        manifestValue = "live-code",
        dialogLabel = "Move live code to a clean mirror module",
        mirrorRootRelativePath = DeadCodeConstants.LIVE_CODE_MIRROR_ROOT_RELATIVE_PATH,
        taskTitle = "Extracting KMP Buddy live code",
        commandName = "Extract KMP Buddy live code",
        reportTitle = "KMP Buddy Live Code Transfer Report",
        movedFilesLabel = "Moved whole-file live code",
        emptyMovedFilesMessage = "No whole-file live code was moved.",
        noCandidateMessage = "No live-code candidates were found.",
        notificationFileLabel = "live",
    );

    fun movableFiles(analysis: DeadCodeAnalysisResult): List<DeadCodeFileAnalysis> {
        return when (this) {
            DEAD_CODE -> analysis.movableDeadFiles
            LIVE_CODE -> analysis.movableLiveFiles
        }
    }

    companion object {
        fun fromManifestValue(value: String?): DeadCodeTransferMode {
            return entries.firstOrNull { mode -> mode.manifestValue == value } ?: DEAD_CODE
        }
    }
}

data class DeadCodeAnalysisResult(
    val sourceModulePath: Path,
    val files: List<DeadCodeFileAnalysis>,
    val reachableDeclarationCount: Int,
) {
    val movableDeadFiles: List<DeadCodeFileAnalysis>
        get() = files.filter(DeadCodeFileAnalysis::isFullyDead)

    val movableLiveFiles: List<DeadCodeFileAnalysis>
        get() = files.filter(DeadCodeFileAnalysis::isFullyLive)

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
    val transferMode: String = DeadCodeTransferMode.DEAD_CODE.manifestValue,
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
    const val ANALYZER_VERSION: String = "2"
    const val MANIFEST_FILE_NAME: String = "dead-code-manifest.json"
    const val REPORT_FILE_NAME: String = "dead-code-report.md"
    const val CONFLICT_REPORT_FILE_NAME: String = "dead-code-restore-conflicts.md"
    const val DEFAULT_SOURCE_MODULE_RELATIVE_PATH: String = "lib/compose/az-compose"
    const val DEAD_CODE_MIRROR_ROOT_RELATIVE_PATH: String = ".kmp-buddy/dead-code-modules"
    const val LIVE_CODE_MIRROR_ROOT_RELATIVE_PATH: String = ".kmp-buddy/live-code-modules"
}
