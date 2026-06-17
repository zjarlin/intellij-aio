package site.addzero.cargo.buddy.automod

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.lang.core.psi.RustPsiChangeListener
import org.rust.lang.core.psi.rustPsiManager
import org.rust.lang.core.macros.MacroExpansionManager
import org.rust.lang.core.macros.macroExpansionManager
import java.util.concurrent.atomic.AtomicBoolean

class AzAutomodRustIntegrationStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (project.getUserData(INSTALLED) == true) return

        try {
            val current = project.macroExpansionManager
            if (current is AzAutomodMacroExpansionManager) {
                project.putUserData(INSTALLED, true)
                return
            }

            val proxy = AzAutomodMacroExpansionManager(current)
            replaceMacroExpansionManager(project, proxy)
            installDefMapPatchListeners(project)
            AzAutomodDefMapPatcher.rebuildAndPatch(project)
            project.putUserData(INSTALLED, true)
            LOG.info("Installed az-automod Rust macro expansion bridge")
        } catch (e: Throwable) {
            LOG.warn("Unable to install az-automod Rust macro expansion bridge", e)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(AzAutomodRustIntegrationStartupActivity::class.java)
        private val INSTALLED = Key.create<Boolean>("cargo.buddy.azAutomod.rustExpansion.installed")

        private fun replaceMacroExpansionManager(
            project: Project,
            proxy: MacroExpansionManager,
        ) {
            val method = project.javaClass.methods.firstOrNull { method ->
                method.name == "replaceServiceInstance" &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == Class::class.java &&
                    method.parameterTypes[2] == Disposable::class.java
            } ?: error("Project service replacement API is unavailable")

            method.invoke(
                project,
                MacroExpansionManager::class.java,
                proxy,
                Disposer.newDisposable("Cargo Buddy az-automod Rust expansion bridge"),
            )
        }

        private fun installDefMapPatchListeners(project: Project) {
            val disposable = Disposer.newDisposable("Cargo Buddy az-automod def-map patch listeners")
            Disposer.register(project, disposable)

            val job = SupervisorJob()
            Disposer.register(disposable) {
                job.cancel()
            }
            val scope = CoroutineScope(job + Dispatchers.Default)
            val running = AtomicBoolean(false)
            val pending = AtomicBoolean(false)
            fun schedulePatch() {
                pending.set(true)
                if (!running.compareAndSet(false, true)) return

                scope.launch {
                    try {
                        while (pending.getAndSet(false)) {
                            AzAutomodDefMapPatcher.rebuildAndPatch(project)
                        }
                    } finally {
                        running.set(false)
                        if (pending.get()) {
                            schedulePatch()
                        }
                    }
                }
            }

            val connection = project.messageBus.connect(disposable)
            connection.subscribe(
                CargoProjectsService.CARGO_PROJECTS_TOPIC,
                CargoProjectsService.CargoProjectsListener { _: CargoProjectsService, _: Collection<CargoProject> ->
                    schedulePatch()
                },
            )
            project.rustPsiManager.subscribeRustPsiChange(
                connection,
                object : RustPsiChangeListener {
                    override fun rustPsiChanged(
                        file: PsiFile,
                        element: PsiElement,
                        isStructureModification: Boolean,
                    ) {
                        if (isStructureModification) {
                            schedulePatch()
                        }
                    }
                },
            )
        }
    }
}
