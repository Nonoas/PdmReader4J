package indi.nonoas.pdmreader.repository

import indi.nonoas.pdmreader.model.*
import java.nio.file.Path
import java.sql.Connection
import java.sql.Statement
import java.time.LocalDateTime
import java.util.logging.Logger

class PdmRepository(
    private val databaseFactory: DatabaseFactory,
) {
    private val logger = Logger.getLogger(PdmRepository::class.java.name)

    init {
        initializeSchema()
    }

    fun replaceImport(filePath: Path, fileHash: String, parsedModel: ParsedPdmModel): PdmImportSummary {
        logger.info("Persisting PDM metadata for: $filePath")
        databaseFactory.openConnection().use { connection ->
            connection.autoCommit = false
            try {
                deleteExistingImport(connection, filePath.toAbsolutePath().toString())

                val importId = insertImport(connection, filePath, fileHash, parsedModel)
                parsedModel.tables.forEach { table ->
                    val tableId = insertTable(connection, importId, table)
                    table.columns.forEach { column ->
                        insertColumn(
                            connection = connection,
                            tableId = tableId,
                            column = column,
                            pkColumnIds = table.primaryKeyColumnIds.toSet(),
                        )
                    }
                }
                connection.commit()
                return getImportById(connection, importId)
                    ?: error("导入完成后未找到 import_file 记录: $importId")
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun listImports(): List<PdmImportSummary> =
        databaseFactory.openConnection().use { connection ->
            connection.prepareStatement(
                """
                select id, file_path, file_name, group_name, model_name, target_db, import_time
                from import_file
                order by import_time desc, id desc
                """.trimIndent()
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toImportSummary())
                        }
                    }
                }
            }
        }

    fun deleteImport(importId: Long): Boolean =
        deleteImports(listOf(importId)) > 0

    fun deleteImports(importIds: Collection<Long>): Int {
        val normalizedIds = importIds.distinct()
        if (normalizedIds.isEmpty()) {
            return 0
        }

        val placeholders = normalizedIds.joinToString(",") { "?" }
        return databaseFactory.openConnection().use { connection ->
            connection.prepareStatement(
                """
                delete from import_file
                where id in ($placeholders)
                """.trimIndent()
            ).use { statement ->
                normalizedIds.forEachIndexed { index, importId ->
                    statement.setLong(index + 1, importId)
                }
                statement.executeUpdate()
            }
        }
    }

    fun renameImportGroup(importIds: Collection<Long>, groupName: String): Int {
        val normalizedIds = importIds.distinct()
        if (normalizedIds.isEmpty()) {
            return 0
        }

        val placeholders = normalizedIds.joinToString(",") { "?" }
        return databaseFactory.openConnection().use { connection ->
            connection.prepareStatement(
                """
                update import_file
                set group_name = ?
                where id in ($placeholders)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, groupName)
                normalizedIds.forEachIndexed { index, importId ->
                    statement.setLong(index + 2, importId)
                }
                statement.executeUpdate()
            }
        }
    }

    fun listTableNavigation(importId: Long): List<TableNavigationItem> =
        databaseFactory.openConnection().use { connection ->
            connection.prepareStatement(
                """
                select i.id as import_id,
                       i.file_name,
                       t.id as table_id,
                       t.table_name,
                       t.table_code,
                       t.table_comment
                from pdm_table t
                join import_file i on i.id = t.import_file_id
                where t.import_file_id = ?
                order by upper(coalesce(t.table_code, t.table_name)), t.id
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, importId)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                TableNavigationItem(
                                    type = NavigationItemType.TABLE,
                                    importId = resultSet.getLong("import_id"),
                                    importFileName = resultSet.getString("file_name"),
                                    tableId = resultSet.getLong("table_id"),
                                    tableName = resultSet.getString("table_name"),
                                    tableCode = resultSet.getString("table_code"),
                                    tableComment = resultSet.getString("table_comment"),
                                )
                            )
                        }
                    }
                }
            }
        }

    fun searchNavigation(keyword: String): List<TableNavigationItem> {
        val likeKeyword = "%${keyword.trim().lowercase()}%"
        return databaseFactory.openConnection().use { connection ->
            connection.prepareStatement(
                """
                select *
                from (
                    select 0 as sort_order,
                           'TABLE' as match_type,
                           i.id as import_id,
                           i.file_name,
                           t.id as table_id,
                           t.table_name,
                           t.table_code,
                           t.table_comment,
                           cast(null as varchar(255)) as column_id_in_pdm,
                           cast(null as varchar(255)) as column_name,
                           cast(null as varchar(255)) as column_code
                    from pdm_table t
                    join import_file i on i.id = t.import_file_id
                    where lower(coalesce(t.table_name, '')) like ? or lower(coalesce(t.table_code, '')) like ?

                    union all

                    select 1 as sort_order,
                           'COLUMN' as match_type,
                           i.id as import_id,
                           i.file_name,
                           t.id as table_id,
                           t.table_name,
                           t.table_code,
                           t.table_comment,
                           c.column_id_in_pdm,
                           c.column_name,
                           c.column_code
                    from pdm_column c
                    join pdm_table t on t.id = c.table_id
                    join import_file i on i.id = t.import_file_id
                    where lower(coalesce(c.column_name, '')) like ? or lower(coalesce(c.column_code, '')) like ?
                ) result
                order by sort_order,
                         upper(file_name),
                         upper(coalesce(table_code, table_name)),
                         upper(coalesce(column_code, column_name))
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, likeKeyword)
                statement.setString(2, likeKeyword)
                statement.setString(3, likeKeyword)
                statement.setString(4, likeKeyword)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                TableNavigationItem(
                                    type = if (resultSet.getString("match_type") == "COLUMN") {
                                        NavigationItemType.COLUMN_MATCH
                                    } else {
                                        NavigationItemType.TABLE
                                    },
                                    importId = resultSet.getLong("import_id"),
                                    importFileName = resultSet.getString("file_name"),
                                    tableId = resultSet.getLong("table_id"),
                                    tableName = resultSet.getString("table_name"),
                                    tableCode = resultSet.getString("table_code"),
                                    tableComment = resultSet.getString("table_comment"),
                                    matchedColumnIdInPdm = resultSet.getString("column_id_in_pdm"),
                                    matchedColumnName = resultSet.getString("column_name"),
                                    matchedColumnCode = resultSet.getString("column_code"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun findTableDetails(tableId: Long): PdmTableDetails? =
        databaseFactory.openConnection().use { connection ->
            connection.prepareStatement(
                """
                select t.id as table_id,
                       t.table_name,
                       t.table_code,
                       t.table_comment,
                       i.id as import_id,
                       i.file_name,
                       i.file_path,
                       i.model_name,
                       i.target_db
                from pdm_table t
                join import_file i on i.id = t.import_file_id
                where t.id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setLong(1, tableId)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }

                    val columns = loadColumns(connection, tableId)
                    PdmTableDetails(
                        importId = resultSet.getLong("import_id"),
                        importFileName = resultSet.getString("file_name"),
                        importFilePath = resultSet.getString("file_path"),
                        modelName = resultSet.getString("model_name"),
                        targetDb = resultSet.getString("target_db"),
                        tableId = resultSet.getLong("table_id"),
                        tableName = resultSet.getString("table_name"),
                        tableCode = resultSet.getString("table_code"),
                        tableComment = resultSet.getString("table_comment"),
                        columns = columns,
                    )
                }
            }
        }

    private fun loadColumns(connection: Connection, tableId: Long): List<PdmColumnDetail> =
        connection.prepareStatement(
            """
            select column_id_in_pdm,
                   column_name,
                   column_code,
                   data_type,
                   data_length,
                   data_precision,
                   data_scale,
                   nullable_flag,
                   default_value,
                   column_comment,
                   pk_flag,
                   ordinal_position
            from pdm_column
            where table_id = ?
            order by ordinal_position
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, tableId)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            PdmColumnDetail(
                                idInPdm = resultSet.getString("column_id_in_pdm"),
                                name = resultSet.getString("column_name"),
                                code = resultSet.getString("column_code"),
                                dataType = resultSet.getString("data_type"),
                                length = resultSet.getObject("data_length", Int::class.javaObjectType),
                                precision = resultSet.getObject("data_precision", Int::class.javaObjectType),
                                scale = resultSet.getObject("data_scale", Int::class.javaObjectType),
                                nullable = resultSet.getBoolean("nullable_flag"),
                                defaultValue = resultSet.getString("default_value"),
                                comment = resultSet.getString("column_comment"),
                                pkFlag = resultSet.getBoolean("pk_flag"),
                                ordinalPosition = resultSet.getInt("ordinal_position"),
                            )
                        )
                    }
                }
            }
        }

    private fun initializeSchema() {
        val schemaSql = javaClass.getResource("/sql/schema.sql")
            ?.readText(Charsets.UTF_8)
            ?: error("Missing resource: /sql/schema.sql")

        databaseFactory.openConnection().use { connection ->
            connection.createStatement().use { statement ->
                schemaSql.split(";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach(statement::execute)
            }
        }
    }

    private fun deleteExistingImport(connection: Connection, filePath: String) {
        connection.prepareStatement(
            """
            delete from import_file
            where file_path = ?
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, filePath)
            statement.executeUpdate()
        }
    }

    private fun insertImport(
        connection: Connection,
        filePath: Path,
        fileHash: String,
        parsedModel: ParsedPdmModel,
    ): Long =
        connection.prepareStatement(
            """
            insert into import_file (
                file_path,
                file_name,
                group_name,
                file_hash,
                model_name,
                target_db,
                import_time
            ) values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            Statement.RETURN_GENERATED_KEYS,
        ).use { statement ->
            statement.setString(1, filePath.toAbsolutePath().toString())
            statement.setString(2, filePath.fileName.toString())
            statement.setString(3, defaultGroupName(filePath.toAbsolutePath().toString()))
            statement.setString(4, fileHash)
            statement.setString(5, parsedModel.modelName)
            statement.setString(6, parsedModel.targetDb)
            statement.setObject(7, LocalDateTime.now())
            statement.executeUpdate()
            statement.generatedKeys.use { generatedKeys ->
                if (generatedKeys.next()) {
                    generatedKeys.getLong(1)
                } else {
                    error("插入 import_file 失败，未返回主键")
                }
            }
        }

    private fun insertTable(connection: Connection, importId: Long, table: ParsedPdmTable): Long =
        connection.prepareStatement(
            """
            insert into pdm_table (
                import_file_id,
                table_id_in_pdm,
                table_name,
                table_code,
                table_comment
            ) values (?, ?, ?, ?, ?)
            """.trimIndent(),
            Statement.RETURN_GENERATED_KEYS,
        ).use { statement ->
            statement.setLong(1, importId)
            statement.setString(2, table.idInPdm)
            statement.setString(3, table.name)
            statement.setString(4, table.code)
            statement.setString(5, table.comment)
            statement.executeUpdate()
            statement.generatedKeys.use { generatedKeys ->
                if (generatedKeys.next()) {
                    generatedKeys.getLong(1)
                } else {
                    error("插入 pdm_table 失败，未返回主键")
                }
            }
        }

    private fun insertColumn(
        connection: Connection,
        tableId: Long,
        column: ParsedPdmColumn,
        pkColumnIds: Set<String>,
    ) {
        connection.prepareStatement(
            """
            insert into pdm_column (
                table_id,
                column_id_in_pdm,
                column_name,
                column_code,
                data_type,
                data_length,
                data_precision,
                data_scale,
                nullable_flag,
                default_value,
                column_comment,
                pk_flag,
                ordinal_position
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, tableId)
            statement.setString(2, column.idInPdm)
            statement.setString(3, column.name)
            statement.setString(4, column.code)
            statement.setString(5, column.dataType)
            statement.setObject(6, column.length)
            statement.setObject(7, column.precision)
            statement.setObject(8, column.scale)
            statement.setBoolean(9, column.nullable)
            statement.setString(10, column.defaultValue)
            statement.setString(11, column.comment)
            statement.setBoolean(12, pkColumnIds.contains(column.idInPdm))
            statement.setInt(13, column.ordinalPosition)
            statement.executeUpdate()
        }
    }

    private fun getImportById(connection: Connection, importId: Long): PdmImportSummary? =
        connection.prepareStatement(
            """
            select id, file_path, file_name, group_name, model_name, target_db, import_time
            from import_file
            where id = ?
            """.trimIndent()
        ).use { statement ->
            statement.setLong(1, importId)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    resultSet.toImportSummary()
                } else {
                    null
                }
            }
        }

    private fun java.sql.ResultSet.toImportSummary(): PdmImportSummary =
        PdmImportSummary(
            id = getLong("id"),
            filePath = getString("file_path"),
            fileName = getString("file_name"),
            groupName = getString("group_name")?.takeIf { it.isNotBlank() } ?: defaultGroupName(getString("file_path")),
            modelName = getString("model_name"),
            targetDb = getString("target_db"),
            importTime = getObject("import_time", LocalDateTime::class.java),
        )

    private fun defaultGroupName(filePath: String): String =
        runCatching {
            Path.of(filePath)
                .parent
                ?.fileName
                ?.toString()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "未分组"
}
