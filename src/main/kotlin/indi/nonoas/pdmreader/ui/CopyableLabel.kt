package indi.nonoas.pdmreader.ui

import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.MenuItem
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.MouseButton

class CopyableLabel : Label {

    constructor() : super()

    constructor(text: String?) : super(text)

    init {
        val copyLabel = this
        val copyMenuItem = MenuItem("复制").apply {
            setOnAction {
                Clipboard.getSystemClipboard().setContent(
                    ClipboardContent().apply { putString(copyLabel.text) }
                )
            }
        }
        val contextMenu = ContextMenu(copyMenuItem).apply {
            styleClass.add("copyable-label-menu")
        }

        setOnContextMenuRequested { event ->
            if (text.isNullOrBlank()) {
                event.consume()
                return@setOnContextMenuRequested
            }
            contextMenu.show(this, event.screenX, event.screenY)
            event.consume()
        }

        setOnMousePressed { event ->
            if (event.button == MouseButton.SECONDARY) {
                contextMenu.hide()
            }
        }

        setOnMouseReleased { event ->
            if (event.button == MouseButton.SECONDARY) {
                contextMenu.hide()
            }
        }
    }
}
