# DOCX Structure Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the phased DOCX true-preview and structured AI workbench flow: preserve official-document structure, allow node-level content and font/format editing, use AI around selected nodes, verify layout with LibreOffice preview, and export traceable `.docx` files.

**Architecture:** Keep the main workbench structured rather than turning it into a full online Word editor. The backend owns DOCX facts, document-kind analysis, mapping, draft nodes, formatting merge, rendering jobs, quality checks, and export traceability. The frontend keeps the three-column mental model: left structure/input facts, center structured Word-like editing plus true preview, right AI/quality/export/node-format operations.

**Tech Stack:** Spring Boot, PostgreSQL/Flyway, Apache POI/docx4j as needed, LibreOffice headless, PDFBox for later coordinates, React + TypeScript + Vite, Vitest/React Testing Library, JUnit/MockMvc.

---

## Source Specs

- `docs/superpowers/specs/2026-05-30-docx-preview-structure-mapping-design.md`
- `docs/superpowers/specs/2026-05-27-workbench-structure-node-design.md`
- `AGENTS.md`
- `DESIGN.md`
- `docs/PROJECT_TASKS.md`

## Progress Discipline

Every agent finishing any task in this plan must update this file in the same commit as the code change.

Required updates per completed task:

1. Tick completed checkboxes in that task.
2. Update the row in **Progress Board**: status, owner, commit, verification, remaining risk.
3. Add one entry to **Progress Log** with date, task id, agent, commit, verification command, and result.
4. If a milestone reaches completion, update `docs/PROJECT_TASKS.md`.
5. If APIs, data model, deployment variables, module boundaries, or AI flow changed, update `AGENTS.md`.

Status values:

```text
未开始
进行中
待集成
已完成
阻塞
```

Completion evidence format:

```text
日期 | Task | Agent | Commit | Verification | Result | Risk
```

## Progress Board

| Task | Status | Preferred Agent | Dependencies | Deliverable | Commit | Verification | Risk |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T0 | 已完成 | Integration Agent | none | frozen contracts, migration sequence, API boundaries, 18081 baseline | 未提交（T0 文档冻结） | `git status --short --branch`; `Get-ChildItem backend/src/main/resources/db/migration \| Select-Object Name`; `Get-NetTCPConnection -LocalPort 18081` | no feature code changed; next agents must preserve frozen migration numbers |
| T1 | 已完成 | Backend Profile Agent | T0 | eastAsia/latin font split and structured line spacing baseline | 未提交（T1 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.template.profile.TemplateProfileParserTest" --tests "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"` | full backend test not run per lightweight-test instruction; line spacing first pass covers AUTO/EXACT |
| T2 | 已完成 | Backend Profile Agent | T1 | document kind classifier hardens manual/template distinction | 未提交（T2 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.template.TemplateUploadServiceTest" --tests "com.gongwen.assistant.template.profile.TemplateProfileParserTest"` | full backend test not run per lightweight-test instruction; rule classifier first pass covers generated fixtures |
| T3 | 已完成 | Backend Structure Agent | T1, T2 | `DocumentStructureProfile` foundation | 未提交（T3 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.documentstructure.DocumentStructureExtractorTest" --tests "com.gongwen.assistant.template.TemplateUploadServiceTest"` | first pass derives nodes from existing `TemplateProfile.structures`; full OOXML fact extraction deferred |
| T4 | 已完成 | Backend Render Agent | T0 | LibreOffice render job backend | 未提交（T4 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.rendering.DocumentRenderPreviewServiceTest" --tests "com.gongwen.assistant.rendering.LibreOfficeRenderClientTest"` | local LibreOffice execution not run; render client command and service path/status behavior covered with test seam |
| T5 | 已完成 | Frontend Template Agent | T3, T4 | template parse workspace with structure and preview states | 未提交（T5 实现） | `npm test -- src/App.test.tsx`; `npm run build`; `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.template.TemplateControllerTest" --tests "com.gongwen.assistant.template.TemplateUploadControllerTest"` | in-app browser tool unavailable; local Vite responded 200, dialog covered by App test |
| T6 | 已完成 | Backend Mapping Agent | T3 | structure mapping save/publish backend | 未提交（T6 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.documentstructure.mapping.StructureMappingServiceTest" --tests "com.gongwen.assistant.documentstructure.mapping.StructureMappingControllerTest"` | first pass required slots are `TITLE` and `BODY`; richer official-document slot policy deferred |
| T7 | 已完成 | Frontend Template Agent | T5, T6 | mapping editor UI and publish flow | 未提交（T7 实现） | `npm test -- src/App.test.tsx`; `npm run build`; `Invoke-WebRequest http://localhost:5173` | first pass uses inline role selects in parse workspace; complex bulk mapping UX deferred |
| T8 | 已完成 | Backend Draft Agent | T6 | persisted `DraftNode` model and API | 未提交（T8 实现） | `.\gradlew.bat --no-daemon --console=plain compileTestJava`; `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest" --tests "com.gongwen.assistant.draft.node.DraftNodeControllerTest"` | plan script could not run because `.tools\gradle-8.10.2` is absent; wrapper with JDK 21 passed focused tests; frontend not touched |
| T9 | 已完成 | Frontend Workbench Agent | T8 | left structure tree and center structured editor bind to nodes | 未提交（T9 实现） | `npm test -- src/workbenchNodes.test.ts src/App.test.tsx`; `npm run build` | first pass keeps legacy `DraftBlock` materialization for AI/quality/export compatibility; node-aware AI/export deferred to T10/T14 |
| T10 | 已完成 | Backend AI Agent | T8 | node-aware AI operations | 未提交（T10 实现） | `.\gradlew.bat --no-daemon --console=plain compileTestJava`; `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.PromptBuilderTest" --tests "com.gongwen.assistant.ai.AiOutlineServiceTest" --tests "com.gongwen.assistant.ai.AiParagraphServiceTest" --tests "com.gongwen.assistant.ai.AiLocalOperationServiceTest"` | full backend test skipped per lightweight-test instruction; frontend routing still deferred to T11 |
| T11 | 已完成 | Frontend AI Agent | T9, T10 | right panel context follows selected node | 未提交（T11 实现） | `npm test -- src/App.test.tsx`; `npm run build` | browser screenshot not captured because Browser/node_repl tooling unavailable; focused tests cover node action switching and node-metadata request routing |
| T12 | 已完成 | Backend Format Agent | T8 | draft node format override backend | 未提交（T12 实现） | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeFormatOverrideTest" --tests "com.gongwen.assistant.draft.node.DraftNodeControllerTest" --tests "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"` | export/preview consumers still deferred to T14/T15; frontend format panel deferred to T13 |
| T13 | 未开始 | Frontend Format Agent | T9, T12 | node-level font and format panel |  |  |  |
| T14 | 未开始 | Backend Export Agent | T6, T8, T12 | export consumes mapping, nodes, and format merge |  |  |  |
| T15 | 未开始 | Frontend Export Agent | T11, T13, T14 | preview refresh and export status flow |  |  |  |
| T16 | 未开始 | QA Agent | T1-T15 | fixture suite, regression tests, and docs sync |  |  |  |
| T17 | 未开始 | Integration Agent | T1-T16 | final integration verification and progress closure |  |  |  |

