# P10B Export Reproduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make template structure formatting a real production loop by reusing saved P10B formatting in quality checks and final `.docx` export output.

**Architecture:** Keep `P10B` focused on one shared backend source of truth: merge `TemplateProfile.structures` defaults with `STRUCTURE_FORMATTING_OVERRIDE` overrides into an effective formatting view, then reuse that view in both `QualityCheckService` and `DocxTemplateRenderer`. Do not fold `P10C` node persistence or `P11` record/download work into this slice; this plan only closes the formatting reproduction gap.

**Tech Stack:** Spring Boot, PostgreSQL JSONB-backed template profile storage, Apache POI, JUnit 5, existing frontend React workbench and template APIs.

---

## File Structure

- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateEffectiveFormattingService.java`
  - Merge profile structure defaults and saved overrides into an effective formatting map keyed by semantic slot.
- Create: `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateEffectiveFormattingServiceTest.java`
  - Verify fallback-to-profile, override-precedence, and semantic slot resolution.
- Create: `backend/src/main/java/com/gongwen/assistant/exporting/word/ExportFormattingContext.java`
  - Small export-only model carrying effective formatting for title, recipient, body, signature, and date.
- Create: `backend/src/test/java/com/gongwen/assistant/exporting/word/DocxTemplateRendererTest.java`
  - Verify snapshot export and placeholder export both apply formatting.
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/WordExportRequest.java`
  - Carry optional export formatting context alongside placeholder values.
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/DraftWordExportService.java`
  - Build export formatting context from bound template version.
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/WordExportService.java`
  - Pass formatting context into renderer and keep existing export record behavior unchanged.
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/word/DocxTemplateRenderer.java`
  - Apply effective formatting in both placeholder and snapshot paths.
- Modify: `backend/src/main/java/com/gongwen/assistant/quality/QualityCheckService.java`
  - Reuse effective formatting and emit structure-format warnings/errors.
- Modify: `backend/src/test/java/com/gongwen/assistant/exporting/DraftWordExportServiceTest.java`
  - Verify draft export loads formatting context from template version.
- Modify: `backend/src/test/java/com/gongwen/assistant/quality/QualityCheckServiceTest.java`
  - Verify formatting-related quality items.
- Modify: `docs/PROJECT_TASKS.md`
  - Update P10B/P11 progress and remaining gaps.
- Modify: `AGENTS.md`
  - Record the new P10B contract once shipped.

## Task 1: Build A Shared Effective Formatting Resolver

**Files:**
- Create: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateEffectiveFormattingService.java`
- Create: `backend/src/test/java/com/gongwen/assistant/template/profile/TemplateEffectiveFormattingServiceTest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/template/profile/TemplateStructureFormattingProfile.java`

- [ ] **Step 1: Write the failing resolver test**

Add focused tests that prove three cases:

```java
@Test
void resolvesBodyFormattingUsingOverrideOverProfileDefault() {
    TemplateProfile profile = templateProfileWithStructures(
            structure("body-1", "BODY", formatting(null, 32, false, "LEFT", 420, 360, 0, 0)),
            structure("signature-1", "SIGNATURE", formatting("FangSong", 32, false, "RIGHT", 0, 0, 0, 0))
    );
    Map<String, TemplateStructureFormattingProfile> overrides = Map.of(
            "body-1", formatting("KaiTi", 32, null, null, 560, 420, null, null)
    );

    TemplateEffectiveFormattingService service = new TemplateEffectiveFormattingService();
    ExportFormattingContext context = service.resolve(profile, overrides);

    assertThat(context.body().fontFamily()).isEqualTo("KaiTi");
    assertThat(context.body().indentationFirstLine()).isEqualTo(560);
    assertThat(context.body().alignment()).isEqualTo("LEFT");
}

@Test
void fallsBackToProfileFormattingWhenNoOverrideExists() {
    TemplateProfile profile = templateProfileWithStructures(
            structure("title-1", "TITLE", formatting("FangSong", 44, true, "CENTER", 0, 0, 0, 240))
    );

    ExportFormattingContext context = new TemplateEffectiveFormattingService().resolve(profile, Map.of());

    assertThat(context.title().alignment()).isEqualTo("CENTER");
    assertThat(context.title().fontSizeHalfPoints()).isEqualTo(44);
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
cd D:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"
```

Expected: fail because the resolver and export formatting context do not exist yet.

- [ ] **Step 3: Implement the resolver**

Create a backend-only resolver that maps structure types to semantic export slots:

```java
public final class TemplateEffectiveFormattingService {
    public ExportFormattingContext resolve(
            TemplateProfile profile,
            Map<String, TemplateStructureFormattingProfile> overrides
    ) {
        return new ExportFormattingContext(
                resolveFirst(profile, overrides, List.of("TITLE")),
                resolveFirst(profile, overrides, List.of("RECIPIENT")),
                resolveFirst(profile, overrides, List.of("BODY")),
                resolveFirst(profile, overrides, List.of("SIGNATURE")),
                resolveFirst(profile, overrides, List.of("DATE"))
        );
    }
}
```

