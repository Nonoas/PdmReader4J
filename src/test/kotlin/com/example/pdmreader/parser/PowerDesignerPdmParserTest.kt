package com.example.pdmreader.parser

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerDesignerPdmParserTest {
    private val parser = PowerDesignerPdmParser()

    @Test
    fun shouldParseModelTableColumnsAndPrimaryKey() {
        val path = fixturePath("fixtures/minimal-sample.pdm")

        val model = parser.parse(path)

        assertEquals("SampleModel", model.modelName)
        assertEquals("ORACLE Version 11g", model.targetDb)
        assertEquals(1, model.tables.size)

        val table = model.tables.single()
        assertEquals("示例表", table.name)
        assertEquals("SAMPLE_TABLE", table.code)
        assertEquals(listOf("o11"), table.primaryKeyColumnIds)

        val firstColumn = table.columns.first()
        assertEquals("ID", firstColumn.code)
        assertEquals("NUMBER(18,0)", firstColumn.dataType)
        assertEquals(18, firstColumn.precision)
        assertEquals(0, firstColumn.scale)
        assertFalse(firstColumn.nullable)

        val secondColumn = table.columns.last()
        assertEquals("'UNKNOWN'", secondColumn.defaultValue)
        assertTrue(secondColumn.nullable)
    }

    private fun fixturePath(resourcePath: String): Path =
        Path.of(requireNotNull(javaClass.classLoader.getResource(resourcePath)) { "Missing resource: $resourcePath" }.toURI())
}
