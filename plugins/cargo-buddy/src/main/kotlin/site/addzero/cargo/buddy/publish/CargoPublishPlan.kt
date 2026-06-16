package site.addzero.cargo.buddy.publish

data class CargoPublishPackage(
    val name: String,
    val version: String,
    val manifestPath: String,
) {
    val displayName: String = "$name v$version"
}

data class CargoPublishPlan(
    val rootPackage: CargoPublishPackage,
    val packages: List<CargoPublishPackage>,
) {
    val hasLocalDependencies: Boolean = packages.size > 1

    fun confirmationMessage(): String {
        val order = packages.joinToString(separator = "\n") { cargoPackage ->
            "- ${cargoPackage.displayName}\n  ${cargoPackage.manifestPath}"
        }
        return buildString {
            appendLine("Run cargo publish in dependency order?")
            appendLine()
            appendLine(order)
        }.trim()
    }
}
