package site.addzero.koog.agent.settings

internal object KoogAgentModelDeduplicator {
    fun deduplicate(models: Collection<KoogAgentModelState>): MutableList<KoogAgentModelState> {
        val byIdentity = linkedMapOf<String, KoogAgentModelState>()
        models.asSequence()
            .map { model -> sanitize(model) }
            .sortedWith(compareBy<KoogAgentModelState> { it.order }.thenBy { it.vendor }.thenBy { it.model })
            .forEach { model ->
                byIdentity.putIfAbsent(identity(model), model)
            }
        return byIdentity.values.toMutableList()
    }

    private fun sanitize(model: KoogAgentModelState): KoogAgentModelState {
        val copy = model.copy()
        copy.vendor = KoogAgentProvider.from(copy.vendor).name
        copy.baseUrl = normalizeBaseUrl(copy.baseUrl)
        copy.model = copy.model.trim()
        copy.apiKey = copy.apiKey.trim()
        copy.source = copy.source.trim()
        if (copy.order <= 0) {
            copy.order = 100
        }
        return copy
    }

    private fun identity(model: KoogAgentModelState): String {
        return listOf(
            model.vendor.uppercase(),
            normalizeBaseUrl(model.baseUrl).lowercase(),
            model.model.lowercase(),
            model.apiKey,
        )
            .joinToString("\u0000")
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/')
    }
}
