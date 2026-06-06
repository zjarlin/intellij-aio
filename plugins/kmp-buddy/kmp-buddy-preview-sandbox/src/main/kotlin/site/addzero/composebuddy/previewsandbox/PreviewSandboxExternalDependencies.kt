package site.addzero.composebuddy.previewsandbox

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

object PreviewSandboxExternalDependencies {
    private const val ANDROIDX_LIFECYCLE_VERSION = "2.10.0"
    private const val KYANT_BACKDROP_VERSION = "2.0.0-alpha03"
    private const val KYANT_SHAPES_VERSION = "1.2.0"
    private const val KOIN_VERSION = "4.2.1"
    private const val KOTLINX_COROUTINES_VERSION = "1.11.0"
    private const val KOTLINX_SERIALIZATION_VERSION = "1.11.0"

    const val KYANT_BACKDROP: String = "io.github.kyant0:backdrop:$KYANT_BACKDROP_VERSION"
    const val KYANT_SHAPES: String = "io.github.kyant0:shapes:$KYANT_SHAPES_VERSION"
    const val KOIN_ANNOTATIONS: String = "io.insert-koin:koin-annotations:$KOIN_VERSION"
    const val KOIN_COMPOSE: String = "io.insert-koin:koin-compose:$KOIN_VERSION"
    const val KOIN_CORE: String = "io.insert-koin:koin-core:$KOIN_VERSION"

    const val KOTLINX_SERIALIZATION_CORE: String =
        "org.jetbrains.kotlinx:kotlinx-serialization-core:$KOTLINX_SERIALIZATION_VERSION"

    fun infer(
        files: List<PreviewSandboxSourceFile>,
        dependencyClassPath: List<String> = emptyList(),
    ): List<String> {
        val text = files.joinToString("\n") { file ->
            buildString {
                file.imports.forEach(::appendLine)
                file.declarations.forEach(::appendLine)
            }
        }
        val importedPackages = files.externalImportPackages()
        val resolvedDependencies = resolveFromDependencyClasspath(importedPackages, dependencyClassPath)
        return buildList {
            addAll(resolvedDependencies)
            if (text.contains("androidx.lifecycle.")) {
                add("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:$ANDROIDX_LIFECYCLE_VERSION")
            }
            if (text.contains("com.kyant.backdrop.")) {
                add(KYANT_BACKDROP)
            }
            if (text.contains("com.kyant.shapes.")) {
                add(KYANT_SHAPES)
            }
            if (text.contains("org.koin.compose.")) {
                add(KOIN_COMPOSE)
                add(KOIN_CORE)
            }
            if (text.contains("org.koin.core.annotation.")) {
                add(KOIN_ANNOTATIONS)
            }
            if (text.contains("org.koin.core.")) {
                add(KOIN_CORE)
            }
            if (text.contains("kotlinx.coroutines.")) {
                add("org.jetbrains.kotlinx:kotlinx-coroutines-core:$KOTLINX_COROUTINES_VERSION")
            }
            if (text.contains("kotlinx.serialization.")) {
                add(KOTLINX_SERIALIZATION_CORE)
            }
        }.distinct().sorted()
    }

    private fun List<PreviewSandboxSourceFile>.externalImportPackages(): Set<String> {
        val sourcePackages = map(PreviewSandboxSourceFile::packageName)
            .filter(String::isNotBlank)
            .toSet()

        return flatMap { sourceFile -> sourceFile.imports }
            .mapNotNull { importText -> importText.importPackageName() }
            .filterNot { packageName -> packageName.isPlatformPackage() }
            .filterNot { packageName ->
                sourcePackages.any { sourcePackage ->
                    packageName == sourcePackage || packageName.startsWith("$sourcePackage.")
                }
            }
            .toSet()
    }

    private fun String.importPackageName(): String? {
        if (!startsWith("import ")) {
            return null
        }
        val importBody = removePrefix("import")
            .trim()
            .substringBefore(" as ")
            .trim()
            .removeSuffix(".*")
        if (importBody.isBlank() || "." !in importBody) {
            return null
        }
        return importBody.substringBeforeLast('.').takeIf(String::isNotBlank)
    }

