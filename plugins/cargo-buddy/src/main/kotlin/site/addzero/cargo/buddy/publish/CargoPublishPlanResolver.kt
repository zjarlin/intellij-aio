package site.addzero.cargo.buddy.publish

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.ProgressIndicator
import site.addzero.cargo.buddy.model.CargoCrate
import java.nio.file.Path
import java.nio.file.Paths

object CargoPublishPlanResolver {
    fun resolve(
        cargoCrate: CargoCrate,
        indicator: ProgressIndicator,
    ): CargoPublishPlan {
        indicator.text = "Resolving Cargo metadata"
        val metadata = readMetadata(cargoCrate, indicator)
        indicator.text = "Resolving local Cargo dependencies"
        return buildPublishPlan(cargoCrate, metadata)
    }

    private fun readMetadata(
        cargoCrate: CargoCrate,
        indicator: ProgressIndicator,
    ): JsonObject {
        val commandLine = GeneralCommandLine("cargo")
            .withWorkDirectory(cargoCrate.workspaceRootPath)
            .withCharset(Charsets.UTF_8)
            .withParameters(
                "metadata",
                "--format-version",
                "1",
                "--manifest-path",
                cargoCrate.manifestPath,
            )
        val output = CapturingProcessHandler(commandLine)
            .runProcessWithProgressIndicator(indicator)
        indicator.checkCanceled()
        if (output.exitCode != 0) {
            val message = buildString {
                appendLine("cargo metadata failed with exit code ${output.exitCode}.")
                appendLine()
                appendLine(commandLine.commandLineString)
                appendLine()
                if (output.stderr.isNotBlank()) {
                    appendLine("stderr:")
                    appendLine(output.stderr.trimEnd())
                    appendLine()
                }
                if (output.stdout.isNotBlank()) {
                    appendLine("stdout:")
                    appendLine(output.stdout.trimEnd())
                }
            }.trim()
            throw IllegalStateException(message)
        }
        return JsonParser.parseString(output.stdout).asJsonObject
    }

    private fun buildPublishPlan(
        cargoCrate: CargoCrate,
        metadata: JsonObject,
    ): CargoPublishPlan {
        val packages = metadata.getAsJsonArray("packages")
            ?.mapNotNull { element -> parsePackage(element) }
            ?: emptyList()
        val packageById = packages.associateBy { cargoPackage -> cargoPackage.id }
        val localPackageIds = packages
            .filter { cargoPackage -> cargoPackage.source == null }
            .mapTo(mutableSetOf()) { cargoPackage -> cargoPackage.id }
        val rootPackage = findRootPackage(cargoCrate, metadata, packageById)
            ?: throw IllegalStateException("Unable to find Cargo package for ${cargoCrate.manifestPath}.")
        if (rootPackage.id !in localPackageIds) {
            throw IllegalStateException("${rootPackage.name} is not a local Cargo package.")
        }

        val dependencyGraph = readDependencyGraph(metadata, localPackageIds)
        val ordered = topologicalOrder(rootPackage, packageById, dependencyGraph)
            .map { cargoPackage -> cargoPackage.toPublishPackage() }
        return CargoPublishPlan(
            rootPackage = rootPackage.toPublishPackage(),
            packages = ordered,
        )
    }

    private fun parsePackage(element: JsonElement): MetadataPackage? {
        val json = element.asJsonObject
        val id = json.stringOrNull("id") ?: return null
        val name = json.stringOrNull("name") ?: return null
        val version = json.stringOrNull("version") ?: return null
        val manifestPath = json.stringOrNull("manifest_path") ?: return null
        return MetadataPackage(
            id = id,
            name = name,
            version = version,
            source = json.stringOrNull("source"),
            manifestPath = manifestPath,
        )
    }

    private fun findRootPackage(
        cargoCrate: CargoCrate,
        metadata: JsonObject,
        packageById: Map<String, MetadataPackage>,
    ): MetadataPackage? {
        val currentManifest = normalizedPath(cargoCrate.manifestPath)
        return packageById.values.firstOrNull { cargoPackage ->
            normalizedPath(cargoPackage.manifestPath) == currentManifest
        } ?: metadata.getAsJsonObject("resolve")
            ?.stringOrNull("root")
            ?.let(packageById::get)
    }

    private fun readDependencyGraph(
        metadata: JsonObject,
        localPackageIds: Set<String>,
    ): Map<String, List<String>> {
        val resolve = metadata.getAsJsonObject("resolve") ?: return emptyMap()
        val nodes = resolve.getAsJsonArray("nodes") ?: return emptyMap()
        return nodes.associate { element ->
            val node = element.asJsonObject
            val id = node.stringOrNull("id").orEmpty()
            val dependencyIds = node.getAsJsonArray("deps")
                ?.mapNotNull { depElement -> parseDependency(depElement.asJsonObject, localPackageIds) }
                ?: node.getAsJsonArray("dependencies")
                    ?.mapNotNull { dependencyElement ->
                        dependencyElement.asString.takeIf(localPackageIds::contains)
                    }
                ?: emptyList()
            id to dependencyIds.distinct()
        }
    }

    private fun parseDependency(
        dependency: JsonObject,
        localPackageIds: Set<String>,
    ): String? {
        val packageId = dependency.stringOrNull("pkg") ?: return null
        if (packageId !in localPackageIds) return null
        val kinds = dependency.getAsJsonArray("dep_kinds") ?: return packageId
        val isPublishDependency = kinds.any { kindElement ->
            kindElement.asJsonObject.stringOrNull("kind") != "dev"
        }
        return packageId.takeIf { isPublishDependency }
    }

    private fun topologicalOrder(
        rootPackage: MetadataPackage,
        packageById: Map<String, MetadataPackage>,
        dependencyGraph: Map<String, List<String>>,
    ): List<MetadataPackage> {
        val visiting = linkedSetOf<String>()
        val visited = linkedSetOf<String>()
        val ordered = mutableListOf<MetadataPackage>()

        fun visit(packageId: String) {
            if (packageId in visited) return
            if (!visiting.add(packageId)) {
                val cycle = (visiting + packageId)
                    .mapNotNull(packageById::get)
                    .joinToString(" -> ") { cargoPackage -> cargoPackage.name }
                throw IllegalStateException("Cargo local dependency cycle cannot be published automatically: $cycle")
            }
            dependencyGraph[packageId].orEmpty().forEach(::visit)
            visiting.remove(packageId)
            visited.add(packageId)
            ordered += packageById.getValue(packageId)
        }

        visit(rootPackage.id)
        return ordered
    }

    private fun normalizedPath(path: String): Path {
        return Paths.get(path).toAbsolutePath().normalize()
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return value.asString
    }

    private fun MetadataPackage.toPublishPackage(): CargoPublishPackage {
        return CargoPublishPackage(
            name = name,
            version = version,
            manifestPath = manifestPath,
        )
    }

    private data class MetadataPackage(
        val id: String,
        val name: String,
        val version: String,
        val source: String?,
        val manifestPath: String,
    )
}
