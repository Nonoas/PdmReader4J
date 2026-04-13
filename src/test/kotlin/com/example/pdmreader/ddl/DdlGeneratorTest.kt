package com.example.pdmreader.ddl

import com.example.pdmreader.model.PdmColumnDetail
import com.example.pdmreader.model.PdmTableDetails
import kotlin.test.Test
import kotlin.test.assertTrue

class DdlGeneratorTest {
    private val ddlGenerator = DdlGenerator()

    @Test
    fun shouldGenerateOracleStyleCreateTableStatement() {
        val ddl = ddlGenerator.generate(
            PdmTableDetails(
                importId = 1L,
                importFileName = "sample.pdm",
                importFilePath = "D:/sample.pdm",
                modelName = "SampleModel",
                targetDb = "ORACLE Version 11g",
                tableId = 10L,
                tableName = "示例表",
                tableCode = "SAMPLE_TABLE",
                tableComment = "示例表备注",
                columns = listOf(
                    PdmColumnDetail(
                        idInPdm = "o11",
                        name = "主键",
                        code = "ID",
                        dataType = "NUMBER(18,0)",
                        length = 18,
                        precision = 18,
                        scale = 0,
                        nullable = false,
                        defaultValue = null,
                        comment = "主键",
                        pkFlag = true,
                        ordinalPosition = 1,
                    ),
                    PdmColumnDetail(
                        idInPdm = "o12",
                        name = "名称",
                        code = "NAME",
                        dataType = "VARCHAR2(64)",
                        length = 64,
                        precision = null,
                        scale = null,
                        nullable = true,
                        defaultValue = "'UNKNOWN'",
                        comment = "名称",
                        pkFlag = false,
                        ordinalPosition = 2,
                    ),
                ),
            )
        )

        assertTrue(ddl.contains("CREATE TABLE SAMPLE_TABLE"))
        assertTrue(ddl.contains("ID NUMBER(18,0) NOT NULL"))
        assertTrue(ddl.contains("NAME VARCHAR2(64) DEFAULT 'UNKNOWN'"))
        assertTrue(ddl.contains("PRIMARY KEY (ID)"))
    }
}
