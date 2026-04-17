package indi.nonoas.pdmreader.app

import github.nonoas.jfx.flat.ui.AppState
import github.nonoas.jfx.flat.ui.theme.ClaudeTheme
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
import javafx.scene.paint.Color
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
        val root = MainView(controller, stage, useExtendedWindow).createContent()
        val scene = Scene(root, 1080.0, 720.0)

        setUserAgentStylesheet(ClaudeTheme().userAgentStylesheet)

        stage.initStyle(if (useExtendedWindow) StageStyle.EXTENDED else StageStyle.DECORATED)
        stage.title = controller.windowTitle
        stage.icons.add(Image("/images/logo.png"))
        scene.fill = Color.web("#f7f3ee")
        stage.scene = scene
        stage.minWidth = 900.0
        stage.minHeight = 600.0

        AppState.setStage(stage)
        stage.show()
    }
}
