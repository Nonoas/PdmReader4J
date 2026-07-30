package indi.nonoas.pdmreader.app

import github.nonoas.jfx.flat.ui.theme.*
import indi.nonoas.pdmreader.repository.PdmRepository
import javafx.application.Application
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.scene.Scene
import javafx.scene.paint.Color

class AppThemeManager(
    initialTheme: Theme = Claude(),
    private val repository: PdmRepository? = null,
) {
    companion object {
        private const val THEME_CONFIG_KEY = "app.theme"
    }

    private val availableThemes = listOf(
        Claude(),
        CupertinoDark(),
        CupertinoLight(),
        Dracula(),
        NordDark(),
        NordLight(),
        PrimerDark(),
        PrimerLight()
    )

    private val savedThemeName: String? = repository?.getConfig(THEME_CONFIG_KEY)
    private val resolvedInitial: Theme = if (savedThemeName != null) {
        findTheme(savedThemeName) ?: resolveTheme(initialTheme)
    } else {
        resolveTheme(initialTheme)
    }
    private val currentTheme = ReadOnlyObjectWrapper(resolvedInitial)

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
        repository?.saveConfig(THEME_CONFIG_KEY, resolvedTheme.name)
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
