package indi.nonoas.pdmreader.ui

import github.nonoas.jfx.flat.ui.concurrent.TaskHandler
import github.nonoas.jfx.flat.ui.theme.Styles
import indi.nonoas.pdmreader.model.PdmColumnDetail
import indi.nonoas.pdmreader.model.PdmTableViewData
import indi.nonoas.pdmreader.model.TableNavigationItem
import indi.nonoas.pdmreader.service.PdmCatalogService
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.slf4j.LoggerFactory

class TableDetailTab(
    val item: TableNavigationItem,
    private val catalogService: PdmCatalogService,
    private val onError: (Throwable) -> Unit,
) : Tab() {

    private val logger = LoggerFactory.getLogger(TableDetailTab::class.java)

    val tableId: Long = item.tableId
    val importId: Long = item.importId

    private val columns: ObservableList<PdmColumnDetail> = FXCollections.observableArrayList()
    private val ddlText = SimpleStringProperty("")
    val hasDdlProperty: ReadOnlyBooleanProperty = SimpleBooleanProperty(false).apply {
        bind(ddlText.map { it.isNotBlank() })
    }

    private val titleText = SimpleStringProperty("加载中...")
    private val metaText = SimpleStringProperty("")
    private val commentText = SimpleStringProperty("")
    private val highlightedColumnId = SimpleStringProperty("")

    init {
        text = buildTabTitle()
        tooltip = Tooltip("${item.tableName}\n${item.importFilePath}")
        isClosable = true
        content = buildContent()
        loadData()

        setOnClosed {
            logger.debug("Tab closed for table: {}", item.tableName)
        }
    }

    fun copyDdl(): Boolean {
        val ddl = ddlText.get().trim()
        if (ddl.isEmpty()) return false
        Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(ddl) })
        return true
    }

    private fun buildTabTitle(): String =
        item.tableCode?.takeIf { it.isNotBlank() } ?: item.tableName.ifBlank { "未命名表" }

    private fun buildContent(): Node {
        val columnsTable = createColumnsTable()
        val ddlArea = TextArea().apply {
            isEditable = false
            isWrapText = false
            promptText = "选择表后在这里预览生成的 DDL。"
            textProperty().bind(ddlText)
            styleClass.add("code-area")
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        val headerBox = VBox(
            4.0,
            Label().apply {
                textProperty().bind(titleText)
                styleClass.add(Styles.TITLE_1)
            },
            Label().apply {
                textProperty().bind(metaText)
                styleClass.add("detail-meta")
                isWrapText = true
            },
            Label().apply {
                textProperty().bind(commentText)
                styleClass.add("detail-comment")
                isWrapText = true
            },
        ).apply {
            padding = MainView.SECTION_PADDING
            styleClass.add("detail-header")
        }

        val detailTabs = TabPane(
            Tab("字段", columnsTable).apply { isClosable = false },
            Tab("DDL", ddlArea).apply { isClosable = false }
        ).apply {
            tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
            styleClass.add("content-tabs")
            VBox.setVgrow(this, Priority.ALWAYS)
        }

        return VBox(8.0, headerBox, detailTabs).apply {
            isFillWidth = true
            VBox.setVgrow(this, Priority.ALWAYS)
        }
    }

    private fun createColumnsTable(): TableView<PdmColumnDetail> {
        val table = TableView(columns).apply {
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
            placeholder = Label("选择表后显示字段列表")
            styleClass.add("data-table")
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

        highlightedColumnId.addListener { _, _, newValue ->
            if (newValue.isNullOrBlank()) {
                table.selectionModel.clearSelection()
                return@addListener
            }
            val index = columns.indexOfFirst { it.idInPdm == newValue }
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

    private fun loadData() {
        TaskHandler<Result<PdmTableViewData>>()
            .whenCall {
                runCatching { catalogService.loadTableViewData(item.tableId) }
            }
            .andThen { result ->
                result
                    .onSuccess { data -> applyData(data, item.matchedColumnIdInPdm) }
                    .onFailure { e ->
                        logger.error("Failed to load table data for tableId={}", item.tableId, e)
                        onError(e)
                    }
            }
            .handle()
    }

    private fun applyData(data: PdmTableViewData, highlightColumnId: String?) {
        val details = data.details
        columns.setAll(details.columns)
        ddlText.set(data.ddl)
        titleText.set(
            details.tableCode?.takeIf { it.isNotBlank() }?.let { "${details.tableName} / $it" }
                ?: details.tableName
        )
        metaText.set(
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
        commentText.set(details.tableComment ?: "")
        if (!highlightColumnId.isNullOrBlank()) {
            highlightedColumnId.set(highlightColumnId)
        }
    }
}
