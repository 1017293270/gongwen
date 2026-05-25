# Draft Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first real draft workbench: backend document types/drafts/blocks plus frontend loading, editing, previewing, and saving real draft data.

**Architecture:** Use JDBC-backed Spring services for document types and drafts, with Flyway creating the core tables and seed document types. The React app consumes the backend API through a small client module and keeps the existing three-column design while replacing hardcoded content with editable draft state.

**Tech Stack:** Java 21, Spring Boot 3.3.5, JdbcTemplate, PostgreSQL/Flyway, React 18, TypeScript, Vite, Vitest, Testing Library.

---

## File Structure

Create or modify:

- `backend/src/main/resources/db/migration/V3__draft_workbench_foundation.sql`: document type, draft, draft block tables and seed data.
- `backend/src/main/java/com/gongwen/assistant/document/DocumentTypeController.java`: document type API.
- `backend/src/main/java/com/gongwen/assistant/document/DocumentTypeDto.java`: document type DTO.
- `backend/src/main/java/com/gongwen/assistant/document/DocumentTypeRepository.java`: JDBC document type queries.
- `backend/src/main/java/com/gongwen/assistant/draft/*`: draft DTOs, repository, service, controller, exceptions.
- `backend/src/test/java/com/gongwen/assistant/document/DocumentTypeControllerTest.java`: document type API test.
- `backend/src/test/java/com/gongwen/assistant/draft/DraftServiceTest.java`: draft create/read/update tests.
- `backend/src/test/java/com/gongwen/assistant/draft/DraftControllerTest.java`: draft API tests.
- `frontend/src/api.ts`: API client.
- `frontend/src/draftTypes.ts`: frontend data types.
- `frontend/src/App.tsx`: real data workbench.
- `frontend/src/App.test.tsx`: async workbench tests.
- `AGENTS.md` and `docs/PROJECT_TASKS.md`: update status after verification.

## Tasks

### Task 1: Backend schema and document type API

- [x] Add V3 migration with `document_type`, `draft`, and `draft_block`.
- [x] Write `DocumentTypeControllerTest` expecting three seeded document types.
- [x] Implement document type DTO, repository, and controller.
- [x] Run the document type test and verify it passes.

### Task 2: Draft service with TDD

- [x] Write `DraftServiceTest` for creating a default NOTICE draft with default blocks.
- [x] Write `DraftServiceTest` for updating blocks and reading them back.
- [x] Implement draft DTOs, repository, service, and not-found exception.
- [x] Run service tests and verify they pass.

### Task 3: Draft API with TDD

- [x] Write `DraftControllerTest` for POST, GET, PUT, and missing draft.
- [x] Implement `DraftController`.
- [x] Add JSON error handling for missing draft.
- [x] Run backend tests and verify they pass.

### Task 4: Frontend API client and real draft state

- [x] Add frontend data types and API client.
- [x] Update `App.tsx` to load document types and create a default draft when needed.
- [x] Bind left fields and center preview to draft block state.
- [x] Add save action and right-panel status.
- [x] Preserve existing design tokens and three-column layout.

### Task 5: Frontend tests and browser verification

- [x] Update `App.test.tsx` to mock fetch, wait for loaded draft, edit title, and save.
- [x] Run `npm test` and `npm run build`.
- [x] Start/reuse frontend dev server and verify the workbench in the browser.

### Task 6: Final verification, docs, commit, push

- [x] Run backend full test suite.
- [x] Run frontend tests and build.
- [x] Verify backend bootRun applies Flyway v3 and draft APIs respond.
- [x] Update `AGENTS.md` and `docs/PROJECT_TASKS.md`.
- [x] Commit and push `p2-p3-draft-workbench`.

## Self-Review

Spec coverage:

- Document types are covered by Task 1.
- Draft create/read/update is covered by Task 2 and Task 3.
- Frontend real data workbench is covered by Task 4 and Task 5.
- Verification and documentation are covered by Task 6.

Placeholder scan:

- No unfinished markers, incomplete implementation instructions, or ambiguous follow-up placeholders are used.

Type consistency:

- Backend block type uses `blockType`.
- Frontend DTOs match backend JSON names.
- API responses continue to use existing `ApiResponse`.
