package site.addzero.kcp.i18n.idea

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.facet.KotlinFacet

class I18NProjectActivity : ProjectActivity {

    private val logger = Logger.getInstance(I18NProjectActivity::class.java)

    override suspend fun execute(project: Project) {
        logger.info("Starting kcp-i18n IDEA companion diagnostics")
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
                        "kcp-i18n" in classpath || "site.addzero.kcp.i18n" in classpath
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
                "kcp-i18n compiler plugin classpath was not found in Kotlin facet arguments. " +
                    "Gradle sync may be stale, so compile-time rewrite diagnostics can be out of date.",
            )
            return
        }

        detected.forEach { line ->
            logger.info("Detected kcp-i18n compiler plugin classpath for $line")
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
