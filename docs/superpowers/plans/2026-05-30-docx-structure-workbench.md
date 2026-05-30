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
| T0 | 未开始 | Integration Agent | none | frozen contracts and branch hygiene |  |  |  |
| T1 | 未开始 | Backend Profile Agent | T0 | eastAsia font and effective formatting fix |  |  |  |
| T2 | 未开始 | Backend Profile Agent | T1 | document kind classifier hardens manual/template distinction |  |  |  |
| T3 | 未开始 | Backend Structure Agent | T1, T2 | `DocumentStructureProfile` foundation |  |  |  |
| T4 | 未开始 | Backend Render Agent | T0 | LibreOffice render job backend |  |  |  |
| T5 | 未开始 | Frontend Template Agent | T3, T4 | template parse workspace with structure and preview states |  |  |  |
| T6 | 未开始 | Backend Mapping Agent | T3 | structure mapping save/publish backend |  |  |  |
| T7 | 未开始 | Frontend Template Agent | T5, T6 | mapping editor UI and publish flow |  |  |  |
| T8 | 未开始 | Backend Draft Agent | T6 | persisted `DraftNode` model and API |  |  |  |
| T9 | 未开始 | Frontend Workbench Agent | T8 | left structure tree and center structured editor bind to nodes |  |  |  |
| T10 | 未开始 | Backend AI Agent | T8 | node-aware AI operations |  |  |  |
| T11 | 未开始 | Frontend AI Agent | T9, T10 | right panel context follows selected node |  |  |  |
| T12 | 未开始 | Backend Format Agent | T8 | draft node format override backend |  |  |  |
| T13 | 未开始 | Frontend Format Agent | T9, T12 | node-level font and format panel |  |  |  |
| T14 | 未开始 | Backend Export Agent | T6, T8, T12 | export consumes mapping, nodes, and format merge |  |  |  |
| T15 | 未开始 | Frontend Export Agent | T11, T13, T14 | preview refresh and export status flow |  |  |  |
| T16 | 未开始 | QA Agent | T1-T15 | fixture suite, regression tests, and docs sync |  |  |  |
| T17 | 未开始 | Integration Agent | T1-T16 | final integration verification and progress closure |  |  |  |

## Progress Log

| Date | Task | Agent | Commit | Verification | Result | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-05-30 | Plan | Codex | plan document commit | `git diff --check` | pass | implementation not started |

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
STATIC_TEXT
IGNORE
UNKNOWN
```

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

### Render Preview Status

```text
PENDING
RENDERING
CURRENT
OUTDATED
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
npm test -- --run src/workbenchNodes.test.ts
```

Frontend full/build:

```powershell
cd E:\gongwen\frontend
npm test -- --run
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

- [ ] Confirm branch and cleanliness with `git status --short --branch`.
- [ ] Read `AGENTS.md`, `DESIGN.md`, `docs/PROJECT_TASKS.md`, and `docs/superpowers/specs/2026-05-30-docx-preview-structure-mapping-design.md`.
- [ ] Confirm the shared enums in this plan match the design spec.
- [ ] Confirm next migration number by listing `backend/src/main/resources/db/migration`.
- [ ] Record the next migration number in this task before backend agents start.
- [ ] Confirm whether existing server on `18081` is only for manual verification and should not be treated as part of tests.
- [ ] Update Progress Board row T0 with commit, verification, and risk.

**Verification:**

```powershell
cd E:\gongwen
git status --short --branch
Get-ChildItem backend/src/main/resources/db/migration | Select-Object Name
```

**Completion note required:** migration number chosen, branch name, whether worktree was clean.

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

- [ ] Add or update a failing test proving Chinese text prefers OOXML `w:rFonts/@w:eastAsia` over `Times New Roman`.
- [ ] Add or update a failing test proving latin text still reports `ascii` or `hAnsi` separately.
- [ ] Add structured line spacing fields that distinguish exact point value from multiple/auto spacing.
- [ ] Implement parser changes without removing existing `fontFamily` compatibility fields.
- [ ] Update effective formatting merge so structure defaults and overrides keep eastAsia and latin fonts separate.
- [ ] Run focused profile tests.
- [ ] Update Progress Board row T1 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateProfileParserTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"
```

**Completion note required:** include before/after behavior for the manual file symptom: Chinese paragraphs no longer display as `Times New Roman` when eastAsia is present.

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

- [ ] Add test fixtures or generated test documents covering a placeholder template, a no-placeholder official-document example, and a manual/guide-like document.
- [ ] Add a failing test where text such as `标题：方正小标宋简体` is not treated as the actual official-document title slot.
- [ ] Ensure manual/guide documents return `MANUAL_OR_GUIDE` or `ORDINARY_DOCUMENT` with a blocking warning for auto template usage.
- [ ] Ensure placeholder templates still return `PLACEHOLDER_TEMPLATE`.
- [ ] Ensure document kind analysis includes reason codes and recommended workflow.
- [ ] Run focused template upload/profile tests.
- [ ] Update Progress Board row T2 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateProfileParserTest"
```

**Completion note required:** list the three fixture categories tested and their detected `documentKind`.