## Progress Log

| Date | Task | Agent | Commit | Verification | Result | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-05-30 | Plan | Codex | plan document commit | `git diff --check` | pass | implementation not started |
| 2026-05-30 | T0 | Codex Integration Agent | 未提交（T0 文档冻结） | `git status --short --branch`; `Get-ChildItem backend/src/main/resources/db/migration \| Select-Object Name`; `Get-NetTCPConnection -LocalPort 18081`; `Get-Process -Id 8376` | pass; branch `codex/2026-05-30-dev`, initial worktree clean, latest migration `V11`, next migration `V12`, `18081` is a local manual Java backend | implementation not started; commit id to be filled after user/integration commit |
| 2026-05-30 | T1 | Codex Backend Profile Agent | 未提交（T1 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.template.profile.TemplateProfileParserTest" --tests "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"` | pass; parser now prefers `w:rFonts/@w:eastAsia` for Chinese-compatible `fontFamily`, preserves latin font separately, and records AUTO/EXACT structured line spacing | full backend test skipped by user direction to reduce test weight |
| 2026-05-30 | T2 | Codex Backend Profile Agent | 未提交（T2 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.template.TemplateUploadServiceTest" --tests "com.gongwen.assistant.template.profile.TemplateProfileParserTest"` | pass; placeholder fixture -> `PLACEHOLDER_TEMPLATE`, notice-like no-placeholder fixture -> `STYLE_TEMPLATE`, manual/guide fixture -> `MANUAL_OR_GUIDE` with blocking warning | full backend test skipped by user direction to reduce test weight |
| 2026-05-30 | T3 | Codex Backend Structure Agent | 未提交（T3 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.documentstructure.DocumentStructureExtractorTest" --tests "com.gongwen.assistant.template.TemplateUploadServiceTest"` | pass; V12 migration added, `DocumentStructureProfile`/nodes/repository added, upload persists structure profile | full backend test skipped by user direction to reduce test weight |
| 2026-05-30 | T4 | Codex Backend Render Agent | 未提交（T4 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.rendering.DocumentRenderPreviewServiceTest" --tests "com.gongwen.assistant.rendering.LibreOfficeRenderClientTest"` | pass; V13 migration added, render preview status/page APIs added, storage paths constrained under preview root, LibreOffice command construction covered | local LibreOffice binary execution mocked/not run to keep this pass lightweight and environment independent |
| 2026-05-30 | T5 | Codex Frontend Template Agent | 未提交（T5 实现） | `npm test -- src/App.test.tsx`; `npm run build`; `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.template.TemplateControllerTest" --tests "com.gongwen.assistant.template.TemplateUploadControllerTest"` | pass; template parse dialog is now a read-only workspace with document kind, structure tree, risks, and render preview status; thin backend structure/document-kind read APIs added | first attempted `npm test -- --run src/App.test.tsx` failed because project script already includes `--run`; reran corrected command; browser screenshot not captured because Browser tool was unavailable |
| 2026-05-30 | T6 | Codex Backend Mapping Agent | 未提交（T6 实现） | `.\gradlew.bat --no-daemon compileTestJava`; `.\gradlew.bat --no-daemon test --tests "com.gongwen.assistant.documentstructure.mapping.StructureMappingServiceTest" --tests "com.gongwen.assistant.documentstructure.mapping.StructureMappingControllerTest"` | pass; V14 migration added, GET mapping/default suggestions, PUT draft save, POST publish, node-key validation, template-admin permission, and publish blockers implemented | richer slot requirements beyond `TITLE`/`BODY`, mapping audit table, and published mapping consumers deferred |
| 2026-05-30 | T7 | Codex Frontend Template Agent | 未提交（T7 实现） | `npm test -- src/App.test.tsx`; `npm run build`; `Invoke-WebRequest http://localhost:5173` | pass; parse workspace can edit node roles, save mapping draft, publish mapping, and show backend blockers | Browser screenshot not captured because Browser tool was unavailable; local Vite responded 200 |
| 2026-05-30 | T8 | Codex Backend Draft Agent | 未提交（T8 实现） | `.\gradlew.bat --no-daemon --console=plain compileTestJava`; `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest" --tests "com.gongwen.assistant.draft.node.DraftNodeControllerTest"` | pass; V15 migration added, `draft.node` package added, initialize/list/update node APIs added, service checks draft access through existing `DraftService` | plan script failed before Gradle because `.tools\gradle-8.10.2\bin\gradle.bat` is missing; used wrapper with `C:\Users\10172\.jdks\ms-21.0.11` instead |
| 2026-05-30 | T9 | Codex Frontend Workbench Agent | 未提交（T9 实现） | `npm test -- src/workbenchNodes.test.ts src/App.test.tsx`; `npm run build` | pass; workbench now loads/initializes draft nodes, prefers backend `DraftNode` records in `WorkbenchNode`, shows a left structure tree with status badges, edits selected node content in the center pane, and saves dirty nodes before legacy blocks | initial command `npm test -- --run src/workbenchNodes.test.ts src/App.test.tsx` failed because the script already includes `--run`; corrected command passed; browser screenshot not captured because Browser tool was unavailable |
| 2026-05-31 | T10 | Codex Backend AI Agent | 未提交（T10 实现） | `.\gradlew.bat --no-daemon --console=plain compileTestJava`; `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.PromptBuilderTest" --tests "com.gongwen.assistant.ai.AiOutlineServiceTest" --tests "com.gongwen.assistant.ai.AiParagraphServiceTest" --tests "com.gongwen.assistant.ai.AiLocalOperationServiceTest"` | pass; AI requests now accept optional node metadata, outline returns node-level suggestions without applying changes, paragraph generation can write a target `DraftNode`, and local operation targets nodes before legacy blocks | first focused run exposed a null-node compatibility bug in old paragraph requests; fixed and reran passing; full backend test skipped per lightweight-test instruction |
| 2026-05-31 | T11 | Codex Frontend AI Agent | 未提交（T11 实现） | `npm test -- src/App.test.tsx`; `npm run build` | pass; right-panel AI context now follows the selected workbench node, local operation requests send `nodeId/nodeRole/nodeTitle/nodeContext` for persisted nodes, and legacy `targetBlockId` paragraph fallback remains compatible | visual browser screenshot not captured because Browser/node_repl tooling was unavailable; full frontend suite beyond `App.test.tsx` skipped per lightweight-test instruction |
| 2026-05-31 | T12 | Codex Backend Format Agent | 未提交（T12 实现） | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeFormatOverrideTest" --tests "com.gongwen.assistant.draft.node.DraftNodeControllerTest" --tests "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"` | pass; draft nodes can save and clear local format overrides, endpoints enforce draft access through `DraftService`, and formatting merge priority is covered in `TemplateEffectiveFormattingServiceTest` | first RED run failed on missing service/merge/repository methods as expected; full backend suite skipped per lightweight-test instruction |

