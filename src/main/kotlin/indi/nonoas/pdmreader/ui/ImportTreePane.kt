package indi.nonoas.pdmreader.ui

import github.nonoas.jfx.flat.ui.theme.Styles
import indi.nonoas.pdmreader.controller.MainController
import indi.nonoas.pdmreader.model.PdmImportSummary
import javafx.beans.binding.Bindings
import javafx.collections.ListChangeListener
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.util.Callback
import org.slf4j.LoggerFactory
import java.nio.file.Path

class ImportTreePane(
    private val controller: MainController,
    private val onError: (Throwable) -> Unit,
    private val onSelectionContextChanged: (String) -> Unit,
) : StackPane() {

    private val log = LoggerFactory.getLogger(ImportTreePane::class.java)

    private val treeView = TreeView<ImportTreeNode>().apply {
        isShowRoot = false
        selectionModel.selectionMode = SelectionMode.MULTIPLE
        styleClass.add("compact-tree")
        cellFactory = Callback { ImportTreeCell() }
    }

    private var currentTreeSelection: ImportTreeNode? = null

    init {
        treeView.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            val node = newValue?.value
            currentTreeSelection = node

            when (node) {
                is ImportTreeNode.Import -> {
                    val selectedImport = node.summary
                    if (controller.selectedImportProperty().value == selectedImport) {
                        return@addListener
                    }
                    onSelectionContextChanged(node.summary.fileName)
                    controller.selectImport(selectedImport, onError)
                }

                is ImportTreeNode.Group -> {
                    onSelectionContextChanged(node.displayName)
                    controller.selectImportGroup(node.imports, onError)
                }

                else -> {
                    if (controller.selectedNavigationItemProperty().value == null) {
                        onSelectionContextChanged("未选中")
                    }
                }
            }
        }

        controller.imports.addListener(ListChangeListener {
            log.debug("pdm列表变更")
            rebuildImportTree()
        })

        controller.selectedImportProperty().addListener { _, _, newValue ->
            val currentNode = treeView.selectionModel.selectedItem?.value
            if (currentNode is ImportTreeNode.Group) {
                return@addListener
            }
            selectImportTreeNode(newValue?.id)
        }

        children.addAll(
            treeView,
            Label("尚未导入任何 PDM 文件").apply {
                styleClass.add("tree-placeholder")
                isMouseTransparent = true
                visibleProperty().bind(Bindings.isEmpty(controller.imports))
                managedProperty().bind(visibleProperty())
            },
        )

        rebuildImportTree()
    }

    fun currentSelectionContextText(): String =
        when (val node = currentTreeSelection) {
            is ImportTreeNode.Group -> node.displayName
            is ImportTreeNode.Import -> node.summary.fileName
            else -> "未选中"
        }

    private fun rebuildImportTree() {
        // 保存当前各分组的折叠状态
        val collapsedGroupNames = treeView.root?.children
            ?.filter { it.value is ImportTreeNode.Group && !it.isExpanded }
            ?.map { (it.value as ImportTreeNode.Group).displayName }
            ?.toSet() ?: emptySet()

        val root = TreeItem<ImportTreeNode>(ImportTreeNode.Root).apply {
            isExpanded = true
        }

        val groupedImports = controller.imports
            .groupBy { importSummary -> importSummary.groupName.trim().ifBlank { "未分组" } }
            .entries
            .sortedWith(compareBy({ groupEntry ->
                groupEntry.key.lowercase()
            }))

        groupedImports.forEach { (groupName, imports) ->
            val groupItem = TreeItem<ImportTreeNode>(
                ImportTreeNode.Group(
                    displayName = groupName,
                    imports = imports,
                    locationSummary = buildGroupLocationSummary(imports),
                )
            ).apply {
                isExpanded = groupName !in collapsedGroupNames
            }
            imports.forEach { importSummary ->
                groupItem.children.add(TreeItem<ImportTreeNode>(ImportTreeNode.Import(importSummary)))
            }
            root.children.add(groupItem)
        }

        treeView.root = root
        selectImportTreeNode(controller.selectedImportProperty().value?.id)
    }

    private fun selectImportTreeNode(importId: Long?) {
        if (importId == null) {
            treeView.selectionModel.clearSelection()
            return
        }

        val targetItem = treeView.root?.children
            ?.asSequence()
            ?.flatMap { groupItem -> groupItem.children.asSequence() }
            ?.firstOrNull { treeItem ->
                (treeItem.value as? ImportTreeNode.Import)?.summary?.id == importId
            }

        if (targetItem == null) {
            treeView.selectionModel.clearSelection()
            return
        }

        treeView.selectionModel.clearSelection()
        treeView.selectionModel.select(targetItem)
    }

    private fun collectSelectedImports(): List<PdmImportSummary> =
        treeView.selectionModel.selectedItems
            .toList()
            .ifEmpty { listOfNotNull(treeView.selectionModel.selectedItem) }
            .flatMap(::collectImportsFromTreeItem)
            .distinctBy { it.id }

    private fun collectImportsFromTreeItem(treeItem: TreeItem<ImportTreeNode>?): List<PdmImportSummary> {
        if (treeItem == null) {
            return emptyList()
        }

        return when (val node = treeItem.value) {
            is ImportTreeNode.Import -> listOf(node.summary)
            is ImportTreeNode.Group -> treeItem.children.flatMap(::collectImportsFromTreeItem)
            ImportTreeNode.Root,
            null -> emptyList()
        }
    }

    private fun confirmRemoveImport(importSummary: PdmImportSummary): Boolean =
        DialogWithIcon.confirm(
            "确认移除",
            "确认移除已导入的 PDM 吗？\n${importSummary.modelName}\n${importSummary.fileName}"
        )

    private fun confirmRemoveImports(importSummaries: List<PdmImportSummary>): Boolean {
        val targets = importSummaries.distinctBy { it.id }
        if (targets.size == 1) {
            return confirmRemoveImport(targets.first())
        }

        val preview = targets.take(8).joinToString("\n") { "• ${it.fileName}" } +
                if (targets.size > 8) "\n• ..." else ""
        return DialogWithIcon.confirm(
            "确认移除",
            "确认移除选中的 ${targets.size} 个 PDM 吗？\n$preview"
        )
    }

    private fun promptGroupName(initialGroupName: String, sourceDescription: String): String? {
        while (true) {
            val result = DialogWithIcon.textInput(
                title = "重命名分组",
                headerText = "来源：$sourceDescription",
                defaultValue = initialGroupName,
            )
            if (result.isEmpty) {
                return null
            }

            val groupName = result.get().trim()
            if (groupName.isNotEmpty()) {
                return groupName
            }

            DialogWithIcon.warning("分组名称无效", "分组名称不能为空。")
        }
    }

    private fun buildGroupLocationSummary(imports: List<PdmImportSummary>): String {
        val directories = imports.mapNotNull { importSummary ->
            runCatching {
                Path.of(importSummary.filePath)
                    .parent
                    ?.normalize()
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }.distinct()

        return when (directories.size) {
            0 -> "未知目录"
            1 -> directories.first()
            else -> "${directories.size} 个来源目录"
        }
    }

    private inner class ImportTreeCell() : TreeCell<ImportTreeNode>() {
        private val titleLabel = Label().apply { styleClass.add("list-item-title") }
        private val metaLabel = Label().apply {
            styleClass.add("list-item-meta")
            isWrapText = true
        }
        private val contentBox = HBox(2.0, titleLabel, metaLabel)
        private val removeMenuItem = MenuItem("移除选中 PDM").apply {
            styleClass.add(Styles.DANGER)
            setOnAction {
                val selectedImports = collectSelectedImports()
                if (selectedImports.isNotEmpty() && confirmRemoveImports(selectedImports)) {
                    controller.removeImports(selectedImports, onError)
                }
            }
        }
        private val renameGroupMenuItem = MenuItem("重命名分组").apply {
            setOnAction {
                val groupNode = treeItem?.value as? ImportTreeNode.Group ?: return@setOnAction
                val renamedGroupName = promptGroupName(
                    initialGroupName = groupNode.displayName,
                    sourceDescription = groupNode.locationSummary,
                ) ?: return@setOnAction
                controller.renameImportGroup(groupNode.imports, renamedGroupName, onError)
            }
        }
        private val importContextMenu = ContextMenu(removeMenuItem)
        private val groupContextMenu = ContextMenu(
            renameGroupMenuItem,
            SeparatorMenuItem(),
            removeMenuItem,
        )

        override fun updateItem(item: ImportTreeNode?, empty: Boolean) {
            super.updateItem(item, empty)

            if (empty || item == null) {
                text = null
                graphic = null
                contextMenu = null
                return
            }

            when (item) {
                is ImportTreeNode.Group -> {
                    titleLabel.text = item.displayName
                    metaLabel.text = "${item.locationSummary} · ${item.importCount} 个 PDM"
                    contextMenu = groupContextMenu
                }

                is ImportTreeNode.Import -> {
                    titleLabel.text = item.summary.modelName.ifBlank { item.summary.fileName }
                    metaLabel.text = buildString {
                        append(item.summary.fileName)
                    }
                    contextMenu = importContextMenu
                }

                ImportTreeNode.Root -> {
                    text = null
                    graphic = null
                    contextMenu = null
                    return
                }
            }
            if (graphic !== contentBox) {
                graphic = contentBox
            }
        }
    }

    private sealed interface ImportTreeNode {
        data object Root : ImportTreeNode

        data class Group(
            val displayName: String,
            val imports: List<PdmImportSummary>,
            val locationSummary: String,
        ) : ImportTreeNode {
            val importCount: Int
                get() = imports.size
        }

        data class Import(
            val summary: PdmImportSummary,
        ) : ImportTreeNode
    }
}