## Task T3: Add DocumentStructureProfile Foundation

**Preferred Agent:** Backend Structure Agent

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentStructureProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentNode.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentNodeFormatting.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentStructureExtractor.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentStructureProfileRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/JdbcDocumentStructureProfileRepository.java`
- Create: `backend/src/main/resources/db/migration/V__document_structure_profile.sql` using the next migration number frozen in T0.
- Create/Test: `backend/src/test/java/com/gongwen/assistant/documentstructure/DocumentStructureExtractorTest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/template/TemplateUploadService.java`

**Dependencies:** T1, T2

- [ ] Create the migration with `document_structure_profile` table keyed by template version.
- [ ] Define `DocumentStructureProfile` JSON fields: version, source file, nodes, styles, sections, risks, createdAt.
- [ ] Define `DocumentNode` fields: `nodeKey`, `parentKey`, `nodeType`, `roleSuggestion`, `text`, `textPreview`, `orderIndex`, `path`, `formatting`, `riskCodes`.
- [ ] Implement extractor from existing parsed DOCX/profile data first; do not try to cover all OOXML structures in this task.
- [ ] Persist structure profile during template upload.
- [ ] Add repository round-trip test for JSON persistence.
- [ ] Run focused document structure tests.
- [ ] Update Progress Board row T3 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.documentstructure.DocumentStructureExtractorTest"
```

**Completion note required:** number of node types supported in first pass and known unsupported structures.

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
- Create: `backend/src/main/java/com/gongwen/assistant/rendering/RenderPreviewController.java`
- Create: `backend/src/main/resources/db/migration/V__document_render_preview.sql` using the next migration number after T3.
- Create/Test: `backend/src/test/java/com/gongwen/assistant/rendering/DocumentRenderPreviewServiceTest.java`
- Modify: `.env.example`
- Modify: `AGENTS.md` when env vars are added.

**Dependencies:** T0

- [ ] Add environment variables for storage dir, renderer, LibreOffice path, DPI, timeout.
- [ ] Add `document_render_preview` table with template version, status, page count, storage path, error summary.
- [ ] Implement render service as asynchronous-capable service, but allow synchronous test seam.
- [ ] Implement `LibreOfficeRenderClient` with timeout and command construction tests.
- [ ] Ensure output paths stay under configured preview storage directory.
- [ ] Add API to get preview status and page metadata.
- [ ] Add API to download one preview page.
- [ ] Run focused render tests.
- [ ] Update Progress Board row T4 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.rendering.DocumentRenderPreviewServiceTest"
```

**Completion note required:** whether local LibreOffice execution was actually tested or mocked, with reason.

## Task T5: Template Parse Workspace Read-Only UI

**Preferred Agent:** Frontend Template Agent

**Files:**

- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Modify/Test: `frontend/src/App.test.tsx`

**Dependencies:** T3, T4

- [ ] Add frontend types for document structure profile, node, render preview, and document kind.
- [ ] Add API client methods for structure overview and render preview status/page URLs.
- [ ] Replace the current parse result modal content with read-only sections for document kind, structure tree, risks, and preview status.
- [ ] Show manual/ordinary document warning as non-template flow, not as parse failure.
- [ ] Show loading, failed render, unavailable render, and no-permission states.
- [ ] Keep visual style aligned with existing global components and `DESIGN.md`.
- [ ] Add frontend test for manual document warning and render-preview loading state.
- [ ] Run frontend focused/full build checks.
- [ ] Update Progress Board row T5 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- --run src/App.test.tsx
npm run build
```

**Completion note required:** screenshot or browser verification note for desktop parse workspace.

## Task T6: Structure Mapping Save And Publish Backend

**Preferred Agent:** Backend Mapping Agent

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingProfile.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingItem.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/JdbcStructureMappingRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingService.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingController.java`
- Create: `backend/src/main/resources/db/migration/V__structure_mapping_profile.sql` using the next migration number after T4.
- Create/Test: `backend/src/test/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingServiceTest.java`
- Create/Test: `backend/src/test/java/com/gongwen/assistant/documentstructure/mapping/StructureMappingControllerTest.java`

**Dependencies:** T3

- [ ] Add `structure_mapping_profile` and optional audit table.
- [ ] Implement save draft mapping endpoint.
- [ ] Implement publish mapping endpoint.
- [ ] Validate node keys exist in the corresponding `DocumentStructureProfile`.
- [ ] Validate required slots before publish for template-like documents.
- [ ] Block publish for `MANUAL_OR_GUIDE` and `ORDINARY_DOCUMENT` unless an explicit admin override flag exists in request.
- [ ] Add permission checks consistent with template administrator/system administrator behavior.
- [ ] Run focused mapping tests.
- [ ] Update Progress Board row T6 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.documentstructure.mapping.StructureMappingServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.documentstructure.mapping.StructureMappingControllerTest"
```

**Completion note required:** list publish blockers and successful publish response shape.

## Task T7: Mapping Editor UI And Publish Flow

**Preferred Agent:** Frontend Template Agent

**Files:**

- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Modify/Test: `frontend/src/App.test.tsx`

