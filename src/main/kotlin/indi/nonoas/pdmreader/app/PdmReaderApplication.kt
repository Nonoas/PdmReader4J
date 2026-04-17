package indi.nonoas.pdmreader.app

import github.nonoas.jfx.flat.ui.AppState
import indi.nonoas.pdmreader.controller.MainController
import indi.nonoas.pdmreader.ddl.DdlGenerator
import indi.nonoas.pdmreader.parser.PowerDesignerPdmParser
import indi.nonoas.pdmreader.repository.DatabaseFactory
import indi.nonoas.pdmreader.repository.PdmRepository
import indi.nonoas.pdmreader.service.PdmCatalogService
import indi.nonoas.pdmreader.ui.MainView
import javafx.application.Application
import javafx.application.ConditionalFeature
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.stage.StageStyle

class PdmReaderApplication : Application() {
    override fun start(stage: Stage) {
        val repository = PdmRepository(DatabaseFactory())
        val service = PdmCatalogService(
            parser = PowerDesignerPdmParser(),
            repository = repository,
            ddlGenerator = DdlGenerator(),
        )
        val controller = MainController(service)
        val useExtendedWindow = Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW)
        val themeManager = AppThemeManager()
        val root = MainView(controller, stage, useExtendedWindow, themeManager).createContent()
        val scene = Scene(root, 1080.0, 720.0)
        themeManager.bind(scene)

        stage.initStyle(if (useExtendedWindow) StageStyle.EXTENDED else StageStyle.DECORATED)
        stage.title = controller.windowTitle
        stage.icons.add(Image("/images/logo.png"))
        stage.scene = scene
        stage.minWidth = 900.0
        stage.minHeight = 600.0

        AppState.setScene(scene)
        AppState.setStage(stage)
        stage.show()
    }
}
