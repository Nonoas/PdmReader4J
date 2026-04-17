package indi.nonoas.pdmreader.ui

import github.nonoas.jfx.flat.ui.AppState
import github.nonoas.jfx.flat.ui.theme.Theme
import indi.nonoas.pdmreader.app.AppThemeManager
import indi.nonoas.pdmreader.controller.MainController
import indi.nonoas.pdmreader.model.NavigationItemType
import indi.nonoas.pdmreader.model.PdmColumnDetail
import indi.nonoas.pdmreader.model.PdmImportSummary
import indi.nonoas.pdmreader.model.TableNavigationItem
import javafx.application.Platform
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
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
) {
    private val logger = LoggerFactory.getLogger(MainView::class.java)

    companion object {
        const val APP_VERSION = "0.0.1"
        const val GITHUB_REPO = "Nonoas/PdmReader4J"
        val INSET_1 =  Insets(10.0)
    }

    fun createContent(): BorderPane {
        val searchField = TextField().apply {
            promptText = "搜索表名、表编码、字段名或字段编码"
            styleClass.add("search-field")
            textProperty().addListener { _, _, newValue ->
                controller.setSearchKeyword(newValue, ::showError)
            }
            HBox.setHgrow(this, Priority.ALWAYS)
        }

        val importButton = Button("导入 PDM").apply {
            styleClass.addAll("toolbar-button", "primary-button")
            setOnAction {
                val selectedFiles = choosePdmFiles()
                if (selectedFiles.isNotEmpty()) {
                    controller.importPdms(selectedFiles.map(File::toPath), ::showError)
                }
            }
        }
        val removeImportButton = Button("移除选中").apply {
            styleClass.add("toolbar-button")
            disableProperty().bind(controller.selectedImportProperty().isNull)
            setOnAction {
                val selectedImport = controller.selectedImportProperty().value ?: return@setOnAction
                if (confirmRemoveImport(selectedImport)) {
                    controller.removeImport(selectedImport, ::showError)
                }
            }
        }
        val refreshButton = Button("刷新").apply {
            styleClass.add("toolbar-button")
            setOnAction {
                controller.reloadImports(onError = ::showError)
            }
        }
        val clearSearchButton = Button("清空搜索").apply {
            styleClass.add("toolbar-button")
            setOnAction {
                if (searchField.text.isEmpty()) {
                    controller.clearSearch(::showError)
                } else {
                    searchField.clear()
                }
            }
        }
        val copyDdlButton = Button("复制 DDL").apply {
            styleClass.add("toolbar-button")
            disableProperty().bind(controller.canCopyDdlProperty().not())
            setOnAction {
                runOnUiAction { controller.copySelectedDdlToClipboard() }
            }
        }
        val aboutButton = Button("关于").apply {
            styleClass.add("toolbar-button")
            setOnAction {
                showAboutDialog()
            }
        }
        val themeSwitcher = createThemeSwitcher()

        val toolbar = HBox(
            10.0,
            importButton,
            removeImportButton,
            refreshButton,
            copyDdlButton,
            searchField,
            clearSearchButton,
            themeSwitcher,
            aboutButton,
        ).apply {
            alignment = Pos.CENTER_LEFT
            padding =INSET_1
            styleClass.add("action-toolbar")
        }

        val importListView = createImportListView()
        val navigationListView = createNavigationListView()
        val columnsTable = createColumnsTable()
        val ddlArea = TextArea().apply {
            isEditable = false
            isWrapText = false
            promptText = "选择表后在这里预览生成的 DDL。"
            textProperty().bind(controller.ddlTextProperty)
            styleClass.add("ddl-area")
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        val headerBox = VBox(
            8.0,
            Label().apply {
                textProperty().bind(controller.selectedTableTitleProperty)
                styleClass.add("table-title")
            },
            Label().apply {
                textProperty().bind(controller.selectedTableMetaProperty)
                styleClass.add("table-meta")
                isWrapText = true
            },
            Label().apply {
                textProperty().bind(controller.selectedTableCommentProperty)
                styleClass.add("table-comment")
                isWrapText = true
            },
        ).apply {
            padding =INSET_1
            styleClass.addAll("detail-header", "content-card")
        }

        val rightPane = BorderPane().apply {
            top = headerBox
            center = TabPane(
                Tab("字段明细", columnsTable).apply { isClosable = false },
                Tab("DDL 预览", ddlArea).apply { isClosable = false }
            ).apply {
                padding =INSET_1
                styleClass.add("content-card")
            }
            padding = Insets(0.0, 0.0, 0.0, 4.0)
            BorderPane.setMargin(headerBox, Insets(0.0, 0.0, 10.0, 0.0))
        }

        val leftPane = StackPane(
            VBox(
                10.0,
                createSectionCard("已导入文件", importListView, Priority.ALWAYS),
                createSectionCard("表与搜索结果", navigationListView, Priority.ALWAYS),
            ).apply {
                prefWidth = 340.0
            }
        ).apply {
            padding = Insets(0.0, 4.0, 0.0, 0.0)
        }

        val splitPane = SplitPane(leftPane, rightPane).apply {
            orientation = Orientation.HORIZONTAL
            setDividerPositions(0.33)
            styleClass.add("app-split-pane")
        }

        val statusLabel = Label().apply {
            textProperty().bind(controller.statusTextProperty)
            maxWidth = Double.MAX_VALUE
            styleClass.add("status-label")
        }

        val topContainer = VBox().apply {
            if (useExtendedWindow) {
                children.add(createHeaderBar())
            }
            children.add(toolbar)
        }

        val root = BorderPane().apply {
            top = topContainer
            center = splitPane
            bottom = statusLabel
            styleClass.add("app-root")
            stylesheets.add(
                MainView::class.java.getResource("/styles/app.css")?.toExternalForm()
                    ?: error("Missing stylesheet: /styles/app.css")
            )
        }

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
            prefWidth = 132.0
            styleClass.add("theme-combo-box")
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
            8.0,
            Label("主题").apply { styleClass.add("theme-label") },
            themeComboBox,
        ).apply {
            alignment = Pos.CENTER_LEFT
            styleClass.add("theme-switcher")
        }
    }

    @Suppress("DEPRECATION")
    private fun createHeaderBar(): HeaderBar {
        val brandBlock = HBox(
            12.0,
            Label("PDM").apply { styleClass.add("header-badge") },
            VBox(
                2.0,
                Label(controller.windowTitle).apply { styleClass.add("header-title") },
                Label("PowerDesigner 模型阅读与检索").apply { styleClass.add("header-subtitle") },
            ).apply {
                alignment = Pos.CENTER_LEFT
            },
        ).apply {
            alignment = Pos.CENTER_LEFT
            styleClass.add("header-brand")
        }

        val previewChip = Label("JavaFX 25 Preview").apply {
            styleClass.add("header-chip")
        }

        return HeaderBar(brandBlock, null, previewChip).apply {
            styleClass.add("app-header-bar")
            prefHeight = 54.0
            isLeadingSystemPadding = true
            isTrailingSystemPadding = true
            HeaderBar.setPrefButtonHeight(stage, 44.0)
            HeaderBar.setMargin(brandBlock, Insets(0.0, 0.0, 0.0, 4.0))
            HeaderBar.setMargin(previewChip, Insets(0.0, 6.0, 0.0, 0.0))
            HeaderBar.setDragType(brandBlock, HeaderDragType.DRAGGABLE_SUBTREE)
            HeaderBar.setDragType(previewChip, HeaderDragType.DRAGGABLE)
        }
    }

    private fun createSectionCard(title: String, content: Region, grow: Priority): VBox =
        VBox(
            12.0,
            Label(title).apply { styleClass.add("section-title") },
            content,
        ).apply {
            padding =INSET_1
            styleClass.add("content-card")
            VBox.setVgrow(content, grow)
        }

    private fun createImportListView(): ListView<PdmImportSummary> =
        ListView(controller.imports).apply {
            selectionModel.selectionMode = SelectionMode.SINGLE
            placeholder = Label("尚未导入任何 PDM 文件")
            styleClass.add("rounded-list-view")
            setRoundedClip(this)
            cellFactory = Callback {
                object : ListCell<PdmImportSummary>() {
                    override fun updateItem(item: PdmImportSummary?, empty: Boolean) {
                        super.updateItem(item, empty)

                        styleClass.remove("multi-line-cell")

                        if (empty || item == null) {
                            text = null
                            graphic = null
                            return
                        }

                        styleClass.add("multi-line-cell")
                        text = null

                        graphic = buildSingleLineText(
                            Text(item.modelName).apply { styleClass.add("model-name-text") },
                            Text(" | ").apply { styleClass.add("cell-separator-text") },
                            Text(item.fileName).apply { styleClass.add("file-name-text") }
                        )
                    }
                }
            }
            selectionModel.selectedItemProperty().addListener { _, _, newValue ->
                if (controller.selectedImportProperty().value == newValue) {
                    return@addListener
                }
                controller.selectImport(newValue, ::showError)
            }
            bindSelection(controller.selectedImportProperty())
        }

    private fun createNavigationListView(): ListView<TableNavigationItem> =
        ListView(controller.navigationItems).apply {
            selectionModel.selectionMode = SelectionMode.SINGLE
            placeholder = Label("选择导入文件后显示表清单，输入关键字后可搜索全部已导入的 PDM")
            styleClass.add("rounded-list-view")
            setRoundedClip(this)
            cellFactory = Callback {
                object : ListCell<TableNavigationItem>() {
                    override fun updateItem(item: TableNavigationItem?, empty: Boolean) {
                        super.updateItem(item, empty)

                        styleClass.remove("multi-line-cell")

                        if (empty || item == null) {
                            text = null
                            graphic = null
                            return
                        }

                        styleClass.add("multi-line-cell")
                        text = null

                        val tableCode = item.tableCode?.takeIf { it.isNotBlank() } ?: item.tableName
                        val mainText = when (item.type) {
                            NavigationItemType.TABLE -> "[表] $tableCode - ${item.tableName}"
                            else -> {
                                val columnCode =
                                    item.matchedColumnCode?.takeIf { it.isNotBlank() } ?: item.matchedColumnName
                                "[列] $tableCode > ${columnCode.orEmpty()} - ${item.tableName}"
                            }
                        }

                        graphic = buildSingleLineText(
                            Text(mainText).apply { styleClass.add("model-name-text") },
                            Text(" | ").apply { styleClass.add("cell-separator-text") },
                            Text(item.importFileName).apply { styleClass.add("file-name-text") }
                        )
                    }
                }
            }
            selectionModel.selectedItemProperty().addListener { _, _, newValue ->
                if (controller.selectedNavigationItemProperty().value == newValue) {
                    return@addListener
                }
                controller.selectNavigationItem(newValue, ::showError)
            }
            bindSelection(controller.selectedNavigationItemProperty())
        }

    private fun buildSingleLineText(vararg texts: Text): HBox =
        HBox(*texts).apply {
            alignment = Pos.CENTER_LEFT
            spacing = 0.0
        }

    private fun setRoundedClip(control: Control, radius: Double = 18.0) {
        val clip = Rectangle()
        clip.arcWidth = radius * 2
        clip.arcHeight = radius * 2
        control.clip = clip
        control.layoutBoundsProperty().addListener { _, _, newValue ->
            clip.width = newValue.width + 1
            clip.height = newValue.height + 1
        }
    }

    private fun createColumnsTable(): TableView<PdmColumnDetail> {
        val table = TableView(controller.columns).apply {
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
            placeholder = Label("选择表后显示字段列表")
            styleClass.add("columns-table")
            setRoundedClip(this)
        }

        table.columns += textColumn("序号") { it.ordinalPosition.toString() }.apply {
            prefWidth = 70.0
            comparator = Comparator { a, b -> a.toInt().compareTo(b.toInt()) }
        }
        table.columns += textColumn("字段名称") { it.name }.apply { prefWidth = 140.0 }
        table.columns += textColumn("字段编码") { it.code.orEmpty() }.apply { prefWidth = 160.0 }
        table.columns += textColumn("数据类型") { it.dataType.orEmpty() }.apply { prefWidth = 150.0 }
        table.columns += textColumn("非空") { if (it.nullable) "否" else "是" }.apply { prefWidth = 70.0 }
        table.columns += textColumn("主键") { if (it.pkFlag) "是" else "" }.apply { prefWidth = 70.0 }
        table.columns += textColumn("默认值") { it.defaultValue.orEmpty() }.apply { prefWidth = 140.0 }
        table.columns += textColumn("说明") { it.comment.orEmpty() }.apply { prefWidth = 180.0 }

        controller.highlightedColumnIdProperty.addListener { _, _, newValue ->
            if (newValue.isNullOrBlank()) {
                table.selectionModel.clearSelection()
                return@addListener
            }
            val index = controller.columns.indexOfFirst { it.idInPdm == newValue }
            if (index >= 0) {
                table.selectionModel.select(index)
                table.scrollTo(index)
            }
        }

        return table
    }

    private fun textColumn(
        title: String,
        valueProvider: (PdmColumnDetail) -> String,
    ): TableColumn<PdmColumnDetail, String> =
        TableColumn<PdmColumnDetail, String>(title).apply {
            setCellValueFactory { cell ->
                SimpleStringProperty(valueProvider(cell.value))
            }
        }

    private fun <T> ListView<T>.bindSelection(property: ReadOnlyObjectProperty<T?>) {
        property.addListener { _, _, newValue ->
            if (selectionModel.selectedItem != newValue) {
                selectionModel.select(newValue)
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

    private fun confirmRemoveImport(importSummary: PdmImportSummary): Boolean =
        DialogWithIcon.confirm(
            "确认移除",
            "确认移除已导入的 PDM 吗？\n${importSummary.modelName}\n${importSummary.fileName}"
        )

    private fun defaultInitialDirectory(): File {
        val sampleDirectory = Path.of(
            "D:\\kingdom\\0_Project\\fvs\\fvs-doc\\02Design\\2.3ScriptDesign\\01表结构设计\\database"
        ).toFile()
        return when {
            sampleDirectory.exists() -> sampleDirectory
            else -> File(System.getProperty("user.home"))
        }
    }

    private fun runOnUiAction(action: () -> Unit) {
        try {
            action()
        } catch (exception: Exception) {
            showError(exception)
        }
    }

    private fun showError(exception: Throwable) {
        logger.error(exception.message, exception)
        DialogWithIcon.error("执行失败", exception.message ?: "未知错误")
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
