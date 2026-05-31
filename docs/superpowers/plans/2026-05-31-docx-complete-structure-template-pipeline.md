# DOCX Complete Structure Template Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fully implement a DOCX structure pipeline that extracts all usable structure from uploaded Word files, with or without placeholders, lets administrators map that structure into template roles, initializes drafts without duplicated content, and exports no-placeholder reference documents by preserving the original DOCX structure.

**Architecture:** P10E upgrades P10D from a shallow `TemplateProfile.structures`-derived flow into a fact-first DOCX pipeline. Backend extraction records DOCX facts before semantic role suggestions; mapping is a human-correctable overlay; draft nodes are initialized from confirmed mapped source nodes; placeholder templates continue using placeholder replacement, while no-placeholder reference templates use original DOCX node replacement instead of document reconstruction.

**Tech Stack:** Spring Boot, PostgreSQL JSONB/Flyway, Apache POI XWPF/OOXML, LibreOffice headless render preview, PDFBox, React + TypeScript + Vite, Vitest/React Testing Library, JUnit/AssertJ/Mockito.

---

## Source Context

- `AGENTS.md`
- `DESIGN.md`
- `docs/PROJECT_TASKS.md`
- `docs/superpowers/specs/2026-05-30-docx-preview-structure-mapping-design.md`
- `docs/superpowers/plans/2026-05-30-docx-structure-workbench.md`
- Regression fixture: `docs/examples/公文助手_讲话稿范文_示例.docx`
- Regression fixture: `docs/examples/公文助手_占位符通知模板_示例.docx`

## Product Outcome

When P10E is complete, uploading a real `.docx` file must not depend on explicit `{{字段名}}` placeholders.

Expected end-user behavior:

- The template parse workspace shows every supported structure node: body paragraphs, table cell paragraphs, headers, footers, numbering metadata, and supported media/risk markers.
- The structure tree distinguishes immutable facts from editable semantic role suggestions.
- For a reference speech document, the system suggests title, date, recipient, body headings, body paragraphs, table/supporting facts, header, and footer without hiding unknown nodes.
- Administrators can correct mappings, batch-confirm body nodes, mark static text, ignore nodes, and publish a mapping once required roles are confirmed.
- Draft initialization uses each mapped source node's own original text. Multiple body nodes never duplicate the first legacy `BODY_PARAGRAPH`.
- The workbench title comes from the mapped title node or explicit draft title, not from a document type default such as `未命名通知`.
- The center workbench preview is labelled as a structured editing preview. It does not pretend to be the exact DOCX original.
- The true original preview remains LibreOffice-rendered and is used for visual verification.
- Export for placeholder templates keeps the existing placeholder replacement path.
- Export for no-placeholder reference templates copies the original DOCX and replaces mapped nodes in place, preserving original paragraph, table, header/footer, font, spacing, and page furniture as far as Apache POI can preserve them.

## Definition Of Done

P10E is complete only when all items below are true:

- `公文助手_讲话稿范文_示例.docx` uploads as `REFERENCE_DOCUMENT`.
- Its structure profile contains all non-empty main paragraphs, all table-cell paragraphs, header paragraph, and footer paragraph.
- Role suggestions for the sample include:
  - `paragraph-0` -> `TITLE`
  - `paragraph-2` -> `DATE`
  - `paragraph-3` -> `RECIPIENT`
  - `paragraph-6`, `paragraph-9`, `paragraph-14`, `paragraph-17` -> `BODY_HEADING_LEVEL_1`
  - body prose/list paragraphs -> `BODY`
  - table cell paragraphs -> `TABLE_ATTACHMENT` or `STATIC_TEXT`
  - header/footer nodes -> `HEADER` / `FOOTER` fact types with non-destructive mapping defaults
- Creating or reinitializing a draft from this mapping produces one `DraftNode` per confirmed editable/static mapped node, each with its own source text.
- No draft node repeats the first body paragraph unless the original DOCX itself repeats that text.
- The workbench preview no longer renders template `UNIT`/`META` body misclassifications above the title for the speech fixture.
- Exporting a no-placeholder reference template does not clear and rebuild the document body; it replaces mapped original nodes in place.
- Focused backend tests, focused frontend tests, and `npm run build` pass.
- `AGENTS.md`, `docs/PROJECT_TASKS.md`, and this plan's progress board/log are updated with final status.

## Current Known Defects To Eliminate

Evidence gathered on 2026-05-31:

- `DraftNodeService.initialContent` currently prefers legacy `BODY_PARAGRAPH` for every `BODY` node. This duplicates the first body paragraph across many nodes.
- `TemplateProfileParser.inferStructureType` currently treats any text containing `机关`, `单位`, or `号` as `UNIT`/`META`, even after the actual body has started.
- `DocumentStructureExtractor` currently derives `DocumentNode` from `TemplateProfile.structures`, so facts and semantic guesses are already mixed before mapping.
- `DocxTemplateRenderer.renderReferenceDraft` clears document body elements and rebuilds title/recipient/body/signature/date. This loses original no-placeholder reference structure.
- `frontend/src/App.tsx` still renders some template semantic structures directly above and below the editable document, so misclassified nodes visibly distort the preview.

## Progress Discipline

Every agent finishing a task in this plan must update this file in the same change as the implementation.

Required updates per completed task:

1. Tick completed checkboxes in that task.
2. Update the row in **Progress Board**: status, owner, commit, verification, remaining risk.
3. Add one entry to **Progress Log** with date, task id, agent, commit, verification command, and result.
4. If user-visible project state changes, update `docs/PROJECT_TASKS.md`.
5. If APIs, data model, module boundaries, environment variables, AI flow, or deployment behavior change, update `AGENTS.md`.

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
| T18 | 已完成 | Integration Agent | P10D | P10E contracts, final API boundaries, fixture baseline | `9b8d517` | `git status --short --branch`; speech fixture inspection command | Must preserve P10D APIs while adding P10E endpoints |
| T19 | 已完成 | Backend Structure Agent | T18 | fact-first DOCX structure extraction | `9b8d517` | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.DocumentStructureExtractorTest" --tests "com.gongwen.assistant.template.TemplateUploadServiceTest"` | Complex OOXML features become explicit risk nodes rather than silent omissions |
| T20 | 已完成 | Backend Semantic Agent | T19 | independent semantic role suggester | `d27e917` | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.semantic.DocumentSemanticSuggesterTest" --tests "com.gongwen.assistant.template.profile.TemplateProfileParserTest" --tests "com.gongwen.assistant.template.TemplateUploadServiceTest"` | Suggestions remain advisory and facts remain unchanged; more document types can add heuristics later |
| T21 | 已完成 | Backend Draft Agent | T19, T20 | clean draft node initialization and reinitialization | `416f912` | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest" --tests "com.gongwen.assistant.draft.node.DraftNodeControllerTest"` | Existing nodes are kept until explicit reinitialize; frontend action completed in T23 |
| T22 | 已完成 | Frontend Template Agent | T20 | complete structure mapping workspace | `e058ea1` | `npm test -- src/components/template/TemplateParseWorkspace.test.tsx src/App.test.tsx`; `npm run build`; Browser smoke at `http://localhost:5173/` | `App.tsx` template workspace is extracted; workbench preview cleanup completed in T23 |
| T23 | 已完成 | Frontend Workbench Agent | T21, T22 | structured editing preview and node tree cleanup | `dd8182a` | `npm test -- src/workbenchNodes.test.ts src/App.test.tsx`; `npm run build`; Browser smoke at `http://localhost:5173/` | Workbench preview is explicitly structured-editing only; original DOCX in-place export completed in T24 |
| T24 | 已完成 | Backend Export Agent | T19, T21 | original DOCX in-place replacement export for reference templates | 本提交（T24 实现） | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.exporting.word.DocxNodeReplacementRendererTest" --tests "com.gongwen.assistant.exporting.DraftWordExportServiceTest" --tests "com.gongwen.assistant.exporting.word.DocxTemplateRendererTest"` | Multi-line replacement currently uses line breaks in the original paragraph; broader fixtures remain T25 |
| T25 | 已完成 | QA Agent | T19-T24 | fixture regression suite and render verification | 本提交（T25 实现） | `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks --tests "com.gongwen.assistant.integration.DocxCompleteStructurePipelineTest" --tests "com.gongwen.assistant.documentstructure.*" --tests "com.gongwen.assistant.draft.node.*" --tests "com.gongwen.assistant.exporting.*"`; `npm test -- src/components/template/TemplateParseWorkspace.test.tsx src/workbenchNodes.test.ts src/App.test.tsx`; `npm run build`; LibreOffice CLI PDF smoke | Browser authenticated automation remains for T26/manual; LibreOffice smoke used direct CLI conversion |
| T26 | 已完成 | Integration Agent | T18-T25 | final integration closure and docs sync | 本提交（T26 收口） | `git status --short --branch`; `git diff --check`; migration continuity check; API route grep; final focused backend/frontend commands; Browser smoke; LibreOffice CLI smoke from T25 | Full backend/frontend suites were intentionally not run per lightweight-test direction; focused regression and smoke checks passed |

