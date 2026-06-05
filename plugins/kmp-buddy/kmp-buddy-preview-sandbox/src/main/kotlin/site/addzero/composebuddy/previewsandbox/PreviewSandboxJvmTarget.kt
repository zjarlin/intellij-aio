package site.addzero.composebuddy.previewsandbox

object PreviewSandboxJvmTarget {
    private const val MIN_TARGET = 17
    private const val MAX_TARGET = 21

    fun currentTarget(): String {
        val runtimeVersion = System.getProperty("java.specification.version")
            ?.substringAfterLast('.')
            ?.toIntOrNull()
            ?: MAX_TARGET
        return runtimeVersion
            .coerceIn(MIN_TARGET, MAX_TARGET)
            .toString()
    }
}