Rules:

- Override wins field-by-field over profile formatting.
- If a structure has no override, use parsed profile defaults.
- Keep this service read-only; do not move persistence into it.
- Keep semantic matching limited to `TITLE`, `RECIPIENT`, `BODY`, `SIGNATURE`, `DATE` in this slice.

- [ ] **Step 4: Re-run the focused test**

Run the same focused test command and confirm it passes.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/gongwen/assistant/template/profile backend/src/test/java/com/gongwen/assistant/template/profile
git commit -m "feat: resolve effective template formatting"
```

## Task 2: Pass Effective Formatting Into Draft Export

**Files:**
- Create: `backend/src/main/java/com/gongwen/assistant/exporting/word/ExportFormattingContext.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/WordExportRequest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/DraftWordExportService.java`
- Modify: `backend/src/test/java/com/gongwen/assistant/exporting/DraftWordExportServiceTest.java`

- [ ] **Step 1: Write the failing export service test**

Add a focused test that proves `DraftWordExportService` reads template profile plus saved formatting overrides and passes them into `WordExportRequest`:

```java
@Test
void exportDraftBuildsFormattingContextFromTemplateVersion() {
    // arrange draft with templateVersionId
    // arrange profile BODY/TITLE structures
    // arrange saved override for body first-line indent

    WordExportResult result = service.exportDraft(11L);

    verify(wordExportService).export(any(), requestCaptor.capture());
    assertThat(requestCaptor.getValue().formatting().body().indentationFirstLine()).isEqualTo(560);
    assertThat(requestCaptor.getValue().formatting().title().alignment()).isEqualTo("CENTER");
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```powershell
cd D:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.exporting.DraftWordExportServiceTest"
```

Expected: fail because `WordExportRequest` has no formatting field and `DraftWordExportService` does not load template formatting yet.

- [ ] **Step 3: Implement minimal request and service changes**

Update the export request contract:

```java
public record WordExportRequest(
        String templateName,
        int templateVersion,
        Map<String, String> values,
        ExportFormattingContext formatting
) {
}
```

In `DraftWordExportService`:

- Load `TemplateProfile` by `draft.templateVersionId()`.
- Load overrides via `TemplateStructureFormattingRepository.findOverrides(versionId)`.
- Resolve effective formatting with `TemplateEffectiveFormattingService`.
- Keep current placeholder value mapping unchanged in this task.

- [ ] **Step 4: Re-run the focused test**

Run the same focused backend test and confirm it passes.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/gongwen/assistant/exporting backend/src/test/java/com/gongwen/assistant/exporting
git commit -m "feat: attach effective formatting to draft export"
```

## Task 3: Reproduce Formatting In Final `.docx` Output

**Files:**
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/WordExportService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/exporting/word/DocxTemplateRenderer.java`
- Create: `backend/src/test/java/com/gongwen/assistant/exporting/word/DocxTemplateRendererTest.java`

- [ ] **Step 1: Write the failing renderer tests**

Add two tests:

```java
@Test
void snapshotExportAppliesTitleBodyAndSignatureFormatting() {
    byte[] bytes = renderer.renderDraftSnapshot(
            Map.of("标题", "关于召开会议的通知", "正文", "一、会议时间\n2026年6月1日上午9:30。", "落款", "综合管理部"),
            new ExportFormattingContext(
                    formatting("FangSong", 44, true, "CENTER", 0, 0, 0, 240),
                    null,
                    formatting("FangSong", 32, false, "LEFT", 560, 360, 0, 0),
                    formatting("FangSong", 32, false, "RIGHT", 0, 0, 0, 0),
                    null
            )
    );

    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
        assertThat(document.getParagraphs().get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
        assertThat(document.getParagraphs().get(1).getFirstLineIndent()).isEqualTo(560);
    }
}

@Test
void placeholderExportAppliesFormattingToMatchedParagraphs() {
    // load a tiny in-memory template with {{标题}} and {{正文}}
    // render with context and assert paragraph alignment / indent after replacement
}
```

- [ ] **Step 2: Run the focused renderer test to verify it fails**

Run:

```powershell
cd D:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.exporting.word.DocxTemplateRendererTest"
```

Expected: fail because renderer methods do not accept formatting context and no formatting is applied today.

- [ ] **Step 3: Implement formatting-aware rendering**

Apply formatting in both branches:

- For placeholder templates:
  - After text replacement, detect semantic placeholders in the paragraph text before replacement.
  - Apply matching paragraph/run formatting to title, recipient, body, signature, and date paragraphs.
- For snapshot templates:
  - Replace the current hardcoded title/body/right-align defaults with `ExportFormattingContext`.
  - Fall back to current hardcoded defaults only when the context slot is null.

Keep this task scoped:

- Do not redesign the placeholder parser.
- Do not attempt generic OOXML cloning.
- Do not introduce `draft_node` persistence here.

- [ ] **Step 4: Re-run the focused renderer test**

Run the same focused test and confirm it passes.

- [ ] **Step 5: Run the existing export service test**

Run:

```powershell
cd D:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.exporting.DraftWordExportServiceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/gongwen/assistant/exporting backend/src/test/java/com/gongwen/assistant/exporting
git commit -m "feat: reproduce template formatting in word export"
```

## Task 4: Reuse Effective Formatting In Quality Check

**Files:**
- Modify: `backend/src/main/java/com/gongwen/assistant/quality/QualityCheckService.java`
- Modify: `backend/src/test/java/com/gongwen/assistant/quality/QualityCheckServiceTest.java`

- [ ] **Step 1: Write the failing quality test**

Add tests that prove quality check reads effective formatting and emits actionable findings for critical official-document rules:

```java
@Test
void addsWarningWhenTitleIsNotCenteredInEffectiveFormatting() {
    // arrange draft with templateVersionId and profile title structure alignment LEFT
    QualityCheckResponse response = service.runCheck(7L);

    assertThat(response.items()).anyMatch(item ->
            item.code().equals("TEMPLATE_TITLE_ALIGNMENT_RISK")
                    && item.severity().equals("WARNING"));
}

@Test
void addsWarningWhenBodyFirstLineIndentIsMissing() {
    QualityCheckResponse response = service.runCheck(7L);

    assertThat(response.items()).anyMatch(item ->
            item.code().equals("TEMPLATE_BODY_INDENT_RISK"));
}
```

- [ ] **Step 2: Run the focused quality test to verify it fails**

Run:

```powershell
cd D:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.quality.QualityCheckServiceTest"
```

Expected: fail because `QualityCheckService` currently checks placeholders and validation items only.

- [ ] **Step 3: Implement minimal rule set**

Add P10B-specific quality items based on effective formatting:

- `TEMPLATE_TITLE_ALIGNMENT_RISK` when title alignment is not `CENTER`
- `TEMPLATE_BODY_INDENT_RISK` when body first-line indent is null or `<= 0`
- `TEMPLATE_BODY_SPACING_RISK` when body line spacing is null
- `TEMPLATE_SIGNATURE_ALIGNMENT_RISK` when signature alignment is not `RIGHT`
- `TEMPLATE_DATE_ALIGNMENT_RISK` when date alignment is not `RIGHT`

Severity for this slice:

- Use `WARNING` for format reproduction risks
- Do not block export on these items yet unless a genuine export failure already exists
- Keep `exportBlocked` behavior unchanged in this task

- [ ] **Step 4: Re-run the focused quality test**

Run the same focused backend test and confirm it passes.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/java/com/gongwen/assistant/quality backend/src/test/java/com/gongwen/assistant/quality
git commit -m "feat: add template formatting quality checks"
```

## Task 5: Validate The Closed Loop And Update Docs

**Files:**
- Modify: `docs/PROJECT_TASKS.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Run the backend focused suite for this slice**

Run:

```powershell
cd D:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.exporting.word.DocxTemplateRendererTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.exporting.DraftWordExportServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.quality.QualityCheckServiceTest"
```

Expected: all PASS.

- [ ] **Step 2: Run the full backend test suite**

Run:

```powershell
cd D:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test.ps1
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Update project docs**

Update `docs/PROJECT_TASKS.md` to reflect:

- P10B now reuses saved structure formatting in export and quality check
- Remaining P10B gap is richer style inheritance and unsupported OOXML surfacing
- P11 still owns export blocking, record list, and download history

Update `AGENTS.md` to reflect:

- Effective formatting is now the shared contract across preview, quality, and export
- Critical current reproduction rules are title center, body indent/spacing, signature right-align, date right-align

- [ ] **Step 4: Commit**

```powershell
git add docs/PROJECT_TASKS.md AGENTS.md
git commit -m "docs: record p10b export reproduction contract"
```

## Scope Guardrails

- Keep `P10B` focused on formatting reproduction. Do not absorb `P11` export record list/history download into this slice.
- Do not introduce `draft_node` persistence here; that remains `P10C`.
- Do not attempt full Word style-engine parity. This slice only closes the main public-sector formatting rules already exposed in P10B UI.
- If a format cannot be reproduced safely, emit a quality warning instead of silently pretending success.

## Exit Criteria

- Saved structure formatting is the backend source of truth, not just a frontend preview helper.
- Final `.docx` export visibly reflects title/body/signature/date formatting for both placeholder and snapshot export paths.
- Quality check surfaces formatting risks using the same effective formatting data.
- `docs/PROJECT_TASKS.md` and `AGENTS.md` are updated to describe the new closed-loop contract.
