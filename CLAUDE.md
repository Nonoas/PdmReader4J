# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PDMReader4J is a JavaFX desktop application for reading PowerDesigner PDM (Physical Data Model) files. It parses XML-based PDM files, displays table structures, supports search by table/column names, and generates DDL statements.

## Tech Stack

- **Language**: Kotlin (JVM 23)
- **UI Framework**: JavaFX 25 + jfx-flat-ui library (custom flat theme components)
- **Build Tool**: Gradle with Kotlin DSL
- **Database**: H2 (file-based, stored in `~/.pdm-reader/data/`)
- **Dependencies**: H2, JSON-java, SLF4J + Logback

## Build Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew run

# Run tests
./gradlew test

# Package the application (creates distributable with bundled JRE)
./gradlew packageMyApp

# Clean build
./gradlew clean
```

## Project Architecture

### Package Structure (`src/main/kotlin/indi/nonoas/pdmreader/`)

```
app/            # Application entry point and JavaFX Application class
  └─ PdmReaderApplication.kt

controller/     # JavaFX controllers - bind UI events to service calls
  └─ MainController.kt

ui/             # UI component builders (not FXML-based, pure code)
  ├─ MainView.kt         # Main UI layout builder
  └─ DialogWithIcon.kt   # Custom dialogs

service/        # Business logic orchestration
  └─ PdmCatalogService.kt

parser/         # PDM XML parsing
  └─ PowerDesignerPdmParser.kt

repository/     # H2 database access
  ├─ DatabaseFactory.kt
  └─ PdmRepository.kt

ddl/            # DDL generation
  └─ DdlGenerator.kt

model/          # Data classes
  ├─ PdmCatalogModels.kt    # View models (import summary, navigation items, column details)
  └─ ParsedPdmModel.kt    # Parser output models

util/           # Utilities
  ├─ HashUtils.kt
  └─ XmlElementUtils.kt
```

### Key Entry Points

- **Launcher**: `PdmReaderLauncherKt` (defined in `gradle.properties`)
- **Main Class**: `PdmReaderApplication` in `app/` package

## Architecture Patterns

### Layer Dependencies
```
UI (MainView) → Controller (MainController) → Service (PdmCatalogService)
                                                    ↓
                                    ┌───────────────┴───────────────┐
                              Parser                      Repository + DDL Generator
                      (PowerDesignerPdmParser)              (PdmRepository, DdlGenerator)
```

### Async Operations
Controller uses `TaskHandler` from jfx-flat-ui for background operations:
```kotlin
TaskHandler<Result<T>>()
    .whenCall { runCatching(action) }
    .andThen { result -> /* handle on JavaFX thread */ }
    .handle()
```

### State Management
- Controller exposes JavaFX `ObservableList` and `Property` objects
- UI binds directly to these properties
- Selection state managed bidirectionally between UI and controller

## UI Style Guidelines

### Theme System
- Uses jfx-flat-ui's `AppThemeManager` with `LightTheme`, `DarkTheme`, and custom `ClaudeTheme`
- CSS color variables come from theme (e.g., `-color-bg-default`, `-color-accent-emphasis`)
- Do not hardcode colors; always use theme variables

### CSS Conventions
- All controls in `src/main/resources/styles/app.css`
- Use rounded corners (12-22px radius) for modern flat design
- Consistent spacing via `INSET_1` constant (10px)
- Components use style classes like `.content-card`, `.toolbar-button`

### Layout Pattern
```kotlin
// Main layout structure
BorderPane
  ├─ top: VBox(headerBar, toolbar)
  ├─ center: SplitPane(leftSidebar, rightDetail)
  └─ bottom: statusLabel
```

## Database Schema

H2 schema defined in `src/main/resources/sql/schema.sql`:

- **import_file**: Stores imported PDM file metadata
- **pdm_table**: Tables within each import
- **pdm_column**: Columns within each table

All tables have foreign key cascades for deletion.

## Testing

Test files located in `src/test/kotlin/indi/nonoas/pdmreader/`:
- `parser/PowerDesignerPdmParserTest.kt`
- `ddl/DdlGeneratorTest.kt`
- `repository/PdmRepositoryTest.kt`

Run single test:
```bash
./gradlew test --tests "indi.nonoas.pdmreader.parser.PowerDesignerPdmParserTest"
```

## External Dependencies Reference

- **jfx-flat-ui**: Custom JavaFX flat UI library (SNAPSHOT version from Sonatype)
- **H2**: Embedded database for metadata persistence
- **JSON-java**: GitHub release API parsing

## Development Notes

- Target PDM sample file: `D:\kingdom\0_Project\fvs\fvs-doc\02Design\2.3ScriptDesign\01表结构设计\database\09文件导入导出.pdm`
- PDM files are XML with `PDM_DATA_MODEL_XML` signature
- Parser uses namespace-aware DOM parsing with security features disabled for external entities
- DDL generation targets Oracle-style syntax (VARCHAR2, NUMBER) based on sample file target DB
