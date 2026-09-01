package indi.nonoas.pdmreader.model

import java.time.LocalDateTime

data class PdmImportSummary(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val groupName: String,
    val modelName: String,
    val targetDb: String?,
    val importTime: LocalDateTime,
)

data class PdmImportRefreshCandidate(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val groupName: String,
    val fileHash: String,
)

data class PdmRefreshResult(
    val checkedCount: Int,
    val reimported: List<PdmImportSummary>,
)

enum class NavigationItemType {
    TABLE,
    COLUMN_MATCH,
}

data class TableNavigationItem(
    val type: NavigationItemType,
    val importId: Long,
    val importFileName: String,
    val importFilePath: String,
    val importGroupName: String,
    val tableId: Long,
    val tableName: String,
    val tableCode: String?,
    val tableComment: String?,
    val matchedColumnIdInPdm: String? = null,
    val matchedColumnName: String? = null,
    val matchedColumnCode: String? = null,
)

data class NavigationSearchPage(
    val items: List<TableNavigationItem>,
    val totalCount: Int,
    val pageIndex: Int,
    val pageSize: Int,
)

data class PdmColumnDetail(
    val idInPdm: String,
    val name: String,
    val code: String?,
    val dataType: String?,
    val length: Int?,
    val precision: Int?,
    val scale: Int?,
    val nullable: Boolean,
    val defaultValue: String?,
    val comment: String?,
    val pkFlag: Boolean,
    val ordinalPosition: Int,
)

data class PdmTableDetails(
    val importId: Long,
    val importFileName: String,
    val importFilePath: String,
    val importGroupName: String,
    val modelName: String,
    val targetDb: String?,
    val tableId: Long,
    val tableName: String,
    val tableCode: String?,
    val tableComment: String?,
    val columns: List<PdmColumnDetail>,
)

data class PdmTableViewData(
    val details: PdmTableDetails,
    val ddl: String,
)
