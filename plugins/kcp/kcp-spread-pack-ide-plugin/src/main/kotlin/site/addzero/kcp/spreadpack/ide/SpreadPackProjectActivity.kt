package site.addzero.kcp.spreadpack.ide

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.kotlin.idea.facet.KotlinFacet

class SpreadPackProjectActivity : ProjectActivity {

    private val logger = Logger.getInstance(SpreadPackProjectActivity::class.java)

    override suspend fun execute(project: Project) {
        logDetectedPluginClasspaths(project)
        project.getService(SpreadPackStubService::class.java).scheduleRefresh()
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
                        "kcp-spread-pack" in classpath || "site.addzero.kcp.spread-pack" in classpath
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
                "Spread-pack compiler plugin classpath was not found in Kotlin facet arguments. " +
                    "Gradle sync may be stale, so IDE stubs can be out of date.",
            )
            return
        }

        detected.forEach { line ->
            logger.info("Detected spread-pack compiler plugin classpath for $line")
        }
    }
}
