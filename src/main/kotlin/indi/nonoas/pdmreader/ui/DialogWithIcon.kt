package indi.nonoas.pdmreader.ui

import javafx.geometry.Insets
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.TextInputDialog
import javafx.scene.image.Image
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.net.URI
import java.util.*

/**
 * 标准化弹窗，窗口图标与主窗口一致（使用 logo.png）
 */
class DialogWithIcon private constructor(
    type: DialogType,
    title: String,
    contentText: String? = null,
    customContent: Region? = null,
) : Dialog<ButtonType>() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /** 弹窗类型枚举 */
    enum class DialogType {
        INFO, WARNING, ERROR, CONFIRMATION, CUSTOM
    }

    companion object {
        private val logoImage: Image by lazy {
            Image(DialogWithIcon::class.java.getResource("/images/logo.png")?.toExternalForm())
        }

        fun info(title: String, message: String) {
            DialogWithIcon(DialogType.INFO, title, contentText = message).showAndWait()
        }

        fun warning(title: String, message: String) {
            DialogWithIcon(DialogType.WARNING, title, contentText = message).showAndWait()
        }

        fun error(title: String, message: String) {
            DialogWithIcon(DialogType.ERROR, title, contentText = message).showAndWait()
        }

        /**
         * @return true 表示用户点击了"确定"
         */
        fun confirm(title: String, message: String): Boolean {
            val dialog = DialogWithIcon(DialogType.CONFIRMATION, title, contentText = message)
            val result = dialog.showAndWait()
            return result.orElse(null) == ButtonType.OK
        }

        /**
         * 自定义内容弹窗，调用方负责 showAndWait()
         */
        fun custom(
            title: String,
            customContent: Region? = null,
        ): DialogWithIcon {
            return DialogWithIcon(DialogType.CUSTOM, title, customContent = customContent)
        }

        /**
         * 显示文本输入对话框
         */
        fun textInput(title: String, headerText: String? = null, defaultValue: String? = null): Optional<String> {
            val dialog = TextInputDialog(defaultValue ?: "")
            dialog.title = title
            dialog.headerText = headerText
            setStageIcon(dialog)
            return dialog.showAndWait()
        }

        /**
         * 打开外部链接（适配操作系统）
         */
        fun openLink(url: String) {
            try {
                Desktop.getDesktop().browse(URI(url))
            } catch (e: Exception) {
                LoggerFactory.getLogger(DialogWithIcon::class.java).error("无法打开链接: $url", e)
                error("打开链接失败", "请手动在浏览器中打开:\n$url")
            }
        }

        private fun setStageIcon(dialog: Dialog<*>) {
            dialog.setOnShown {
                val stage = dialog.dialogPane.scene.window as? Stage ?: return@setOnShown
                stage.icons.add(logoImage)
            }
        }
    }

    init {
        this.title = title
        dialogPane.content = buildContent(contentText, customContent)
        initButtons(type)
        setStageIcon(this)
    }

    private fun buildContent(contentText: String?, customContent: Region?): Region {
        if (customContent != null) return customContent
        return VBox(10.0).apply {
            padding = Insets(20.0)
            children.add(
                Label(contentText ?: "").apply {
                    isWrapText = true
                }
            )
        }
    }

    private fun initButtons(type: DialogType) {
        when (type) {
            DialogType.INFO, DialogType.WARNING, DialogType.ERROR -> {
                dialogPane.buttonTypes.setAll(ButtonType.OK)
            }

            DialogType.CONFIRMATION -> {
                dialogPane.buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
            }

            DialogType.CUSTOM -> {
                // 自定义弹窗不预设按钮
            }
        }
    }

    /**
     * 添加自定义按钮
     */
    fun addButton(button: ButtonType) {
        dialogPane.buttonTypes.add(button)
    }
}