## Agent File Boundaries

Integration Agent:

- Owns this plan, `docs/PROJECT_TASKS.md`, `AGENTS.md`, final diff review, and cross-agent API contract alignment.
- Must not make large feature edits while integration reviewing another agent's work.

Backend Profile/Structure/Mapping Agents:

- Primary files:
  - `backend/src/main/java/com/gongwen/assistant/template/profile/**`
  - `backend/src/main/java/com/gongwen/assistant/template/**`
  - new `backend/src/main/java/com/gongwen/assistant/documentstructure/**`
  - related backend tests under `backend/src/test/java/**`
- Avoid editing `frontend/src/App.tsx`.

Backend Render Agent:

- Primary files:
  - new `backend/src/main/java/com/gongwen/assistant/rendering/**`
  - new migrations for render preview records
  - `.env.example`
  - render-related tests
- Must isolate local filesystem paths under configured storage directories.

Backend Draft/AI/Export Agents:

- Primary files:
  - `backend/src/main/java/com/gongwen/assistant/draft/**`
  - `backend/src/main/java/com/gongwen/assistant/ai/**`
  - `backend/src/main/java/com/gongwen/assistant/quality/**`
  - `backend/src/main/java/com/gongwen/assistant/exporting/**`
  - related backend tests
- Coordinate before changing shared DTOs consumed by frontend.

Frontend Template/Workbench/AI/Format/Export Agents:

- Primary files:
  - `frontend/src/App.tsx`
  - `frontend/src/api.ts`
  - `frontend/src/draftTypes.ts`
  - `frontend/src/workbenchNodes.ts`
  - `frontend/src/workbenchNodes.test.ts`
  - `frontend/src/styles/app.css`
  - shared UI components only when genuinely reusable
- Must reuse global UI components and `DESIGN.md` tokens.
- Coordinate before modifying the same `App.tsx` regions.

QA/Docs Agent:

- Primary files:
  - tests and fixtures
  - `docs/**`
  - `AGENTS.md`
  - `.env.example`
- Does not refactor production code unless fixing a verified test failure.

## T0 Frozen Baseline

- Branch at T0: `codex/2026-05-30-dev`.
- Initial `git status --short --branch`: clean output with only `## codex/2026-05-30-dev`.
- Workspace mode: normal checkout, not a linked git worktree; T0 works in place because it only freezes documentation contracts.
- Existing migrations: `V1` through `V11`; latest migration is `V11__export_record_traceability.sql`.
- Frozen migration sequence for P10D:
  - T3: `V12__document_structure_profile.sql`
  - T4: `V13__document_render_preview.sql`
  - T6: `V14__structure_mapping_profile.sql`
  - T8: `V15__draft_node_foundation.sql`
  - T14, if needed: `V16__export_node_traceability.sql`
- Port `18081`: a local Java process is listening with `--server.port=18081`; treat it as manual verification only. Automated verification must use the commands in this plan and must not assume that server exists or is fresh.
- T0 does not implement feature code, does not create migrations, and does not change runtime behavior.

## T0 Frozen API Boundaries

Existing APIs stay compatible during P10D:

- `POST /api/templates`
- `POST /api/templates/{templateId}/versions`
- `GET /api/templates/versions/{versionId}/profile`
- `PUT /api/drafts/{id}/template-version`
- `POST /api/drafts/{draftId}/quality-check`
- `POST /api/exports/drafts/{draftId}/word`

New P10D API groups are frozen at boundary level before implementation:

- Structure read:
  - `GET /api/templates/versions/{versionId}/structure-profile`
  - `GET /api/templates/versions/{versionId}/document-kind`
- Render preview:
  - `GET /api/templates/versions/{versionId}/render-preview`
  - `POST /api/templates/versions/{versionId}/render-preview`
  - `GET /api/render-previews/{previewId}/pages/{pageIndex}`
- Structure mapping:
  - `GET /api/templates/versions/{versionId}/structure-mapping`
  - `PUT /api/templates/versions/{versionId}/structure-mapping/draft`
  - `POST /api/templates/versions/{versionId}/structure-mapping/publish`
- Draft nodes:
  - `POST /api/drafts/{draftId}/nodes/initialize`
  - `GET /api/drafts/{draftId}/nodes`
  - `PUT /api/drafts/{draftId}/nodes/{nodeId}`
  - `PUT /api/drafts/{draftId}/nodes/{nodeId}/format`
  - `DELETE /api/drafts/{draftId}/nodes/{nodeId}/format`

AI, quality, and export boundaries:

- Existing AI endpoints remain in place; later tasks add optional `nodeId`, `nodeRole`, `nodeTitle`, and `nodeContext` fields without removing `DraftBlock` fallbacks.
- Quality check keeps the current endpoint and extends result items with optional `nodeKey`, `nodeId`, and `slotKey`.
- Draft export keeps `POST /api/exports/drafts/{draftId}/word`; P10D adds mapping/node/format traceability and stable blocker objects: `code`, `message`, optional `nodeKey`, optional `nodeLabel`, and `blockers[]`.

Permission boundaries:

- Structure/profile read follows existing template/version visibility.
- Mapping save/publish requires template administrator or system administrator permission.
- Draft node read/write requires existing current-user draft access.
- Render preview page download requires access to the template version or draft context that owns the preview.

## T0 Frozen Agent Dispatch

- P10D-A Backend Profile Agent: T1 then T2. Owns eastAsia/effective formatting and document-kind hardening.
- P10D-B Backend Structure Agent: T3 after T1/T2. Uses `V12`.
- P10D-C Backend Render Agent: T4 can start after T0 using the frozen `V13`, but must not reuse `V12`.
- P10D-D Backend Mapping Agent: T6 after T3. Uses `V14`.
- P10D-E Frontend Template Agent: T5 after T3/T4 API contracts; T7 after T5/T6.
- P10D-F Backend Draft/AI/Format/Export Agents: T8-T15 in dependency order, using frozen migrations where listed.
- P10D-QA Docs Agent: T16 after T1-T15, then Integration Agent closes T17.
- Parallel agents must not edit the same `frontend/src/App.tsx` region without an integration handoff.
- Every completed task must update this plan's `Progress Board` and `Progress Log` in the same change as its implementation.

