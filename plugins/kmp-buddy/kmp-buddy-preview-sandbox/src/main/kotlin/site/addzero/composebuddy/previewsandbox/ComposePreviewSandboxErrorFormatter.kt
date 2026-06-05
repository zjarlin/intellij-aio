package site.addzero.composebuddy.previewsandbox

import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException

object ComposePreviewSandboxErrorFormatter {
    fun format(
        context: String,
        throwable: Throwable,
    ): String {
        val root = throwable.unwrapInvocationTarget()
        return buildString {
            appendLine(context)
            appendLine()
            appendLine(root.describe())
            if (!root.message.isNullOrBlank()) {
                appendLine(root.message)
            }
            appendLine()
            appendLine("stacktrace:")
            appendLine(root.stackTraceString())
            if (root !== throwable) {
                appendLine()
                appendLine("reflection wrapper:")
                appendLine(throwable.stackTraceString())
            }
        }.trim()
    }

    private fun Throwable.unwrapInvocationTarget(): Throwable {
        return if (this is InvocationTargetException && targetException != null) {
            targetException
        } else {
            this
        }
    }

    private fun Throwable.describe(): String {
        return javaClass.name
    }

    private fun Throwable.stackTraceString(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString().trimEnd()
    }
}
