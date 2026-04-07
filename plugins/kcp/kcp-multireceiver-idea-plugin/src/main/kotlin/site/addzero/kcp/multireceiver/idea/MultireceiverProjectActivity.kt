package site.addzero.kcp.multireceiver.idea

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.facet.KotlinFacet

class MultireceiverProjectActivity : ProjectActivity {

    private val logger = Logger.getInstance(MultireceiverProjectActivity::class.java)

    override suspend fun execute(project: Project) {
        logger.warn(
            "Multireceiver IDEA bundled compiler-plugin support is temporarily disabled on K2 " +
                "because it is incompatible with the current Kotlin IDE classloader boundary.",
        )
        logDetectedPluginClasspaths(project)
        restartAnalysis(project)
    }

    private fun logDetectedPluginClasspaths(project: Project) {
        val detected = ModuleManager.getInstance(project)
            .modules
            .mapNotNull { module ->
                val classpaths = KotlinFacet.get(module)
                    ?.configuration
                    ?.settings
                    ?.mergedCompilerArguments
                    ?.pluginClasspaths
                    ?.filter { classpath ->
                        "multireceiver" in classpath || "kcp-multireceiver-plugin" in classpath
                    }
                    .orEmpty()
                if (classpaths.isEmpty()) {
                    null
                } else {
                    "${module.name}: ${classpaths.joinToString()}"
                }
            }

        if (detected.isEmpty()) {
            logger.warn(
                "Multireceiver compiler plugin classpath was not found in Kotlin facet arguments. " +
                    "Gradle sync may be stale, so IDE support can still be unavailable.",
            )
        } else {
            detected.forEach { line ->
                logger.info("Detected multireceiver compiler plugin classpath for $line")
            }
        }
    }

    private fun restartAnalysis(project: Project) {
        val application = ApplicationManager.getApplication()
        application.invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            PsiManager.getInstance(project).dropPsiCaches()
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}
