package indi.nonoas.pdmreader.controller

import github.nonoas.jfx.flat.ui.concurrent.TaskHandler
import indi.nonoas.pdmreader.model.*
import indi.nonoas.pdmreader.service.PdmCatalogService
import javafx.application.Platform
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.ProgressIndicator
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

class MainController(
    private val catalogService: PdmCatalogService,
) {
    val windowTitle: String = "PDMReader4J"
    val imports: ObservableList<PdmImportSummary> = FXCollections.observableArrayList()
    val navigationItems: ObservableList<TableNavigationItem> = FXCollections.observableArrayList()
    val columns: ObservableList<PdmColumnDetail> = FXCollections.observableArrayList()

    val statusTextProperty: StringProperty = SimpleStringProperty("准备就绪，等待导入 PDM 文件。")
    val selectedTableTitleProperty: StringProperty = SimpleStringProperty("未选择表")
    val selectedTableMetaProperty: StringProperty = SimpleStringProperty("导入 PDM 后可浏览表结构。")
    val selectedTableCommentProperty: StringProperty = SimpleStringProperty("")
    val ddlTextProperty: StringProperty = SimpleStringProperty("")
    val highlightedColumnIdProperty: StringProperty = SimpleStringProperty("")
    val importProgressVisibleProperty: BooleanProperty = SimpleBooleanProperty(false)
    val importProgressTextProperty: StringProperty = SimpleStringProperty("")
    val importProgressValueProperty: DoubleProperty = SimpleDoubleProperty(ProgressIndicator.INDETERMINATE_PROGRESS)
    val searchPageTextProperty: StringProperty = SimpleStringProperty("")

    private val selectedImport = ReadOnlyObjectWrapper<PdmImportSummary?>()
    private val selectedNavigationItem = ReadOnlyObjectWrapper<TableNavigationItem?>()
    private val canCopyDdl = ReadOnlyBooleanWrapper(false)
    private val searchPaginationVisible = ReadOnlyBooleanWrapper(false)
    private val canGoToPreviousSearchPage = ReadOnlyBooleanWrapper(false)
    private val canGoToNextSearchPage = ReadOnlyBooleanWrapper(false)

    private var searchKeyword: String = ""
    private var searchGroupName: String? = null
    private var searchColumns: Boolean = false
    private var searchPageIndex: Int = 0
    private var scopedImportIds: List<Long> = emptyList()
    private var currentTableId: Long? = null
    private val requestSequence = AtomicLong(0)

    fun selectedImportProperty(): ReadOnlyObjectProperty<PdmImportSummary?> = selectedImport.readOnlyProperty

    fun selectedNavigationItemProperty(): ReadOnlyObjectProperty<TableNavigationItem?> =
        selectedNavigationItem.readOnlyProperty

    fun canCopyDdlProperty(): ReadOnlyBooleanProperty = canCopyDdl.readOnlyProperty

    fun searchPaginationVisibleProperty(): ReadOnlyBooleanProperty = searchPaginationVisible.readOnlyProperty

    fun canGoToPreviousSearchPageProperty(): ReadOnlyBooleanProperty = canGoToPreviousSearchPage.readOnlyProperty

    fun canGoToNextSearchPageProperty(): ReadOnlyBooleanProperty = canGoToNextSearchPage.readOnlyProperty

    fun initialize(onError: (Throwable) -> Unit = {}) {
        reloadImports(onError = onError)
    }

    fun setSearchGroupName(groupName: String?, onError: (Throwable) -> Unit = {}) {
        val normalizedGroupName = groupName?.trim()?.takeIf { it.isNotEmpty() }
        if (searchGroupName == normalizedGroupName) {
            return
        }

        searchGroupName = normalizedGroupName
        searchPageIndex = 0
        rebuildCurrentView(onError, statusText = buildSearchProgressText(searchKeyword))
    }

    fun setSearchColumns(searchColumns: Boolean, onError: (Throwable) -> Unit = {}) {
        if (this.searchColumns == searchColumns) {
            return
        }

        this.searchColumns = searchColumns
        searchPageIndex = 0
        rebuildCurrentView(onError, statusText = buildSearchProgressText(searchKeyword))
    }

    fun importPdm(path: Path, onError: (Throwable) -> Unit = {}) {
        importPdms(listOf(path), onError)
    }

    fun importPdms(paths: List<Path>, onError: (Throwable) -> Unit = {}) {
        if (paths.isEmpty()) {
            return
        }

        val keyword = searchKeyword
        beginImportProgress(
            if (paths.size == 1) {
                "准备导入 ${paths.first().fileName}..."
            } else {
                "准备导入 ${paths.size} 个 PDM 文件..."
            }
        )
        statusTextProperty.set(
            if (paths.size == 1) {
                "正在导入 ${paths.first().fileName}..."
            } else {
                "正在导入 ${paths.size} 个 PDM 文件..."
            }
        )
        runAsync(
            onError = onError,
            action = {
                val imported = paths.mapIndexed { index, path ->
                    updateImportProgress(index, paths.size, "正在导入 ${path.fileName}...")
                    catalogService.importPdm(path).also {
                        updateImportProgress(index + 1, paths.size, "已导入 ${index + 1}/${paths.size}")
                    }
                }
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
            onSuccess = { snapshot ->
                applySnapshot(snapshot)
            },
            onFinished = ::endImportProgress,
        )
    }

    fun removeImport(importSummary: PdmImportSummary, onError: (Throwable) -> Unit = {}) {
        removeImports(listOf(importSummary), onError)
    }

    fun removeImports(importSummaries: List<PdmImportSummary>, onError: (Throwable) -> Unit = {}) {
        val targets = importSummaries.distinctBy { it.id }
        if (targets.isEmpty()) {
            return
        }

        val keyword = searchKeyword
        val removedIds = targets.map { it.id }.toSet()
        val currentSelectedImportId = selectedImport.get()?.id
        statusTextProperty.set(
            if (targets.size == 1) {
                "正在移除 ${targets.first().fileName}..."
            } else {
                "正在移除 ${targets.size} 个 PDM..."
            }
        )
        runAsync(onError,
            action = {
                val removed = catalogService.deleteImports(removedIds)
                val imports = catalogService.listImports()
                val preferredImport = currentSelectedImportId
                    ?.takeIf { it !in removedIds }
                    ?.let { importId -> imports.firstOrNull { it.id == importId } }
                    ?: imports.firstOrNull()

                if (removed <= 0) {
                    buildSnapshot(
                        imports = imports,
                        selectedImport = preferredImport,
                        searchKeyword = keyword,
                        preferredTableId = null,
                        emptyStatusText = if (targets.size == 1) {
                            "未找到要移除的导入记录：${targets.first().fileName}"
                        } else {
                            "未找到要移除的导入记录。"
                        },
                    )
                } else {
                    val emptyStatusText = if (imports.isEmpty()) {
                        "已移除 ${removed} 个 PDM，当前没有已导入的 PDM 元数据。"
                    } else {
                        "当前没有已导入的 PDM 元数据。"
                    }
                    buildSnapshot(
                        imports = imports,
                        selectedImport = preferredImport,
                        searchKeyword = keyword,
                        preferredTableId = null,
                        emptyStatusText = emptyStatusText,
                    )
                }
            },
            onSuccess = ::applySnapshot
        )
    }

    fun renameImportGroup(
        importSummaries: List<PdmImportSummary>,
        groupName: String,
        onError: (Throwable) -> Unit = {},
    ) {
        val targets = importSummaries.distinctBy { it.id }
        val normalizedGroupName = groupName.trim()
        if (targets.isEmpty() || normalizedGroupName.isEmpty()) {
            return
        }

        val keyword = searchKeyword
        val currentSelectedImportId = selectedImport.get()?.id
        val preferredTableId = currentTableId
        statusTextProperty.set(
            if (targets.size == 1) {
                "正在更新 ${targets.first().fileName} 的分组..."
            } else {
                "正在重命名分组为 $normalizedGroupName..."
            }
        )
        runAsync(onError,
            action = {
                catalogService.renameImportGroup(targets.map { it.id }, normalizedGroupName)
                val imports = catalogService.listImports()
                val preferredImport = currentSelectedImportId?.let { importId ->
                    imports.firstOrNull { it.id == importId }
                } ?: imports.firstOrNull()
                buildSnapshot(
                    imports = imports,
                    selectedImport = preferredImport,
                    searchKeyword = keyword,
                    preferredTableId = preferredTableId,
                    emptyStatusText = "当前没有已导入的 PDM 元数据。",
                )
            },
            onSuccess = ::applySnapshot
        )
    }

    fun reloadImports(preferredImportId: Long? = selectedImport.get()?.id, onError: (Throwable) -> Unit = {}) {
        val keyword = searchKeyword
        val preferredTableId = currentTableId
        val preferredFilePath = imports.firstOrNull { it.id == preferredImportId }?.filePath
        statusTextProperty.set("正在刷新导入列表...")
        beginImportProgress("正在校验 PDM 文件是否更新...")
        runAsync(
            onError = onError,
            action = {
                val refreshResult = catalogService.refreshChangedImports { completed, total, message ->
                    updateImportProgress(completed, total, message)
                }
                val imports = catalogService.listImports()
                val targetImport = preferredImportId
                    ?.let { importId -> imports.firstOrNull { it.id == importId } }
                    ?: preferredFilePath?.let { path -> imports.firstOrNull { it.filePath == path } }
                    ?: refreshResult.reimported.lastOrNull()?.let { reimported ->
                        imports.firstOrNull { it.filePath == reimported.filePath }
                    }
                    ?: imports.firstOrNull()
                buildSnapshot(
                    imports = imports,
                    selectedImport = targetImport,
                    searchKeyword = keyword,
                    preferredTableId = preferredTableId,
                    emptyStatusText = "当前没有已导入的 PDM 元数据。",
                )
            },
            onSuccess = { snapshot ->
                applySnapshot(snapshot)
            },
            onFinished = ::endImportProgress,
        )
    }

    fun selectImport(importSummary: PdmImportSummary?, onError: (Throwable) -> Unit = {}) {
        scopedImportIds = importSummary?.let { listOf(it.id) } ?: emptyList()
        currentTableId = null
        searchPageIndex = 0
        val importsSnapshot = imports.toList()
        val keyword = searchKeyword
        statusTextProperty.set(
            if (keyword.isBlank()) {
                importSummary?.let { "正在加载 ${it.fileName} 的表结构..." } ?: "未选择导入文件。"
            } else {
                buildSearchProgressText(keyword)
            }
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

    fun selectImportGroup(importSummaries: List<PdmImportSummary>, onError: (Throwable) -> Unit = {}) {
        scopedImportIds = importSummaries.map { it.id }.distinct()
        currentTableId = null
        searchPageIndex = 0
        val importsSnapshot = imports.toList()
        val selectedImportSnapshot = selectedImport.get()
        statusTextProperty.set(
            if (scopedImportIds.isEmpty()) {
                "未选择分组。"
            } else if (searchKeyword.isNotBlank()) {
                buildSearchProgressText(searchKeyword)
            } else {
                "正在加载当前分组的表结构..."
            }
        )
        runAsync(onError,
            action = {
                buildSnapshot(
                    imports = importsSnapshot,
                    selectedImport = selectedImportSnapshot,
                    searchKeyword = searchKeyword,
                    preferredTableId = null,
                    emptyStatusText = "当前没有已导入的 PDM 元数据。",
                )
            },
            onSuccess = ::applySnapshot
        )
    }

    fun setSearchKeyword(keyword: String, onError: (Throwable) -> Unit = {}) {
        updateSearchKeyword(keyword, syncSelectionScope = false, onError = onError)
    }

    fun clearSearch(onError: (Throwable) -> Unit = {}) {
        updateSearchKeyword("", syncSelectionScope = true, onError = onError)
    }

    fun goToPreviousSearchPage(onError: (Throwable) -> Unit = {}) {
        if (searchPageIndex <= 0) {
            return
        }

        searchPageIndex -= 1
        rebuildCurrentView(onError, statusText = buildSearchProgressText(searchKeyword))
    }

    fun goToNextSearchPage(onError: (Throwable) -> Unit = {}) {
        if (!canGoToNextSearchPage.get()) {
            return
        }

        searchPageIndex += 1
        rebuildCurrentView(onError, statusText = buildSearchProgressText(searchKeyword))
    }

    private fun updateSearchKeyword(
        keyword: String,
        syncSelectionScope: Boolean,
        onError: (Throwable) -> Unit = {},
    ) {
        searchKeyword = keyword.trim()
        searchPageIndex = 0
        val normalizedKeyword = searchKeyword
        val importsSnapshot = imports.toList()
        val selectedImportSnapshot = selectedImport.get()
        val preferredTableId = currentTableId
        statusTextProperty.set(buildSearchProgressText(normalizedKeyword))
        runAsync(onError,
            action = {
                buildSnapshot(
                    imports = importsSnapshot,
                    selectedImport = selectedImportSnapshot,
                    searchKeyword = normalizedKeyword,
                    preferredTableId = preferredTableId,
                    emptyStatusText = "当前没有已导入的 PDM 元数据。",
                    syncSelectionScope = syncSelectionScope,
                )
            },
            onSuccess = ::applySnapshot
        )
    }

    fun selectNavigationItem(item: TableNavigationItem?, onError: (Throwable) -> Unit = {}) {
        if (item == null) {
            selectedNavigationItem.set(null)
            clearTableDetails()
            return
        }

        currentTableId = item.tableId
        statusTextProperty.set("正在加载 ${item.tableName} 的表详情...")
        runAsync(onError,
            action = {
                TableSelectionSnapshot(
                    item = item,
                    tableViewData = catalogService.loadTableViewData(item.tableId),
                )
            },
            onSuccess = { snapshot ->
                imports.firstOrNull { it.id == snapshot.item.importId }?.let(selectedImport::set)
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
        if (imports != snapshot.imports) {
            imports.setAll(snapshot.imports)
        }
        selectedImport.set(snapshot.selectedImport)
        navigationItems.setAll(snapshot.navigationItems)
        selectedNavigationItem.set(snapshot.selectedNavigationItem)
        applySearchPage(snapshot.searchPage)
        if (snapshot.tableViewData == null) {
            clearTableDetails(clearCurrentTableSelection = snapshot.syncSelectionScope)
            statusTextProperty.set(snapshot.emptyStatusText)
            return
        }

        applyTableViewData(
            tableViewData = snapshot.tableViewData,
            highlightedColumnId = snapshot.highlightedColumnId,
            syncCurrentTableSelection = snapshot.syncSelectionScope,
        )
    }

    private fun applySearchPage(searchPage: NavigationSearchPage?) {
        if (searchPage == null) {
            searchPageIndex = 0
            searchPaginationVisible.set(false)
            canGoToPreviousSearchPage.set(false)
            canGoToNextSearchPage.set(false)
            searchPageTextProperty.set("")
            return
        }

        searchPageIndex = searchPage.pageIndex
        val totalPages = if (searchPage.totalCount <= 0) {
            0
        } else {
            (searchPage.totalCount - 1) / searchPage.pageSize + 1
        }
        searchPaginationVisible.set(true)
        canGoToPreviousSearchPage.set(searchPage.pageIndex > 0)
        canGoToNextSearchPage.set(searchPage.pageIndex + 1 < totalPages)
        searchPageTextProperty.set(
            if (totalPages == 0) {
                "第 0 / 0 页 · 共 0 条"
            } else {
                "第 ${searchPage.pageIndex + 1} / $totalPages 页 · 共 ${searchPage.totalCount} 条"
            }
        )
    }

    private fun applyTableViewData(
        tableViewData: PdmTableViewData,
        highlightedColumnId: String?,
        syncCurrentTableSelection: Boolean = true,
    ) {
        val details = tableViewData.details
        if (syncCurrentTableSelection) {
            currentTableId = details.tableId
        }
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
                append('\t')
                append("分组：")
                append(details.importGroupName)
                append('\t')
                append("目标库：")
                append(details.targetDb ?: "未知")
                append('\n')
                append("所属文件：")
                append(details.importFilePath)
            }
        )
        selectedTableCommentProperty.set(details.tableComment ?: "")
        this.highlightedColumnIdProperty.set(highlightedColumnId.orEmpty())
        statusTextProperty.set(buildTableStatus(tableViewData))
    }

    private fun clearTableDetails(clearCurrentTableSelection: Boolean = true) {
        if (clearCurrentTableSelection) {
            currentTableId = null
        }
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
        syncSelectionScope: Boolean = true,
    ): ViewSnapshot {
        if (imports.isEmpty()) {
            return ViewSnapshot(
                imports = imports,
                selectedImport = null,
                navigationItems = emptyList(),
                selectedNavigationItem = null,
                tableViewData = null,
                highlightedColumnId = "",
                emptyStatusText = emptyStatusText,
                syncSelectionScope = syncSelectionScope,
                searchPage = null,
            )
        }

        val normalizedSelectedImport = selectedImport ?: imports.firstOrNull()
        val normalizedScopedImportIds = scopedImportIds.filter { scopedId ->
            imports.any { it.id == scopedId }
        }
        if (searchKeyword.isBlank() && normalizedSelectedImport == null && normalizedScopedImportIds.isEmpty()) {
            return ViewSnapshot(
                imports = imports,
                selectedImport = null,
                navigationItems = emptyList(),
                selectedNavigationItem = null,
                tableViewData = null,
                highlightedColumnId = "",
                emptyStatusText = emptyStatusText,
                syncSelectionScope = syncSelectionScope,
                searchPage = null,
            )
        }

        val effectiveSearchScope = resolveEffectiveSearchScope(
            keyword = searchKeyword,
            imports = imports,
        )
        var searchPage: NavigationSearchPage? = null
        val navigationItems = if (searchKeyword.isBlank()) {
            val loadedNavigationPage = when {
                effectiveSearchScope is EffectiveSearchScope.IMPORTS -> catalogService.loadNavigationPage(
                    importIds = effectiveSearchScope.importIds,
                    pageIndex = searchPageIndex,
                    pageSize = DEFAULT_SEARCH_PAGE_SIZE,
                )

                normalizedScopedImportIds.isNotEmpty() -> catalogService.loadNavigationPage(
                    importIds = normalizedScopedImportIds,
                    pageIndex = searchPageIndex,
                    pageSize = DEFAULT_SEARCH_PAGE_SIZE,
                )

                normalizedSelectedImport != null -> catalogService.loadNavigationPage(
                    importIds = listOf(normalizedSelectedImport.id),
                    pageIndex = searchPageIndex,
                    pageSize = DEFAULT_SEARCH_PAGE_SIZE,
                )

                else -> NavigationSearchPage(
                    items = emptyList(),
                    totalCount = 0,
                    pageIndex = 0,
                    pageSize = DEFAULT_SEARCH_PAGE_SIZE,
                )
            }
            searchPage = loadedNavigationPage
            loadedNavigationPage.items
        } else {
            val loadedSearchPage = when (effectiveSearchScope) {
                EffectiveSearchScope.GLOBAL -> catalogService.searchNavigationPage(
                    keyword = searchKeyword,
                    searchColumns = searchColumns,
                    pageIndex = searchPageIndex,
                    pageSize = DEFAULT_SEARCH_PAGE_SIZE,
                )

                is EffectiveSearchScope.IMPORTS -> catalogService.searchNavigationPage(
                    keyword = searchKeyword,
                    importIds = effectiveSearchScope.importIds,
                    searchColumns = searchColumns,
                    pageIndex = searchPageIndex,
                    pageSize = DEFAULT_SEARCH_PAGE_SIZE,
                )
            }
            searchPage = loadedSearchPage
            loadedSearchPage.items
        }
        if (navigationItems.isEmpty()) {
            return ViewSnapshot(
                imports = imports,
                selectedImport = normalizedSelectedImport,
                navigationItems = emptyList(),
                selectedNavigationItem = null,
                tableViewData = null,
                highlightedColumnId = "",
                emptyStatusText = if (searchKeyword.isBlank()) {
                    buildEmptyNavigationStatusText(effectiveSearchScope, normalizedScopedImportIds, normalizedSelectedImport)
                } else {
                    "未在${describeSearchScope(effectiveSearchScope)}中找到与“$searchKeyword”匹配的${searchTargetName()}。"
                },
                syncSelectionScope = syncSelectionScope,
                searchPage = searchPage,
            )
        }

        val preferredItem = preferredTableId?.let { tableId ->
            navigationItems.firstOrNull { it.tableId == tableId }
        } ?: if (searchKeyword.isNotBlank() && normalizedSelectedImport != null) {
            navigationItems.firstOrNull { it.importId == normalizedSelectedImport.id }
        } else {
            navigationItems.firstOrNull()
        }

        if (preferredItem == null) {
            return ViewSnapshot(
                imports = imports,
                selectedImport = normalizedSelectedImport,
                navigationItems = navigationItems,
                selectedNavigationItem = null,
                tableViewData = null,
                highlightedColumnId = "",
                emptyStatusText = if (searchKeyword.isBlank()) {
                    buildEmptyNavigationStatusText(effectiveSearchScope, normalizedScopedImportIds, normalizedSelectedImport)
                } else {
                    "未在${describeSearchScope(effectiveSearchScope)}中找到与“$searchKeyword”匹配的${searchTargetName()}。"
                },
                syncSelectionScope = syncSelectionScope,
                searchPage = searchPage,
            )
        }

        val resolvedSelectedImport = imports.firstOrNull { it.id == preferredItem.importId } ?: normalizedSelectedImport
        val snapshotSelectedImport = if (syncSelectionScope) {
            resolvedSelectedImport
        } else {
            normalizedSelectedImport
        }

        return ViewSnapshot(
            imports = imports,
            selectedImport = snapshotSelectedImport,
            navigationItems = navigationItems,
            selectedNavigationItem = preferredItem,
            tableViewData = catalogService.loadTableViewData(preferredItem.tableId),
            highlightedColumnId = preferredItem.matchedColumnIdInPdm.orEmpty(),
            emptyStatusText = "",
            syncSelectionScope = syncSelectionScope,
            searchPage = searchPage,
        )
    }

    private fun buildEmptyNavigationStatusText(
        effectiveSearchScope: EffectiveSearchScope,
        scopedImportIds: List<Long>,
        selectedImport: PdmImportSummary?,
    ): String =
        when {
            effectiveSearchScope is EffectiveSearchScope.IMPORTS -> "${describeSearchScope(effectiveSearchScope)}中没有可展示的表。"
            scopedImportIds.size > 1 -> "当前分组中没有可展示的表。"
            selectedImport != null -> "文件 ${selectedImport.fileName} 中没有可展示的表。"
            else -> "当前没有已导入的 PDM 元数据。"
        }

    private fun resolveEffectiveSearchScope(
        keyword: String,
        imports: List<PdmImportSummary>,
    ): EffectiveSearchScope {
        val normalizedSearchGroupName = searchGroupName?.trim()?.takeIf { it.isNotEmpty() }
        if (keyword.isBlank() && normalizedSearchGroupName == null) {
            return EffectiveSearchScope.GLOBAL
        }

        normalizedSearchGroupName?.let { groupName ->
            return EffectiveSearchScope.IMPORTS(
                importIds = imports
                    .filter { importSummary -> importSummary.groupName == groupName }
                    .map { importSummary -> importSummary.id },
                groupName = groupName,
            )
        }

        return EffectiveSearchScope.GLOBAL
    }

    private fun describeSearchScope(scope: EffectiveSearchScope): String =
        when (scope) {
            EffectiveSearchScope.GLOBAL -> "全部已导入的 PDM"
            is EffectiveSearchScope.IMPORTS -> "分组 ${scope.groupName}"
        }

    private fun buildSearchProgressText(keyword: String): String =
        if (keyword.isBlank()) {
            "正在加载表清单..."
        } else {
            "正在${describeConfiguredSearchScope()}中${describeSearchAction()}“$keyword”..."
        }

    private fun describeSearchAction(): String =
        if (searchColumns) "搜索字段" else "搜索表"

    private fun searchTargetName(): String =
        if (searchColumns) "字段" else "表"

    private fun describeConfiguredSearchScope(): String =
        searchGroupName?.let { "分组 $it" } ?: "全部已导入 PDM"

    private fun rebuildCurrentView(
        onError: (Throwable) -> Unit,
        statusText: String,
    ) {
        val importsSnapshot = imports.toList()
        val selectedImportSnapshot = selectedImport.get()
        val preferredTableId = currentTableId
        statusTextProperty.set(statusText)
        runAsync(onError,
            action = {
                buildSnapshot(
                    imports = importsSnapshot,
                    selectedImport = selectedImportSnapshot,
                    searchKeyword = searchKeyword,
                    preferredTableId = preferredTableId,
                    emptyStatusText = "当前没有已导入的 PDM 元数据。",
                )
            },
            onSuccess = ::applySnapshot
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

    private fun beginImportProgress(message: String) {
        updateImportProgress(0, 0, message)
        Platform.runLater {
            importProgressVisibleProperty.set(true)
        }
    }

    private fun updateImportProgress(completed: Int, total: Int, message: String) {
        val progress = if (total <= 0) {
            ProgressIndicator.INDETERMINATE_PROGRESS
        } else {
            completed.toDouble() / total.toDouble()
        }
        Platform.runLater {
            importProgressTextProperty.set(message)
            importProgressValueProperty.set(progress)
        }
    }

    private fun endImportProgress() {
        Platform.runLater {
            importProgressVisibleProperty.set(false)
            importProgressValueProperty.set(ProgressIndicator.INDETERMINATE_PROGRESS)
            importProgressTextProperty.set("")
        }
    }

    private fun <T> runAsync(
        onError: (Throwable) -> Unit,
        action: () -> T,
        onSuccess: (T) -> Unit,
        onFinished: () -> Unit = {},
    ) {
        val requestId = requestSequence.incrementAndGet()
        TaskHandler<Result<T>>()
            .whenCall { runCatching(action) }
            .andThen { result ->
                try {
                    if (requestId != requestSequence.get()) {
                        return@andThen
                    }
                    result
                        .onSuccess(onSuccess)
                        .onFailure { exception ->
                            statusTextProperty.set("执行失败：${exception.message ?: "未知错误"}")
                            onError(exception)
                        }
                } finally {
                    onFinished()
                }
            }
            .handle()
    }

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private const val DEFAULT_SEARCH_PAGE_SIZE = 50
    }

    private data class ViewSnapshot(
        val imports: List<PdmImportSummary>,
        val selectedImport: PdmImportSummary?,
        val navigationItems: List<TableNavigationItem>,
        val selectedNavigationItem: TableNavigationItem?,
        val tableViewData: PdmTableViewData?,
        val highlightedColumnId: String,
        val emptyStatusText: String,
        val syncSelectionScope: Boolean,
        val searchPage: NavigationSearchPage? = null,
    )

    private data class TableSelectionSnapshot(
        val item: TableNavigationItem,
        val tableViewData: PdmTableViewData,
    )

    private sealed interface EffectiveSearchScope {
        data object GLOBAL : EffectiveSearchScope

        data class IMPORTS(val importIds: List<Long>, val groupName: String) : EffectiveSearchScope
    }
}
