package site.addzero.gradle.buddy.migration

import com.intellij.openapi.components.Service

@Service(Service.Level.PROJECT)
class PublishCommandQueueService {

    private val entries = linkedMapOf<String, PublishCommandEntry>()

    fun addAll(items: List<PublishCommandEntry>): Int {
        var added = 0
        synchronized(entries) {
            for (item in items) {
                val key = item.rootPath + "|" + item.modulePath
                if (entries.putIfAbsent(key, item) == null) {
                    added++
                }
            }
        }
        return added
    }

    fun snapshot(): List<PublishCommandEntry> {
        return synchronized(entries) {
            entries.values.toList()
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }

    fun size(): Int {
        return synchronized(entries) {
            entries.size
        }
    }

    fun buildClipboardText(): String {
        val grouped = snapshot()
            .groupBy { it.rootPath }
            .toSortedMap()

        return grouped.entries.joinToString(separator = "\n\n") { (rootPath, items) ->
            buildString {
                appendLine("# $rootPath")
                items.sortedBy { it.modulePath }.forEach { item ->
                    appendLine(item.command)
                }
            }.trimEnd()
        }
    }
}

data class PublishCommandEntry(
    val moduleName: String,
    val modulePath: String,
    val rootPath: String,
    val command: String
)
