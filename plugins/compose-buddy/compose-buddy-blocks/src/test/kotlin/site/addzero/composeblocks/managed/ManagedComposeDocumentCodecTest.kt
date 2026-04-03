package site.addzero.composeblocks.managed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import site.addzero.composeblocks.model.BlockSpec
import site.addzero.composeblocks.model.ComposeBlockType
import site.addzero.composeblocks.model.ManagedComposeDocument
import site.addzero.composeblocks.model.ManagedComposeKind
import site.addzero.composeblocks.model.PropSpec

class ManagedComposeDocumentCodecTest {

    @Test
    fun `managed compose source stays stable and round-trips from header metadata`() {
        val document = ManagedComposeDocument(
            kind = ManagedComposeKind.SCREEN,
            packageName = "site.addzero.demo",
            composableName = "ProfileScreen",
            root = BlockSpec.create(
                id = "block-1",
                type = ComposeBlockType.COLUMN,
                note = "ProfileScreen",
                props = listOf(
                    PropSpec("fillMaxWidth", "true"),
                    PropSpec("padding", "16.dp"),
                ),
                children = listOf(
                    BlockSpec.create(
                        id = "block-2",
                        type = ComposeBlockType.TEXT,
                        note = "Title",
                        props = listOf(PropSpec("text", "Profile")),
                    ),
                    BlockSpec.create(
                        id = "block-3",
                        type = ComposeBlockType.BUTTON,
                        note = "Save action",
                        props = listOf(PropSpec("text", "Save")),
                    ),
                ),
            ),
        )

        val generated = ManagedComposeSourceGenerator.generate(document)
        assertEquals(
            """
            /*
            ComposeBlocks.Managed:v1
            <compose-blocks kind="SCREEN" packageName="site.addzero.demo" composableName="ProfileScreen">
              <block id="block-1" type="COLUMN" note="ProfileScreen">
                <prop name="fillMaxWidth" value="true" />
                <prop name="padding" value="16.dp" />
                <slot name="children">
                  <block id="block-2" type="TEXT" note="Title">
                    <prop name="text" value="Profile" />
                  </block>
                  <block id="block-3" type="BUTTON" note="Save action">
                    <prop name="text" value="Save" />
                  </block>
                </slot>
              </block>
            </compose-blocks>
            */
            
            package site.addzero.demo
            
            import androidx.compose.foundation.Image
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp
            
            @Composable
            fun ProfileScreen() {
                /** ProfileScreen */
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    /** Title */
                    Text(
                        text = "Profile",
                    )
                    /** Save action */
                    Button(
                        onClick = { },
                    ) {
                        Text("Save")
                    }
                }
            }
            """.trimIndent(),
            generated.trim(),
        )

        val parsed = ManagedComposeDocumentCodec.parseDocument(generated)
        assertNotNull(parsed)
        assertEquals(document, parsed)
    }
}
