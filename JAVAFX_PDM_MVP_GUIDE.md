# JavaFX PDM Reader MVP Implementation Guide

## Goal

Implement a JavaFX desktop application with only these features:

1. Import `.pdm` files and view table structures
2. Search by table name or column name
3. Generate and copy DDL
4. Persist imported metadata in `H2` instead of `SQLite`

Do not implement:

- NDM support
- ER diagram rendering/editing
- Multi-database client features
- Word/Excel/Markdown export
- Login, tray, or online features

The implementation should target a clean Java architecture, not a line-by-line port of the Electron project.

## Reference PDM Sample

Use this file as the primary compatibility sample:

`D:\kingdom\0_Project\fvs\fvs-doc\02Design\2.3ScriptDesign\01表结构设计\database\09文件导入导出.pdm`

Confirmed from the sample header:

- XML file
- PowerDesigner physical model
- Signature: `PDM_DATA_MODEL_XML`
- Target DB: `ORACLE Version 11g`

The file starts like:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?PowerDesigner ... signature="PDM_DATA_MODEL_XML" version="16.7.4.6866"?>
<Model xmlns:a="attribute" xmlns:c="collection" xmlns:o="object">
```

## Recommended Tech Stack

- Java 17 or 21
- JavaFX
- Maven or Gradle
- H2 database
- XML parser: DOM + XPath, or JAXB if preferred

## Required Functional Scope

### 1. Import PDM

Support selecting a `.pdm` file from local disk and parsing:

- model name
- table name
- table code
- table comment/description if present
- column name
- column code
- data type
- length
- precision / scale if present
- nullable / mandatory
- default value if present
- column comment if present
- primary key membership

Expected output after import:

- imported metadata is stored into H2
- UI shows the list of tables
- selecting a table shows its column list

### 2. Search

Support:

- search by table name/code
- search by column name/code

Expected behavior:

- keyword search is case-insensitive
- search results can locate the target table directly
- when searching by column, the result should include the owning table

### 3. Copy DDL

For the selected table, generate a `CREATE TABLE` statement and copy it to clipboard.

At minimum the generated DDL should include:

- table name
- column definitions
- type/length/precision
- `NOT NULL` when required
- `DEFAULT` when present
- primary key clause

Target flavor for MVP:

- Prefer Oracle-style output, because the sample file target is `ORACLE Version 11g`
- If the PDM does not provide enough metadata for exact DB-specific syntax, generate a conservative generic SQL form

### 4. H2 Persistence

Persist imported data into H2 so the app can reopen and browse previously imported PDM metadata.

Minimum persistence requirements:

- imported files
- tables
- columns

If the same file is imported again, either:

- replace the previous import by file path, or
- replace by file hash

Choose one strategy and keep it consistent.

## Suggested UI

Keep the UI simple:

- left: imported files and table list
- top or left-top: search box
- right: selected table details and column grid
- action button: `Import PDM`
- action button: `Copy DDL`

Recommended JavaFX controls:

- `BorderPane`
- `SplitPane`
- `TreeView` or `ListView`
- `TableView`
- `TextField`
- `Button`
- `TextArea` for DDL preview

## Suggested Package Structure

```text
indi.nonoas.pdmreader
  ├─ app
  ├─ ui
  ├─ controller
  ├─ model
  ├─ service
  ├─ parser
  ├─ repository
  ├─ ddl
  └─ util