## Progress Log

| Date | Task | Agent | Commit | Verification | Result | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-05-31 | Plan | Codex | plan document change | `git status --short --branch` | plan written on dirty P10D branch without touching feature code | implementation not started |
| 2026-05-31 | T18 | Codex Integration Agent | `9b8d517` | `git status --short --branch`; Python fixture inspection | pass; checkpoint commit left branch clean, speech fixture has 20 main paragraphs, 1 table, header `内部测试资料`, footer `测试文档 \| 讲话稿范文示例` | implementation starts at T19; existing P10D APIs must remain compatible |
| 2026-05-31 | T19 | Codex Backend Structure Agent | `9b8d517` | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.DocumentStructureExtractorTest" --tests "com.gongwen.assistant.template.TemplateUploadServiceTest"` | pass; extractor now reads DOCX bytes directly, emits v2 fact nodes for body paragraphs, table cell paragraphs, headers and footers, and keeps old `DocumentNode` constructor compatibility | role suggestions intentionally remain `UNKNOWN` until T20 semantic suggester |
| 2026-05-31 | T20 | Codex Backend Semantic Agent | `d27e917` | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.semantic.DocumentSemanticSuggesterTest" --tests "com.gongwen.assistant.template.profile.TemplateProfileParserTest" --tests "com.gongwen.assistant.template.TemplateUploadServiceTest"` | pass; upload now persists semantic suggestions for fact nodes, speech headings/date/recipient/body are suggested, and late body unit/meta keyword false positives are demoted | suggestions are deterministic heuristics and remain user-confirmable mapping defaults |
| 2026-05-31 | T21 | Codex Backend Draft Agent | `416f912` | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest" --tests "com.gongwen.assistant.draft.node.DraftNodeControllerTest"` | pass; initialize now uses each source node text, avoids duplicate legacy body copy, keeps existing nodes non-destructively, and adds explicit reinitialize endpoint with preserve-user-edits behavior | frontend reinitialize action and preview cleanup completed in T23 |
| 2026-05-31 | T18-T21 | Codex Integration Agent | `416f912` | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.DocumentStructureExtractorTest" --tests "com.gongwen.assistant.template.TemplateUploadServiceTest" --tests "com.gongwen.assistant.documentstructure.semantic.DocumentSemanticSuggesterTest" --tests "com.gongwen.assistant.template.profile.TemplateProfileParserTest" --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest" --tests "com.gongwen.assistant.draft.node.DraftNodeControllerTest"` | pass; fact extraction, upload persistence, semantic suggestions, parser demotion, and draft node initialize/reinitialize contracts pass together | full backend/frontend suites remain for T25/T26 or CI |
| 2026-05-31 | T22 | Codex Frontend Template Agent | `e058ea1` | `npm test -- src/components/template/TemplateParseWorkspace.test.tsx src/App.test.tsx`; `npm run build`; Browser smoke at `http://localhost:5173/` | pass; template parse workspace extracted from `App.tsx`, all fact nodes render with separate fact type and role suggestion, selected nodes support batch role changes, draft save and publish actions remain wired | browser smoke reached login page only; authenticated template workflow remains manual or T25/T26 |
| 2026-05-31 | T23 | Codex Frontend Workbench Agent | `dd8182a` | `npm test -- src/workbenchNodes.test.ts src/App.test.tsx`; `npm run build`; Browser smoke at `http://localhost:5173/` | pass; workbench structure tree and structured editing preview were extracted, static facts stay visible in the tree but out of paper preview, and `从原稿重建结构` calls `POST /api/drafts/{draftId}/nodes/reinitialize` preserving user edits | browser smoke reached login page only; authenticated template workflow remains manual or T25/T26 |
| 2026-05-31 | T24 | Codex Backend Export Agent | 本提交（T24 实现） | `.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.exporting.word.DocxNodeReplacementRendererTest" --tests "com.gongwen.assistant.exporting.DraftWordExportServiceTest" --tests "com.gongwen.assistant.exporting.word.DocxTemplateRendererTest"` | pass; no-placeholder `REFERENCE_DOCUMENT` export now uses original DOCX node replacement, preserves paragraph objects/header text, records `ORIGINAL_NODE_REPLACEMENT`, and persists `export_strategy` via Flyway `V17` | T25 still needs fixture-level pipeline coverage across upload, mapping, node init, export, and optional render smoke |
| 2026-05-31 | T25 | Codex QA Agent | 本提交（T25 实现） | `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks --tests "com.gongwen.assistant.integration.DocxCompleteStructurePipelineTest" --tests "com.gongwen.assistant.documentstructure.*" --tests "com.gongwen.assistant.draft.node.*" --tests "com.gongwen.assistant.exporting.*"`; `npm test -- src/components/template/TemplateParseWorkspace.test.tsx src/workbenchNodes.test.ts src/App.test.tsx`; `npm run build`; LibreOffice CLI PDF smoke | pass; speech reference fixture now covers upload, fact extraction, semantic suggestions, published mapping, duplicate-free DraftNode initialization, original DOCX node replacement export, and no-placeholder mapping UI behavior | browser authenticated workflow still needs T26/manual verification; full backend/frontend suites remain optional per lightweight-test direction |
| 2026-05-31 | T26 | Codex Integration Agent | 本提交（T26 收口） | `git status --short --branch`; `git diff --check`; migration continuity check V1-V17; `rg -n "/nodes/reinitialize\|structure-facts\|structure-profile\|structure-mapping\|render-preview" frontend/src backend/src/main/java`; `.\gradlew.bat --no-daemon --console=plain test --rerun-tasks --tests "com.gongwen.assistant.documentstructure.*" --tests "com.gongwen.assistant.documentstructure.semantic.*" --tests "com.gongwen.assistant.draft.node.*" --tests "com.gongwen.assistant.exporting.*" --tests "com.gongwen.assistant.integration.DocxCompleteStructurePipelineTest"`; `npm test -- src/components/template/TemplateParseWorkspace.test.tsx src/workbenchNodes.test.ts src/App.test.tsx`; `npm run build`; Browser smoke at `http://localhost:5173/` | pass; P10E plan closed, API routes aligned, migrations continuous through `V17`, Browser smoke reached app/login signal with 0 console errors | full backend/frontend suites remain optional; authenticated browser workflow remains best covered manually or by later E2E |

## Agent File Boundaries

Integration Agent:

- Owns this plan, `docs/PROJECT_TASKS.md`, `AGENTS.md`, final API/migration review, and cross-agent merge checks.
- May inspect all files.
- Must not make broad feature edits while reviewing another agent's result.

Backend Structure Agent:

- Primary files:
  - `backend/src/main/java/com/gongwen/assistant/documentstructure/**`
  - `backend/src/test/java/com/gongwen/assistant/documentstructure/**`
  - `backend/src/test/resources/docx-fixtures/**`
- Coordinates with Backend Semantic Agent before changing fields consumed by mapping.
- Avoids `frontend/src/**`.

Backend Semantic Agent:

- Primary files:
  - `backend/src/main/java/com/gongwen/assistant/documentstructure/semantic/**`
  - `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileParser.java`
  - `backend/src/main/java/com/gongwen/assistant/template/TemplateIntelligenceService.java`
  - semantic-related tests
- Must keep semantic suggestions separate from fact extraction.

Backend Draft Agent:

- Primary files:
  - `backend/src/main/java/com/gongwen/assistant/draft/node/**`
  - `backend/src/main/java/com/gongwen/assistant/draft/**`
  - draft-node tests
- Must preserve current draft access checks through `DraftService.getDraft`.

Backend Export Agent:

- Primary files:
  - `backend/src/main/java/com/gongwen/assistant/exporting/**`
  - `backend/src/main/java/com/gongwen/assistant/exporting/word/**`
  - export tests
- Must not weaken existing placeholder-template export behavior.

Frontend Template Agent:

