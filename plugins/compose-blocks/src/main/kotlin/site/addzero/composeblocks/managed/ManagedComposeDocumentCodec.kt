package site.addzero.composeblocks.managed

import com.intellij.openapi.util.TextRange
import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import site.addzero.composeblocks.model.BlockSpec
import site.addzero.composeblocks.model.ComposeBlockType
import site.addzero.composeblocks.model.ManagedComposeDocument
import site.addzero.composeblocks.model.ManagedComposeKind
import site.addzero.composeblocks.model.PropSpec
import site.addzero.composeblocks.model.RawCodeBlockSpec
import site.addzero.composeblocks.model.SlotSpec
import java.io.StringReader

object ManagedComposeDocumentCodec {
    private val headerRegex = Regex(
        pattern = "^\\s*/\\*\\s*ComposeBlocks\\.Managed:v(\\d+)\\s*(.*?)\\*/",
        options = setOf(RegexOption.DOT_MATCHES_ALL),
    )

    fun isManagedDocument(text: String): Boolean = headerRegex.containsMatchIn(text)

    fun findHeaderRange(text: String): TextRange? {
        val match = headerRegex.find(text) ?: return null
        return TextRange(match.range.first, match.range.last + 1)
    }

    fun parseDocument(text: String): ManagedComposeDocument? {
        val match = headerRegex.find(text) ?: return null
        val version = match.groupValues[1].toIntOrNull() ?: return null
        val xml = match.groupValues[2].trim()
        if (xml.isBlank()) {
            return null
        }

        return runCatching {
            val rootElement = SAXBuilder().build(StringReader(xml)).rootElement
            val kind = ManagedComposeKind.valueOf(rootElement.getAttributeValue("kind"))
            val packageName = rootElement.getAttributeValue("packageName").orEmpty()
            val composableName = rootElement.getAttributeValue("composableName").orEmpty()
            val rootBlock = rootElement.getChild("block")?.toBlockSpec() ?: return null
            val rawBlocks = rootElement.getChildren("raw-code").map { rawElement ->
                RawCodeBlockSpec(
                    id = rawElement.getAttributeValue("id"),
                    note = rawElement.getAttributeValue("note").orEmpty(),
                    source = rawElement.text.orEmpty(),
                )
            }
            ManagedComposeDocument(
                version = version,
                kind = kind,
                packageName = packageName,
                composableName = composableName,
                root = rootBlock,
                rawCodeBlocks = rawBlocks,
            )
        }.getOrNull()
    }

    fun renderHeader(document: ManagedComposeDocument): String {
        val xml = buildXml(document)
        return buildString {
            append("/*\n")
            append("ComposeBlocks.Managed:v")
            append(document.version)
            append('\n')
            append(xml.trim())
            append("\n*/")
        }
    }

    private fun buildXml(document: ManagedComposeDocument): String {
        val root = Element("compose-blocks")
            .setAttribute("kind", document.kind.name)
            .setAttribute("packageName", document.packageName)
            .setAttribute("composableName", document.composableName)

        root.addContent(document.root.toElement())
        document.rawCodeBlocks.forEach { rawBlock ->
            root.addContent(
                Element("raw-code")
                    .setAttribute("id", rawBlock.id)
                    .setAttribute("note", rawBlock.note)
                    .setText(rawBlock.source),
            )
        }

        return XMLOutputter(
            Format.getPrettyFormat()
                .setIndent("  ")
                .setLineSeparator("\n")
                .setOmitDeclaration(true)
                .setOmitEncoding(true),
        ).outputString(root)
    }

    private fun Element.toBlockSpec(): BlockSpec {
        val type = ComposeBlockType.valueOf(getAttributeValue("type"))
        val props = getChildren("prop").map { prop ->
            PropSpec(
                name = prop.getAttributeValue("name"),
                value = prop.getAttributeValue("value"),
            )
        }
        val slots = getChildren("slot").map { slot ->
            SlotSpec(
                name = slot.getAttributeValue("name"),
                blocks = slot.getChildren("block").map { child -> child.toBlockSpec() },
            )
        }
        return BlockSpec(
            id = getAttributeValue("id"),
            type = type,
            note = getAttributeValue("note").orEmpty(),
            props = props,
            slots = slots,
        )
    }

    private fun BlockSpec.toElement(): Element {
        val element = Element("block")
            .setAttribute("id", id)
            .setAttribute("type", type.name)
            .setAttribute("note", note)

        props.forEach { prop ->
            element.addContent(
                Element("prop")
                    .setAttribute("name", prop.name)
                    .setAttribute("value", prop.value),
            )
        }

        slots.forEach { slot ->
            val slotElement = Element("slot").setAttribute("name", slot.name)
            slot.blocks.forEach { child ->
                slotElement.addContent(child.toElement())
            }
            element.addContent(slotElement)
        }

        return element
    }
}
