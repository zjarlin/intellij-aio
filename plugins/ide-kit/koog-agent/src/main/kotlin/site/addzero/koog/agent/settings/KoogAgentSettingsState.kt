package site.addzero.koog.agent.settings

class KoogAgentSettingsState {
    var enabled: Boolean = true
    var debounceMillis: Int = 1600
    var detectedDefaultsInitialized: Boolean = false
    var models: MutableList<KoogAgentModelState> = mutableListOf()

    fun copy(): KoogAgentSettingsState {
        val copy = KoogAgentSettingsState()
        copy.enabled = enabled
        copy.debounceMillis = debounceMillis
        copy.detectedDefaultsInitialized = detectedDefaultsInitialized
        copy.models = models.map { it.copy() }.toMutableList()
        return copy
    }

    fun enabledModels(): List<KoogAgentModelState> {
        return KoogAgentModelDeduplicator.deduplicate(models)
            .filter { model -> model.enabled && model.baseUrl.isNotBlank() && model.model.isNotBlank() && model.apiKey.isNotBlank() }
            .sortedWith(
                compareBy<KoogAgentModelState> { it.order }
                    .thenBy { it.detected }
                    .thenBy { it.vendor }
                    .thenBy { it.model },
            )
    }
}

class KoogAgentModelState() {
    var enabled: Boolean = true
    var vendor: String = KoogAgentProvider.OPENAI_COMPATIBLE.name
    var baseUrl: String = ""
    var model: String = ""
    var apiKey: String = ""
    var order: Int = 100
    var detected: Boolean = false
    var source: String = ""

    constructor(
        enabled: Boolean,
        vendor: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        order: Int,
        detected: Boolean,
        source: String,
    ) : this() {
        this.enabled = enabled
        this.vendor = vendor
        this.baseUrl = baseUrl
        this.model = model
        this.apiKey = apiKey
        this.order = order
        this.detected = detected
        this.source = source
    }

    fun copy(): KoogAgentModelState {
        return KoogAgentModelState(
            enabled = enabled,
            vendor = vendor,
            baseUrl = baseUrl,
            model = model,
            apiKey = apiKey,
            order = order,
            detected = detected,
            source = source,
        )
    }
}