- Primary files:
  - `frontend/src/components/template/TemplateParseWorkspace.tsx`
  - `frontend/src/components/template/StructureNodeTree.tsx`
  - `frontend/src/components/template/MappingBulkToolbar.tsx`
  - `frontend/src/components/template/TemplateParseWorkspace.test.tsx`
  - targeted imports in `frontend/src/App.tsx`
  - `frontend/src/draftTypes.ts`
  - `frontend/src/api.ts`
- Must use `DESIGN.md` tokens and global UI components.

Frontend Workbench Agent:

- Primary files:
  - `frontend/src/components/workbench/WorkbenchPreview.tsx`
  - `frontend/src/components/workbench/WorkbenchStructureTree.tsx`
  - `frontend/src/workbenchNodes.ts`
  - `frontend/src/workbenchNodes.test.ts`
  - targeted imports in `frontend/src/App.tsx`
  - `frontend/src/styles/app.css`
- Must not change backend contracts.

QA Agent:

- Primary files:
  - backend focused tests
  - frontend focused tests
  - fixture README
  - docs verification notes
- Does not refactor production code except to fix a verified regression uncovered by its tests.

## Frozen P10E Contracts

### Existing API Compatibility

These APIs must remain compatible:

- `GET /api/templates/versions/{versionId}/profile`
- `GET /api/templates/versions/{versionId}/structure-profile`
- `GET /api/templates/versions/{versionId}/document-kind`
- `GET /api/templates/versions/{versionId}/structure-mapping`
- `PUT /api/templates/versions/{versionId}/structure-mapping/draft`
- `POST /api/templates/versions/{versionId}/structure-mapping/publish`
- `POST /api/drafts/{draftId}/nodes/initialize`
- `GET /api/drafts/{draftId}/nodes`
- `PUT /api/drafts/{draftId}/nodes/{nodeId}`
- `POST /api/exports/drafts/{draftId}/word`

### New API Boundaries

P10E adds these API boundaries:

- `POST /api/drafts/{draftId}/nodes/reinitialize`
  - Requires current-user access to the draft.
  - Requires draft has a bound template version and that template version has a published mapping.
  - Request:
    ```json
    {
      "mode": "FROM_SOURCE_DOCUMENT",
      "preserveUserEditedNodes": true
    }
    ```
  - Response: `DraftNode[]`.
  - Behavior: rebuilds nodes from the current published mapping and source `DocumentStructureProfile`; keeps user edited nodes when `preserveUserEditedNodes=true` and node key/role still match.

- `GET /api/templates/versions/{versionId}/structure-facts`
  - Returns the same profile as `structure-profile` but names the UI intent clearly for P10E.
  - This can be implemented by the same controller method internally if the response DTO is identical.

### Document Node Fact Contract

`DocumentNode` remains JSONB-backed and may evolve without a migration. It must expose these stable fields in API JSON:

```json
{
  "nodeKey": "paragraph-6",
  "parentKey": null,
  "nodeType": "PARAGRAPH",
  "roleSuggestion": "BODY_HEADING_LEVEL_1",
  "text": "一、提高政治站位，把思想和行动统一到重点任务落实上来",
  "textPreview": "一、提高政治站位，把思想和行动统一到重点任务落实上来",
  "orderIndex": 6,
  "path": "body/paragraph[6]",
  "location": {
    "part": "BODY",
    "paragraphIndex": 6,
    "tableIndex": null,
    "rowIndex": null,
    "cellIndex": null
  },
  "formatting": {},
  "runs": [],
  "numbering": null,
  "riskCodes": []
}
```

Compatibility rule:

- Existing consumers that only know `nodeKey`, `nodeType`, `roleSuggestion`, `textPreview`, `orderIndex`, `path`, `formatting`, and `riskCodes` must keep working.
- New fields are additive.

### Node Type Values

```text
DOCUMENT
PARAGRAPH
TABLE
TABLE_ROW
TABLE_CELL
TABLE_PARAGRAPH
HEADER_PARAGRAPH
FOOTER_PARAGRAPH
FOOTNOTE_PARAGRAPH
ENDNOTE_PARAGRAPH
TEXTBOX_PARAGRAPH
MEDIA
FIELD
UNSUPPORTED
```

P10E implementation must fully support:

- `PARAGRAPH`
- `TABLE_PARAGRAPH`
- `HEADER_PARAGRAPH`
- `FOOTER_PARAGRAPH`

P10E implementation must detect and expose risks for:

- `TEXTBOX_PARAGRAPH`
- `MEDIA`
- `FIELD`
- `UNSUPPORTED`

### Role Suggestion Values

Reuse P10D roles and add no new role without updating `AGENTS.md`:

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
HEADER
FOOTER
```

`HEADER` and `FOOTER` roles are mapping roles only if the current code already accepts them. If the current backend role allowlist does not accept them, map header/footer nodes to `STATIC_TEXT` by default and keep their `nodeType` as `HEADER_PARAGRAPH` / `FOOTER_PARAGRAPH`.

### Export Strategy Contract

`DraftWordExportService` must choose strategy by template profile:

```text
PLACEHOLDER_TEMPLATE -> placeholder replacement, existing path
STYLE_TEMPLATE -> generated draft snapshot or mapping-based reference path when source node locators exist
REFERENCE_DOCUMENT -> original DOCX in-place replacement
OFFICIAL_DOCUMENT -> original DOCX in-place replacement
MANUAL_OR_GUIDE -> blocked
POLICY_OR_REGULATION -> blocked
ORDINARY_DOCUMENT -> blocked
UNKNOWN_DOCUMENT -> blocked unless admin override is explicitly supported
```

For P10E, `REFERENCE_DOCUMENT` must not use a body-clearing rebuild path.

## Task T18: Freeze P10E Contracts And Fixture Baseline

**Files:**

- Modify: `docs/superpowers/plans/2026-05-31-docx-complete-structure-template-pipeline.md`
- Modify: `docs/PROJECT_TASKS.md`
- Modify: `AGENTS.md`

**Steps:**

- [x] **Step 1: Confirm worktree state**

  Run:

  ```powershell
  git status --short --branch
  ```

  Expected:

  - Branch is visible.
  - Dirty files are reviewed and not reverted.
  - The agent records unrelated dirty files in its final summary.

- [x] **Step 2: Inspect the speech fixture facts**

  Run:

  ```powershell
  $doc = Get-ChildItem -LiteralPath 'E:\gongwen\docs\examples' -Filter '*.docx' | Where-Object { $_.Name -like '*讲话稿范文*' } | Select-Object -First 1
  $env:DOCX_PATH = $doc.FullName
  @'
  import os
  from docx import Document
  doc = Document(os.environ["DOCX_PATH"])
  print("paragraphs", len(doc.paragraphs))
  print("tables", len(doc.tables))
  for i, p in enumerate(doc.paragraphs):
      if p.text.strip():
          print(i, p.text[:80])
  for si, section in enumerate(doc.sections):
      print("header", si, [p.text for p in section.header.paragraphs if p.text.strip()])
      print("footer", si, [p.text for p in section.footer.paragraphs if p.text.strip()])
  '@ | & 'C:\Users\10172\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' -
  ```

  Expected:

  - 20 main paragraphs.
  - 1 table.
  - Header contains `内部测试资料`.
  - Footer contains `测试文档 | 讲话稿范文示例`.

- [x] **Step 3: Update docs with P10E planned stage**

  Add P10E to `docs/PROJECT_TASKS.md` current recommendation:

  ```markdown
  - P10E DOCX 完整结构事实与无占位符范文套版：在 P10D 基础上补齐 fact-first 结构抽取、独立语义建议、DraftNode 干净初始化、结构映射 UI、无占位符范文原位替换导出和 fixture 回归。
  ```

- [x] **Step 4: Update AGENTS with P10E dispatch guidance**

  Add a concise "当前下一轮并行建议" entry:

  ```text
  - Agent P10E-Structure：升级 DocumentStructureProfile 为 fact-first OOXML 结构层，禁止把语义猜测写死为事实。
  - Agent P10E-Semantic：独立语义建议器，覆盖讲话稿/通知/请示/报告常见结构。
  - Agent P10E-Draft：修 DraftNode 初始化和 reinitialize，消除旧 DraftBlock 复制污染。
  - Agent P10E-Frontend：拆出模板解析工作台和工作台预览组件，展示完整结构树与映射批量操作。
  - Agent P10E-Export：实现无占位符参考文档的原 DOCX 原位替换导出。
  - Agent P10E-QA：以示例 DOCX 和动态 fixtures 固化结构、映射、初始化、导出和渲染回归。
  ```

- [x] **Step 5: Mark T18 progress**

  Update Progress Board T18 and add Progress Log entry with verification commands and result.

## Task T19: Fact-First DOCX Structure Extraction

**Files:**

- Modify: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentNode.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentStructureExtractor.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentNodeLocation.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentRunFact.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentNumberingFact.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocxStructureWalker.java`
- Test: `backend/src/test/java/com/gongwen/assistant/documentstructure/DocumentStructureExtractorTest.java`
- Test support: `backend/src/test/java/com/gongwen/assistant/support/DocxTestFactory.java`

