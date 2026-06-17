package site.addzero.cargo.buddy.automod

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

object AzAutomodBridgeAnalyzer {
    fun redundantBridge(
        file: Path,
        source: String,
        manifestDirectory: Path,
        rootSources: List<RootSource>,
    ): RedundantBridge? {
        if (!file.isRegularFile() || file.extension != "rs") return null
        if (!AzAutomodExpander.hasSingleAutomodMacroCall(source)) return null

        val bridgeCall = runCatching {
            AzAutomodExpander.parseSingleMacroCall(source, manifestDirectory)
        }.getOrNull() ?: return null

        val normalizedFile = file.normalize()
        val sourceName = normalizedFile.fileName.name.removeSuffix(".rs")
        val siblingDirectory = normalizedFile.parent?.resolve(sourceName)?.normalize() ?: return null
        if (bridgeCall.directory != siblingDirectory) return null
        if (!Files.isDirectory(siblingDirectory)) return null

        for (rootSource in rootSources) {
            val rootCalls = AzAutomodExpander.parseMacroCalls(rootSource.source, manifestDirectory)
            for (rootCall in rootCalls) {
                if (rootCall.directory == siblingDirectory) continue
                if (!normalizedFile.startsWith(rootCall.directory)) continue
                if (!siblingDirectory.startsWith(rootCall.directory)) continue
                return RedundantBridge(
                    bridgePath = normalizedFile,
                    bridgeDirectory = siblingDirectory,
                    rootSourcePath = rootSource.path.normalize(),
                    rootMacroPath = rootCall.relativePath,
                    bridgeMacroPath = bridgeCall.relativePath,
                    visibilityChanges = rootCall.visibility != bridgeCall.visibility,
                    rootVisibility = rootCall.visibility,
                    bridgeVisibility = bridgeCall.visibility,
                )
            }
        }

        return null
    }
}

data class RootSource(
    val path: Path,
    val source: String,
)

data class RedundantBridge(
    val bridgePath: Path,
    val bridgeDirectory: Path,
    val rootSourcePath: Path,
    val rootMacroPath: String,
    val bridgeMacroPath: String,
    val visibilityChanges: Boolean,
    val rootVisibility: String,
    val bridgeVisibility: String,
)
