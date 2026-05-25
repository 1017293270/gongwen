# Template Docx Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the backend minimum loop for parsing `.docx` template placeholders and exporting a filled Word document.

**Architecture:** Add focused Spring Boot modules for template parsing and Word export. Apache POI handles `.docx` reads/writes, Flyway adds template/export tables, and MockMvc/service tests verify behavior without requiring a frontend template admin page.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Gradle, PostgreSQL/Flyway, Apache POI, JUnit 5, MockMvc.

---

## File Structure

Create or modify:

- `backend/build.gradle`: add Apache POI dependency.
- `backend/src/main/resources/db/migration/V2__template_export_foundation.sql`: template/export tables.
- `backend/src/main/java/com/gongwen/assistant/template/parser/DocxPlaceholderParser.java`: parse placeholders.
- `backend/src/main/java/com/gongwen/assistant/template/parser/ParsedTemplate.java`: parser DTO.
- `backend/src/main/java/com/gongwen/assistant/template/TemplateController.java`: multipart parse endpoint.
- `backend/src/main/java/com/gongwen/assistant/exporting/WordExportController.java`: minimal export endpoint.
- `backend/src/main/java/com/gongwen/assistant/exporting/WordExportRequest.java`: export request DTO.
- `backend/src/main/java/com/gongwen/assistant/exporting/WordExportService.java`: validation and export orchestration.
- `backend/src/main/java/com/gongwen/assistant/exporting/ExportRecordRepository.java`: save export records via JDBC.
- `backend/src/main/java/com/gongwen/assistant/exporting/word/DocxTemplateRenderer.java`: fill `.docx`.
- `backend/src/test/java/com/gongwen/assistant/support/DocxTestFactory.java`: test `.docx` builder/read helpers.
- `backend/src/test/java/com/gongwen/assistant/template/parser/DocxPlaceholderParserTest.java`: parser tests.
- `backend/src/test/java/com/gongwen/assistant/exporting/word/DocxTemplateRendererTest.java`: renderer tests.
- `backend/src/test/java/com/gongwen/assistant/template/TemplateControllerTest.java`: parse API test.
- `backend/src/test/java/com/gongwen/assistant/exporting/WordExportServiceTest.java`: validation/export service test.
- `AGENTS.md` and `docs/PROJECT_TASKS.md`: update P1 status after verification.

## Tasks

### Task 1: Add Apache POI and database migration

- [ ] Add `org.apache.poi:poi-ooxml:5.3.0` to `backend/build.gradle`.
- [ ] Add `V2__template_export_foundation.sql` with `document_template`, `template_field`, and `export_record`.
- [ ] Run backend tests through Docker Gradle image.

### Task 2: Implement placeholder parser with TDD

- [ ] Create `DocxTestFactory` helper to build `.docx` bytes in tests.
- [ ] Write parser tests for paragraph placeholders, table placeholders, and duplicate placeholders.
- [ ] Run parser tests and verify they fail before implementation.
- [ ] Implement `ParsedTemplate` and `DocxPlaceholderParser`.
- [ ] Run parser tests and verify they pass.

### Task 3: Implement renderer with TDD

- [ ] Write renderer test for successful replacement and newline handling.
- [ ] Write renderer test for missing fields failing clearly.
- [ ] Run renderer tests and verify they fail before implementation.
- [ ] Implement `DocxTemplateRenderer`.
- [ ] Run renderer tests and verify they pass.

### Task 4: Add minimal API and export orchestration

- [ ] Write `TemplateControllerTest` for `POST /api/templates/parse`.
- [ ] Write `WordExportServiceTest` for missing fields and successful export record save.
- [ ] Implement `TemplateController`.
- [ ] Implement `WordExportRequest`, `WordExportService`, `ExportRecordRepository`, and `WordExportController`.
- [ ] Run backend tests and fix any failures.

### Task 5: Verify and update docs

- [ ] Run backend tests.
- [ ] Run frontend tests and build to ensure monorepo still works.
- [ ] Update `AGENTS.md` current status.
- [ ] Update `docs/PROJECT_TASKS.md` P1 status.
- [ ] Commit and push `p1-template-docx-export`.

## Self-Review

Spec coverage:

- Placeholder parsing is covered by Task 2.
- Word rendering/export is covered by Task 3 and Task 4.
- Database migration is covered by Task 1.
- API coverage is covered by Task 4.
- Docs and verification are covered by Task 5.

Placeholder scan:

- No unfinished markers, incomplete implementation instructions, or ambiguous follow-up placeholders are used.

Type consistency:

- Parser uses `ParsedTemplate`.
- Renderer uses `Map<String, String>` values.
- API response continues to use existing `ApiResponse`.