**Implementation Requirements:**

- `DocumentStructureExtractor` must parse DOCX bytes directly, not only `TemplateProfile.structures`.
- It must preserve every non-empty main body paragraph in document order.
- It must preserve every non-empty table cell paragraph with table coordinates.
- It must preserve every non-empty header/footer paragraph.
- It must include a stable `nodeKey`.
- It must include `text` as complete node text and `textPreview` as a shortened preview.
- It must include `nodeType` based on physical location, not semantic role.
- It may include `roleSuggestion`, but that value must be provided by T20 semantic logic and must never be the only representation of the node.

**Steps:**

- [x] **Step 1: Add failing test for complete speech fixture extraction**

  In `backend/src/test/java/com/gongwen/assistant/documentstructure/DocumentStructureExtractorTest.java`, add:

  ```java
  @Test
  void extractsAllSupportedFactsFromSpeechReferenceDocument() {
      byte[] docx = DocxTestFactory.speechReferenceDocument();

      DocumentStructureProfile profile = extractor.extract(docx, "hash");

      assertThat(profile.nodes())
              .filteredOn(node -> "PARAGRAPH".equals(node.nodeType()))
              .extracting(DocumentNode::text)
              .contains(
                      "在全区重点工作推进会上的讲话",
                      "政务会议讲话稿测试样例",
                      "2026年5月30日",
                      "同志们：",
                      "一、提高政治站位，把思想和行动统一到重点任务落实上来",
                      "结束语",
                      "我就讲这些，谢谢大家。"
              );
      assertThat(profile.nodes())
              .filteredOn(node -> "TABLE_PARAGRAPH".equals(node.nodeType()))
              .extracting(DocumentNode::text)
              .contains("文档类型", "政务会议讲话稿（测试样例）", "使用说明");
      assertThat(profile.nodes())
              .filteredOn(node -> "HEADER_PARAGRAPH".equals(node.nodeType()))
              .extracting(DocumentNode::text)
              .contains("内部测试资料");
      assertThat(profile.nodes())
              .filteredOn(node -> "FOOTER_PARAGRAPH".equals(node.nodeType()))
              .extracting(DocumentNode::text)
              .contains("测试文档 | 讲话稿范文示例");
  }
  ```

- [x] **Step 2: Run failing test**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.DocumentStructureExtractorTest"
  ```

  Expected:

  - FAIL because `DocumentStructureExtractor.extract(byte[], String)` does not exist or still derives incomplete facts.

- [x] **Step 3: Add fact DTO records**

  Create `DocumentNodeLocation.java`:

  ```java
  package com.gongwen.assistant.documentstructure;

  public record DocumentNodeLocation(
          String part,
          Integer paragraphIndex,
          Integer tableIndex,
          Integer rowIndex,
          Integer cellIndex,
          Integer cellParagraphIndex
  ) {
      public DocumentNodeLocation {
          part = part == null || part.isBlank() ? "BODY" : part;
      }
  }
  ```

  Create `DocumentRunFact.java`:

  ```java
  package com.gongwen.assistant.documentstructure;

  public record DocumentRunFact(
          int runIndex,
          String text,
          String eastAsiaFontFamily,
          String latinFontFamily,
          Integer fontSizeHalfPoints,
          Boolean bold,
          Boolean italic,
          String colorHex
  ) {
      public DocumentRunFact {
          text = text == null ? "" : text;
      }
  }
  ```

  Create `DocumentNumberingFact.java`:

  ```java
  package com.gongwen.assistant.documentstructure;

  public record DocumentNumberingFact(
          String numId,
          String ilvl,
          String styleId
  ) {
  }
  ```

- [x] **Step 4: Extend `DocumentNode` additively**

  Modify `DocumentNode.java` to include:

  ```java
  public record DocumentNode(
          String nodeKey,
          String parentKey,
          String nodeType,
          String roleSuggestion,
          String text,
          String textPreview,
          int orderIndex,
          String path,
          TemplateStructureFormattingProfile formatting,
          List<String> riskCodes,
          DocumentNodeLocation location,
          List<DocumentRunFact> runs,
          DocumentNumberingFact numbering
  ) {
      public DocumentNode {
          nodeKey = nodeKey == null ? "" : nodeKey;
          nodeType = nodeType == null || nodeType.isBlank() ? "PARAGRAPH" : nodeType;
          roleSuggestion = roleSuggestion == null || roleSuggestion.isBlank() ? "UNKNOWN" : roleSuggestion;
          text = text == null ? "" : text;
          textPreview = textPreview == null || textPreview.isBlank() ? text : textPreview;
          path = path == null ? "" : path;
          riskCodes = riskCodes == null ? List.of() : List.copyOf(riskCodes);
          runs = runs == null ? List.of() : List.copyOf(runs);
      }

      public DocumentNode(
              String nodeKey,
              String parentKey,
              String nodeType,
              String roleSuggestion,
              String text,
              String textPreview,
              int orderIndex,
              String path,
              TemplateStructureFormattingProfile formatting,
              List<String> riskCodes
      ) {
          this(
                  nodeKey,
                  parentKey,
                  nodeType,
                  roleSuggestion,
                  text,
                  textPreview,
                  orderIndex,
                  path,
                  formatting,
                  riskCodes,
                  null,
                  List.of(),
                  null
          );
      }
  }
  ```

- [x] **Step 5: Implement `DocxStructureWalker`**

  Create `DocxStructureWalker.java` with methods:

  ```java
  List<DocumentNode> walk(XWPFDocument document)
  ```

  Required implementation details:

  - Iterate `document.getBodyElements()` so paragraph/table order is preserved.
  - For body paragraphs, emit node key `paragraph-{paragraphIndex}`.
  - For table cell paragraphs, emit node key `table-{tableIndex}-row-{rowIndex}-cell-{cellIndex}-paragraph-{cellParagraphIndex}`.
  - For headers, emit node key `header-{headerIndex}-paragraph-{paragraphIndex}`.
  - For footers, emit node key `footer-{footerIndex}-paragraph-{paragraphIndex}`.
  - Skip only blank text nodes.
  - For each paragraph, collect first non-empty run formatting and run facts.
  - Assign `roleSuggestion` as `UNKNOWN` in T19. T20 replaces suggestions.

- [x] **Step 6: Modify `DocumentStructureExtractor` to use direct DOCX bytes**

  Add overload:

  ```java
  public DocumentStructureProfile extract(byte[] docxBytes, String sourceFileHash)
  ```

  Behavior:

  - Open `XWPFDocument`.
  - Use `DocxStructureWalker`.
  - Return profile with extractor version `document-structure-v2`.
  - Keep the existing `extract(TemplateProfile, String)` method for compatibility, but make upload use the byte-based method in Step 7.

- [x] **Step 7: Update `TemplateUploadService` to persist byte-based structure facts**

  Modify `saveDocumentStructureProfile` call so upload uses:

  ```java
  documentStructureExtractor.extract(content, profileHash)
  ```

  Preserve profile save behavior.

- [x] **Step 8: Run fact extraction tests**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.DocumentStructureExtractorTest" --tests "com.gongwen.assistant.template.TemplateUploadServiceTest"
  ```

  Expected:

  - PASS.

- [x] **Step 9: Update Progress Board and Progress Log**

  Record commands, result, and remaining unsupported OOXML risks.