## Shared Contracts To Freeze In T0

### Document Kind

```text
PLACEHOLDER_TEMPLATE
STYLE_TEMPLATE
REFERENCE_DOCUMENT
OFFICIAL_DOCUMENT
MANUAL_OR_GUIDE
POLICY_OR_REGULATION
ORDINARY_DOCUMENT
MIXED_DOCUMENT
UNKNOWN_DOCUMENT
```

### Node Roles

```text
ISSUING_ORGAN
RED_HEADER
DOC_NUMBER
SIGNER
TITLE
RECIPIENT
BODY
BODY_HEADING_LEVEL_1
BODY_HEADING_LEVEL_2
BODY_HEADING_LEVEL_3
ATTACHMENT_NOTE
ATTACHMENT_CONTENT
SIGNATURE
DATE
COPY_TO
PRINT_ORGAN
PRINT_DATE
PAGE_NUMBER
SEAL_OR_IMAGE
TABLE_ATTACHMENT
STATIC_TEXT
IGNORE
UNKNOWN
```

`STATIC_TEXT` is the T0 frozen extension for template-managed fixed content. `TABLE_ATTACHMENT` is included to match the design spec's official-document role list.

### Draft Node Status

```text
EMPTY
USER_FILLED
AI_GENERATED
USER_MODIFIED_AFTER_AI
NEEDS_REVIEW
QUALITY_WARNING
QUALITY_ERROR
EXPORT_BLOCKED
FORMAT_OVERRIDDEN
LOCKED
```

### Mapping Profile Status

```text
DRAFT
PUBLISHED
ARCHIVED
```

### Mapping Item Status

```text
SUGGESTED
CONFIRMED
IGNORED
NEEDS_REVIEW
```

### Suggestion Source

```text
RULE
AI
USER
IMPORT
SYSTEM
```

### Risk Severity

```text
INFO
WARNING
BLOCKING
```

### Render Job Status

```text
PENDING
RENDERING
READY
FAILED
UNSUPPORTED
```

### Workbench Render Preview Status

```text
CURRENT
OUTDATED
RENDERING
FAILED
UNAVAILABLE
```

### Format Merge Priority

```text
DraftNodeFormatOverride
  > StructureMapping formattingOverride
  > DocumentStructureProfile effectiveFormatting
  > document type default formatting
  > system default formatting
```

## Validation Commands

Backend focused:

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateProfileParserTest"
```

Backend full:

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test.ps1
```

Frontend focused:

```powershell
cd E:\gongwen\frontend
npm test -- src/workbenchNodes.test.ts
```

Frontend full/build:

```powershell
cd E:\gongwen\frontend
npm test --
npm run build
```

Database:

```powershell
cd E:\gongwen
docker compose up -d postgres
docker compose ps
docker exec gongwen-postgres pg_isready -U gongwen -d gongwen
```

## Task T0: Freeze Contracts And Baseline

**Preferred Agent:** Integration Agent

**Files:**

- Modify: `docs/superpowers/plans/2026-05-30-docx-structure-workbench.md`
- Modify when needed: `docs/PROJECT_TASKS.md`
- Modify when needed: `AGENTS.md`

**Dependencies:** none

- [x] Confirm branch and cleanliness with `git status --short --branch`.
- [x] Read `AGENTS.md`, `DESIGN.md`, `docs/PROJECT_TASKS.md`, and `docs/superpowers/specs/2026-05-30-docx-preview-structure-mapping-design.md`.
- [x] Confirm the shared enums in this plan match the design spec.
- [x] Confirm next migration number by listing `backend/src/main/resources/db/migration`.
- [x] Record the next migration number in this task before backend agents start.
- [x] Confirm whether existing server on `18081` is only for manual verification and should not be treated as part of tests.
- [x] Update Progress Board row T0 with commit, verification, and risk.

**Verification:**

```powershell
cd E:\gongwen
git status --short --branch
Get-ChildItem backend/src/main/resources/db/migration | Select-Object Name
```

**Completion note required:** migration number chosen, branch name, whether worktree was clean.

T0 completion note:

- Branch: `codex/2026-05-30-dev`.
- Initial worktree: clean; `git status --short --branch` returned only `## codex/2026-05-30-dev`.
- Migration number chosen: next migration is `V12`; reserved sequence is `V12` T3, `V13` T4, `V14` T6, `V15` T8, `V16` T14 if needed.
- `18081`: local Java backend is listening and is for manual verification only, not part of automated test prerequisites.

## Task T1: Fix Chinese Font And Effective Formatting Baseline

**Preferred Agent:** Backend Profile Agent

**Files:**

- Modify: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileParser.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateStructureFormattingProfile.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateEffectiveFormattingService.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateProfileParserTest.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateEffectiveFormattingServiceTest.java`
- Modify when needed: `backend/src/test/java/com/gongwen/assistant/support/DocxTestFactory.java`

**Dependencies:** T0

- [x] Add or update a failing test proving Chinese text prefers OOXML `w:rFonts/@w:eastAsia` over `Times New Roman`.
- [x] Add or update a failing test proving latin text still reports `ascii` or `hAnsi` separately.
- [x] Add structured line spacing fields that distinguish exact point value from multiple/auto spacing.
- [x] Implement parser changes without removing existing `fontFamily` compatibility fields.
- [x] Update effective formatting merge so structure defaults and overrides keep eastAsia and latin fonts separate.
- [x] Run focused profile tests.
- [x] Update Progress Board row T1 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateProfileParserTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"
```

**Completion note required:** include before/after behavior for the manual file symptom: Chinese paragraphs no longer display as `Times New Roman` when eastAsia is present.

T1 completion note:

- Before: formatting read only `run.getFontFamily()`, so a run with latin `Times New Roman` and eastAsia `FangSong` could surface the latin font as the single family.
- After: parser stores `eastAsiaFontFamily` and `latinFontFamily`; compatibility `fontFamily` prefers eastAsia when present, so Chinese paragraphs no longer display as `Times New Roman` when `w:eastAsia` is available.
- Structured line spacing now records `TemplateLineSpacingProfile(mode, valueTwips, multipleHundred)` while keeping legacy `spacingBetween` for AUTO/multiple line spacing.

## Task T2: Harden Document Kind Analysis

**Preferred Agent:** Backend Profile Agent

**Files:**

- Modify: `backend/src/main/java/com/gongwen/assistant/template/TemplateIntelligenceService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateAnalysisProfile.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/template/TemplateUploadServiceTest.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateProfileParserTest.java`
- Create when useful: `backend/src/test/resources/docx-fixtures/manual-or-guide/`
- Create when useful: `backend/src/test/resources/docx-fixtures/placeholder-template/`

**Dependencies:** T1

