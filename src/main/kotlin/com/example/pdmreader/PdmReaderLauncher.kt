package com.example.pdmreader

import com.example.pdmreader.app.PdmReaderApplication
import javafx.application.Application

object PdmReaderLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        Application.launch(PdmReaderApplication::class.java, *args)
    }
}