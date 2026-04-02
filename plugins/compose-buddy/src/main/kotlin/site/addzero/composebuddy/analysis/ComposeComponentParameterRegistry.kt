package site.addzero.composebuddy.analysis

object ComposeComponentParameterRegistry {
    private val specs = mapOf(
        "Text" to listOf(
            ComposeParameterSpec("text", "String"),
            ComposeParameterSpec("modifier", "Modifier", "Modifier"),
            ComposeParameterSpec("color", "Color", "Color.Unspecified"),
            ComposeParameterSpec("fontSize", "TextUnit", "TextUnit.Unspecified"),
            ComposeParameterSpec("fontStyle", "FontStyle?", "null"),
            ComposeParameterSpec("fontWeight", "FontWeight?", "null"),
            ComposeParameterSpec("fontFamily", "FontFamily?", "null"),
            ComposeParameterSpec("letterSpacing", "TextUnit", "TextUnit.Unspecified"),
            ComposeParameterSpec("textDecoration", "TextDecoration?", "null"),
            ComposeParameterSpec("textAlign", "TextAlign?", "null"),
            ComposeParameterSpec("lineHeight", "TextUnit", "TextUnit.Unspecified"),
            ComposeParameterSpec("overflow", "TextOverflow", "TextOverflow.Clip"),
            ComposeParameterSpec("softWrap", "Boolean", "true"),
            ComposeParameterSpec("maxLines", "Int", "Int.MAX_VALUE"),
            ComposeParameterSpec("minLines", "Int", "1"),
            ComposeParameterSpec("style", "TextStyle", "LocalTextStyle.current"),
        ),
        "Button" to listOf(
            ComposeParameterSpec("onClick", "() -> Unit"),
            ComposeParameterSpec("modifier", "Modifier", "Modifier"),
            ComposeParameterSpec("enabled", "Boolean", "true"),
            ComposeParameterSpec("content", "@Composable RowScope.() -> Unit"),
        ),
        "Image" to listOf(
            ComposeParameterSpec("painter", "Painter"),
            ComposeParameterSpec("contentDescription", "String?", "null"),
            ComposeParameterSpec("modifier", "Modifier", "Modifier"),
            ComposeParameterSpec("alignment", "Alignment", "Alignment.Center"),
            ComposeParameterSpec("contentScale", "ContentScale", "ContentScale.Fit"),
            ComposeParameterSpec("alpha", "Float", "1f"),
        ),
    )

    fun parametersFor(targetName: String): List<ComposeParameterSpec> = specs[targetName].orEmpty()

    fun isSupported(targetName: String): Boolean = specs.containsKey(targetName)
}
