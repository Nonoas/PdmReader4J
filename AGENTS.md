# Repository Guidelines

## Project Structure & Module Organization

This repository currently contains a single planning document, [JAVAFX_PDM_MVP_GUIDE.md](/D:/MyCode/IDEA/MyPDMReader/JAVAFX_PDM_MVP_GUIDE.md), which defines the MVP for a JavaFX desktop PDM reader. When implementing the app, keep the codebase aligned with that guide:

- `src/main/java/com/example/pdmreader/app`: startup and bootstrap
- `src/main/java/com/example/pdmreader/ui` and `controller`: JavaFX views and event handling
- `src/main/java/com/example/pdmreader/parser`: PowerDesigner XML parsing
- `src/main/java/com/example/pdmreader/service`: import, search, and DDL orchestration
- `src/main/java/com/example/pdmreader/repository`: H2 persistence
- `src/main/java/com/example/pdmreader/model`, `ddl`, `util`: domain types and shared helpers
- `src/test/java/...`: tests mirroring production packages
- `src/main/resources/`: FXML, CSS, SQL schema, and sample fixtures

## Coding Style & Naming Conventions

Use Java 17+ with 4-space indentation and UTF-8 source files. Prefer clear package boundaries over large controller classes. Use `PascalCase` for classes, `camelCase` for methods and fields, and `UPPER_SNAKE_CASE` for constants. Follow role-based suffixes such as `*Controller`, `*Service`, `*Repository`, and `*Parser`. Keep JavaFX UI logic separated from parsing and persistence logic.

## Agent-Specific Notes

Do not treat this project as an Electron port. Preserve a clean Java architecture, keep parser/service/repository/ui layers separate, and use the sample `.pdm` file referenced in `JAVAFX_PDM_MVP_GUIDE.md` as the primary compatibility target.

## 其他

- 使用简体中文执行任务
