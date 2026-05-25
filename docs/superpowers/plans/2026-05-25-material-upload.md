# Material Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build P4 Word/PDF material upload, text extraction, persistence, and unified frontend feedback.

**Architecture:** Add a focused `material` backend module with controller/service/repository/storage/extractor boundaries. Extend the workbench left panel with material upload and list UI, using Radix Toast wrapped in project-styled feedback components.

**Tech Stack:** Spring Boot, JDBC, PostgreSQL/Flyway, Apache POI, Apache PDFBox, React, TypeScript, Radix UI Toast, Vitest.

---

### Task 1: Backend Material Domain And Persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__material_upload_foundation.sql`
- Create: `backend/src/main/java/com/gongwen/assistant/material/MaterialDto.java`
- Create: `backend/src/main/java/com/gongwen/assistant/material/MaterialRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/material/JdbcMaterialRepository.java`
- Test: `backend/src/test/java/com/gongwen/assistant/material/MaterialServiceTest.java`

- [ ] **Step 1: Write failing repository-facing service tests**

Create tests that expect `MaterialService` to save `READY` and `FAILED` records.

- [ ] **Step 2: Run the focused backend test**

Run: `docker run --rm -v "E:\gongwen\backend:/workspace" -w /workspace -e GRADLE_USER_HOME=/tmp/gradle-home gradle:8.10.2-jdk21 gradle --project-cache-dir /tmp/gradle-project-cache test --tests "com.gongwen.assistant.material.MaterialServiceTest"`

Expected: fails because material classes do not exist.

- [ ] **Step 3: Add migration and minimal repository/domain code**

Create `material` table with draft FK, original file metadata, storage path, status, extracted text, error summary, tenant/department/version fields, timestamps, and draft/status indexes.

- [ ] **Step 4: Re-run focused test**

Expected: service tests pass after minimal implementation.

### Task 2: Backend Storage And Text Extraction

**Files:**
- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/gongwen/assistant/material/MaterialStorage.java`
- Create: `backend/src/main/java/com/gongwen/assistant/material/LocalMaterialStorage.java`
- Create: `backend/src/main/java/com/gongwen/assistant/material/MaterialTextExtractor.java`
- Test: `backend/src/test/java/com/gongwen/assistant/material/MaterialTextExtractorTest.java`

- [ ] **Step 1: Write failing extractor tests**

Test `.docx` extraction with `DocxTestFactory`; test `.pdf` extraction with a generated PDFBox document.

- [ ] **Step 2: Run extractor test and confirm failure**

Expected: fails because extractor does not exist.

- [ ] **Step 3: Add PDFBox and implement storage/extractor**

Add `org.apache.pdfbox:pdfbox` and implement Word/PDF text extraction plus deterministic local storage paths.

- [ ] **Step 4: Re-run extractor test**

Expected: tests pass.

### Task 3: Backend API

**Files:**
- Create: `backend/src/main/java/com/gongwen/assistant/material/MaterialController.java`
- Create: `backend/src/main/java/com/gongwen/assistant/material/MaterialUploadException.java`
- Test: `backend/src/test/java/com/gongwen/assistant/material/MaterialControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Cover `POST /api/drafts/{draftId}/materials`, `GET /api/drafts/{draftId}/materials`, and unsupported file type.

- [ ] **Step 2: Run controller tests and confirm failure**

Expected: fails because controller does not exist.

- [ ] **Step 3: Implement controller and exception mapping**

Map validation errors to `400` API errors; reuse `DRAFT_NOT_FOUND` semantics when draft lookup fails.

- [ ] **Step 4: Re-run controller tests**

Expected: tests pass.

### Task 4: Frontend API And Unified Toast

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Create: `frontend/src/components/feedback/ToastProvider.tsx`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: Install Radix Toast**

Run: `cd E:\gongwen\frontend; npm install @radix-ui/react-toast`

- [ ] **Step 2: Write failing frontend tests**

Expect app load to request materials; expect successful upload to call material API and display success toast; expect failed upload to display error toast.

- [ ] **Step 3: Run frontend tests and confirm failure**

Run: `cd E:\gongwen\frontend; npm test`

Expected: fails because material API and toast UI are missing.

- [ ] **Step 4: Implement material API helpers and toast provider**

Add `listDraftMaterials`, `uploadDraftMaterial`, `Material` type, `ToastProvider`, and `useToast`.

- [ ] **Step 5: Re-run frontend tests**

Expected: tests pass.

### Task 5: Frontend Workbench Material UI

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`

- [ ] **Step 1: Implement upload/list UI**

Add hidden file input, upload button, material list, upload disabled state, and status chips styled with existing CSS variables.

- [ ] **Step 2: Verify responsive layout in build**

Run: `cd E:\gongwen\frontend; npm run build`

Expected: TypeScript and Vite build succeed.

### Task 6: Documentation And Full Verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/PROJECT_TASKS.md`
- Modify: `.env.example`

- [ ] **Step 1: Update docs**

Record P4 API, material table/storage/extraction status, Radix Toast feedback convention, and verification commands.

- [ ] **Step 2: Run full backend tests**

Run Docker Gradle full test command from `docs/PROJECT_TASKS.md`.

- [ ] **Step 3: Run full frontend tests and build**

Run `npm test` and `npm run build`.

- [ ] **Step 4: Review git diff**

Confirm changes are limited to P4 backend/frontend/docs and no unrelated user work was reverted.

