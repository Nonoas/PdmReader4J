package com.example.pdmreader.app

import com.example.pdmreader.controller.MainController
import com.example.pdmreader.ddl.DdlGenerator
import com.example.pdmreader.parser.PowerDesignerPdmParser
import com.example.pdmreader.repository.DatabaseFactory
import com.example.pdmreader.repository.PdmRepository
import com.example.pdmreader.service.PdmCatalogService
import com.example.pdmreader.ui.MainView
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
