package indi.nonoas.pdmreader.repository

import indi.nonoas.pdmreader.ddl.DdlGenerator
import indi.nonoas.pdmreader.parser.PowerDesignerPdmParser
import indi.nonoas.pdmreader.service.PdmCatalogService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdmRepositoryTest {
    @Test
    fun shouldPersistSearchAndReplaceByFilePath() {
        val tempDir = createTempDirectory("pdm-reader-test")
        val databaseFactory = DatabaseFactory(tempDir.resolve("catalog"))
        val repository = PdmRepository(databaseFactory)
        val service = PdmCatalogService(PowerDesignerPdmParser(), repository, DdlGenerator())
        val sourceFixture = fixturePath("fixtures/minimal-sample.pdm")
        val importedFile = tempDir.resolve("minimal-sample.pdm")
        Files.copy(sourceFixture, importedFile)

        val firstImport = service.importPdm(importedFile)
        val secondImport = service.importPdm(importedFile)

        val imports = service.listImports()
        assertEquals(1, imports.size)
        assertEquals(secondImport.id, imports.single().id)

        val tables = service.loadNavigation(secondImport.id, "")
        assertEquals(1, tables.size)

        val searchByTable = service.loadNavigation(secondImport.id, "sample")
        assertEquals(1, searchByTable.size)
        assertEquals("SAMPLE_TABLE", searchByTable.single().tableCode)

        val searchByColumn = service.loadNavigation(secondImport.id, "name")
        assertEquals(1, searchByColumn.size)
        assertEquals("NAME", searchByColumn.single().matchedColumnCode)

        val tableViewData = service.loadTableViewData(tables.single().tableId)
        assertEquals(2, tableViewData.details.columns.size)
        assertTrue(tableViewData.ddl.contains("PRIMARY KEY (ID)"))
        assertTrue(firstImport.id != secondImport.id)

        assertTrue(service.deleteImport(secondImport.id))
        assertTrue(service.listImports().isEmpty())
    }

    private fun fixturePath(resourcePath: String): Path =
        Path.of(requireNotNull(javaClass.classLoader.getResource(resourcePath)) { "Missing resource: $resourcePath" }.toURI())
}
