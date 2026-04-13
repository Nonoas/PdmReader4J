package com.example.pdmreader.service

import com.example.pdmreader.ddl.DdlGenerator
import com.example.pdmreader.model.PdmImportSummary
import com.example.pdmreader.model.PdmTableViewData
import com.example.pdmreader.model.TableNavigationItem
import com.example.pdmreader.parser.PowerDesignerPdmParser
import com.example.pdmreader.repository.PdmRepository
import com.example.pdmreader.util.HashUtils
import java.nio.file.Path
import java.util.logging.Logger

class PdmCatalogService(
    private val parser: PowerDesignerPdmParser,
    private val repository: PdmRepository,
    private val ddlGenerator: DdlGenerator,
) {
    private val logger = Logger.getLogger(PdmCatalogService::class.java.name)

    fun importPdm(path: Path): PdmImportSummary {
        logger.info("Importing PDM file: $path")
        val parsedModel = parser.parse(path)
        val fileHash = HashUtils.sha256(path)
        return repository.replaceImport(path, fileHash, parsedModel)
    }

    fun listImports(): List<PdmImportSummary> = repository.listImports()

    fun deleteImport(importId: Long): Boolean = repository.deleteImport(importId)

    fun loadNavigation(importId: Long, keyword: String): List<TableNavigationItem> {
        val normalizedKeyword = keyword.trim()
        return if (normalizedKeyword.isEmpty()) {
            repository.listTableNavigation(importId)
        } else {
            repository.searchNavigation(importId, normalizedKeyword)
        }
    }

    fun loadTableViewData(tableId: Long): PdmTableViewData {
        val details = repository.findTableDetails(tableId)
            ?: throw IllegalArgumentException("未找到表信息，tableId=$tableId")
        return PdmTableViewData(
            details = details,
            ddl = ddlGenerator.generate(details),
        )
    }
}
