package indi.nonoas.pdmreader.model

data class ParsedPdmModel(
    val modelName: String,
    val targetDb: String?,
    val tables: List<ParsedPdmTable>,
)

data class ParsedPdmTable(
    val idInPdm: String,
    val name: String,
    val code: String?,
    val comment: String?,
    val columns: List<ParsedPdmColumn>,
    val primaryKeyColumnIds: List<String>,
)

data class ParsedPdmColumn(
    val idInPdm: String,
    val name: String,
    val code: String?,
    val dataType: String?,
    val length: Int?,
    val precision: Int?,
    val scale: Int?,
    val nullable: Boolean,
    val defaultValue: String?,
    val comment: String?,
    val ordinalPosition: Int,
)