    private fun String.isPlatformPackage(): Boolean {
        return this == "kotlin" ||
            startsWith("kotlin.") ||
            this == "java" ||
            startsWith("java.") ||
            this == "javax" ||
            startsWith("javax.") ||
            this == "sun" ||
            startsWith("sun.") ||
            this == "androidx.compose" ||
            startsWith("androidx.compose.") ||
            this == "org.jetbrains.compose" ||
            startsWith("org.jetbrains.compose.") ||
            this == "org.jetbrains.skia" ||
            startsWith("org.jetbrains.skia.") ||
            this == "org.jetbrains.skiko" ||
            startsWith("org.jetbrains.skiko.")
    }

    private fun resolveFromDependencyClasspath(
        importedPackages: Set<String>,
        dependencyClassPath: List<String>,
    ): List<String> {
        if (importedPackages.isEmpty() || dependencyClassPath.isEmpty()) {
            return emptyList()
        }

        return dependencyClassPath
            .asSequence()
            .mapNotNull { classPath -> runCatching { Paths.get(classPath) }.getOrNull() }
            .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".jar") }
            .filter { jarPath -> jarContainsAnyPackage(jarPath, importedPackages) }
            .mapNotNull(::mavenCoordinateFromGradleCachePath)
            .distinct()
            .sorted()
            .toList()
    }

    private fun jarContainsAnyPackage(
        jarPath: Path,
        importedPackages: Set<String>,
    ): Boolean {
        val packageNames = importedPackages.toSet()
        val packagePrefixes = packageNames
            .map { packageName -> packageName.replace('.', '/') + "/" }
            .toSet()
        val metadataPackageMarkers = packageNames
            .map { packageName -> "package_$packageName/" }
            .toSet()

        return runCatching {
            ZipFile(jarPath.toFile()).use { zip ->
                zip.entries().asSequence().any { entry ->
                    val name = entry.name
                    (!entry.isDirectory &&
                        packagePrefixes.any { packagePrefix ->
                            name.startsWith(packagePrefix)
                        }) ||
                        (!entry.isDirectory &&
                            metadataPackageMarkers.any { metadataPackageMarker -> name.contains(metadataPackageMarker) })
                }
            }
        }.getOrDefault(false)
    }

    private fun mavenCoordinateFromGradleCachePath(jarPath: Path): String? {
        val normalized = jarPath.toAbsolutePath().normalize()
        val names = normalized.map { path -> path.toString() }
        val filesIndex = names.indexOf("files-2.1")
        if (filesIndex < 0 || filesIndex + 4 >= names.size) {
            return null
        }
        val group = names[filesIndex + 1]
        val module = names[filesIndex + 2]
        val version = names[filesIndex + 3]
        if (group.isBlank() || module.isBlank() || version.isBlank()) {
            return null
        }
        return "$group:$module:$version".normalizeKmpPlatformCoordinate(normalized)
    }

    private fun String.normalizeKmpPlatformCoordinate(jarPath: Path): String {
        val (group, module, version) = split(':').takeIf { parts -> parts.size == 3 } ?: return this
        val rootModule = module.removeKnownKmpPlatformSuffix()
        val rootModuleVersionDirectory = jarPath.parent
            ?.parent
            ?.parent
            ?.parent
            ?.resolve(rootModule)
            ?.resolve(version)
        val rootModuleFileExists = rootModuleVersionDirectory?.let { moduleDirectory ->
            Files.isDirectory(moduleDirectory) &&
                moduleDirectory.hasGradleModuleFile("$rootModule-$version.module")
        } ?: false
        return if (rootModuleFileExists) {
            "$group:$rootModule:$version"
        } else {
            this
        }
    }

    private fun Path.hasGradleModuleFile(fileName: String): Boolean {
        val paths = Files.walk(this, 2)
        return try {
            paths.anyMatch { path -> path.fileName.toString() == fileName }
        } finally {
            paths.close()
        }
    }

    private fun String.removeKnownKmpPlatformSuffix(): String {
        return knownKmpPlatformSuffixes.firstNotNullOfOrNull { suffix ->
            removeSuffix(suffix).takeIf { candidate -> candidate != this }
        } ?: this
    }

    private val knownKmpPlatformSuffixes = listOf(
        "-jvm",
        "-desktop",
        "-android",
        "-wasm-js",
        "-js",
        "-iosarm64",
        "-iosx64",
        "-iossimulatorarm64",
        "-macosarm64",
        "-macosx64",
        "-linuxx64",
        "-mingwx64",
    ).sortedByDescending(String::length)
}
