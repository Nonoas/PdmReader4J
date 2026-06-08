package indi.nonoas.pdmreader.app

import github.nonoas.jfx.flat.ui.AppState
import github.nonoas.jfx.flat.ui.stage.AppStage
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
import javafx.scene.image.Image
import javafx.stage.Stage

class PdmReaderApplication : Application() {
    override fun start(stage0: Stage) {
        stage0.close()
        val appStage = AppStage()

        val repository = PdmRepository(DatabaseFactory())
        val service = PdmCatalogService(
            parser = PowerDesignerPdmParser(),
            repository = repository,
            ddlGenerator = DdlGenerator(),
        )
        val controller = MainController(service)
        val useExtendedWindow = Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW)
        val themeManager = AppThemeManager()
        val root = MainView(controller, appStage, useExtendedWindow, themeManager, service).createContent()
        val appStylesheet = PdmReaderApplication::class.java.getResource("/styles/app.css")?.toExternalForm()
            ?: error("Missing stylesheet: /styles/app.css")

        appStage.setSize(1080.0, 720.0)
        appStage.setContentView(root)
        appStage.scene?.stylesheets?.add(appStylesheet)
        appStage.scene?.let(themeManager::bind)

        appStage.title = controller.windowTitle
        appStage.icons.add(Image("/images/logo.png"))
        appStage.minWidth = 900.0
        appStage.minHeight = 600.0

        AppState.setStage(appStage)
        AppState.setAppStage(appStage)
        appStage.show()
    }
}
