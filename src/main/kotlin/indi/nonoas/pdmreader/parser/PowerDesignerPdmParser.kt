package indi.nonoas.pdmreader.parser

import indi.nonoas.pdmreader.model.ParsedPdmColumn
import indi.nonoas.pdmreader.model.ParsedPdmModel
import indi.nonoas.pdmreader.model.ParsedPdmTable
import indi.nonoas.pdmreader.util.directChildElements
import indi.nonoas.pdmreader.util.firstDescendant
import indi.nonoas.pdmreader.util.firstDirectChildElement
import indi.nonoas.pdmreader.util.firstDirectChildText
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory

class PowerDesignerPdmParser {
    private val logger = Logger.getLogger(PowerDesignerPdmParser::class.java.name)

    fun parse(path: Path): ParsedPdmModel {
        logger.info("Parsing PDM file: $path")
        val document = Files.newInputStream(path).use { input ->
            documentBuilderFactory().newDocumentBuilder().parse(input)
        }
        document.documentElement.normalize()

        val modelElement = document.documentElement
            .firstDirectChildElement("RootObject")
            ?.firstDescendant("Model")
            ?: throw IllegalArgumentException("未找到 PowerDesigner Model 节点: $path")

        val modelName = modelElement.firstDirectChildText("Name").orEmpty()
        val targetDb = extractTargetDb(path)
        val tableElements = modelElement.firstDirectChildElement("Tables")
            ?.directChildElements("Table")
            .orEmpty()

        val tables = tableElements.map { tableElement ->
            parseTable(tableElement)
        }

        return ParsedPdmModel(
            modelName = modelName,
            targetDb = targetDb,
            tables = tables,
        )
    }

    private fun parseTable(tableElement: Element): ParsedPdmTable {
        val tableId = tableElement.getAttribute("Id").ifBlank {
            tableElement.firstDirectChildText("ObjectID").orEmpty()
        }
        val columns = tableElement.firstDirectChildElement("Columns")
            ?.directChildElements("Column")
            .orEmpty()
            .mapIndexed { index, columnElement ->
                parseColumn(columnElement, index + 1)
            }
        val keysById = parseKeys(tableElement.firstDirectChildElement("Keys"))
        val primaryKeyId = tableElement.firstDirectChildElement("PrimaryKey")
            ?.firstDirectChildElement("Key")
            ?.getAttribute("Ref")
            ?.takeIf { it.isNotBlank() }

        return ParsedPdmTable(
            idInPdm = tableId,
            name = tableElement.firstDirectChildText("Name").orEmpty(),
            code = tableElement.firstDirectChildText("Code"),
            comment = tableElement.firstDirectChildText("Comment"),
            columns = columns,
            primaryKeyColumnIds = primaryKeyId?.let(keysById::get).orEmpty(),
        )
    }

    private fun parseColumn(columnElement: Element, ordinalPosition: Int): ParsedPdmColumn {
        val rawDataType = columnElement.firstDirectChildText("DataType")
        val parsedType = parseTypeDetails(rawDataType)

        return ParsedPdmColumn(
            idInPdm = columnElement.getAttribute("Id").ifBlank {
                columnElement.firstDirectChildText("ObjectID").orEmpty()
            },
            name = columnElement.firstDirectChildText("Name").orEmpty(),
            code = columnElement.firstDirectChildText("Code"),
            dataType = rawDataType,
            length = columnElement.firstDirectChildText("Length")?.toIntOrNull() ?: parsedType.length,
            precision = columnElement.firstDirectChildText("Precision")?.toIntOrNull() ?: parsedType.precision,
            scale = columnElement.firstDirectChildText("Scale")?.toIntOrNull() ?: parsedType.scale,
            nullable = columnElement.firstDirectChildText("Column.Mandatory") != "1",
            defaultValue = columnElement.firstDirectChildText("DefaultValue"),
            comment = columnElement.firstDirectChildText("Comment"),
            ordinalPosition = ordinalPosition,
        )
    }

    private fun parseKeys(keysElement: Element?): Map<String, List<String>> {
        if (keysElement == null) {
            return emptyMap()
        }

        return keysElement.directChildElements("Key").associate { keyElement ->
            val keyId = keyElement.getAttribute("Id")
            val columnRefs = keyElement.firstDirectChildElement("Key.Columns")
                ?.directChildElements("Column")
                .orEmpty()
                .mapNotNull { columnRef ->
                    columnRef.getAttribute("Ref").takeIf { it.isNotBlank() }
                }
            keyId to columnRefs
        }
    }

    private fun extractTargetDb(path: Path): String? {
        Files.newBufferedReader(path).useLines { lines ->
            val header = lines.take(5).joinToString("\n")
            val match = TARGET_REGEX.find(header)
            return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }

    private fun documentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        }

    private fun parseTypeDetails(rawDataType: String?): ParsedTypeDetails {
        if (rawDataType.isNullOrBlank()) {
            return ParsedTypeDetails()
        }

        val matcher = TYPE_PATTERN.find(rawDataType.trim()) ?: return ParsedTypeDetails()
        val firstNumber = matcher.groupValues.getOrNull(1)?.toIntOrNull()
        val secondNumber = matcher.groupValues.getOrNull(2)?.toIntOrNull()

        return when {
            rawDataType.startsWith("NUMBER", ignoreCase = true) -> ParsedTypeDetails(
                precision = firstNumber,
                scale = secondNumber,
            )

            else -> ParsedTypeDetails(length = firstNumber)
        }
    }

    private data class ParsedTypeDetails(
        val length: Int? = null,
        val precision: Int? = null,
        val scale: Int? = null,
    )

    companion object {
        private val TARGET_REGEX = Regex("""Target="([^"]+)"""")
        private val TYPE_PATTERN = Regex("""\((\d+)(?:\s*,\s*(\d+))?\)""")
    }
}