- [x] Add test fixtures or generated test documents covering a placeholder template, a no-placeholder official-document example, and a manual/guide-like document.
- [x] Add a failing test where text such as `标题：方正小标宋简体` is not treated as the actual official-document title slot.
- [x] Ensure manual/guide documents return `MANUAL_OR_GUIDE` or `ORDINARY_DOCUMENT` with a blocking warning for auto template usage.
- [x] Ensure placeholder templates still return `PLACEHOLDER_TEMPLATE`.
- [x] Ensure document kind analysis includes reason codes and recommended workflow.
- [x] Run focused template upload/profile tests.
- [x] Update Progress Board row T2 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateProfileParserTest"
```

**Completion note required:** list the three fixture categories tested and their detected `documentKind`.

T2 completion note:

- Placeholder template fixture: generated `docxWithOfficialStyles()` with explicit `{{标题}}`/`{{正文}}`; detected `documentKind=PLACEHOLDER_TEMPLATE`.
- No-placeholder notice-like fixture: generated title/body paragraphs; detected `documentKind=STYLE_TEMPLATE` with workflow `REVIEW_AND_ADD_PLACEHOLDERS`.
- Manual/guide fixture: generated `docxWithManualGuideLikeDocument()` containing `标题：方正小标宋简体` and `正文：方正仿宋...`; detected `documentKind=MANUAL_OR_GUIDE`, workflow `BLOCK_AUTO_TEMPLATE`, blocking warning present.

## Task T3: Add DocumentStructureProfile Foundation

**Preferred Agent:** Backend Structure Agent

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentStructureProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentNode.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentNodeFormatting.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentStructureExtractor.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentStructureProfileRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/JdbcDocumentStructureProfileRepository.java`
- Create: `backend/src/main/resources/db/migration/V12__document_structure_profile.sql`.
- Create/Test: `backend/src/test/java/com/gongwen/assistant/documentstructure/DocumentStructureExtractorTest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/template/TemplateUploadService.java`

**Dependencies:** T1, T2

- [x] Create the migration with `document_structure_profile` table keyed by template version.
- [x] Define `DocumentStructureProfile` JSON fields: version, source file, nodes, styles, sections, risks, createdAt.
- [x] Define `DocumentNode` fields: `nodeKey`, `parentKey`, `nodeType`, `roleSuggestion`, `text`, `textPreview`, `orderIndex`, `path`, `formatting`, `riskCodes`.
- [x] Implement extractor from existing parsed DOCX/profile data first; do not try to cover all OOXML structures in this task.
- [x] Persist structure profile during template upload.
- [x] Add repository round-trip test for JSON persistence.
- [x] Run focused document structure tests.
- [x] Update Progress Board row T3 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.documentstructure.DocumentStructureExtractorTest"
```

**Completion note required:** number of node types supported in first pass and known unsupported structures.

T3 completion note:

- First pass node types supported: `PARAGRAPH`, `TABLE_PARAGRAPH`, `HEADER_PARAGRAPH`, `FOOTER_PARAGRAPH`.
- Structure source: derived from existing `TemplateProfile.structures`; this intentionally avoids a broad OOXML rewrite in T3.
- Known unsupported structures: numbering definitions, textbox/shape content, drawing anchors, comments/revisions, footnotes/endnotes, exact PDF/page coordinates, and full style inheritance beyond the T1 formatting baseline.

## Task T4: Add LibreOffice Render Preview Backend

**Preferred Agent:** Backend Render Agent

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/rendering/DocumentRenderPreview.java`
- Create: `backend/src/main/java/com/gongwen/assistant/rendering/DocumentRenderPreviewStatus.java`
- Create: `backend/src/main/java/com/gongwen/assistant/rendering/DocumentRenderPreviewRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/rendering/JdbcDocumentRenderPreviewRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/rendering/DocumentRenderPreviewService.java`
- Create: `backend/src/main/java/com/gongwen/assistant/rendering/LibreOfficeRenderClient.java`
- Create: `backend/src/main/java/com/gongwen/assistant/rendering/RenderPreviewProperties.java`
- Create: `backend/src/main/java/com/gongwen/assistant/rendering/DocumentRenderPreviewController.java`
- Create: `backend/src/main/resources/db/migration/V13__document_render_preview.sql`.
- Create/Test: `backend/src/test/java/com/gongwen/assistant/rendering/DocumentRenderPreviewServiceTest.java`
- Modify: `.env.example`
- Modify: `AGENTS.md` when env vars are added.

**Dependencies:** T0

- [x] Add environment variables for storage dir, renderer, LibreOffice path, DPI, timeout.
- [x] Add `document_render_preview` table with template version, status, page count, storage path, error summary.
- [x] Implement render service as asynchronous-capable service, but allow synchronous test seam.
- [x] Implement `LibreOfficeRenderClient` with timeout and command construction tests.
- [x] Ensure output paths stay under configured preview storage directory.
- [x] Add API to get preview status and page metadata.
- [x] Add API to download one preview page.
- [x] Run focused render tests.
- [x] Update Progress Board row T4 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.rendering.DocumentRenderPreviewServiceTest"
```

**Completion note required:** whether local LibreOffice execution was actually tested or mocked, with reason.

T4 completion note:

- Local LibreOffice execution was not run in this pass; it is intentionally covered through a synchronous renderer seam and command-construction test so development is not blocked by host LibreOffice availability.
- Implemented APIs: `GET/POST /api/templates/versions/{versionId}/render-preview` and `GET /api/render-previews/{previewId}/pages/{pageNumber}`.
- Render output is stored under `GONGWEN_RENDER_PREVIEW_STORAGE_DIR` and page downloads validate both preview directory and storage root containment.

## Task T5: Template Parse Workspace Read-Only UI

**Preferred Agent:** Frontend Template Agent

**Files:**

- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Modify/Test: `frontend/src/App.test.tsx`

**Dependencies:** T3, T4

- [x] Add frontend types for document structure profile, node, render preview, and document kind.
- [x] Add API client methods for structure overview and render preview status/page URLs.
- [x] Replace the current parse result modal content with read-only sections for document kind, structure tree, risks, and preview status.
- [x] Show manual/ordinary document warning as non-template flow, not as parse failure.
- [x] Show loading, failed render, unavailable render, and no-permission states.
- [x] Keep visual style aligned with existing global components and `DESIGN.md`.
- [x] Add frontend test for manual document warning and render-preview loading state.
- [x] Run frontend focused/full build checks.
- [x] Update Progress Board row T5 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- src/App.test.tsx
npm run build
```

**Completion note required:** screenshot or browser verification note for desktop parse workspace.

T5 completion note:

