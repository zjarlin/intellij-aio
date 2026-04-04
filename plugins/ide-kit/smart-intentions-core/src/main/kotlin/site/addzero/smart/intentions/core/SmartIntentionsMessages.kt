package site.addzero.smart.intentions.core

object SmartIntentionsMessages {
    const val FAMILY_NAME = "Smart Intentions"
    const val REMOVE_REDUNDANT_KOIN_DEPENDENCY = "移除可由其他依赖提供的注入参数"
    const val REMOVE_PROJECT_SINGLE_BINDS = "删除项目中 @Single 的 binds"
    const val REMOVE_REDUNDANT_EXPLICIT_TYPE = "移除冗余显式类型"
    const val REMOVE_PROJECT_REDUNDANT_EXPLICIT_TYPE = "移除项目中的冗余显式类型"
    const val REDUNDANT_EXPLICIT_TYPE_SHORT_NAME = "SmartRedundantExplicitType"
    const val REDUNDANT_EXPLICIT_TYPE_DISPLAY_NAME = "冗余的显式类型声明"
    const val REDUNDANT_EXPLICIT_TYPE_DESCRIPTION = "这里的显式类型声明是冗余的"
}
