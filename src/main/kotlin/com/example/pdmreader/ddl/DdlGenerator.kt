package com.example.pdmreader.ddl

import com.example.pdmreader.model.PdmColumnDetail
import com.example.pdmreader.model.PdmTableDetails

class DdlGenerator {
    fun generate(table: PdmTableDetails): String {
        val physicalTableName = table.tableCode?.takeIf { it.isNotBlank() } ?: table.tableName
        val lines = table.columns.map { column ->
            buildColumnLine(column)
        }.toMutableList()

        val primaryKeyColumns = table.columns
            .filter { it.pkFlag }
            .sortedBy { it.ordinalPosition }
            .map { column ->
                column.code?.takeIf { it.isNotBlank() } ?: column.name
            }

        if (primaryKeyColumns.isNotEmpty()) {
            lines += "    PRIMARY KEY (${primaryKeyColumns.joinToString(", ")})"
        }

        return buildString {
            append("CREATE TABLE ")
            append(physicalTableName)
            append(" (\n")
            append(lines.joinToString(",\n"))
            append("\n);")
        }
    }

    private fun buildColumnLine(column: PdmColumnDetail): String {
        val physicalColumnName = column.code?.takeIf { it.isNotBlank() } ?: column.name
        val typeDefinition = resolveTypeDefinition(column)
        val notNullClause = if (column.nullable) "" else " NOT NULL"
        val defaultClause = column.defaultValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { " DEFAULT $it" }
            .orEmpty()

        return "    $physicalColumnName $typeDefinition$defaultClause$notNullClause"
    }

    private fun resolveTypeDefinition(column: PdmColumnDetail): String {
        val rawType = column.dataType?.trim()
        if (!rawType.isNullOrEmpty()) {
            return rawType
        }

        return when {
            column.precision != null && column.scale != null -> "NUMBER(${column.precision},${column.scale})"
            column.precision != null -> "NUMBER(${column.precision})"
            column.length != null -> "VARCHAR2(${column.length})"
            else -> "VARCHAR2(255)"
        }
    }
}
