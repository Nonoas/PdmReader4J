package indi.nonoas.pdmreader.service

import indi.nonoas.pdmreader.ddl.DdlGenerator
import indi.nonoas.pdmreader.model.PdmImportSummary
import indi.nonoas.pdmreader.model.PdmRefreshResult
import indi.nonoas.pdmreader.model.PdmTableViewData
import indi.nonoas.pdmreader.model.TableNavigationItem
import indi.nonoas.pdmreader.parser.PowerDesignerPdmParser
import indi.nonoas.pdmreader.repository.PdmRepository
import indi.nonoas.pdmreader.util.HashUtils
import java.nio.file.Files
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
        val fileHash = HashUtils.md5(path)
        return repository.replaceImport(path, fileHash, parsedModel)
    }

    fun refreshChangedImports(
        onProgress: (completed: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
    ): PdmRefreshResult {
        val candidates = repository.listRefreshCandidates()
        if (candidates.isEmpty()) {
            return PdmRefreshResult(checkedCount = 0, reimported = emptyList())
        }

        val reimported = mutableListOf<PdmImportSummary>()
        candidates.forEachIndexed { index, candidate ->
            val completed = index + 1
            val path = Path.of(candidate.filePath)
            onProgress(index, candidates.size, "正在校验 ${candidate.fileName}...")
            if (!Files.isRegularFile(path)) {
                logger.warning("Skipping missing PDM file during refresh: ${candidate.filePath}")
                onProgress(completed, candidates.size, "文件不存在，已跳过 ${candidate.fileName}")
                return@forEachIndexed
            }

            val currentHash = HashUtils.md5(path)
            if (!currentHash.equals(candidate.fileHash, ignoreCase = true)) {
                logger.info("PDM file changed, reimporting: ${candidate.filePath}")
                onProgress(index, candidates.size, "检测到更新，正在重新导入 ${candidate.fileName}...")
                val parsedModel = parser.parse(path)
                reimported += repository.replaceImport(
                    filePath = path,
                    fileHash = currentHash,
                    parsedModel = parsedModel,
                    groupName = candidate.groupName,
                )
            }
            onProgress(completed, candidates.size, "已校验 $completed/${candidates.size}")
        }
        return PdmRefreshResult(checkedCount = candidates.size, reimported = reimported)
    }

    fun listImports(): List<PdmImportSummary> = repository.listImports()

    fun deleteImport(importId: Long): Boolean = repository.deleteImport(importId)

    fun deleteImports(importIds: Collection<Long>): Int = repository.deleteImports(importIds)

    fun renameImportGroup(importIds: Collection<Long>, groupName: String): Int =
        repository.renameImportGroup(importIds, groupName)

    fun loadNavigation(importId: Long, keyword: String): List<TableNavigationItem> {
        val normalizedKeyword = keyword.trim()
        return if (normalizedKeyword.isEmpty()) {
            repository.listTableNavigation(importId)
        } else {
            repository.searchNavigation(normalizedKeyword, importIds = listOf(importId))
        }
    }

    fun loadNavigation(importIds: Collection<Long>): List<TableNavigationItem> =
        repository.listTableNavigation(importIds)

    fun loadColumnNavigation(tableId: Long): List<TableNavigationItem> =
        repository.listColumnNavigation(tableId)

    fun searchNavigation(
        keyword: String,
        importIds: Collection<Long> = emptyList(),
        tableId: Long? = null,
    ): List<TableNavigationItem> =
        repository.searchNavigation(
            keyword = keyword.trim(),
            importIds = importIds,
            tableId = tableId,
        )

    fun loadTableViewData(tableId: Long): PdmTableViewData {
        val details = repository.findTableDetails(tableId)
            ?: throw IllegalArgumentException("未找到表信息，tableId=$tableId")
        return PdmTableViewData(
            details = details,
            ddl = ddlGenerator.generate(details),
        )
    }
}
