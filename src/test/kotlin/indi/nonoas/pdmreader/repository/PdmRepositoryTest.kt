package indi.nonoas.pdmreader.repository

import indi.nonoas.pdmreader.ddl.DdlGenerator
import indi.nonoas.pdmreader.model.NavigationItemType
import indi.nonoas.pdmreader.model.ParsedPdmColumn
import indi.nonoas.pdmreader.model.ParsedPdmModel
import indi.nonoas.pdmreader.model.ParsedPdmTable
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
        assertTrue(imports.all { it.groupName == tempDir.fileName.toString() })

        val tables = service.loadNavigation(secondImport.id, "")
        assertEquals(1, tables.size)
        assertEquals(secondImport.groupName, tables.single().importGroupName)
        assertEquals(importedFile.toAbsolutePath().toString(), tables.single().importFilePath)

        val searchByTable = service.searchNavigation("sample")
        assertEquals(2, searchByTable.size)
        assertTrue(searchByTable.all { it.tableCode == "SAMPLE_TABLE" })
        assertEquals(setOf(secondImport.id, thirdImport.id), searchByTable.map { it.importId }.toSet())
        assertEquals(
            setOf(importedFile.toAbsolutePath().toString(), anotherImportedFile.toAbsolutePath().toString()),
            searchByTable.map { it.importFilePath }.toSet()
        )

        val searchByColumn = service.searchNavigation("name")
        assertTrue(searchByColumn.isEmpty())

        val searchByColumnEnabled = service.searchNavigation("name", searchColumns = true)
        assertEquals(2, searchByColumnEnabled.size)
        assertTrue(searchByColumnEnabled.all { it.matchedColumnCode == "NAME" })
        assertEquals(setOf(secondImport.id, thirdImport.id), searchByColumnEnabled.map { it.importId }.toSet())

        val scopedSearchByImport = service.searchNavigation("name", importIds = listOf(secondImport.id), searchColumns = true)
        assertEquals(1, scopedSearchByImport.size)
        assertEquals(secondImport.id, scopedSearchByImport.single().importId)

        val tableViewData = service.loadTableViewData(tables.single().tableId)
        assertEquals(2, tableViewData.details.columns.size)
        assertTrue(tableViewData.ddl.contains("PRIMARY KEY (ID)"))
        assertEquals(secondImport.groupName, tableViewData.details.importGroupName)
        assertEquals(importedFile.toAbsolutePath().toString(), tableViewData.details.importFilePath)
        assertTrue(firstImport.id != secondImport.id)

        assertEquals(1, service.renameImportGroup(listOf(secondImport.id), "核心模型-A"))
        assertEquals(1, service.renameImportGroup(listOf(thirdImport.id), "核心模型-B"))
        val renamedImports = service.listImports()
        assertEquals(setOf("核心模型-A", "核心模型-B"), renamedImports.map { it.groupName }.toSet())

        val groupNavigation = service.loadNavigation(listOf(secondImport.id, thirdImport.id))
        assertEquals(2, groupNavigation.size)
        assertEquals(setOf(secondImport.id, thirdImport.id), groupNavigation.map { it.importId }.toSet())

        val searchByTableScope = service.searchNavigation("sample", tableId = tables.single().tableId)
        assertEquals(1, searchByTableScope.size)
        assertEquals(tables.single().tableId, searchByTableScope.single().tableId)

        val columnsByTableScope = service.loadColumnNavigation(tables.single().tableId)
        assertEquals(2, columnsByTableScope.size)
        assertTrue(columnsByTableScope.all { it.type == NavigationItemType.COLUMN_MATCH })
        assertEquals(listOf("ID", "NAME"), columnsByTableScope.map { it.matchedColumnCode })

        assertEquals(2, service.deleteImports(listOf(secondImport.id, thirdImport.id)))
        assertTrue(service.listImports().isEmpty())
    }

    @Test
    fun shouldPageSearchResults() {
        val tempDir = createTempDirectory("pdm-reader-page-test")
        val databaseFactory = DatabaseFactory(tempDir.resolve("catalog"))
        val repository = PdmRepository(databaseFactory)
        val service = PdmCatalogService(PowerDesignerPdmParser(), repository, DdlGenerator())
        val pagedImport = repository.replaceImport(
            filePath = tempDir.resolve("paged-sample.pdm"),
            fileHash = "test-hash",
            parsedModel = ParsedPdmModel(
                modelName = "PagedSample",
                targetDb = "Oracle",
                tables = List(55) { index ->
                    ParsedPdmTable(
                        idInPdm = "t$index",
                        name = "分页示例表$index",
                        code = "SAMPLE_TABLE_${index.toString().padStart(2, '0')}",
                        comment = null,
                        columns = listOf(
                            ParsedPdmColumn(
                                idInPdm = "c$index",
                                name = "主键$index",
                                code = "ID_$index",
                                dataType = "NUMBER",
                                length = null,
                                precision = 18,
                                scale = 0,
                                nullable = false,
                                defaultValue = null,
                                comment = null,
                                ordinalPosition = 1,
                            )
                        ),
                        primaryKeyColumnIds = listOf("c$index"),
                    )
                },
            ),
        )

        val firstNavigationPage = service.loadNavigationPage(
            importIds = listOf(pagedImport.id),
            pageIndex = 0,
            pageSize = 50,
        )
        assertEquals(55, firstNavigationPage.totalCount)
        assertEquals(50, firstNavigationPage.items.size)
        assertEquals(0, firstNavigationPage.pageIndex)

        val secondNavigationPage = service.loadNavigationPage(
            importIds = listOf(pagedImport.id),
            pageIndex = 1,
            pageSize = 50,
        )
        assertEquals(55, secondNavigationPage.totalCount)
        assertEquals(5, secondNavigationPage.items.size)
        assertEquals(1, secondNavigationPage.pageIndex)

        val firstPage = service.searchNavigationPage(
            keyword = "sample",
            pageIndex = 0,
            pageSize = 50,
        )
        assertEquals(55, firstPage.totalCount)
        assertEquals(50, firstPage.items.size)
        assertEquals(0, firstPage.pageIndex)
        assertEquals(50, firstPage.pageSize)

        val secondPage = service.searchNavigationPage(
            keyword = "sample",
            pageIndex = 1,
            pageSize = 50,
        )
        assertEquals(55, secondPage.totalCount)
        assertEquals(5, secondPage.items.size)
        assertEquals(1, secondPage.pageIndex)

        val boundedPage = service.searchNavigationPage(
            keyword = "sample",
            pageIndex = 20,
            pageSize = 50,
        )
        assertEquals(55, boundedPage.totalCount)
        assertEquals(5, boundedPage.items.size)
        assertEquals(1, boundedPage.pageIndex)
    }

    private fun fixturePath(resourcePath: String): Path =
        Path.of(requireNotNull(javaClass.classLoader.getResource(resourcePath)) { "Missing resource: $resourcePath" }.toURI())
}
