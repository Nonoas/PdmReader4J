package com.example.pdmreader.app

import javafx.application.Application

object PdmReaderLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        Application.launch(PdmReaderApplication::class.java, *args)
    }
}
