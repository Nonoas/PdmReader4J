package indi.nonoas.pdmreader

import indi.nonoas.pdmreader.app.PdmReaderApplication
import javafx.application.Application

fun main(args: Array<String>) {
    System.setProperty("prism.lcdtext", "true")
    Application.launch(PdmReaderApplication::class.java, *args)
}
