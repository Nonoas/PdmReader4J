package indi.nonoas.pdmreader.ui

import indi.nonoas.pdmreader.controller.MainController
import indi.nonoas.pdmreader.model.NavigationItemType
import indi.nonoas.pdmreader.model.PdmColumnDetail
import indi.nonoas.pdmreader.model.PdmImportSummary
import indi.nonoas.pdmreader.model.TableNavigationItem
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.control.*
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.util.Callback
import java.io.File
import java.nio.file.Path

class MainView(
    private val controller: MainController,
) {
    fun createContent(): BorderPane {
        val searchField = TextField().apply {
            promptText = "搜索表名、表编码、字段名或字段编码"
            textProperty().addListener { _, _, newValue ->
                controller.setSearchKeyword(newValue, ::showError)
            }
            HBox.setHgrow(this, Priority.ALWAYS)
        }

        val importButton = Button("导入 PDM").apply {
            styleClass.add("primary-button")
            setOnAction {
                val selectedFiles = choosePdmFiles()
                if (selectedFiles.isNotEmpty()) {
                    controller.importPdms(selectedFiles.map(File::toPath), ::showError)
                }
            }
        }
        val removeImportButton = Button("移除选中").apply {
            disableProperty().bind(controller.selectedImportProperty().isNull)
            setOnAction {
                val selectedImport = controller.selectedImportProperty().value ?: return@setOnAction
                if (confirmRemoveImport(selectedImport)) {
                    controller.removeImport(selectedImport, ::showError)
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
                if (searchField.text.isEmpty()) {
                    controller.clearSearch(::showError)
                } else {
                    searchField.clear()
                }
            }
        }
        val copyDdlButton = Button("复制 DDL").apply {
            disableProperty().bind(controller.canCopyDdlProperty().not())
            setOnAction {
                runOnUiAction { controller.copySelectedDdlToClipboard() }
            }
        }

        val toolbar = ToolBar(
            importButton,
            removeImportButton,
            refreshButton,
            copyDdlButton,
            searchField,
            clearSearchButton,
        ).apply {
            styleClass.add("top-toolbar")
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
            },
            Label().apply {
                textProperty().bind(controller.selectedTableCommentProperty)
                styleClass.add("table-comment")
                isWrapText = true
            },
        ).apply {
            padding = Insets(20.0, 20.0, 16.0, 20.0)
            styleClass.add("detail-header")
        }

        val rightPane = BorderPane().apply {
            top = headerBox
            center = VBox(
                10.0,
                Label("字段明细").apply { styleClass.add("section-title") },
                columnsTable,
                Label("DDL 预览").apply { styleClass.add("section-title") },
                ddlArea,
            ).apply {
                padding = Insets(0.0, 20.0, 20.0, 20.0)
                VBox.setVgrow(columnsTable, Priority.ALWAYS)
                VBox.setVgrow(ddlArea, Priority.ALWAYS)
            }
        }

        val leftPane = VBox(
            10.0,
            Label("已导入文件").apply { styleClass.add("section-title") },
            importListView,
            Label("表与搜索结果").apply { styleClass.add("section-title") },
            navigationListView,
        ).apply {
            padding = Insets(20.0)
            prefWidth = 340.0
            VBox.setVgrow(importListView, Priority.SOMETIMES)
            VBox.setVgrow(navigationListView, Priority.ALWAYS)
        }

        val splitPane = SplitPane(leftPane, rightPane).apply {
            orientation = Orientation.HORIZONTAL
            setDividerPositions(0.34)
        }

        val statusLabel = Label().apply {
            textProperty().bind(controller.statusTextProperty)
            styleClass.add("status-label")
        }

        val root = BorderPane().apply {
            top = toolbar
            center = splitPane
            bottom = statusLabel
            stylesheets.add(
                MainView::class.java.getResource("/styles/app.css")?.toExternalForm()
                    ?: error("Missing stylesheet: /styles/app.css")
            )
        }
        controller.initialize(::showError)
        return root
    }

    private fun createImportListView(): ListView<PdmImportSummary> =
        ListView<PdmImportSummary>(controller.imports).apply {
            selectionModel.selectionMode = SelectionMode.SINGLE
            placeholder = Label("尚未导入任何 PDM 文件")
            cellFactory = Callback {
                object : ListCell<PdmImportSummary>() {
                    override fun updateItem(item: PdmImportSummary?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = when {
                            empty || item == null -> null
                            else -> "${item.fileName}-${item.modelName}"
                        }
                        styleClass.remove("multi-line-cell")
                        if (!empty && item != null) {
                            styleClass.add("multi-line-cell")
                        }
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
            placeholder = Label("选择导入文件后显示表清单")
            cellFactory = Callback {
                object : ListCell<TableNavigationItem>() {
                    override fun updateItem(item: TableNavigationItem?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = when {
                            empty || item == null -> null
                            item.type == NavigationItemType.TABLE -> {
                                val tableCode = item.tableCode?.takeIf { it.isNotBlank() } ?: item.tableName
                                "[表] $tableCode-${item.tableName}"
                            }

                            else -> {
                                val tableCode = item.tableCode?.takeIf { it.isNotBlank() } ?: item.tableName
                                val columnCode = item.matchedColumnCode?.takeIf { it.isNotBlank() } ?: item.matchedColumnName
                                "[列] $tableCode > ${columnCode.orEmpty()}-${item.tableName}"
                            }
                        }
                        styleClass.remove("multi-line-cell")
                        if (!empty && item != null) {
                            styleClass.add("multi-line-cell")
                        }
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

    private fun createColumnsTable(): TableView<PdmColumnDetail> {
        val table = TableView<PdmColumnDetail>(controller.columns).apply {
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
            placeholder = Label("选择表后显示字段列表")
            styleClass.add("columns-table")
        }

        table.columns += textColumn("序号") { it.ordinalPosition.toString() }.apply { prefWidth = 70.0 }
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

    private fun textColumn(title: String, valueProvider: (PdmColumnDetail) -> String): TableColumn<PdmColumnDetail, String> =
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
        }.showOpenMultipleDialog(null).orEmpty()

    private fun confirmRemoveImport(importSummary: PdmImportSummary): Boolean =
        Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "确认移除"
            headerText = "确认移除已导入的 PDM 吗？"
            contentText = "${importSummary.modelName}\n${importSummary.fileName}"
        }.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK

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
        Alert(Alert.AlertType.ERROR).apply {
            title = "执行失败"
            headerText = exception.message ?: "未知错误"
            contentText = exception.stackTraceToString()
            isResizable = true
        }.showAndWait()
    }
}