```

Suggested responsibilities:

- `parser`: parse PowerDesigner PDM XML into Java domain objects
- `service`: orchestrate import/search/copy behavior
- `repository`: H2 CRUD
- `ddl`: build `CREATE TABLE` statements
- `ui/controller`: JavaFX event handling and binding

## Suggested H2 Schema

At minimum create these tables:

### `import_file`

- `id`
- `file_path`
- `file_name`
- `file_hash`
- `model_name`
- `target_db`
- `import_time`

### `pdm_table`

- `id`
- `import_file_id`
- `table_id_in_pdm`
- `table_name`
- `table_code`
- `table_comment`

Indexes:

- index on `table_name`
- index on `table_code`

### `pdm_column`

- `id`
- `table_id`
- `column_id_in_pdm`
- `column_name`
- `column_code`
- `data_type`
- `data_length`
- `data_precision`
- `data_scale`
- `nullable`
- `default_value`
- `column_comment`
- `pk_flag`
- `ordinal_position`

Indexes:

- index on `column_name`
- index on `column_code`

## PDM Parsing Guidance

The sample is a PowerDesigner physical model XML file.

The AI implementing this should inspect the actual XML structure of the sample file and build the parser against real nodes, not assumptions only.

Likely relevant nodes in PowerDesigner PDM:

- model metadata under `o:Model`
- tables under `c:Tables / o:Table`
- columns under `c:Columns / o:Column`
- keys under `c:Keys / o:Key`
- primary key reference under `c:PrimaryKey`

Likely useful fields:

- `a:Name`
- `a:Code`
- `a:Comment`
- `a:DataType`
- `a:Length`
- `a:Precision`
- `a:DefaultValue`
- `a:Column.Mandatory` or equivalent mandatory/nullability flag

Important:

- Do not rely on a single optional tag name without fallback
- Use namespace-aware XML parsing
- Resolve primary key membership using key definitions and references if direct per-column flags are absent
- Preserve table and column order where possible

## DDL Generation Rules

For each table:

1. Use `table_code` as the physical table name when present
2. If `table_code` is empty, fall back to `table_name`
3. Use `column_code` as the physical column name when present
4. If `column_code` is empty, fall back to `column_name`
5. Append type definition from parsed metadata:
   - `VARCHAR2(50)`
   - `NUMBER(18,2)`
   - `DATE`
   - etc.
6. Add `NOT NULL` when the column is mandatory
7. Add `DEFAULT ...` when default value exists
8. Add `PRIMARY KEY (...)` using PK columns in original order if recoverable

Provide the generated DDL in:

- preview area in UI
- clipboard copy action

## Search Rules

Implement two search modes or one combined mode:

- search table
- search column

Combined mode is acceptable if results clearly show:

- match type
- table name
- column name if applicable

Search behavior:

- trim input
- ignore empty search
- case-insensitive
- fuzzy `LIKE` search is sufficient for MVP

## Persistence Rules

When importing a file:

1. compute hash
2. check whether the file already exists in H2
3. if exists, delete old table/column metadata for that import
4. insert fresh parsed metadata

On app startup:

- load imported file list from H2
- allow reopening and browsing without re-importing

## Recommended Delivery Order

1. Create project skeleton
2. Create H2 schema and repository layer
3. Implement parser against the sample `.pdm`
4. Persist parsed metadata
5. Build JavaFX table browser UI
6. Add search
7. Add DDL preview and clipboard copy
8. Fix parsing edge cases from the sample file

## Acceptance Criteria

The implementation is complete only if all items below pass:

1. User can import the sample `.pdm` file successfully
2. Imported tables are visible after restart because metadata is stored in H2
3. User can click a table and view its column list
4. User can search by table name and locate the table
5. User can search by column name and see the owning table
6. User can generate and copy DDL for a selected table
7. DDL includes PK and nullability when the sample file contains that metadata
8. No Electron-specific structure or JavaScript-style global state is copied into the Java design

## Implementation Constraints

- Favor maintainable Java code over quick hacks
- Keep parser and UI separated
- Do not hardcode table names from the sample file
- Do not make the parser depend on one single exact XML position if multiple equivalent paths exist
- Add concise logging around import and parse failures

## Definition Of Done

The work is done when:

- the app runs locally
- the sample PDM imports successfully
- H2 persistence works
- table structure browsing works
- search works
- DDL copy works
- core logic is separated into parser/service/repository/ui layers

## Suggested Prompt To The Next AI Window

Use this as the working instruction:

```text
Build a JavaFX desktop app that supports importing PowerDesigner PDM files, viewing table structures, searching by table/column, copying DDL, and persisting metadata in H2. Use the sample file at D:\kingdom\0_Project\fvs\fvs-doc\02Design\2.3ScriptDesign\01表结构设计\database\09文件导入导出.pdm as the primary compatibility target. Follow the requirements in JAVAFX_PDM_MVP_GUIDE.md. Do not implement NDM, ER diagrams, or external database client features. Start by creating the project skeleton, H2 schema, and a real parser against the sample PDM XML.
```
