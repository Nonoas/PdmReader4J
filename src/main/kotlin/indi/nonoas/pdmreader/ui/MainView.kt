package indi.nonoas.pdmreader.ui

import github.nonoas.jfx.flat.ui.AppState
import github.nonoas.jfx.flat.ui.stage.AppStage
import github.nonoas.jfx.flat.ui.theme.Styles
import github.nonoas.jfx.flat.ui.theme.Theme
import indi.nonoas.pdmreader.app.AppThemeManager
import indi.nonoas.pdmreader.controller.MainController
import indi.nonoas.pdmreader.model.NavigationItemType
import indi.nonoas.pdmreader.model.SearchScopeMode
import indi.nonoas.pdmreader.model.TableNavigationItem
import indi.nonoas.pdmreader.service.PdmCatalogService
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleStringProperty
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.stage.FileChooser
import javafx.stage.Stage
import javafx.util.Callback
import javafx.util.StringConverter
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Path

class MainView(
    private val controller: MainController,
    private val stage: Stage,
    private val useExtendedWindow: Boolean,
    private val themeManager: AppThemeManager,
    private val catalogService: PdmCatalogService,
) {
    private val logger = LoggerFactory.getLogger(MainView::class.java)

    private val selectionContextProperty = SimpleStringProperty("未选中")
    private var importProgressDialog: Alert? = null

    companion object {
        const val APP_VERSION = "0.0.1"
        const val GITHUB_REPO = "Nonoas/PdmReader4J"
        val PAGE_PADDING = Insets(12.0)
        val SECTION_PADDING = Insets(10.0)
    }

    fun createContent(): BorderPane {
        var suppressSearchFieldChange = false
        val searchField = TextField().apply {
            promptText = "搜索表名、表编码、字段名或字段编码"
            styleClass.add("search-input")
            textProperty().addListener { _, _, newValue ->
                if (suppressSearchFieldChange) {
                    return@addListener
                }
                controller.setSearchKeyword(newValue, ::showError)
            }
            HBox.setHgrow(this, Priority.ALWAYS)
        }
        val searchScopeComboBox = ComboBox<SearchScopeMode>().apply {
            items.addAll(SearchScopeMode.CURRENT_SELECTION, SearchScopeMode.GLOBAL)
            value = SearchScopeMode.CURRENT_SELECTION
            converter = object : StringConverter<SearchScopeMode>() {
                override fun toString(scope: SearchScopeMode?): String = when (scope) {
                    SearchScopeMode.CURRENT_SELECTION -> "当前选中范围"
                    SearchScopeMode.GLOBAL -> "全局"
                    null -> ""
                }

                override fun fromString(text: String?): SearchScopeMode? =
                    items.firstOrNull { toString(it) == text }
            }
            buttonCell = object : ListCell<SearchScopeMode>() {
                init {
                    textProperty().bind(Bindings.createStringBinding({
                        val currentItem: SearchScopeMode? = item
                        when (currentItem) {
                            SearchScopeMode.CURRENT_SELECTION -> selectionContextProperty.get()
                            SearchScopeMode.GLOBAL -> "全局"
                            null -> ""
                        }
                    }, selectionContextProperty, itemProperty()))
                }
            }
            prefWidth = 110.0
            styleClass.add("toolbar-combo")
            tooltip = Tooltip("当前选中支持分组、单个 PDM 文件和表；全局会搜索全部已导入内容")
            selectionModel.selectedItemProperty().addListener { _, _, newValue ->
                newValue?.let { controller.setSearchScopeMode(it, ::showError) }
            }
        }

        val importButton = Button("导入 PDM").apply {
            styleClass.add(Styles.ACCENT)
            setOnAction {
                val selectedFiles = choosePdmFiles()
                if (selectedFiles.isNotEmpty()) {
                    controller.importPdms(selectedFiles.map(File::toPath), ::showError)
                }
            }
        }
        val refreshButton = Button("刷新").apply {
            setOnAction {
                controller.reloadImports(onError = ::showError)
            }
        }
        val clearSearchButton = Button("清空搜索").apply {
            setOnAction {
                suppressSearchFieldChange = true
                try {
                    searchField.clear()
                } finally {
                    suppressSearchFieldChange = false
                }
                controller.clearSearch(::showError)
            }
        }
        val copyDdlButton = Button("复制 DDL").apply {
            isDisable = true
        }
        val aboutButton = Button("关于").apply {
            setOnAction {
                showAboutDialog()
            }
        }
        val themeSwitcher = createThemeSwitcher()
        val searchScopeSwitcher = HBox(
            6.0,
            Label("范围").apply { styleClass.add("field-label") },
            searchScopeComboBox,
        ).apply {
            alignment = Pos.CENTER_LEFT
            styleClass.add("toolbar-group")
        }

        val toolbar = HBox(
            8.0,
            importButton,
            refreshButton,
            copyDdlButton,
            searchScopeSwitcher,
            searchField,
            clearSearchButton,
            themeSwitcher,
            aboutButton
        ).apply {
            alignment = Pos.CENTER_LEFT
            padding = PAGE_PADDING
            styleClass.add("toolbar-strip")
        }

        val tableTabPane = TabPane().apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.ALL_TABS
            styleClass.add("table-tab-pane")
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        // Track active tab for toolbar interactions
        val activeTableTabProperty = ReadOnlyObjectWrapper<TableDetailTab?>()
        tableTabPane.selectionModel.selectedItemProperty().addListener { _, _, tab ->
            activeTableTabProperty.set(tab as? TableDetailTab)
        }

        // Re-bind copy DDL button to active tab
        copyDdlButton.setOnAction {
            val tab = activeTableTabProperty.get()
            if (tab != null && tab.copyDdl()) {
                controller.statusTextProperty.set("DDL 已复制到剪贴板。")
            }
        }
        copyDdlButton.disableProperty().unbind()
        copyDdlButton.disableProperty().bind(
            Bindings.createBooleanBinding(
                { activeTableTabProperty.get()?.hasDdlProperty?.get() != true },
                activeTableTabProperty,
            ).also { binding ->
                activeTableTabProperty.addListener { _, _, tab ->
                    binding.invalidate()
                    tab?.hasDdlProperty?.addListener { _, _, _ -> binding.invalidate() }
                }
            }
        )

        // Close tabs for removed PDM imports
        controller.imports.addListener(ListChangeListener { change ->
            while (change.next()) {
                if (change.wasRemoved()) {
                    val removedIds = change.removed.map { it.id }.toSet()
                    tableTabPane.tabs.removeAll(
                        tableTabPane.tabs.filterIsInstance<TableDetailTab>()
                            .filter { it.importId in removedIds }
                    )
                }
            }
        })

        val importTreePane = ImportTreePane(controller, ::showError, selectionContextProperty::set)
        val navigationListView = createNavigationListView(tableTabPane, importTreePane)

        val rightPane = StackPane(
            tableTabPane,
            Label("选择左侧列表中的表后查看详情").apply {
                styleClass.add("tree-placeholder")
                isMouseTransparent = true
                visibleProperty().bind(
                    Bindings.isEmpty(tableTabPane.tabs)
                )
                managedProperty().bind(visibleProperty())
            },
        ).apply {
            padding = PAGE_PADDING
            styleClass.add("detail-panel")
        }

        val leftPane = VBox(
            8.0,
            createSectionPane("已导入文件", importTreePane),
            createSectionPane("表与搜索结果", navigationListView),
        ).apply {
            prefWidth = 320.0
            padding = PAGE_PADDING
            styleClass.add("sidebar-panel")
        }

        val splitPane = SplitPane(leftPane, rightPane).apply {
            orientation = Orientation.HORIZONTAL
            setDividerPositions(0.31)
            styleClass.add("workspace-split")
        }

        val statusLabel = Label().apply {
            textProperty().bind(controller.statusTextProperty)
            maxWidth = Double.MAX_VALUE
            styleClass.add("status-bar")
        }

        val topContainer = VBox().apply {
            styleClass.add("top-shell")
            if (useExtendedWindow) {
                children.add(createHeaderBar())
            }
            children.add(toolbar)
        }

        val root = BorderPane().apply {
            top = topContainer
            center = splitPane
            bottom = statusLabel
            styleClass.add("app-shell")
        }

        installImportProgressDialog()
        controller.initialize(::showError)
        return root
    }

    private fun createThemeSwitcher(): HBox {
        val themeComboBox = ComboBox<Theme>().apply {
            items.addAll(themeManager.availableThemes())
            converter = object : StringConverter<Theme>() {
                override fun toString(theme: Theme?): String = theme?.displayName().orEmpty()

                override fun fromString(text: String?): Theme? =
                    items.firstOrNull { it.displayName() == text }
            }
            value = themeManager.currentTheme()
            visibleRowCount = items.size.coerceAtMost(6)
            prefWidth = 120.0
            styleClass.add("toolbar-combo")
            tooltip = Tooltip("切换界面主题")
            selectionModel.selectedItemProperty().addListener { _, _, newTheme ->
                themeManager.switchTheme(newTheme)
            }
            themeManager.currentThemeProperty().addListener { _, _, newTheme ->
                if (newTheme != null && selectionModel.selectedItem?.name != newTheme.name) {
                    selectionModel.select(newTheme)
                }
            }
        }

        return HBox(
            6.0,
            Label("主题").apply { styleClass.add("field-label") },
            themeComboBox,
        ).apply {
            alignment = Pos.CENTER_LEFT
            styleClass.add("toolbar-group")
        }
    }

    @Suppress("DEPRECATION")
    private fun createHeaderBar(): HeaderBar {
        val brandBlock = HBox(
            10.0,
            Label("PDM").apply {
                styleClass.add("brand-mark")
            },
            VBox(
                2.0,
                Label(controller.windowTitle).apply { styleClass.add("brand-title") },
                Label("PowerDesigner 模型阅读与检索").apply { styleClass.add("brand-subtitle") },
            ).apply {
                alignment = Pos.CENTER_LEFT
            },
        ).apply {
            alignment = Pos.CENTER_LEFT
            styleClass.add("brand-block")
        }

        val previewChip = Label("JavaFX 25 Preview").apply {
            styleClass.add("window-pill")
        }

        return (stage as AppStage).headerBar.apply {
            styleClass.add("window-header")
            leading = brandBlock
            prefHeight = 48.0
            padding = PAGE_PADDING
            HeaderBar.setMargin(brandBlock, Insets(0.0, 0.0, 0.0, 4.0))
            HeaderBar.setMargin(previewChip, Insets(0.0, 6.0, 0.0, 0.0))
            HeaderBar.setDragType(brandBlock, HeaderDragType.DRAGGABLE_SUBTREE)
            HeaderBar.setDragType(previewChip, HeaderDragType.DRAGGABLE)
        }
    }

    private fun createSectionPane(title: String, content: Region): VBox {
        val contentWrapper = StackPane(content).apply {
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        return VBox(
            6.0,
            Label(title).apply { styleClass.add("panel-title") },
            contentWrapper,
        ).apply {
            styleClass.add("panel-section")
            styleClass.add("panel-surface")
            VBox.setVgrow(contentWrapper, Priority.ALWAYS)
        }
    }

    private fun createNavigationListView(
        tableTabPane: TabPane,
        importTreePane: ImportTreePane,
    ): ListView<TableNavigationItem> =
        ListView(controller.navigationItems).apply {
            val applyingControllerSelection = booleanArrayOf(false)
            selectionModel.selectionMode = SelectionMode.SINGLE
            placeholder = Label("选择分组或文件后显示表清单，输入关键字后可按当前选中范围或全局搜索")
            styleClass.addAll("compact-list", "navigation-list")
            cellFactory = Callback {
                object : ListCell<TableNavigationItem>() {
                    private val typeLabel = Label().apply {
                        styleClass.add("type-badge")
                    }
                    private val nameLabel = Label().apply {
                        styleClass.add("list-item-title")
                    }
                    private val titleRow = HBox(4.0, typeLabel, nameLabel).apply {
                        alignment = Pos.CENTER_LEFT
                    }
                    private val metaLabel = Label().apply {
                        styleClass.add("list-item-meta")
                        isWrapText = true
                    }

                    private val contentCard = VBox(2.0, titleRow, metaLabel).apply {
                        styleClass.add("list-item-box")
                    }

                    private val contentBox = StackPane(contentCard)

                    init {
                        contentDisplay = ContentDisplay.GRAPHIC_ONLY
                        addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
                            val currentItem = item
                            if (
                                event.button == MouseButton.PRIMARY &&
                                !isEmpty &&
                                currentItem != null &&
                                selectionModel.selectedItem == currentItem
                            ) {
                                activateNavigationItem(tableTabPane, currentItem)
                            }
                        }
                    }

                    override fun updateItem(item: TableNavigationItem?, empty: Boolean) {
                        super.updateItem(item, empty)

                        if (empty || item == null) {
                            graphic = null
                            return
                        }

                        val tableCode = item.tableCode?.takeIf { it.isNotBlank() } ?: item.tableName
                        val tableName = item.tableName.ifBlank { "未命名表" }
                        typeLabel.apply {
                            styleClass.removeAll("table-type", "column-type")
                            when (item.type) {
                                NavigationItemType.TABLE -> styleClass.add("table-type")
                                NavigationItemType.COLUMN_MATCH -> styleClass.add("column-type")
                            }
                            text = when (item.type) {
                                NavigationItemType.TABLE -> "[表]"
                                else -> "[列]"
                            }
                        }
                        nameLabel.text = when (item.type) {
                            NavigationItemType.TABLE -> tableCode
                            else -> {
                                val columnCode =
                                    item.matchedColumnCode?.takeIf { it.isNotBlank() } ?: item.matchedColumnName
                                "$tableCode / ${columnCode.orEmpty()}"
                            }
                        }
                        metaLabel.text = when (item.type) {
                            NavigationItemType.TABLE -> buildString {
                                append(tableName)
                                append(" · ")
                                append(item.importFileName)
                                append(" · ")
                                append(item.importGroupName)
                            }

                            else -> {
                                val columnName = item.matchedColumnName?.takeIf { it.isNotBlank() } ?: "未命名字段"
                                buildString {
                                    append(columnName)
                                    append(" · ")
                                    append(tableName)
                                    append(" · ")
                                    append(item.importFileName)
                                    append(" · ")
                                    append(item.importGroupName)
                                }
                            }
                        }
                        if (graphic !== contentBox) graphic = contentBox
                    }
                }
            }
            selectionModel.selectedItemProperty().addListener { _, _, newValue ->
                if (applyingControllerSelection[0]) {
                    return@addListener
                }
                if (controller.selectedNavigationItemProperty().value == newValue) {
                    return@addListener
                }
                if (newValue != null) {
                    activateNavigationItem(tableTabPane, newValue)
                } else {
                    Platform.runLater {
                        if (selectionModel.selectedItem == null && controller.selectedNavigationItemProperty().value == null) {
                            selectionContextProperty.set(importTreePane.currentSelectionContextText())
                            controller.selectNavigationItem(null, ::showError)
                        }
                    }
                    return@addListener
                }
            }
            bindSelection(controller.selectedNavigationItemProperty(), applyingControllerSelection)
        }

    private fun activateNavigationItem(tableTabPane: TabPane, item: TableNavigationItem) {
        val tableCode = item.tableCode?.takeIf { it.isNotBlank() } ?: item.tableName
        selectionContextProperty.set(tableCode)
        openTableTab(tableTabPane, item)

        if (controller.selectedNavigationItemProperty().value != item) {
            controller.selectNavigationItem(item, ::showError)
        }
    }

    private fun openTableTab(tableTabPane: TabPane, item: TableNavigationItem) {
        val existing = tableTabPane.tabs
            .filterIsInstance<TableDetailTab>()
            .firstOrNull { it.tableId == item.tableId }
        if (existing != null) {
            tableTabPane.selectionModel.select(existing)
            existing.focusColumn(item.matchedColumnIdInPdm)
        } else {
            val tab = TableDetailTab(item, catalogService, ::showError)
            tableTabPane.tabs.add(tab)
            tableTabPane.selectionModel.select(tab)
        }
    }

    private fun <T> ListView<T>.bindSelection(
        property: ReadOnlyObjectProperty<T?>,
        applyingControllerSelection: BooleanArray,
    ) {
        property.addListener { _, _, newValue ->
            if (selectionModel.selectedItem != newValue) {
                applyingControllerSelection[0] = true
                try {
                    selectionModel.select(newValue)
                } finally {
                    applyingControllerSelection[0] = false
                }
            }
        }
    }

    private fun choosePdmFiles(): List<File> =
        FileChooser().apply {
            title = "选择 PowerDesigner PDM 文件"
            extensionFilters.add(
                FileChooser.ExtensionFilter("PowerDesigner PDM", "*.pdm")
            )
            initialDirectory = defaultInitialDirectory()
        }.showOpenMultipleDialog(stage).orEmpty()

    private fun defaultInitialDirectory(): File {
        val sampleDirectory = Path.of(
            "D:\\kingdom\\0_Project\\fvs\\fvs-doc\\02Design\\2.3ScriptDesign\\01表结构设计\\database"
        ).toFile()
        return when {
            sampleDirectory.exists() -> sampleDirectory
            else -> File(System.getProperty("user.home"))
        }
    }

    private fun showError(exception: Throwable) {
        logger.error(exception.message, exception)
        DialogWithIcon.error("执行失败", exception.message ?: "未知错误")
    }

    private fun installImportProgressDialog() {
        controller.importProgressVisibleProperty.addListener { _, _, visible ->
            if (visible) {
                showImportProgressDialog()
            } else {
                closeImportProgressDialog()
            }
        }
    }

    private fun showImportProgressDialog() {
        if (importProgressDialog != null) {
            return
        }

        val progressBar = ProgressBar().apply {
            prefWidth = 360.0
            progressProperty().bind(controller.importProgressValueProperty)
        }
        val progressLabel = Label().apply {
            textProperty().bind(controller.importProgressTextProperty)
            maxWidth = 360.0
            isWrapText = true
        }
        val content = VBox(10.0, progressLabel, progressBar).apply {
            padding = Insets(16.0, 4.0, 4.0, 4.0)
        }

        importProgressDialog = Alert(Alert.AlertType.INFORMATION).apply {
            title = "同步 PDM"
            headerText = "正在校验并导入 PDM 文件"
            dialogPane.content = content
            dialogPane.buttonTypes.setAll(ButtonType.CLOSE)
            isResizable = true
            initOwner(stage)
            setOnHidden { importProgressDialog = null }
            show()
        }
    }

    private fun closeImportProgressDialog() {
        importProgressDialog?.dialogPane?.content?.let { content ->
            (content as? VBox)?.children?.forEach { node ->
                when (node) {
                    is Label -> node.textProperty().unbind()
                    is ProgressBar -> node.progressProperty().unbind()
                    else -> Unit
                }
            }
        }
        importProgressDialog?.close()
        importProgressDialog = null
    }

    private fun showAboutDialog() {
        val repoUrl = "https://github.com/$GITHUB_REPO"
        Alert(Alert.AlertType.INFORMATION).apply {
            title = "关于 PdmReader4J"
            headerText = "PdmReader4J - PowerDesigner PDM 文件阅读器"
            initOwner(AppState.getStage())
            dialogPane.content = VBox(14.0).apply {
                padding = Insets(20.0)
                prefWidth = 380.0

                children.addAll(
                    Label("版本: $APP_VERSION"),
                    Label("作者: Nonoas"),
                    HBox(
                        Label("GitHub:"),
                        Hyperlink(repoUrl).apply {
                            setOnAction {
                                Desktop.getDesktop().browse(URI(repoUrl))
                            }
                        },
                    ).apply {
                        alignment = Pos.CENTER_LEFT
                    },
                    Button("检查更新").apply {
                        setOnAction {
                            isDisable = true
                            text = "正在检查..."
                            checkForUpdates {
                                Platform.runLater {
                                    isDisable = false
                                    text = "检查更新"
                                }
                            }
                        }
                    },
                )
            }
        }.showAndWait()
    }

    private fun checkForUpdates(onDone: () -> Unit) {
        Thread({
            try {
                val apiUrl = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
                val release = fetchGitHubRelease(apiUrl)

                Platform.runLater {
                    onDone()
                    when {
                        release == null -> {
                            showError(Exception("无法获取版本信息，请检查网络连接"))
                        }

                        compareVersions(APP_VERSION, release.version) -> {
                            showUpdateDialog(release)
                        }

                        else -> {
                            DialogWithIcon.info("检查更新", "当前版本 $APP_VERSION 已是最新。")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("检查更新失败", e)
                Platform.runLater {
                    onDone()
                    showError(Exception("检查更新失败: ${e.message}"))
                }
            }
        }, "update-checker").apply {
            isDaemon = true
            start()
        }
    }

    private data class GitHubRelease(
        val version: String,
        val htmlUrl: String,
        val body: String,
        val assets: List<GitHubAsset>,
    )

    private data class GitHubAsset(
        val name: String,
        val downloadUrl: String,
        val size: Long,
    )

    private fun fetchGitHubRelease(apiUrl: String): GitHubRelease? {
        return try {
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "PdmReader4J")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                logger.warn("GitHub API 返回 {}，可能尚无 release 或网络不通", responseCode)
                return null
            }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)

            val tagName = obj.getString("tag_name")
            val version = tagName.removePrefix("v")
            val htmlUrl = obj.getString("html_url")
            val body = obj.optString("body", "")

            val assets = mutableListOf<GitHubAsset>()
            val assetsArr = obj.optJSONArray("assets")
            if (assetsArr != null) {
                for (i in 0 until assetsArr.length()) {
                    val a = assetsArr.getJSONObject(i)
                    assets.add(
                        GitHubAsset(
                            name = a.getString("name"),
                            downloadUrl = a.getString("browser_download_url"),
                            size = a.getLong("size"),
                        )
                    )
                }
            }
            logger.info("获取到最新版本: {}，包含 {} 个下载文件", version, assets.size)
            GitHubRelease(version, htmlUrl, body, assets)
        } catch (e: Exception) {
            logger.error("请求 GitHub API 失败", e)
            null
        }
    }

    private fun compareVersions(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until minOf(currentParts.size, latestParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    private fun showUpdateDialog(release: GitHubRelease) {
        val assetList = if (release.assets.isEmpty()) {
            "（无可下载文件，请前往 GitHub 页面查看）"
        } else {
            release.assets.joinToString("\n") { "  • ${it.name}  (${formatSize(it.size)})" }
        }

        val changelog = release.body.takeIf { it.isNotBlank() } ?: "（暂无更新说明）"

        val info = """
            |最新版本: ${release.version}
            |
            |更新说明:
            |$changelog
            |
            |可下载文件:
            |$assetList
        """.trimMargin()

        Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "发现新版本"
            headerText = "有可用更新 - v${release.version}"
            initOwner(AppState.getStage())
            dialogPane.content = TextArea(info).apply {
                isEditable = false
                isWrapText = true
                prefWidth = 480.0
                prefHeight = 220.0
            }
            buttonTypes.setAll(
                ButtonType("前往下载", ButtonBar.ButtonData.OK_DONE),
                ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE),
            )
            isResizable = true
        }.showAndWait().ifPresent { btn ->
            if (btn.buttonData == ButtonBar.ButtonData.OK_DONE) {
                if (release.assets.isNotEmpty()) {
                    downloadAsset(release.assets[0])
                } else {
                    openInBrowser(release.htmlUrl)
                }
            }
        }
    }

    private fun downloadAsset(asset: GitHubAsset) {
        val target = FileChooser().apply {
            title = "保存 ${asset.name}"
            initialFileName = asset.name
        }.showSaveDialog(stage) ?: return

        Thread({
            try {
                val connection = URL(asset.downloadUrl).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "PdmReader4J")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                val totalSize = connection.contentLength.toLong()
                val inputStream = connection.inputStream
                val outputStream = target.outputStream()

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                val progressRef = arrayOf<Alert?>(null)
                val progressBarRef = arrayOf<ProgressBar?>(null)
                val progressLabelRef = arrayOf<Label?>(null)

                Platform.runLater {
                    val bar = ProgressBar(0.0)
                    val label = Label("下载进度: 0%")
                    val content = VBox(10.0, label, bar).apply { padding = Insets(20.0) }
                    val alert = Alert(Alert.AlertType.INFORMATION).apply {
                        title = "下载更新"
                        headerText = "正在下载 ${asset.name}..."
                        dialogPane.content = content
                        buttonTypes.setAll(ButtonType.CANCEL)
                        isResizable = true
                        initOwner(AppState.getStage())
                    }
                    progressBarRef[0] = bar
                    progressLabelRef[0] = label
                    progressRef[0] = alert
                    alert.show()
                }

                Thread.sleep(200)

                var lastUpdate = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 300) {
                        lastUpdate = now
                        val read = totalBytesRead
                        Platform.runLater {
                            if (totalSize > 0) {
                                val pct = read.toDouble() / totalSize.toDouble()
                                progressBarRef[0]?.progress = pct
                                progressLabelRef[0]?.text =
                                    "下载进度: ${"%.1f".format(pct * 100)}%  (${formatSize(read)} / ${
                                        formatSize(
                                            totalSize
                                        )
                                    })"
                            } else {
                                progressLabelRef[0]?.text = "已下载: ${formatSize(read)}"
                            }
                        }
                    }
                }

                outputStream.close()
                inputStream.close()
                connection.disconnect()
                logger.info("下载完成: {}", target.absolutePath)

                Platform.runLater {
                    progressRef[0]?.close()
                    DialogWithIcon.info(
                        "下载完成",
                        "文件已保存到:\n${target.absolutePath}\n\n请手动运行该文件以完成更新。"
                    )
                }
            } catch (e: Exception) {
                logger.error("下载失败", e)
                Platform.runLater {
                    showError(Exception("下载失败: ${e.message}"))
                }
            }
        }, "update-downloader").apply {
            isDaemon = true
            start()
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    private fun openInBrowser(url: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URL(url).toURI())
            }
        } catch (e: Exception) {
            logger.error("无法打开浏览器", e)
            Platform.runLater {
                Alert(Alert.AlertType.INFORMATION).apply {
                    title = "打开下载页面"
                    headerText = "请手动在浏览器中打开以下链接"
                    initOwner(AppState.getStage())
                    dialogPane.content = TextArea(url).apply {
                        isEditable = false
                        prefWidth = 400.0
                        prefHeight = 60.0
                    }
                }.showAndWait()
            }
        }
    }

    private fun Theme.displayName(): String = when (name.lowercase()) {
        "light" -> "浅色"
        "dark" -> "深色"
        "claude" -> "Claude"
        else -> name.ifBlank { "未命名主题" }
    }
}
