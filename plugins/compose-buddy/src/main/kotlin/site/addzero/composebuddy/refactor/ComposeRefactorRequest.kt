package site.addzero.composebuddy.refactor

data class ComposeRefactorRequest(
    val extractProps: Boolean,
    val extractEvents: Boolean,
    val extractState: Boolean,
    val propsTypeName: String,
    val eventsTypeName: String,
    val stateTypeName: String,
    val keepCompatibilityFunction: Boolean,
)
