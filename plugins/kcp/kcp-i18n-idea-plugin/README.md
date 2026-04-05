# KCP I18N IDEA Plugin

Lightweight IDEA companion plugin for `site.addzero.kcp.i18n`.

Current scope:

- detects `kcp-i18n` compiler plugin classpaths from Kotlin facet arguments
- refreshes PSI / daemon analysis after project startup
- keeps K2 support lightweight and avoids bundling compiler plugin classes into the IDE process

This plugin does not add synthetic symbols, because `kcp-i18n` currently rewrites string literals only and does not generate new callable declarations for the IDE to resolve.