- Browser screenshot was not captured because no Browser control tool was exposed in this session; local Vite was already listening on `5173`, and `Invoke-WebRequest http://localhost:5173` returned `200`.
- Desktop parse workspace behavior is covered by `App.test.tsx`: manual/guide documents show a non-template warning, and render-preview loading transitions into `RENDERING`.
- The modal is now read-only for T5; editable mapping/format publishing is deferred to T6/T7.

## Task T6: Structure Mapping Save And Publish Backend

**Preferred Agent:** Backend Mapping Agent

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingItem.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/JdbcStructureMappingRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingService.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingController.java`
- Create: `backend/src/main/resources/db/migration/V14__structure_mapping_profile.sql`.
- Create/Test: `backend/src/test/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingServiceTest.java`
- Create/Test: `backend/src/test/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingControllerTest.java`

**Dependencies:** T3

- [x] Add `structure_mapping_profile` and optional audit table.
- [x] Implement save draft mapping endpoint.
- [x] Implement publish mapping endpoint.
- [x] Validate node keys exist in the corresponding `DocumentStructureProfile`.
- [x] Validate required slots before publish for template-like documents.
- [x] Block publish for `MANUAL_OR_GUIDE` and `ORDINARY_DOCUMENT` unless an explicit admin override flag exists in request.
- [x] Add permission checks consistent with template administrator/system administrator behavior.
- [x] Run focused mapping tests.
- [x] Update Progress Board row T6 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.documentstructure.mapping.StructureMappingServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.documentstructure.mapping.StructureMappingControllerTest"
```

**Completion note required:** list publish blockers and successful publish response shape.

T6 completion note:

- Publish blockers: `DOCUMENT_KIND_BLOCKED` for `MANUAL_OR_GUIDE`, `POLICY_OR_REGULATION`, and `ORDINARY_DOCUMENT` unless `adminOverride=true`; `REQUIRED_SLOT_MISSING` when confirmed `TITLE` or `BODY` is missing.
- Draft save validation errors: `STRUCTURE_MAPPING_NODE_NOT_FOUND`, `STRUCTURE_MAPPING_ROLE_INVALID`, `STRUCTURE_MAPPING_STATUS_INVALID`, `STRUCTURE_MAPPING_SOURCE_INVALID`.
- Successful publish response shape is `StructureMappingProfile`: `mappingProfileId`, `templateVersionId`, `versionNo`, `status=PUBLISHED`, `items`, `validationItems=[]`, `confirmedCount`, `needsReviewCount`, `publishedAt`, `createdAt`, `updatedAt`.
- Audit table was deferred; `structure_mapping_profile` records immutable draft/published versions for this slice.

## Task T7: Mapping Editor UI And Publish Flow

**Preferred Agent:** Frontend Template Agent

**Files:**

- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Modify/Test: `frontend/src/App.test.tsx`

**Dependencies:** T5, T6

- [x] Add frontend types for mapping profile, mapping item, mapping status, and publish response.
- [x] Add API client methods for load mapping, save mapping draft, publish mapping.
- [x] In template parse workspace, allow selecting a structure node and assigning role/slot.
- [x] Add role controls for title, recipient, body, attachment, signature, date, ignore, static text.
- [x] Show mapping validation errors from backend.
- [x] Add publish button with loading, success, blocked, and permission-denied states.
- [x] Add test for mapping a node and seeing publish blocked when required slots are missing.
- [x] Run frontend test/build.
- [x] Update Progress Board row T7 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- src/App.test.tsx
npm run build
```

**Completion note required:** describe which roles are editable in UI and which blocked states are visible.

T7 completion note:

- Editable UI roles: `UNKNOWN`, `TITLE`, `RECIPIENT`, `BODY`, `BODY_HEADING_LEVEL_1`, `ATTACHMENT_NOTE`, `SIGNATURE`, `DATE`, `STATIC_TEXT`, `IGNORE`.
- Visible blocked/error states: mapping API unavailable, draft save failure, publish failure, backend publish blockers via `validationItems`, including missing required slots.
- The UI saves draft mapping before publishing; bulk role assignment, richer role taxonomy, and admin override publishing are deferred.

## Task T8: Persist DraftNode Backend

**Preferred Agent:** Backend Draft Agent

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNode.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeDto.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeFormatOverride.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/JdbcDraftNodeRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeService.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeController.java`
- Create: `backend/src/main/resources/db/migration/V15__draft_node_foundation.sql`.
- Create/Test: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeServiceTest.java`
- Create/Test: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeControllerTest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/draft/DraftService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/draft/DraftDetailDto.java`

**Dependencies:** T6

- [x] Add `draft_node` table while keeping `draft_block` compatibility.
- [x] Add endpoint to initialize nodes for a draft from published mapping.
- [x] Add endpoint to list nodes for a draft.
- [x] Add endpoint to save node content and status.
- [x] Enforce current user draft access checks.
- [x] Preserve existing draft block APIs during transition.
- [x] Add tests for create/list/save and unauthorized draft access.
- [x] Run focused draft node tests.
- [x] Update Progress Board row T8 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.draft.node.DraftNodeServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.draft.node.DraftNodeControllerTest"
```

**Completion note required:** explain how `DraftBlock` compatibility is preserved.

T8 completion note:

- `draft_block` compatibility is preserved by leaving the existing `DraftService`, `DraftController`, `PUT /api/drafts/{id}/blocks`, and `DraftDetailDto.blocks` behavior intact; T8 adds separate `/api/drafts/{draftId}/nodes` endpoints and stores nodes in `draft_node`.
- `DraftDetailDto` now includes a backward-compatible `nodes` list that defaults to empty for existing constructors and repository reads, so current frontend and AI/quality/export callers can continue using `blocks` until T9+ opt into backend nodes.
- Node initialization is idempotent: if a draft already has nodes, the service returns them instead of duplicating rows; otherwise it requires a bound template version, a published structure mapping, and a structure profile.
- Initial node content is seeded from legacy draft blocks where possible (`TITLE`, `BODY_PARAGRAPH`, `DATE`, etc.) and falls back to mapped source text for static/body-heading nodes.

## Task T9: Workbench Structure Tree And Node Editor UI

**Preferred Agent:** Frontend Workbench Agent

**Files:**

- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/workbenchNodes.ts`
- Modify/Test: `frontend/src/workbenchNodes.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Modify/Test: `frontend/src/App.test.tsx`

**Dependencies:** T8

- [x] Add API methods for initialize/list/save draft nodes.
- [x] Update `WorkbenchNode` adapter to prefer backend `DraftNode` records.
- [x] Keep fallback to existing `DraftBlock` data when nodes are absent.
- [x] Render left structure tree with node status badges.
- [x] Render center structured editor blocks from selected nodes.
- [x] Keep selected node synchronized across left tree, center editor, and right panel.
- [x] Mark quality and render preview states stale after node edits.
- [x] Add tests for selecting a node and editing content.
- [x] Run focused frontend tests and build.
- [x] Update Progress Board row T9 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- src/workbenchNodes.test.ts src/App.test.tsx
npm run build
```

