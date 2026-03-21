package site.addzero.diagnostic.http

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

object Problem4AiSkillInstaller {

    private val LOG: Logger = Logger.getInstance(Problem4AiSkillInstaller::class.java)

    private const val SKILL_FOLDER_NAME = "problem4ai-http"
    private val RESOURCE_FILES = listOf(
        "SKILL.md",
        "scripts/problem4ai_http.py"
    )

    fun installBuiltinSkills(project: Project, server: DiagnosticHttpServer) {
        resolveSkillRoots().forEach { skillRoot ->
            runCatching {
                val installed = installSkillDirectory(skillRoot)
                val skillDir = skillRoot.resolve(SKILL_FOLDER_NAME)
                if (installed) {
                    LOG.info(
                        "[Problem4AI][Skill] installed builtin skill for project=${project.name} " +
                            "port=${server.getPort()} at $skillDir"
                    )
                } else {
                    LOG.info("[Problem4AI][Skill] skip reinstall existing skill at $skillDir")
                }
            }.onFailure { error ->
                LOG.warn("[Problem4AI][Skill] failed to install builtin skill at $skillRoot", error)
            }
        }
    }

    private fun resolveSkillRoots(): List<Path> {
        val userHome = Paths.get(System.getProperty("user.home"))
        val codexHome = System.getenv("CODEX_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let { Paths.get(it) }
            ?: userHome.resolve(".codex")
        val claudeHome = userHome.resolve(".claude")

        return listOf(
            codexHome.resolve("skills"),
            claudeHome.resolve("skills")
        ).distinct()
    }

    private fun installSkillDirectory(skillRoot: Path): Boolean {
        val skillDir = skillRoot.resolve(SKILL_FOLDER_NAME)
        if (isSkillAlreadyInstalled(skillDir)) {
            return false
        }
        Files.createDirectories(skillDir)

        RESOURCE_FILES.forEach { relativePath ->
            val targetFile = resolveRelative(skillDir, relativePath)
            Files.createDirectories(targetFile.parent)
            val content = readBundledResource(relativePath)
            writeIfChanged(targetFile, content)
            if (targetFile.fileName.toString().endsWith(".py")) {
                targetFile.toFile().setExecutable(true, false)
            }
        }
        return true
    }

    private fun isSkillAlreadyInstalled(skillDir: Path): Boolean {
        return RESOURCE_FILES.all { relativePath ->
            Files.isRegularFile(resolveRelative(skillDir, relativePath))
        }
    }

    private fun readBundledResource(relativePath: String): ByteArray {
        val resourcePath = "skills/$SKILL_FOLDER_NAME/$relativePath"
        return Problem4AiSkillInstaller::class.java.classLoader
            .getResourceAsStream(resourcePath)
            ?.use { it.readBytes() }
            ?: error("Missing bundled resource: $resourcePath")
    }

    private fun writeIfChanged(targetFile: Path, content: ByteArray) {
        if (Files.exists(targetFile) && Files.readAllBytes(targetFile).contentEquals(content)) {
            return
        }
        Files.write(
            targetFile,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    private fun resolveRelative(baseDir: Path, relativePath: String): Path {
        return relativePath.split("/")
            .fold(baseDir) { current, segment -> current.resolve(segment) }
    }
}
