package indi.nonoas.pdmreader.controller

import indi.nonoas.pdmreader.model.PdmColumnDetail
import indi.nonoas.pdmreader.model.PdmImportSummary
import indi.nonoas.pdmreader.model.PdmTableViewData
import indi.nonoas.pdmreader.model.TableNavigationItem
import indi.nonoas.pdmreader.service.PdmCatalogService
import github.nonoas.jfx.flat.ui.concurrent.TaskHandler
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

class MainController(
    private val catalogService: PdmCatalogService,
) {
    val windowTitle: String = "JavaFX PDM Reader"
    val imports: ObservableList<PdmImportSummary> = FXCollections.observableArrayList()
    val navigationItems: ObservableList<TableNavigationItem> = FXCollections.observableArrayList()
    val columns: ObservableList<PdmColumnDetail> = FXCollections.observableArrayList()

    val statusTextProperty: StringProperty = SimpleStringProperty("准备就绪，等待导入 PDM 文件。")
    val selectedTableTitleProperty: StringProperty = SimpleStringProperty("未选择表")
    val selectedTableMetaProperty: StringProperty = SimpleStringProperty("导入 PDM 后可浏览表结构。")
    val selectedTableCommentProperty: StringProperty = SimpleStringProperty("")
    val ddlTextProperty: StringProperty = SimpleStringProperty("")
    val highlightedColumnIdProperty: StringProperty = SimpleStringProperty("")

    private val selectedImport = ReadOnlyObjectWrapper<PdmImportSummary?>()
    private val selectedNavigationItem = ReadOnlyObjectWrapper<TableNavigationItem?>()
    private val canCopyDdl = ReadOnlyBooleanWrapper(false)

    private var searchKeyword: String = ""
    private var currentTableId: Long? = null
    private val requestSequence = AtomicLong(0)

    fun selectedImportProperty(): ReadOnlyObjectProperty<PdmImportSummary?> = selectedImport.readOnlyProperty

    fun selectedNavigationItemProperty(): ReadOnlyObjectProperty<TableNavigationItem?> =
        selectedNavigationItem.readOnlyProperty

    fun canCopyDdlProperty(): ReadOnlyBooleanProperty = canCopyDdl.readOnlyProperty

    fun initialize(onError: (Throwable) -> Unit = {}) {
        reloadImports(onError = onError)
    }

    fun importPdm(path: Path, onError: (Throwable) -> Unit = {}) {
        importPdms(listOf(path), onError)
    }

    fun importPdms(paths: List<Path>, onError: (Throwable) -> Unit = {}) {
        if (paths.isEmpty()) {
            return
        }

        val keyword = searchKeyword
        statusTextProperty.set(
            if (paths.size == 1) {
                "正在导入 ${paths.first().fileName}..."
            } else {
                "正在导入 ${paths.size} 个 PDM 文件..."
            }
        )
        runAsync(onError,
            action = {
                val imported = paths.map(catalogService::importPdm)
                val preferredImportId = imported.last().id
                val imports = catalogService.listImports()
                val targetImport = imports.firstOrNull { it.id == preferredImportId } ?: imports.firstOrNull()
                buildSnapshot(
                    imports = imports,
                    selectedImport = targetImport,
                    searchKeyword = keyword,
                    preferredTableId = null,
                    emptyStatusText = "当前没有已导入的 PDM 元数据。",
                )
            },
            onSuccess = ::applySnapshot
        )
    }

    fun removeImport(importSummary: PdmImportSummary, onError: (Throwable) -> Unit = {}) {
        val keyword = searchKeyword
        statusTextProperty.set("正在移除 ${importSummary.fileName}...")
        runAsync(onError,
            action = {
                val removed = catalogService.deleteImport(importSummary.id)
                val imports = catalogService.listImports()
                if (!removed) {
                    buildSnapshot(
                        imports = imports,
                        selectedImport = imports.firstOrNull(),
                        searchKeyword = keyword,
                        preferredTableId = null,
                        emptyStatusText = "未找到要移除的导入记录：${importSummary.fileName}",
                    )
                } else {
                    val emptyStatusText = if (imports.isEmpty()) {
                        "已移除 ${importSummary.fileName}，当前没有已导入的 PDM 元数据。"
                    } else {
                        "当前没有已导入的 PDM 元数据。"
                    }
                    buildSnapshot(
                        imports = imports,
                        selectedImport = imports.firstOrNull(),
                        searchKeyword = keyword,
                        preferredTableId = null,
                        emptyStatusText = emptyStatusText,
                    )
                }
            },
            onSuccess = ::applySnapshot
        )
    }

    fun reloadImports(preferredImportId: Long? = selectedImport.get()?.id, onError: (Throwable) -> Unit = {}) {
        val keyword = searchKeyword
        val preferredTableId = currentTableId
        statusTextProperty.set("正在刷新导入列表...")
        runAsync(onError,
            action = {
                val imports = catalogService.listImports()
                val targetImport = preferredImportId?.let { importId ->
                    imports.firstOrNull { it.id == importId }
                } ?: imports.firstOrNull()
                buildSnapshot(
                    imports = imports,
                    selectedImport = targetImport,
                    searchKeyword = keyword,
                    preferredTableId = preferredTableId,
                    emptyStatusText = "当前没有已导入的 PDM 元数据。",
                )
            },
            onSuccess = ::applySnapshot
        )
    }

    fun selectImport(importSummary: PdmImportSummary?, onError: (Throwable) -> Unit = {}) {
        val importsSnapshot = imports.toList()
        val keyword = searchKeyword
        statusTextProperty.set(
            importSummary?.let { "正在加载 ${it.fileName} 的表结构..." } ?: "未选择导入文件。"
        )
        runAsync(onError,
            action = {
                buildSnapshot(
                    imports = importsSnapshot,
                    selectedImport = importSummary,
                    searchKeyword = keyword,
                    preferredTableId = null,
                    emptyStatusText = "未选择导入文件。",
                )
            },
            onSuccess = ::applySnapshot
        )
    }

    fun setSearchKeyword(keyword: String, onError: (Throwable) -> Unit = {}) {
        searchKeyword = keyword.trim()
        val normalizedKeyword = searchKeyword
        val importsSnapshot = imports.toList()
        val selectedImportSnapshot = selectedImport.get()
        val preferredTableId = currentTableId
        if (selectedImportSnapshot == null) {
            applySnapshot(
                buildSnapshot(
                    imports = importsSnapshot,
                    selectedImport = null,
                    searchKeyword = normalizedKeyword,
                    preferredTableId = null,
                    emptyStatusText = "当前没有已导入的 PDM 元数据。",
                )
            )
            return
        }

        statusTextProperty.set(
            if (normalizedKeyword.isBlank()) {
                "正在加载表清单..."
            } else {
                "正在搜索“$normalizedKeyword”..."
            }
        )
        runAsync(onError,
            action = {
                buildSnapshot(
                    imports = importsSnapshot,
                    selectedImport = selectedImportSnapshot,
                    searchKeyword = normalizedKeyword,
                    preferredTableId = preferredTableId,
                    emptyStatusText = "当前没有已导入的 PDM 元数据。",
                )
            },
            onSuccess = ::applySnapshot
        )
    }

    fun clearSearch(onError: (Throwable) -> Unit = {}) {
        setSearchKeyword("", onError)
    }

    fun selectNavigationItem(item: TableNavigationItem?, onError: (Throwable) -> Unit = {}) {
        if (item == null) {
            selectedNavigationItem.set(null)
            clearTableDetails()
            return
        }

        statusTextProperty.set("正在加载 ${item.tableName} 的表详情...")
        runAsync(onError,
            action = {
                TableSelectionSnapshot(
                    item = item,
                    tableViewData = catalogService.loadTableViewData(item.tableId),
                )
            },
            onSuccess = { snapshot ->
                selectedNavigationItem.set(snapshot.item)
                applyTableViewData(snapshot.tableViewData, snapshot.item.matchedColumnIdInPdm)
            }
        )
    }

    fun copySelectedDdlToClipboard(): Boolean {
        val ddl = ddlTextProperty.get().trim()
        if (ddl.isEmpty()) {
            return false
        }

        val content = ClipboardContent().apply {
            putString(ddl)
        }
        Clipboard.getSystemClipboard().setContent(content)
        statusTextProperty.set("DDL 已复制到剪贴板。")
        return true
    }

    private fun applySnapshot(snapshot: ViewSnapshot) {
        imports.setAll(snapshot.imports)
        selectedImport.set(snapshot.selectedImport)
        navigationItems.setAll(snapshot.navigationItems)
        selectedNavigationItem.set(snapshot.selectedNavigationItem)
        if (snapshot.tableViewData == null) {
            clearTableDetails()
            statusTextProperty.set(snapshot.emptyStatusText)
            return
        }

        applyTableViewData(snapshot.tableViewData, snapshot.highlightedColumnId)
    }

    private fun applyTableViewData(tableViewData: PdmTableViewData, highlightedColumnId: String?) {
        val details = tableViewData.details
        currentTableId = details.tableId
        columns.setAll(details.columns)
        ddlTextProperty.set(tableViewData.ddl)
        canCopyDdl.set(tableViewData.ddl.isNotBlank())
        selectedTableTitleProperty.set(
            details.tableCode?.takeIf { it.isNotBlank() }?.let { "${details.tableName} / $it" } ?: details.tableName
        )
        selectedTableMetaProperty.set(
            buildString {
                append("模型：")
                append(details.modelName)
                append("    文件：")
                append(details.importFileName)
                append("    目标库：")
                append(details.targetDb ?: "未知")
            }
        )
        selectedTableCommentProperty.set(details.tableComment ?: "")
        this.highlightedColumnIdProperty.set(highlightedColumnId.orEmpty())
        statusTextProperty.set(buildTableStatus(tableViewData))
    }

    private fun clearTableDetails() {
        currentTableId = null
        columns.clear()
        ddlTextProperty.set("")
        canCopyDdl.set(false)
        highlightedColumnIdProperty.set("")
        selectedTableTitleProperty.set("未选择表")
        selectedTableMetaProperty.set("导入 PDM 后可浏览表结构。")
        selectedTableCommentProperty.set("")
    }

    private fun buildSnapshot(
        imports: List<PdmImportSummary>,
        selectedImport: PdmImportSummary?,
        searchKeyword: String,
        preferredTableId: Long?,
        emptyStatusText: String,
    ): ViewSnapshot {
        if (selectedImport == null) {
            return ViewSnapshot(
                imports = imports,
                selectedImport = null,
                navigationItems = emptyList(),
                selectedNavigationItem = null,
                tableViewData = null,
                highlightedColumnId = "",
                emptyStatusText = emptyStatusText,
            )
        }

        val navigationItems = catalogService.loadNavigation(selectedImport.id, searchKeyword)
        if (navigationItems.isEmpty()) {
            return ViewSnapshot(
                imports = imports,
                selectedImport = selectedImport,
                navigationItems = emptyList(),
                selectedNavigationItem = null,
                tableViewData = null,
                highlightedColumnId = "",
                emptyStatusText = if (searchKeyword.isBlank()) {
                    "文件 ${selectedImport.fileName} 中没有可展示的表。"
                } else {
                    "未找到与“$searchKeyword”匹配的表或字段。"
                },
            )
        }

        val preferredItem = preferredTableId?.let { tableId ->
            navigationItems.firstOrNull { it.tableId == tableId }
        } ?: navigationItems.firstOrNull()

        if (preferredItem == null) {
            return ViewSnapshot(
                imports = imports,
                selectedImport = selectedImport,
                navigationItems = navigationItems,
                selectedNavigationItem = null,
                tableViewData = null,
                highlightedColumnId = "",
                emptyStatusText = if (searchKeyword.isBlank()) {
                    "文件 ${selectedImport.fileName} 中没有可展示的表。"
                } else {
                    "未找到与“$searchKeyword”匹配的表或字段。"
                },
            )
        }

        return ViewSnapshot(
            imports = imports,
            selectedImport = selectedImport,
            navigationItems = navigationItems,
            selectedNavigationItem = preferredItem,
            tableViewData = catalogService.loadTableViewData(preferredItem.tableId),
            highlightedColumnId = preferredItem.matchedColumnIdInPdm.orEmpty(),
            emptyStatusText = "",
        )
    }

    private fun buildTableStatus(tableViewData: PdmTableViewData): String {
        val details = tableViewData.details
        return buildString {
            append("当前表：")
            append(details.tableCode?.takeIf { it.isNotBlank() } ?: details.tableName)
            append("    列数：")
            append(details.columns.size)
            append("    导入时间：")
            append(selectedImport.get()?.importTime?.format(DATE_TIME_FORMATTER) ?: "-")
        }
    }

    private fun <T> runAsync(
        onError: (Throwable) -> Unit,
        action: () -> T,
        onSuccess: (T) -> Unit,
    ) {
        val requestId = requestSequence.incrementAndGet()
        TaskHandler<Result<T>>()
            .whenCall { runCatching(action) }
            .andThen { result ->
                if (requestId != requestSequence.get()) {
                    return@andThen
                }
                result
                    .onSuccess(onSuccess)
                    .onFailure { exception ->
                        statusTextProperty.set("执行失败：${exception.message ?: "未知错误"}")
                        onError(exception)
                    }
            }
            .handle()
    }

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    private data class ViewSnapshot(
        val imports: List<PdmImportSummary>,
        val selectedImport: PdmImportSummary?,
        val navigationItems: List<TableNavigationItem>,
        val selectedNavigationItem: TableNavigationItem?,
        val tableViewData: PdmTableViewData?,
        val highlightedColumnId: String,
        val emptyStatusText: String,
    )

    private data class TableSelectionSnapshot(
        val item: TableNavigationItem,
        val tableViewData: PdmTableViewData,
    )
}
