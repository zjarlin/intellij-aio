package site.addzero.koog.agent.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "KoogAgentSettings",
    storages = [Storage("koog-agent.xml")],
)
class KoogAgentSettingsService : PersistentStateComponent<KoogAgentSettingsState> {
    private var state = KoogAgentSettingsState()

    init {
        initializeDetectedModelsIfNeeded()
    }

    override fun getState(): KoogAgentSettingsState {
        return state
    }

    override fun loadState(state: KoogAgentSettingsState) {
        this.state = state.copy()
        this.state.models = KoogAgentModelDeduplicator.deduplicate(this.state.models)
        initializeDetectedModelsIfNeeded()
    }

    @Synchronized
    fun snapshot(): KoogAgentSettingsState {
        return state.copy()
    }

    @Synchronized
    fun update(newState: KoogAgentSettingsState) {
        state = newState.copy()
        state.models = KoogAgentModelDeduplicator.deduplicate(state.models)
    }

    @Synchronized
    fun initializeDetectedModelsIfNeeded(): Boolean {
        val before = state.modelsSignature()
        if (!state.detectedDefaultsInitialized) {
            mergeDetectedModels(addMissing = true)
            state.detectedDefaultsInitialized = true
        } else {
            mergeDetectedModels(addMissing = false)
        }
        return before != state.modelsSignature()
    }

    @Synchronized
    fun mergeDetectedModels(): Boolean {
        return mergeDetectedModels(addMissing = true)
    }

    private fun mergeDetectedModels(addMissing: Boolean): Boolean {
        val before = state.modelsSignature()
        state.models = KoogAgentDetectedModelMerger.merge(
            existingModels = state.models,
            detectedModels = KoogAgentCredentialDetector.detect(),
            addMissing = addMissing,
        )
        return before != state.modelsSignature()
    }

    companion object {
        fun getInstance(): KoogAgentSettingsService {
            return ApplicationManager.getApplication().getService(KoogAgentSettingsService::class.java)
        }
    }
}

internal fun KoogAgentSettingsState.modelsSignature(): String {
    return models.joinToString("|") { model ->
        listOf(
            model.enabled,
            model.vendor,
            model.baseUrl,
            model.model,
            model.apiKey,
            model.order,
            model.detected,
            model.source,
            detectedDefaultsInitialized,
        ).joinToString("\u0000")
    }
}