## Task T20: Independent Semantic Role Suggestion Layer

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/semantic/DocumentSemanticSuggester.java`
- Create: `backend/src/main/java/com/gongwen/assistant/documentstructure/semantic/DocumentSemanticContext.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/documentstructure/DocumentStructureExtractor.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateProfileParser.java`
- Test: `backend/src/test/java/com/gongwen/assistant/documentstructure/semantic/DocumentSemanticSuggesterTest.java`
- Test: `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateProfileParserTest.java`

**Implementation Requirements:**

- Semantic role suggestions must operate on `DocumentNode` facts.
- Role suggestions must not change `nodeType`, `text`, `path`, or location.
- Body has started once the suggester sees the first `BODY` paragraph after title/date/recipient or the first body heading.
- After body has started, plain body paragraphs must not be suggested as `UNIT` or `META` only because they contain `单位`, `机关`, or `号`.
- Speech headings must be recognized:
  - `一、...`
  - `二、...`
  - `三、...`
  - `（一）...`
  - `1. ...`
  - `结束语`
- A line ending with `：` before body starts can be `RECIPIENT`.
- A Chinese date line can be `DATE`.

**Steps:**

- [x] **Step 1: Add failing speech semantic test**

  In `DocumentSemanticSuggesterTest.java`, add:

  ```java
  @Test
  void suggestsSpeechRolesWithoutTreatingBodyUnitWordsAsIssuingOrgan() {
      DocumentStructureProfile profile = extractor.extract(DocxTestFactory.speechReferenceDocument(), "hash");

      DocumentStructureProfile suggested = suggester.suggest(profile, "REFERENCE_DOCUMENT", "UNKNOWN");

      assertThat(roleByText(suggested, "在全区重点工作推进会上的讲话")).isEqualTo("TITLE");
      assertThat(roleByText(suggested, "2026年5月30日")).isEqualTo("DATE");
      assertThat(roleByText(suggested, "同志们：")).isEqualTo("RECIPIENT");
      assertThat(roleByText(suggested, "一、提高政治站位，把思想和行动统一到重点任务落实上来")).isEqualTo("BODY_HEADING_LEVEL_1");
      assertThat(roleByText(suggested, "二、聚焦关键环节，以务实举措推动工作提质增效")).isEqualTo("BODY_HEADING_LEVEL_1");
      assertThat(roleByText(suggested, "结束语")).isEqualTo("BODY_HEADING_LEVEL_1");
      assertThat(roleByPrefix(suggested, "今年以来，各部门各单位围绕中心")).isEqualTo("BODY");
      assertThat(roleByPrefix(suggested, "要健全闭环机制")).isEqualTo("BODY");
  }
  ```

- [x] **Step 2: Add failing notice/reference semantic test**

  Add:

  ```java
  @Test
  void suggestsNoticeSlotsButKeepsLateUnitWordsAsBody() {
      DocumentStructureProfile profile = extractor.extract(DocxTestFactory.docxWithNoticeReferenceSkeleton(), "hash");

      DocumentStructureProfile suggested = suggester.suggest(profile, "REFERENCE_DOCUMENT", "NOTICE");

      assertThat(suggested.nodes())
              .extracting(DocumentNode::roleSuggestion)
              .containsSubsequence("TITLE", "RECIPIENT", "BODY");
      assertThat(suggested.nodes().stream()
              .filter(node -> node.text().contains("各部门各单位要把职责摆进去"))
              .findFirst()
              .orElseThrow()
              .roleSuggestion()).isEqualTo("BODY");
  }
  ```

- [x] **Step 3: Run failing semantic tests**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.semantic.DocumentSemanticSuggesterTest"
  ```

  Expected:

  - FAIL because the suggester does not exist.

- [x] **Step 4: Implement `DocumentSemanticContext`**

  Create:

  ```java
  package com.gongwen.assistant.documentstructure.semantic;

  public record DocumentSemanticContext(
          String documentKind,
          String documentTypeCode
  ) {
      public DocumentSemanticContext {
          documentKind = documentKind == null || documentKind.isBlank() ? "UNKNOWN_DOCUMENT" : documentKind;
          documentTypeCode = documentTypeCode == null || documentTypeCode.isBlank() ? "UNKNOWN" : documentTypeCode;
      }
  }
  ```

- [x] **Step 5: Implement `DocumentSemanticSuggester`**

  Required public method:

  ```java
  public DocumentStructureProfile suggest(DocumentStructureProfile profile, String documentKind, String documentTypeCode)
  ```

  Required behavior:

  - Sort nodes by `orderIndex`.
  - Suggest only for supported text nodes.
  - Preserve all node facts.
  - Return copied nodes with updated `roleSuggestion`.
  - Use `UNKNOWN` for table/header/footer unless obvious fixed text should become `STATIC_TEXT`.
  - Do not suggest `UNIT` or `META` after `seenBody=true`.

- [x] **Step 6: Wire suggester into upload profile persistence**

  Modify `TemplateUploadService` or `DocumentStructureExtractor` integration so after `TemplateIntelligenceService.enrich(...)` determines document kind, persisted `DocumentStructureProfile` uses:

  ```java
  semanticSuggester.suggest(structureProfile, profile.templateAnalysis().documentKind(), profile.templateAnalysis().documentTypeCode())
  ```

- [x] **Step 7: Demote semantic guessing in `TemplateProfileParser`**

  Keep `TemplateProfileParser` compatible for existing tests, but remove or narrow late-body keyword inference that causes `UNIT`/`META` body misclassification.

  Required rule:

  ```java
  if (seenBody && ("UNIT".equals(inferredType) || "META".equals(inferredType))) {
      return "BODY";
  }
  ```

  This rule belongs in `refineMainParagraphType`, not in `inferStructureType`.

- [x] **Step 8: Run semantic/parser focused tests**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.semantic.DocumentSemanticSuggesterTest" --tests "com.gongwen.assistant.template.profile.TemplateProfileParserTest" --tests "com.gongwen.assistant.template.TemplateUploadServiceTest"
  ```

  Expected:

  - PASS.

- [x] **Step 9: Update Progress Board and Progress Log**

  Record exact test commands and result.

## Task T21: Clean DraftNode Initialization And Reinitialization

**Files:**

- Modify: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeController.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/ReinitializeDraftNodesRequest.java`
- Modify: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeServiceTest.java`
- Modify: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeControllerTest.java`

**Implementation Requirements:**

- `initializeNodes` must no longer duplicate the first legacy body block across every body node.
- If a draft has no existing nodes and the mapped source node has non-placeholder text, the draft node content must be that source node text.
- Legacy `DraftBlock` values may seed top-level singleton roles only when there is exactly one confirmed node for that role.
- Legacy `BODY_PARAGRAPH` may seed body nodes only by matching sort order or node key, never by copying one block into every body node.
- Add explicit reinitialize endpoint for users/admins to clean stale nodes after mapping changes or parser upgrades.

**Steps:**

- [x] **Step 1: Add failing duplicated body regression test**

  In `DraftNodeServiceTest.java`, add:

  ```java
  @Test
  void initializesEachBodyNodeFromItsOwnSourceTextInsteadOfDuplicatingLegacyBody() {
      DraftService draftService = mock(DraftService.class);
      when(draftService.getDraft(5L)).thenReturn(draftWithTemplateAndOneLegacyBody());
      DraftNodeService service = service(
              draftService,
              new InMemoryDraftNodeRepository(),
              multiBodyPublishedMapping(),
              multiBodyStructureProfile()
      );

      List<DraftNodeDto> initialized = service.initializeNodes(5L);

      assertThat(initialized)
              .filteredOn(node -> "BODY".equals(node.role()))
              .extracting(DraftNodeDto::content)
              .containsExactly("第一段源正文", "第二段源正文", "第三段源正文");
  }
  ```

- [x] **Step 2: Add failing reinitialize preservation test**

  Add:

  ```java
  @Test
  void reinitializePreservesUserEditedNodesWhenRequested() {
      DraftService draftService = mock(DraftService.class);
      when(draftService.getDraft(5L)).thenReturn(emptyDraftWithTemplate());
      InMemoryDraftNodeRepository repository = new InMemoryDraftNodeRepository();
      DraftNodeService service = service(draftService, repository, multiBodyPublishedMapping(), multiBodyStructureProfile());
      List<DraftNodeDto> initialized = service.initializeNodes(5L);
      DraftNodeDto edited = initialized.stream().filter(node -> "BODY".equals(node.role())).findFirst().orElseThrow();
      service.updateNode(5L, edited.id(), new UpdateDraftNodeRequest("用户改过的第一段", "USER_MODIFIED_AFTER_AI"));

      List<DraftNodeDto> reinitialized = service.reinitializeNodes(
              5L,
              new ReinitializeDraftNodesRequest("FROM_SOURCE_DOCUMENT", true)
      );

      assertThat(reinitialized)
              .filteredOn(node -> node.templateNodeKey().equals(edited.templateNodeKey()))
              .extracting(DraftNodeDto::content)
              .containsExactly("用户改过的第一段");
  }
  ```

