package indi.nonoas.pdmreader.app

import indi.nonoas.pdmreader.controller.MainController
import indi.nonoas.pdmreader.ddl.DdlGenerator
import indi.nonoas.pdmreader.parser.PowerDesignerPdmParser
import indi.nonoas.pdmreader.repository.DatabaseFactory
import indi.nonoas.pdmreader.repository.PdmRepository
import indi.nonoas.pdmreader.service.PdmCatalogService
import indi.nonoas.pdmreader.ui.MainView
import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage

class PdmReaderApplication : Application() {
    override fun start(stage: Stage) {
        val repository = PdmRepository(DatabaseFactory())
        val service = PdmCatalogService(
            parser = PowerDesignerPdmParser(),
            repository = repository,
            ddlGenerator = DdlGenerator(),
        )
        val controller = MainController(service)
        val root = MainView(controller).createContent()
        val scene = Scene(root, 1080.0, 720.0)

        stage.title = controller.windowTitle
        stage.scene = scene
        stage.minWidth = 900.0
        stage.minHeight = 600.0
        stage.show()
    }
}
