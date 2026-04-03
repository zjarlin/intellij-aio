package site.addzero.composeblocks.parser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile
import site.addzero.composeblocks.model.ComposeBlockNode

class ComposeBlockTreeBuilderTest : BasePlatformTestCase() {

    fun testBuilderSnapshotForInspectMode() {
        val psiFile = myFixture.configureByText(
            "DemoScreen.kt",
            """
            package site.addzero.demo
            
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.Row
            import androidx.compose.material3.Button
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            
            @Composable
            fun DemoScreen() {
                Column {
                    /** Hero title */
                    Text("Hello")
                    Row {
                        Button(onClick = { }) {
                            Text("Open")
                        }
                    }
                }
            }
            """.trimIndent(),
        ) as KtFile

        val snapshot = ComposeBlockTreeBuilder.build(psiFile, hideShells = true)
            .joinToString("\n") { node -> snapshot(node, 0) }

        assertEquals(
            """
            DemoScreen|ROOT|VERTICAL|
              Column|CONTAINER|VERTICAL|
                Text|LEAF|VERTICAL|Hero title
                Row|CONTAINER|HORIZONTAL|
                  Text|LEAF|VERTICAL|
            """.trimIndent(),
            snapshot.trim(),
        )
    }

    private fun snapshot(node: ComposeBlockNode, depth: Int): String {
        val line = buildString {
            append("  ".repeat(depth))
            append(node.name)
            append('|')
            append(node.kind.name)
            append('|')
            append(node.axis.name)
            append('|')
            append(node.commentText.orEmpty())
        }
        val children = node.children.joinToString("\n") { child -> snapshot(child, depth + 1) }
        return if (children.isBlank()) line else "$line\n$children"
    }
}