- [x] **Step 3: Run failing draft tests**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest"
  ```

  Expected:

  - FAIL on duplicate body behavior and missing reinitialize API.

- [x] **Step 4: Add reinitialize request record**

  Create:

  ```java
  package com.gongwen.assistant.draft.node;

  public record ReinitializeDraftNodesRequest(
          String mode,
          boolean preserveUserEditedNodes
  ) {
      public ReinitializeDraftNodesRequest {
          mode = mode == null || mode.isBlank() ? "FROM_SOURCE_DOCUMENT" : mode;
      }
  }
  ```

- [x] **Step 5: Fix body initialization logic**

  Modify `DraftNodeService.initialContent` rules:

  - `TITLE`, `RECIPIENT`, `DATE`, `SIGNATURE`, `ATTACHMENT_*` can use singleton legacy blocks when there is no source text or when there is exactly one matching mapped role.
  - `BODY` and `BODY_HEADING_LEVEL_*` default to `editableSourceText(sourceNode)`.
  - `BODY` can use legacy block only when a legacy block sort order matches the mapping item's sort order.

  Implement helper:

  ```java
  private String legacyBodyBySortOrder(Map<Integer, String> legacyBodyBySortOrder, StructureMappingItem item) {
      return legacyBodyBySortOrder.getOrDefault(item.sortOrder(), "");
  }
  ```

- [x] **Step 6: Add `reinitializeNodes` service method**

  Behavior:

  - Load draft and published mapping.
  - Build new nodes from current mapping and structure profile.
  - Load existing nodes.
  - If `preserveUserEditedNodes=true`, copy content/status/format override from existing node with same `templateNodeKey` and role when status is `USER_FILLED`, `USER_MODIFIED_AFTER_AI`, `AI_GENERATED`, or `FORMAT_OVERRIDDEN`.
  - Replace draft nodes in repository.

- [x] **Step 7: Add controller endpoint**

  In `DraftNodeController`, add:

  ```java
  @PostMapping("/reinitialize")
  public ApiResponse<List<DraftNodeDto>> reinitialize(
          @PathVariable long draftId,
          @RequestBody(required = false) ReinitializeDraftNodesRequest request
  ) {
      return ApiResponse.ok(service.reinitializeNodes(draftId, request));
  }
  ```

- [x] **Step 8: Run draft node tests**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest" --tests "com.gongwen.assistant.draft.node.DraftNodeControllerTest"
  ```

  Expected:

  - PASS.

- [x] **Step 9: Update Progress Board and Progress Log**

  Record exact test command and result.

## Task T22: Complete Structure Mapping Workspace UI

**Files:**

- Create: `frontend/src/components/template/TemplateParseWorkspace.tsx`
- Create: `frontend/src/components/template/StructureNodeTree.tsx`
- Create: `frontend/src/components/template/MappingBulkToolbar.tsx`
- Create: `frontend/src/components/template/TemplateParseWorkspace.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/styles/app.css`

**Implementation Requirements:**

- Move template parse workspace rendering out of `App.tsx`.
- Show fact node type and role suggestion separately.
- Show all nodes, including `UNKNOWN`, table paragraphs, header, and footer.
- Add controls:
  - role select per node
  - mark visible range as `BODY`
  - mark selected nodes as `BODY_HEADING_LEVEL_1`
  - mark selected nodes as `STATIC_TEXT`
  - mark selected nodes as `IGNORE`
  - save draft mapping
  - publish mapping
- Show warning for `REFERENCE_DOCUMENT`: "该文件是参考范文，必须确认映射后再用于套版。"
- Do not block structure mapping merely because there are no placeholders.

**Steps:**

- [x] **Step 1: Add failing UI test for all nodes visible**

  In `TemplateParseWorkspace.test.tsx`, render a profile with:

  - title node
  - unknown subtitle node
  - date node
  - body heading node
  - body node
  - table paragraph node
  - header node
  - footer node

  Assert:

  ```tsx
  expect(screen.getByText('PARAGRAPH')).toBeInTheDocument();
  expect(screen.getByText('TABLE_PARAGRAPH')).toBeInTheDocument();
  expect(screen.getByText('HEADER_PARAGRAPH')).toBeInTheDocument();
  expect(screen.getByText('FOOTER_PARAGRAPH')).toBeInTheDocument();
  expect(screen.getByLabelText('映射角色：在全区重点工作推进会上的讲话')).toHaveValue('TITLE');
  ```

- [x] **Step 2: Add failing UI test for batch actions**

  Assert that selecting two nodes and clicking `标为正文` changes both role selects to `BODY`, then save calls the provided `onSaveMappingDraft`.

- [x] **Step 3: Run failing frontend tests**

  Run:

  ```powershell
  cd frontend
  npm test -- src/components/template/TemplateParseWorkspace.test.tsx
  ```

  Expected:

  - FAIL because components do not exist.

- [x] **Step 4: Create `StructureNodeTree`**

  Required props:

  ```ts
  type StructureNodeTreeProps = {
    nodes: DocumentNode[];
    mappingItems: StructureMappingItem[];
    selectedNodeKeys: Set<string>;
    onToggleNode: (nodeKey: string) => void;
    onRoleChange: (nodeKey: string, role: string, sortOrder: number) => void;
  };
  ```

  Required rendering:

  - Node text preview.
  - `node.nodeType`.
  - role select.
  - risk chips when `riskCodes.length > 0`.
  - stable checkbox per node.

- [x] **Step 5: Create `MappingBulkToolbar`**

  Required actions:

  ```ts
  onApplyRole('BODY')
  onApplyRole('BODY_HEADING_LEVEL_1')
  onApplyRole('STATIC_TEXT')
  onApplyRole('IGNORE')
  ```

  Disable buttons when no nodes are selected.

- [x] **Step 6: Create `TemplateParseWorkspace`**

  Move existing parse-workspace body from `App.tsx` and wire new tree/toolbar.

  Required display:

  - Document kind card.
  - Render preview card.
  - Structure facts card.
  - Mapping actions card.
  - Validation messages.

- [x] **Step 7: Replace inline App workspace**

  Modify `App.tsx` so it imports and uses:

  ```tsx
  <TemplateParseWorkspace
    documentKind={documentKind ?? documentKindFromProfile(profile)}
    mappingItems={mappingItems}
    mappingMessage={mappingMessage}
    mappingProfile={mappingProfile}
    mappingStatus={mappingStatus}
    profile={profile}
    structureProfile={structureProfile}
    renderPreview={renderPreview}
    renderPreviewStatus={renderPreviewStatus}
    renderPreviewMessage={renderPreviewMessage}
    onMappingRoleChange={handleMappingRoleChange}
    onPublishMapping={handlePublishMapping}
    onSaveMappingDraft={handleSaveMappingDraft}
    onRequestRenderPreview={handleRequestRenderPreview}
  />
  ```

- [x] **Step 8: Run frontend focused tests and build**

  Run:

  ```powershell
  cd frontend
  npm test -- src/components/template/TemplateParseWorkspace.test.tsx src/App.test.tsx
  npm run build
  ```

  Expected:

  - PASS.

- [x] **Step 9: Update Progress Board and Progress Log**

  Record exact commands and result.

## Task T23: Workbench Structured Preview And Node Tree Cleanup

**Files:**

