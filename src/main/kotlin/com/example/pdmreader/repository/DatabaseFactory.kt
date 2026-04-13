package com.example.pdmreader.repository

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class DatabaseFactory(
    dbBasePath: Path = defaultDbBasePath(),
) {
    private val jdbcUrl: String

    init {
        Files.createDirectories(dbBasePath.parent)
        jdbcUrl = "jdbc:h2:file:${dbBasePath.toAbsolutePath().toString().replace("\\", "/")};AUTO_SERVER=TRUE;MODE=Oracle"
        Class.forName("org.h2.Driver")
    }

    fun openConnection(): Connection = DriverManager.getConnection(jdbcUrl, "sa", "")

    companion object {
        private fun defaultDbBasePath(): Path {
            val root = Path.of(System.getProperty("user.home"), ".pdm-reader", "data")
            return root.resolve("catalog")
        }
    }
}
