package site.addzero.autoupdate

/**
 * Persistent state for auto-update settings
 */
data class AutoUpdateState(
    var autoPullBeforePush: Boolean = true,
    var showNotification: Boolean = true,
    var pullRebase: Boolean = false
)