- Create: `frontend/src/components/workbench/WorkbenchPreview.tsx`
- Create: `frontend/src/components/workbench/WorkbenchStructureTree.tsx`
- Modify: `frontend/src/workbenchNodes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/workbenchNodes.test.ts`
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`

**Implementation Requirements:**

- Workbench should consume `DraftNode` records without injecting template `UNIT/META` nodes above title.
- Header/footer/table static nodes can be visible in the structure tree but must not distort the editable paper preview.
- The preview label must clarify it is a structured editing preview.
- Add UI action to reinitialize nodes from source document using T21 endpoint.
- Reinitialize action must warn that user-filled nodes can be preserved.

**Steps:**

- [x] **Step 1: Add failing workbench node derivation test**

  In `workbenchNodes.test.ts`, add a draft with:

  - title node content `在全区重点工作推进会上的讲话`
  - date node
  - recipient node
  - heading node `一、提高政治站位...`
  - body node `抓落实是检验干部作风...`
  - static table node

  Assert:

  ```ts
  const nodes = deriveWorkbenchNodes(draft, profile, {});
  expect(nodes.map((node) => node.nodeType)).toContain('TITLE');
  expect(nodes.filter((node) => node.nodeType === 'BODY_SECTION')).toHaveLength(1);
  expect(nodes.find((node) => node.nodeType === 'TITLE')?.content).toBe('在全区重点工作推进会上的讲话');
  expect(nodes.some((node) => node.content.includes('未命名通知'))).toBe(false);
  ```

- [x] **Step 2: Add failing App test for reinitialize action**

  In `App.test.tsx`, assert button `从原稿重建结构` calls:

  ```text
  POST /api/drafts/1/nodes/reinitialize
  ```

  with:

  ```json
  {"mode":"FROM_SOURCE_DOCUMENT","preserveUserEditedNodes":true}
  ```

- [x] **Step 3: Run failing frontend tests**

  Run:

  ```powershell
  cd frontend
  npm test -- src/workbenchNodes.test.ts src/App.test.tsx
  ```

  Expected:

  - FAIL until components/API are added.

- [x] **Step 4: Add API client function**

  In `frontend/src/api.ts`, add:

  ```ts
  export function reinitializeDraftNodes(draftId: number, preserveUserEditedNodes = true) {
    return requestJson<DraftNode[]>(`/api/drafts/${draftId}/nodes/reinitialize`, {
      method: 'POST',
      body: JSON.stringify({
        mode: 'FROM_SOURCE_DOCUMENT',
        preserveUserEditedNodes,
      }),
    });
  }
  ```

- [x] **Step 5: Refine `deriveWorkbenchNodes`**

  Required behavior:

  - `TITLE`, `RECIPIENT`, `DATE`, `SIGNATURE`, `ATTACHMENT` map to editable singleton nodes.
  - `BODY_HEADING_LEVEL_*` pairs with following `BODY` node.
  - `STATIC_TEXT`, `HEADER_PARAGRAPH`, `FOOTER_PARAGRAPH`, `TABLE_PARAGRAPH` appear in structure tree but not as top-of-paper content unless explicitly mapped as editable.
  - No `TemplateProfile` fallback may override existing `DraftNode` values.

- [x] **Step 6: Create `WorkbenchStructureTree`**

  Required:

  - Shows all workbench nodes and static facts.
  - Allows selecting editable nodes.
  - Shows static/ignored nodes as non-editable.
  - Uses status badges.

- [x] **Step 7: Create `WorkbenchPreview`**

  Required props:

  ```ts
  type WorkbenchPreviewProps = {
    title: string;
    recipient: string;
    date: string;
    signature: string;
    attachment: string;
    bodySectionNodes: WorkbenchNode[];
    selectedNodeId: string | null;
    onSelectNode: (nodeId: string) => void;
    onUpdateTitle: (content: string) => void;
    onUpdateRecipient: (content: string) => void;
    onUpdateBodyHeading: (node: WorkbenchNode, heading: string) => void;
    onUpdateBodyContent: (node: WorkbenchNode, content: string) => void;
  };
  ```

- [x] **Step 8: Wire reinitialize action**

  Add UI near structure tree:

  - Button: `从原稿重建结构`
  - Description: `按当前已发布映射重新生成节点，可保留用户已编辑内容。`
  - On success: replace `draftNodes`, clear dirty node ids, mark true preview stale.

- [x] **Step 9: Run frontend focused tests and build**

  Run:

  ```powershell
  cd frontend
  npm test -- src/workbenchNodes.test.ts src/App.test.tsx
  npm run build
  ```

  Expected:

  - PASS.

- [x] **Step 10: Update Progress Board and Progress Log**

  Record commands and result.

## Task T24: Original DOCX In-Place Replacement Export

**Files:**

- Create: `backend/src/main/java/com/gongwen/assistant/exporting/word/DocxNodeReplacementRenderer.java`
- Create: `backend/src/main/java/com/gongwen/assistant/exporting/word/DocxNodeLocator.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/DraftWordExportService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/word/DocxTemplateRenderer.java`
- Test: `backend/src/test/java/com/gongwen/assistant/exporting/word/DocxNodeReplacementRendererTest.java`
- Test: `backend/src/test/java/com/gongwen/assistant/exporting/DraftWordExportServiceTest.java`

**Implementation Requirements:**

- Placeholder templates keep the existing placeholder-render path.
- `REFERENCE_DOCUMENT` and `OFFICIAL_DOCUMENT` use original DOCX in-place replacement when mapped nodes have locators.
- Replacement must preserve paragraph objects and formatting.
- A single-line replacement stays in the original paragraph.
- A multi-line replacement uses line breaks inside the same paragraph in first implementation.
- `IGNORE` nodes are cleared, not physically removed, to preserve layout stability.
- `STATIC_TEXT`, header/footer, table static text are preserved by default.
- Export trace must record strategy:

  ```text
  PLACEHOLDER_REPLACEMENT
  ORIGINAL_NODE_REPLACEMENT
  GENERATED_SNAPSHOT
  ```

**Steps:**

- [x] **Step 1: Add failing renderer test for paragraph in-place replacement**

  In `DocxNodeReplacementRendererTest.java`, add:

  ```java
  @Test
  void replacesMappedParagraphInPlaceAndPreservesFormatting() {
      byte[] template = DocxTestFactory.speechReferenceDocument();
      Map<String, String> replacements = Map.of(
              "paragraph-0", "新的会议讲话标题",
              "paragraph-4", "新的第一段正文"
      );

      byte[] rendered = renderer.render(template, replacements, Set.of());

      XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered));
      assertThat(document.getParagraphs().get(0).getText()).isEqualTo("新的会议讲话标题");
      assertThat(document.getParagraphs().get(4).getText()).isEqualTo("新的第一段正文");
      assertThat(document.getParagraphs().get(0).getAlignment().name()).isEqualTo("CENTER");
      assertThat(document.getHeaderList().getFirst().getParagraphs().getFirst().getText()).isEqualTo("内部测试资料");
  }
  ```

- [x] **Step 2: Add failing service strategy test**

  In `DraftWordExportServiceTest.java`, add:

  ```java
  @Test
  void referenceDocumentExportUsesOriginalNodeReplacementStrategy() {
      // Arrange template analysis documentKind REFERENCE_DOCUMENT,
      // published mapping with TITLE and BODY,
      // draft nodes with templateNodeKey paragraph-0 and paragraph-4.
      ExportRecordDto result = service.exportDraft(5L);

      assertThat(result.status()).isEqualTo("SUCCESS");
      assertThat(exportTrace(result).strategy()).isEqualTo("ORIGINAL_NODE_REPLACEMENT");
  }
  ```

- [x] **Step 3: Run failing export tests**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.exporting.word.DocxNodeReplacementRendererTest" --tests "com.gongwen.assistant.exporting.DraftWordExportServiceTest"
  ```

  Expected:

  - FAIL because renderer/strategy do not exist.

- [x] **Step 4: Implement `DocxNodeLocator`**

  Required methods:

  ```java
  Optional<XWPFParagraph> findParagraph(XWPFDocument document, String nodeKey)
  ```

  Node key rules:

  - `paragraph-{n}` maps to `document.getParagraphs().get(n)`.
  - `table-{t}-row-{r}-cell-{c}-paragraph-{p}` maps to table cell paragraph.
  - `header-{h}-paragraph-{p}` maps to header paragraph.
  - `footer-{f}-paragraph-{p}` maps to footer paragraph.

- [x] **Step 5: Implement `DocxNodeReplacementRenderer`**

  Required method:

  ```java
  public byte[] render(byte[] templateBytes, Map<String, String> replacementsByNodeKey, Set<String> ignoredNodeKeys)
  ```

  Behavior:

  - Open original DOCX.
  - For ignored nodes, replace paragraph text with empty string.
  - For replacement nodes, replace paragraph text while preserving paragraph-level formatting and first-run formatting.
  - Write to byte array.

- [x] **Step 6: Wire export strategy**

  In `DraftWordExportService`, choose:

  ```java
  if (isPlaceholderTemplate(templateBytes)) {
      return placeholderRenderer.render(...);
  }
  if (isReferenceReplacementSupported(profile, mapping, nodes)) {
      return nodeReplacementRenderer.render(templateBytes, replacementMap(nodes), ignoredNodeKeys(mapping));
  }
  return snapshotRenderer.renderDraftSnapshot(...);
  ```

  Required:

  - `REFERENCE_DOCUMENT` cannot use the old clear-body `renderReferenceDraft`.
  - If locator is missing for a required node, fail with stable code `EXPORT_NODE_LOCATOR_MISSING`.

- [x] **Step 7: Run export tests**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.exporting.word.DocxNodeReplacementRendererTest" --tests "com.gongwen.assistant.exporting.DraftWordExportServiceTest" --tests "com.gongwen.assistant.exporting.word.DocxTemplateRendererTest"
  ```

  Expected:

  - PASS.

- [x] **Step 8: Update Progress Board and Progress Log**

  Record commands and result.

## Task T25: Fixture Regression And Render Verification

**Files:**

- Modify: `backend/src/test/resources/docx-fixtures/README.md`
- Create or modify: `backend/src/test/java/com/gongwen/assistant/support/DocxTestFactory.java`
- Create: `backend/src/test/java/com/gongwen/assistant/integration/DocxCompleteStructurePipelineTest.java`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/components/template/TemplateParseWorkspace.test.tsx`