**Dependencies:** T5, T6

- [ ] Add frontend types for mapping profile, mapping item, mapping status, and publish response.
- [ ] Add API client methods for load mapping, save mapping draft, publish mapping.
- [ ] In template parse workspace, allow selecting a structure node and assigning role/slot.
- [ ] Add role controls for title, recipient, body, attachment, signature, date, ignore, static text.
- [ ] Show mapping validation errors from backend.
- [ ] Add publish button with loading, success, blocked, and permission-denied states.
- [ ] Add test for mapping a node and seeing publish blocked when required slots are missing.
- [ ] Run frontend test/build.
- [ ] Update Progress Board row T7 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- --run src/App.test.tsx
npm run build
```

**Completion note required:** describe which roles are editable in UI and which blocked states are visible.

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
- Create: `backend/src/main/resources/db/migration/V__draft_node_foundation.sql` using the next migration number after T6.
- Create/Test: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeServiceTest.java`
- Create/Test: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeControllerTest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/draft/DraftService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/draft/DraftDetailDto.java`

**Dependencies:** T6

- [ ] Add `draft_node` table while keeping `draft_block` compatibility.
- [ ] Add endpoint to initialize nodes for a draft from published mapping.
- [ ] Add endpoint to list nodes for a draft.
- [ ] Add endpoint to save node content and status.
- [ ] Enforce current user draft access checks.
- [ ] Preserve existing draft block APIs during transition.
- [ ] Add tests for create/list/save and unauthorized draft access.
- [ ] Run focused draft node tests.
- [ ] Update Progress Board row T8 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.draft.node.DraftNodeServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.draft.node.DraftNodeControllerTest"
```

**Completion note required:** explain how `DraftBlock` compatibility is preserved.

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

- [ ] Add API methods for initialize/list/save draft nodes.
- [ ] Update `WorkbenchNode` adapter to prefer backend `DraftNode` records.
- [ ] Keep fallback to existing `DraftBlock` data when nodes are absent.
- [ ] Render left structure tree with node status badges.
- [ ] Render center structured editor blocks from selected nodes.
- [ ] Keep selected node synchronized across left tree, center editor, and right panel.
- [ ] Mark quality and render preview states stale after node edits.
- [ ] Add tests for selecting a node and editing content.
- [ ] Run focused frontend tests and build.
- [ ] Update Progress Board row T9 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- --run src/workbenchNodes.test.ts src/App.test.tsx
npm run build
```

**Completion note required:** list fallback behavior when backend nodes are missing.

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

- [ ] Add optional `nodeId`, `nodeRole`, `nodeTitle`, and `nodeContext` request fields.
- [ ] Make paragraph generation write suggestions for a target node without changing node role.
- [ ] Make local operation target a node first, and keep existing `targetBlockId` fallback.
- [ ] Ensure global outline returns node-level creation/update suggestions before applying changes.
- [ ] Ensure AI trace stores node ids and summaries, not complete sensitive content.
- [ ] Add tests proving AI cannot implicitly change title/recipient/signature/date nodes.
- [ ] Run focused AI tests.
- [ ] Update Progress Board row T10 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.ai.PromptBuilderTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.ai.AiParagraphServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.ai.AiLocalOperationServiceTest"
```

**Completion note required:** state exactly which AI operations are node-aware and which still use fallback.

## Task T11: Right Panel AI Context UI

**Preferred Agent:** Frontend AI Agent

**Files:**

- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Modify/Test: `frontend/src/App.test.tsx`

**Dependencies:** T9, T10

- [ ] Route generate paragraph and local operation requests with selected node metadata.
- [ ] Show global actions when no node is selected.
- [ ] Show title actions for title node.
- [ ] Show body actions for body node.
- [ ] Show attachment/signature/date checks for those node roles.
- [ ] Keep existing right-panel quality and export functions visible.
- [ ] Disable node-specific AI actions for locked or unsupported nodes.
- [ ] Add tests for right panel changing available actions when selected node changes.
- [ ] Run frontend tests and build.
- [ ] Update Progress Board row T11 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen\frontend
npm test -- --run src/App.test.tsx
npm run build
```

**Completion note required:** list node roles with role-specific right-panel actions.

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

- [ ] Add save format override endpoint for a draft node.
- [ ] Support eastAsia font, latin font, size, bold, alignment, indent, line spacing, before/after spacing.
- [ ] Add restore-template-default endpoint that clears draft node override.
- [ ] Implement formatting merge priority exactly as defined in this plan.
- [ ] Enforce draft access permissions.
- [ ] Add tests for merge priority and restore default.
- [ ] Run focused format tests.
- [ ] Update Progress Board row T12 and Progress Log.

**Verification:**

```powershell
cd E:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.draft.node.DraftNodeFormatOverrideTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"
```

**Completion note required:** include the exact merge order verified by tests.

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
npm test -- --run src/App.test.tsx
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
- Create migration when needed: `backend/src/main/resources/db/migration/V__export_node_traceability.sql`
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
npm test -- --run src/App.test.tsx
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
npm test -- --run
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
npm test -- --run
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