**Completion note required:** list fallback behavior when backend nodes are missing.

T9 completion note:

- Workbench loading first uses `DraftDetail.nodes` or `GET /api/drafts/{draftId}/nodes` when records exist.
- If a draft has a bound template version but no persisted nodes, the frontend calls `POST /api/drafts/{draftId}/nodes/initialize` and uses the returned nodes.
- If node APIs return no usable data or initialization is unavailable, `deriveWorkbenchNodes` falls back to template `structures` plus existing `DraftBlock` data, preserving the pre-T9 editor path.
- Saving now persists dirty `DraftNode` records first, then still materializes compatible `DraftBlock` payloads so current AI, quality, and export flows keep working until T10/T14 consume nodes directly.
- Node edits mark existing quality/export/local AI state stale; true render-preview refresh remains a later T15 UI flow.

## Task T10: Node-Aware AI Backend

**Preferred Agent:** Backend AI Agent

**Files:**

- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineRequest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiParagraphRequest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiParagraphService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationRequest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/PromptBuilder.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/ai/AiOutlineServiceTest.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/ai/AiParagraphServiceTest.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/ai/AiLocalOperationServiceTest.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/ai/PromptBuilderTest.java`

**Dependencies:** T8

- [x] Add optional `nodeId`, `nodeRole`, `nodeTitle`, and `nodeContext` request fields.
- [x] Make paragraph generation write suggestions for a target node without changing node role.
- [x] Make local operation target a node first, and keep existing `targetBlockId` fallback.
- [x] Ensure global outline returns node-level creation/update suggestions before applying changes.
- [x] Ensure AI trace stores node ids and summaries, not complete sensitive content.
- [x] Add tests proving AI cannot implicitly change title/recipient/signature/date nodes.
- [x] Run focused AI tests.
- [x] Update Progress Board row T10 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
cd backend
.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.PromptBuilderTest" --tests "com.gongwen.assistant.ai.AiOutlineServiceTest" --tests "com.gongwen.assistant.ai.AiParagraphServiceTest" --tests "com.gongwen.assistant.ai.AiLocalOperationServiceTest"
```

**Completion note required:** state exactly which AI operations are node-aware and which still use fallback.

T10 completion note:

- Node-aware now: outline, paragraph generation, and local operation request contracts all accept optional `nodeId`, `nodeRole`, `nodeTitle`, and `nodeContext`.
- Outline remains suggestion-only: it adds `nodeSuggestions` with `CREATE` suggestions for new body sections or an `UPDATE` suggestion for the selected node; it does not write draft blocks or draft nodes.
- Paragraph generation is node-aware when `nodeId` targets a body node: it uses node metadata in the prompt, saves generated content back to that `DraftNode` with status `AI_GENERATED`, and still writes a compatible `BODY_PARAGRAPH` block for current UI/quality/export fallback.
- Paragraph generation rejects protected target roles `TITLE`, `RECIPIENT`, `SIGNATURE`, and `DATE`, and rejects non-body node roles, so AI cannot implicitly rewrite those structural slots through paragraph generation.
- Local operation targets `DraftNode` first when `nodeId` is provided, uses that node content as the original text, returns suggestion text with `targetNodeId`, and does not save the node automatically; if no node is provided it keeps the existing `targetBlockId` fallback.
- AI trace input/output summaries include ids, roles, counts, and character counts, but not full node content, full material text, or full suggestions.

## Task T11: Right Panel AI Context UI

**Preferred Agent:** Frontend AI Agent

**Files:**

- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Modify/Test: `frontend/src/App.test.tsx`

**Dependencies:** T9, T10

- [x] Route generate paragraph and local operation requests with selected node metadata.
- [x] Show global actions when no node is selected.
- [x] Show title actions for title node.
- [x] Show body actions for body node.
- [x] Show attachment/signature/date checks for those node roles.
- [x] Keep existing right-panel quality and export functions visible.
- [x] Disable node-specific AI actions for locked or unsupported nodes.
- [x] Add tests for right panel changing available actions when selected node changes.
- [x] Run frontend tests and build.
- [x] Update Progress Board row T11 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- src/App.test.tsx
npm run build
```

**Completion note required:** list node roles with role-specific right-panel actions.

T11 completion note:

- No selected node: right panel keeps global outline, quality check, and Word export visible; node-specific action is disabled with guidance to use the global actions above.
- `TITLE`: shows "标题节点" and enables "生成标题建议" for persisted draft nodes, sending `nodeId`, `nodeRole`, `nodeTitle`, and `nodeContext`.
- `BODY_SECTION`: shows "正文节点"; persisted draft nodes use "生成正文建议" with node metadata, while legacy block-only body sections keep the old "生成段落建议" and `targetBlockId` fallback.
- `RECIPIENT` and `ATTACHMENT`: use node-local operation actions when backed by persisted draft nodes; unsupported or unsaved targets remain disabled instead of silently falling back to unsafe rewrites.
- `SIGNATURE` and `DATE`: show role-specific node context and route the primary action to quality confirmation instead of direct AI rewriting.
- `STATIC_TEMPLATE_TEXT`, `HEADER`, `FOOTER`, locked nodes, or unsupported nodes: node-specific AI action stays disabled.

## Task T12: DraftNode Format Override Backend

**Preferred Agent:** Backend Format Agent

**Files:**

- Modify: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeFormatOverride.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeController.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateEffectiveFormattingService.java`
- Create/Test: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeFormatOverrideTest.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateEffectiveFormattingServiceTest.java`

**Dependencies:** T8

- [x] Add save format override endpoint for a draft node.
- [x] Support eastAsia font, latin font, size, bold, alignment, indent, line spacing, before/after spacing.
- [x] Add restore-template-default endpoint that clears draft node override.
- [x] Implement formatting merge priority exactly as defined in this plan.
- [x] Enforce draft access permissions.
- [x] Add tests for merge priority and restore default.
- [x] Run focused format tests.
- [x] Update Progress Board row T12 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
cd backend
.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeFormatOverrideTest" --tests "com.gongwen.assistant.draft.node.DraftNodeControllerTest" --tests "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"
```

**Completion note required:** include the exact merge order verified by tests.

T12 completion note:

- Added `PUT /api/drafts/{draftId}/nodes/{nodeId}/format-override` to save draft-local node formatting.
- Added `DELETE /api/drafts/{draftId}/nodes/{nodeId}/format-override` to clear the draft-local override and restore template/default formatting for that node.
- Supported override fields: `eastAsiaFont`, `latinFont`, `fontSizePt`, `bold`, `alignment`, `firstLineIndentTwip`, `lineSpacingRule`, `lineSpacingTwip`, `spacingBeforeTwip`, and `spacingAfterTwip`.
- Exact merge order verified by tests: `DraftNodeFormatOverride` > structure mapping/template override > original DOCX effective formatting > document type default formatting > system default formatting.
- Saving override marks the node `FORMAT_OVERRIDDEN`; restoring clears `formatOverride` and returns to `USER_FILLED` or `EMPTY` based on node content while preserving `LOCKED`.