**Implementation Requirements:**

- Add an integration-style backend test that covers upload -> structure facts -> semantic suggestions -> publish mapping -> initialize nodes -> export.
- Use lightweight generated DOCX fixtures where possible.
- Use `docs/examples/公文助手_讲话稿范文_示例.docx` as a manual/local fixture reference when available.
- Do not require LibreOffice in automated tests; add render smoke command for local verification when environment supports it.

**Steps:**

- [x] **Step 1: Add backend pipeline test**

  Create `DocxCompleteStructurePipelineTest.java` covering:

  ```text
  speech reference DOCX
  -> TemplateUploadService.upload
  -> DocumentStructureProfile has all facts
  -> StructureMappingService publishes confirmed title/body/date/recipient/body headings
  -> DraftNodeService.initializeNodes creates non-duplicated nodes
  -> DraftWordExportService exports with ORIGINAL_NODE_REPLACEMENT
  ```

- [x] **Step 2: Add duplicate guard assertion**

  In the pipeline test, assert:

  ```java
  List<String> bodyContents = nodes.stream()
          .filter(node -> "BODY".equals(node.role()))
          .map(DraftNodeDto::content)
          .toList();
  assertThat(bodyContents).doesNotHaveDuplicates();
  ```

- [x] **Step 3: Add frontend fixture behavior test**

  In template workspace tests, assert:

  - Reference document warning is visible.
  - No-placeholder document still shows mapping controls.
  - Unknown nodes remain visible.

- [x] **Step 4: Run focused regression**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.integration.DocxCompleteStructurePipelineTest" --tests "com.gongwen.assistant.documentstructure.*" --tests "com.gongwen.assistant.draft.node.*" --tests "com.gongwen.assistant.exporting.*"
  cd ..\frontend
  npm test -- src/components/template/TemplateParseWorkspace.test.tsx src/workbenchNodes.test.ts src/App.test.tsx
  npm run build
  ```

  Expected:

  - PASS.

- [x] **Step 5: Run optional local render smoke when LibreOffice exists**

  Run:

  ```powershell
  $env:GONGWEN_LIBREOFFICE_PATH='C:\Program Files\LibreOffice\program\soffice.exe'
  Invoke-RestMethod -Uri 'http://localhost:8080/api/render-previews/environment' -Method Get
  ```

  Expected:

  - If backend is running and LibreOffice is configured, status reports available.
  - If backend is not running, record "not run; backend not running" in Progress Log risk.

- [x] **Step 6: Update fixture README**

  Add categories:

  ```markdown
  - reference speech document without placeholders: verifies complete facts, speech heading suggestions, non-duplicated DraftNode initialization, and original-node replacement export.
  - no-placeholder reference document with table/header/footer: verifies supported non-body facts remain visible and preserved.
  ```

- [x] **Step 7: Update Progress Board and Progress Log**

  Record commands, result, and any render smoke limitation.

## Task T26: Final Integration Closure And Documentation

**Files:**

- Modify: `docs/superpowers/plans/2026-05-31-docx-complete-structure-template-pipeline.md`
- Modify: `docs/PROJECT_TASKS.md`
- Modify: `AGENTS.md`

**Steps:**

- [x] **Step 1: Check worktree and diff**

  Run:

  ```powershell
  git status --short --branch
  git diff --check
  ```

  Expected:

  - No whitespace errors beyond known line-ending warnings.
  - All P10E files are accounted for.

- [x] **Step 2: Check API route alignment**

  Run:

  ```powershell
  rg -n "/nodes/reinitialize|structure-facts|structure-profile|structure-mapping|render-preview" frontend/src backend/src/main/java
  ```

  Expected:

  - Every frontend P10E API call has a matching backend route.

- [x] **Step 3: Run final focused backend tests**

  Run:

  ```powershell
  cd backend
  .\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.documentstructure.*" --tests "com.gongwen.assistant.documentstructure.semantic.*" --tests "com.gongwen.assistant.draft.node.*" --tests "com.gongwen.assistant.exporting.*" --tests "com.gongwen.assistant.integration.DocxCompleteStructurePipelineTest"
  ```

  Expected:

  - PASS.

- [x] **Step 4: Run final focused frontend tests and build**

  Run:

  ```powershell
  cd frontend
  npm test -- src/components/template/TemplateParseWorkspace.test.tsx src/workbenchNodes.test.ts src/App.test.tsx
  npm run build
  ```

  Expected:

  - PASS.

- [x] **Step 5: Update `docs/PROJECT_TASKS.md`**

  Add P10E completion summary under current state:

  ```markdown
  - P10E DOCX 完整结构事实与无占位符范文套版：已完成 fact-first 结构抽取、独立语义建议、DraftNode 干净初始化、结构映射工作台增强、工作台结构预览修正和无占位符参考文档原位替换导出。
  ```

- [x] **Step 6: Update `AGENTS.md` current status**

  Add high-signal summary:

  ```text
  P10E 已把 DOCX 链路升级为 fact-first：DocumentStructureProfile 直接从 OOXML 抽取段落、表格、页眉页脚等事实；语义角色仅作为可校正建议；DraftNode 初始化按源节点文本生成，不再复制旧 BODY_PARAGRAPH；REFERENCE_DOCUMENT 导出走原 DOCX 节点原位替换。
  ```

- [x] **Step 7: Close plan**

  Update every Progress Board row to final status and add final Progress Log entry.

## Multi-Agent Execution Order

Recommended dispatch:

```text
Batch 1: T18
Batch 2: T19
Batch 3: T20
Batch 4: T21 and T22 can run in parallel after T20 contract is stable
Batch 5: T23 after T21/T22
Batch 6: T24 after T21 and T19
Batch 7: T25
Batch 8: T26
```

Parallel safety:

- T19 and T20 are sequential because semantic suggestions depend on fact nodes.
- T21 and T22 can run in parallel after T20 if API fields are frozen.
- T23 must wait for T21 because workbench reinitialize needs backend API.
- T24 can start after T19/T21 but must coordinate with T20 role names and T22 mapping items.
- T25 and T26 are integration-only.

## Verification Matrix

| Capability | Backend Test | Frontend Test | Manual/Smoke |
| --- | --- | --- | --- |
| Complete body paragraph extraction | `DocumentStructureExtractorTest` | `TemplateParseWorkspace.test.tsx` all nodes visible | Upload speech sample and inspect structure tree |
| Table/header/footer facts | `DocumentStructureExtractorTest` | `TemplateParseWorkspace.test.tsx` node type chips | LibreOffice original preview |
| Speech semantic suggestions | `DocumentSemanticSuggesterTest` | mapping role initial values | Template parse workspace role list |
| No duplicate DraftNode content | `DraftNodeServiceTest`; `DocxCompleteStructurePipelineTest` | `workbenchNodes.test.ts` | Reinitialize sample draft |
| Workbench preview cleanup | backend not applicable | `App.test.tsx`; `workbenchNodes.test.ts` | Open `http://localhost:5173/` |
| Reference export in-place replacement | `DocxNodeReplacementRendererTest`; `DraftWordExportServiceTest` | export status already covered in App tests | Export sample and open DOCX |
| Placeholder export unchanged | `DocxTemplateRendererTest`; `DraftWordExportServiceTest` | existing App export tests | Export placeholder notice sample |

## Final User-Visible Effect Checklist

Use this checklist during T26 before claiming completion:

- [ ] Uploading `公文助手_讲话稿范文_示例.docx` shows it as `参考范文` with workflow `先审核并映射结构`.
- [ ] Structure tree shows title, subtitle, date, recipient, all body paragraphs, all heading paragraphs, table cells, header, and footer.
- [ ] Mapping can be saved and published after confirming `TITLE` and at least one `BODY`.
- [ ] A new draft bound to the published mapping initializes from the sample without duplicate body paragraphs.
- [ ] The workbench does not show `未命名通知` unless the draft title is explicitly still that value.
- [ ] The workbench does not place body paragraphs above the title because of `UNIT`/`META` misclassification.
- [ ] Clicking `从原稿重建结构` rebuilds nodes and preserves edited nodes when requested.
- [ ] Export for the reference document preserves header/footer/table and replaces mapped paragraph content in place.
- [ ] Export for the placeholder notice sample still works through placeholder replacement.
