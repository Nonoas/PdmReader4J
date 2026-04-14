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
        val anotherImportedFile = tempDir.resolve("minimal-sample-2.pdm")
        Files.copy(sourceFixture, importedFile)
        Files.copy(sourceFixture, anotherImportedFile)

        val firstImport = service.importPdm(importedFile)
        val secondImport = service.importPdm(importedFile)
        val thirdImport = service.importPdm(anotherImportedFile)

        val imports = service.listImports()
        assertEquals(2, imports.size)
        assertTrue(imports.any { it.id == secondImport.id })
        assertTrue(imports.any { it.id == thirdImport.id })

        val tables = service.loadNavigation(secondImport.id, "")
        assertEquals(1, tables.size)

        val searchByTable = service.searchNavigation("sample")
        assertEquals(2, searchByTable.size)
        assertTrue(searchByTable.all { it.tableCode == "SAMPLE_TABLE" })
        assertEquals(setOf(secondImport.id, thirdImport.id), searchByTable.map { it.importId }.toSet())

        val searchByColumn = service.searchNavigation("name")
        assertEquals(2, searchByColumn.size)
        assertTrue(searchByColumn.all { it.matchedColumnCode == "NAME" })
        assertEquals(setOf(secondImport.id, thirdImport.id), searchByColumn.map { it.importId }.toSet())

        val tableViewData = service.loadTableViewData(tables.single().tableId)
        assertEquals(2, tableViewData.details.columns.size)
        assertTrue(tableViewData.ddl.contains("PRIMARY KEY (ID)"))
        assertTrue(firstImport.id != secondImport.id)

        assertTrue(service.deleteImport(secondImport.id))
        assertEquals(1, service.listImports().size)
        assertTrue(service.deleteImport(thirdImport.id))
        assertTrue(service.listImports().isEmpty())
    }

    private fun fixturePath(resourcePath: String): Path =
        Path.of(requireNotNull(javaClass.classLoader.getResource(resourcePath)) { "Missing resource: $resourcePath" }.toURI())
}
