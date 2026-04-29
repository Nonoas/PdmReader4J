package indi.nonoas.pdmreader.app

import github.nonoas.jfx.flat.ui.theme.Theme
import javafx.application.Application
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.scene.Scene
import javafx.scene.paint.Color

class AppThemeManager(
    initialTheme: Theme = Theme.claude(),
) {
    private val availableThemes = Theme.builtIns().ifEmpty { listOf(initialTheme) }
    private val currentTheme = ReadOnlyObjectWrapper(resolveTheme(initialTheme))

    fun availableThemes(): List<Theme> = availableThemes

    fun currentThemeProperty(): ReadOnlyObjectProperty<Theme> = currentTheme.readOnlyProperty

    fun currentTheme(): Theme = currentTheme.get()

    fun bind(scene: Scene) {
        applyTheme(scene, currentTheme())
        currentThemeProperty().addListener { _, _, newTheme ->
            newTheme?.let { applyTheme(scene, it) }
        }
    }

    fun switchTheme(theme: Theme?) {
        theme ?: return
        val resolvedTheme = findTheme(theme.name) ?: theme
        if (resolvedTheme.name.equals(currentTheme().name, ignoreCase = true)) {
            return
        }
        currentTheme.set(resolvedTheme)
    }

    private fun resolveTheme(initialTheme: Theme): Theme =
        findTheme(initialTheme.name) ?: availableThemes.firstOrNull() ?: initialTheme

    private fun findTheme(name: String?): Theme? =
        name?.let { themeName ->
            availableThemes.firstOrNull { it.name.equals(themeName, ignoreCase = true) }
        }

    private fun applyTheme(scene: Scene, theme: Theme) {
        Application.setUserAgentStylesheet(theme.userAgentStylesheet)
        scene.fill = Color.TRANSPARENT
        scene.root?.applyCss()
        scene.root?.requestLayout()
    }
}