## Task T13: Node-Level Font And Format Panel UI

**Preferred Agent:** Frontend Format Agent

**Files:**

- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Modify/Test: `frontend/src/App.test.tsx`

**Dependencies:** T9, T12

- [ ] Add frontend type for node format override matching backend fields.
- [ ] Add API client methods to save override and restore template default.
- [ ] Add right-panel format section for selected node.
- [ ] Include controls for Chinese font, latin font, size, alignment, line spacing, first-line indent, before/after spacing, bold.
- [ ] Immediately update center structured editor using CSS approximation after save.
- [ ] Mark true preview status as outdated after format change.
- [ ] Disable restricted controls for locked template-critical nodes unless user has permission.
- [ ] Add tests for changing a node font and showing preview outdated state.
- [ ] Run frontend tests and build.
- [ ] Update Progress Board row T13 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- src/App.test.tsx
npm run build
```

**Completion note required:** list supported format controls and restricted controls.

## Task T14: Export Consumes Mapping, Nodes, And Format Merge

**Preferred Agent:** Backend Export Agent

**Files:**

- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/DraftWordExportService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/ExportRecord.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/JdbcExportRecordRepository.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/word/DocxTemplateRenderer.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/word/ExportFormattingContext.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/quality/QualityCheckService.java`
- Create migration when needed: `backend/src/main/resources/db/migration/V16__export_node_traceability.sql`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/exporting/DraftWordExportServiceTest.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/exporting/word/DocxTemplateRendererTest.java`
- Modify/Test: `backend/src/test/java/com/gongwen/assistant/quality/QualityCheckServiceTest.java`

**Dependencies:** T6, T8, T12

- [ ] Make export load published mapping for the draft template version.
- [ ] Make export load `DraftNode` records where available, fallback to `DraftBlock` only during transition.
- [ ] Merge draft node format overrides with template mapping/default formatting.
- [ ] Block export when mapping is missing, document kind is disallowed, required slots are empty, or quality check blocks export.
- [ ] Add export record fields for structure profile, mapping profile, formatting snapshot, and node snapshot.
- [ ] Add tests proving draft-local font/line spacing does not change template defaults.
- [ ] Run focused export and quality tests.
- [ ] Update Progress Board row T14 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.exporting.DraftWordExportServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.exporting.word.DocxTemplateRendererTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.quality.QualityCheckServiceTest"
```

**Completion note required:** list each export blocker and export record trace field.

## Task T15: Preview Refresh And Export Status UI

**Preferred Agent:** Frontend Export Agent

**Files:**

- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Modify/Test: `frontend/src/App.test.tsx`

**Dependencies:** T11, T13, T14

- [ ] Show true preview status in the workbench: current, outdated, rendering, failed, unavailable.
- [ ] Add refresh preview action that requests backend render preview regeneration when available.
- [ ] Show export blockers with node labels when backend returns node-targeted errors.
- [ ] Keep successful export download and history behavior from P11.
- [ ] Ensure editing content or format marks preview outdated.
- [ ] Add tests for outdated preview after edit and blocked export node message.
- [ ] Run frontend tests and build.
- [ ] Update Progress Board row T15 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- src/App.test.tsx
npm run build
```

**Completion note required:** include browser verification for export blocked and preview outdated states.

## Task T16: Fixture Suite, Regression Tests, And Docs Sync

**Preferred Agent:** QA Agent

**Files:**

- Create/Modify: `backend/src/test/resources/docx-fixtures/**`
- Modify: backend focused tests across profile, structure, mapping, draft, ai, export
- Modify: frontend focused tests across App/workbench nodes
- Modify: `AGENTS.md`
- Modify: `docs/PROJECT_TASKS.md`
- Modify when env changed: `.env.example`

**Dependencies:** T1-T15

- [ ] Ensure fixture categories include placeholder template, style template, reference official document, manual/guide, complex table, header/footer, missing font.
- [ ] Add or update tests that prove the manual file category does not enter auto template flow.
- [ ] Add or update tests for Chinese fonts, node AI, draft-local format override, export traceability.
- [ ] Update `AGENTS.md` with final APIs, env vars, module boundaries, and local verification notes.
- [ ] Update `docs/PROJECT_TASKS.md` with milestone statuses and next recommended tasks.
- [ ] Run backend full test if feasible.
- [ ] Run frontend test/build.
- [ ] Update Progress Board row T16 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test.ps1
cd E:\gongwen\frontend
npm test --
npm run build
```

**Completion note required:** list fixture categories and any tests not run with concrete reason.

## Task T17: Final Integration Verification And Closure

**Preferred Agent:** Integration Agent

**Files:**

- Modify: `docs/superpowers/plans/2026-05-30-docx-structure-workbench.md`
- Modify: `docs/PROJECT_TASKS.md`
- Modify: `AGENTS.md`

**Dependencies:** T1-T16

- [ ] Review `git status --short --branch`.
- [ ] Review `git diff --stat` and confirm only expected files changed.
- [ ] Check migrations are sequential and no old migration was edited.
- [ ] Check backend API contracts match frontend types.
- [ ] Run backend full test or document exact blocker.
- [ ] Run frontend test/build or document exact blocker.
- [ ] Use browser verification for workbench desktop layout and template parse workspace.
- [ ] Update every Progress Board row to final status.
- [ ] Add final Progress Log entry with full verification summary.
- [ ] Update `docs/PROJECT_TASKS.md` current state and next recommended tasks.
- [ ] Update `AGENTS.md` if the implementation changed APIs, env vars, data model, or workflow.

**Verification:**

```powershell
cd E:\gongwen
git status --short --branch
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test.ps1
cd E:\gongwen\frontend
npm test --
npm run build
```

**Completion note required:** final remaining risks and the next single recommended task.

## Execution Options

Recommended execution mode:

```text
Subagent-driven development
```

Reason:

- Backend structure/render/mapping can proceed independently after T0.
- Frontend read-only parse workspace can start after T3/T4 APIs are stable.
- DraftNode, AI, format, and export should proceed sequentially with checkpoints.
- QA/docs can run alongside late-stage tasks without changing ownership boundaries.

Batching recommendation:

```text
Batch 1: T0, T1, T2
Batch 2: T3, T4
Batch 3: T5, T6, T7
Batch 4: T8, T9
Batch 5: T10, T11
Batch 6: T12, T13
Batch 7: T14, T15
Batch 8: T16, T17
```
