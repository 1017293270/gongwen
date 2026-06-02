# Node Format Backfill And Paragraph Candidates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Backfill workbench node formatting from existing DOCX/template styles and replace direct paragraph generation with a batch candidate canvas that streams generated text, preserves candidates, and applies replacements only after user confirmation.

**Architecture:** Keep formatting resolution backend-owned so preview, export, and UI read the same effective style contract. Add persisted paragraph candidates as a draft-scoped buffer between AI generation and `DraftNode` writes. The frontend keeps the existing three-column workbench, adding a right-panel candidate canvas with streaming, stop, retry, edit, single accept, and batch accept states.

**Tech Stack:** Spring Boot, PostgreSQL/Flyway, Jackson, Java `HttpClient`, SSE via Spring MVC `SseEmitter`, React + TypeScript + Vite, Vitest/React Testing Library, JUnit/MockMvc.

---

## Source Specs

- `docs/superpowers/specs/2026-06-02-node-format-backfill-and-paragraph-candidates-design.md`
- `AGENTS.md`
- `DESIGN.md`
- `docs/PROJECT_TASKS.md`

## Current Baseline

- Current branch: `codex/p10d-docx-structure-workbench`
- Current migration baseline from `AGENTS.md`: Flyway has reached `V19`.
- Next migration for this plan: `V20__ai_paragraph_candidate.sql`.
- Existing direct paragraph endpoint: `POST /api/drafts/{draftId}/ai/paragraph`.
- Existing node format endpoint: `PUT/DELETE /api/drafts/{draftId}/nodes/{nodeId}/format-override`.
- Existing candidate-free generation writes immediately in `backend/src/main/java/com/gongwen/assistant/ai/AiParagraphService.java`.

## File Structure

Backend format backfill:

- Modify `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeDto.java`
  - Add `baseFormatting` and `effectiveFormatting`.
- Create `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeFormattingResolver.java`
  - Resolve source DOCX/template formatting and merge draft node overrides.
- Modify `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeService.java`
  - Return hydrated DTOs from initialize, reinitialize, list, insert, update, save format, and restore format flows.
- Modify `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeServiceTest.java`
  - Cover default formatting, override formatting, and synthetic style source fallback.

Frontend format backfill:

- Modify `frontend/src/draftTypes.ts`
  - Add `baseFormatting` and `effectiveFormatting` to `DraftNode`.
- Modify `frontend/src/workbenchNodes.ts`
  - Prefer `effectiveFormatting` over locally derived override-only formatting.
- Modify `frontend/src/components/workbench/NodeFormatPanel.tsx`
  - Show effective values, track per-field overrides, and submit only override fields.
- Modify `frontend/src/App.tsx`
  - Pass effective formatting to the panel.
- Modify `frontend/src/App.test.tsx`
  - Cover visible effective styles and restore behavior.

Backend candidates:

- Create `backend/src/main/resources/db/migration/V20__ai_paragraph_candidate.sql`
  - Persist draft-scoped paragraph candidates.
- Create package `backend/src/main/java/com/gongwen/assistant/ai/candidate/**`
  - Candidate record, repository, DTOs, service, controller, stream job support.
- Modify `backend/src/main/java/com/gongwen/assistant/ai/ModelAdapter.java`
  - Add candidate generation and optional streaming.
- Modify `backend/src/main/java/com/gongwen/assistant/ai/DeepSeekModelAdapter.java`
  - Add text-only paragraph candidate generation and streaming path.
- Modify `backend/src/main/java/com/gongwen/assistant/ai/MockModelAdapter.java`
  - Add deterministic candidate output and streaming chunks.
- Modify `backend/src/main/java/com/gongwen/assistant/ai/PromptBuilder.java`
  - Add candidate prompt builder or reuse paragraph prompt with text-only constraints.
- Add tests under `backend/src/test/java/com/gongwen/assistant/ai/candidate/**`
  - Repository, service, controller, stream event behavior.

Frontend candidates:

- Modify `frontend/src/draftTypes.ts`
  - Add paragraph candidate and candidate event types.
- Modify `frontend/src/api.ts`
  - Add candidate CRUD, accept, batch accept, and event stream helpers.
- Create `frontend/src/components/workbench/ParagraphCandidateCanvas.tsx`
  - Right-panel candidate canvas.
- Modify `frontend/src/App.tsx`
  - Wire outline sections into batch candidate generation and acceptance.
- Modify `frontend/src/App.test.tsx`
  - Cover candidate generation without direct replacement, single accept, batch accept, stop, retry, and error states.

Docs:

- Modify `AGENTS.md`
  - Update current state and AI generation behavior.
- Modify `docs/PROJECT_TASKS.md`
  - Add completion entries for this plan.

---

## Task A1: Backend Node Formatting Backfill

**Files:**
- Modify: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeDto.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeFormattingResolver.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeService.java`
- Test: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Add tests that assert a listed draft node includes template-derived formatting and that saved overrides win.

```java
@Test
void listNodesReturnsBaseAndEffectiveFormatting() {
    long draftId = createDraftWithPublishedMappingAndStructure();
    List<DraftNodeDto> nodes = service.initializeNodes(draftId);

    DraftNodeDto body = nodes.stream()
            .filter(node -> "BODY".equals(node.role()))
            .findFirst()
            .orElseThrow();

    assertThat(body.baseFormatting().eastAsiaFontFamily()).isEqualTo("仿宋_GB2312");
    assertThat(body.baseFormatting().fontSizeHalfPoints()).isEqualTo(32);
    assertThat(body.effectiveFormatting().eastAsiaFontFamily()).isEqualTo("仿宋_GB2312");
    assertThat(body.effectiveFormatting().fontSizeHalfPoints()).isEqualTo(32);
}

@Test
void listNodesMergesDraftFormatOverrideIntoEffectiveFormatting() {
    long draftId = createDraftWithPublishedMappingAndStructure();
    DraftNodeDto body = service.initializeNodes(draftId).stream()
            .filter(node -> "BODY".equals(node.role()))
            .findFirst()
            .orElseThrow();

    service.saveFormatOverride(draftId, body.id(), new DraftNodeFormatOverride(
            "方正仿宋简体",
            null,
            18.0,
            true,
            "BOTH",
            720,
            "AUTO",
            180,
            null,
            null
    ));

    DraftNodeDto updated = service.listNodes(draftId).stream()
            .filter(node -> node.id() == body.id())
            .findFirst()
            .orElseThrow();

    assertThat(updated.baseFormatting().eastAsiaFontFamily()).isEqualTo("仿宋_GB2312");
    assertThat(updated.effectiveFormatting().eastAsiaFontFamily()).isEqualTo("方正仿宋简体");
    assertThat(updated.effectiveFormatting().fontSizeHalfPoints()).isEqualTo(36);
    assertThat(updated.effectiveFormatting().bold()).isTrue();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest"
```

Expected: FAIL because `DraftNodeDto` has no `baseFormatting` or `effectiveFormatting`.

- [ ] **Step 3: Extend DTO**

Change `DraftNodeDto` to include formatting:

```java
public record DraftNodeDto(
        long id,
        long draftId,
        Long structureMappingProfileId,
        String templateNodeKey,
        String parentTemplateNodeKey,
        String nodeType,
        String role,
        String slotKey,
        String title,
        String content,
        int sortOrder,
        String status,
        DraftNodeFormatOverride formatOverride,
        TemplateStructureFormattingProfile baseFormatting,
        TemplateStructureFormattingProfile effectiveFormatting,
        DraftNodeMetadata metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public static DraftNodeDto from(
            DraftNode node,
            TemplateStructureFormattingProfile baseFormatting,
            TemplateStructureFormattingProfile effectiveFormatting
    ) {
        return new DraftNodeDto(
                node.id(),
                node.draftId(),
                node.structureMappingProfileId(),
                node.templateNodeKey(),
                node.parentTemplateNodeKey(),
                node.nodeType(),
                node.role(),
                node.slotKey(),
                node.title(),
                node.content(),
                node.sortOrder(),
                node.status(),
                node.formatOverride(),
                baseFormatting,
                effectiveFormatting,
                node.metadata(),
                node.createdAt(),
                node.updatedAt()
        );
    }
}
```

Keep a compatibility overload only if tests or existing call sites still need it:

```java
public static DraftNodeDto from(DraftNode node) {
    return from(node, null, null);
}
```

- [ ] **Step 4: Add `DraftNodeFormattingResolver`**

Create a focused resolver:

```java
@Service
public class DraftNodeFormattingResolver {
    private final DocumentStructureProfileRepository structureProfileRepository;
    private final TemplateStructureFormattingRepository formattingRepository;
    private final TemplateEffectiveFormattingService effectiveFormattingService;

    public DraftNodeFormattingResolver(
            DocumentStructureProfileRepository structureProfileRepository,
            TemplateStructureFormattingRepository formattingRepository,
            TemplateEffectiveFormattingService effectiveFormattingService
    ) {
        this.structureProfileRepository = structureProfileRepository;
        this.formattingRepository = formattingRepository;
        this.effectiveFormattingService = effectiveFormattingService;
    }

    public Map<Long, ResolvedDraftNodeFormatting> resolve(Long templateVersionId, List<DraftNode> nodes) {
        if (templateVersionId == null || nodes.isEmpty()) {
            return Map.of();
        }
        Map<String, DocumentNode> sourceNodes = structureProfileRepository.findByTemplateVersionId(templateVersionId)
                .map(profile -> profile.nodes().stream()
                        .collect(Collectors.toMap(DocumentNode::nodeKey, Function.identity(), (first, ignored) -> first)))
                .orElse(Map.of());
        Map<String, TemplateStructureFormattingProfile> templateOverrides =
                formattingRepository.findOverrides(templateVersionId);

        Map<Long, ResolvedDraftNodeFormatting> resolved = new LinkedHashMap<>();
        for (DraftNode node : nodes) {
            String sourceKey = sourceKeyFor(node);
            TemplateStructureFormattingProfile original = sourceNodes.containsKey(sourceKey)
                    ? sourceNodes.get(sourceKey).formatting()
                    : null;
            TemplateStructureFormattingProfile mappingOverride = templateOverrides.get(sourceKey);
            TemplateStructureFormattingProfile base = effectiveFormattingService.resolveDraftNodeFormatting(
                    null,
                    null,
                    original,
                    mappingOverride,
                    DraftNodeFormatOverride.empty()
            );
            TemplateStructureFormattingProfile effective = effectiveFormattingService.resolveDraftNodeFormatting(
                    null,
                    null,
                    original,
                    mappingOverride,
                    node.formatOverride()
            );
            resolved.put(node.id(), new ResolvedDraftNodeFormatting(base, effective));
        }
        return resolved;
    }

    private String sourceKeyFor(DraftNode node) {
        if (node.metadata() != null && node.metadata().synthetic() && !node.metadata().styleSourceNodeKey().isBlank()) {
            return node.metadata().styleSourceNodeKey();
        }
        return node.templateNodeKey();
    }

    public record ResolvedDraftNodeFormatting(
            TemplateStructureFormattingProfile baseFormatting,
            TemplateStructureFormattingProfile effectiveFormatting
    ) {
    }
}
```

- [ ] **Step 5: Hydrate DTOs in `DraftNodeService`**

Inject `DraftNodeFormattingResolver` and replace `toDtos(List<DraftNode> nodes)` with a draft-aware overload:

```java
private List<DraftNodeDto> toDtos(DraftDetailDto draft, List<DraftNode> nodes) {
    Map<Long, DraftNodeFormattingResolver.ResolvedDraftNodeFormatting> formattingByNodeId =
            formattingResolver.resolve(draft.templateVersionId(), nodes);
    return nodes.stream()
            .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
            .map(node -> {
                DraftNodeFormattingResolver.ResolvedDraftNodeFormatting formatting = formattingByNodeId.get(node.id());
                return DraftNodeDto.from(
                        node,
                        formatting == null ? null : formatting.baseFormatting(),
                        formatting == null ? null : formatting.effectiveFormatting()
                );
            })
            .toList();
}
```

Update every service return path so it has `DraftDetailDto draft` available before converting.

- [ ] **Step 6: Run focused backend tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest" --tests "com.gongwen.assistant.template.profile.TemplateEffectiveFormattingServiceTest"
```

Expected: PASS.

- [ ] **Step 7: Commit Task A1**

```powershell
git add backend/src/main/java/com/gongwen/assistant/draft/node backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeServiceTest.java
git commit -m "feat: backfill draft node effective formatting"
```

---

## Task A2: Frontend Format Panel Backfill

**Files:**
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/workbenchNodes.ts`
- Modify: `frontend/src/components/workbench/NodeFormatPanel.tsx`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write failing frontend tests**

Add an `App.test.tsx` case where a selected node has `effectiveFormatting` but empty `formatOverride`.

```tsx
it('shows effective node formatting even when no override is saved', async () => {
  mockDraftDetail({
    nodes: [{
      ...sampleDraftNodes()[2],
      formatOverride: emptyFormatOverride(),
      baseFormatting: {
        fontFamily: '仿宋_GB2312',
        eastAsiaFontFamily: '仿宋_GB2312',
        latinFontFamily: 'Times New Roman',
        fontSizeHalfPoints: 32,
        bold: false,
        alignment: 'BOTH',
        indentationFirstLine: 720,
        spacingBetween: null,
        spacingBefore: 0,
        spacingAfter: 0,
        colorHex: null,
        lineSpacing: { mode: 'AUTO', valueTwips: null, multipleHundred: 180 },
      },
      effectiveFormatting: {
        fontFamily: '仿宋_GB2312',
        eastAsiaFontFamily: '仿宋_GB2312',
        latinFontFamily: 'Times New Roman',
        fontSizeHalfPoints: 32,
        bold: false,
        alignment: 'BOTH',
        indentationFirstLine: 720,
        spacingBetween: null,
        spacingBefore: 0,
        spacingAfter: 0,
        colorHex: null,
        lineSpacing: { mode: 'AUTO', valueTwips: null, multipleHundred: 180 },
      },
    }],
  });

  render(<App />);
  await userEvent.click(await screen.findByText('正文'));

  expect(screen.getByLabelText('中文字体')).toHaveValue('仿宋_GB2312');
  expect(screen.getByLabelText('西文字体')).toHaveValue('Times New Roman');
  expect(screen.getByLabelText('字号')).toHaveValue(16);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd frontend
npm test -- src/App.test.tsx
```

Expected: FAIL because the panel still reads only `formatOverride`.

- [ ] **Step 3: Extend TypeScript types**

Add `baseFormatting` and `effectiveFormatting` to `DraftNode`:

```ts
export type DraftNode = {
  id: number;
  draftId: number;
  structureMappingProfileId: number | null;
  templateNodeKey: string;
  parentTemplateNodeKey: string | null;
  nodeType: string;
  role: string;
  slotKey: string;
  title: string;
  content: string;
  sortOrder: number;
  status: string;
  formatOverride: DraftNodeFormatOverride;
  baseFormatting?: TemplateStructureFormatting | null;
  effectiveFormatting?: TemplateStructureFormatting | null;
  metadata?: DraftNodeMetadata | null;
  createdAt: string;
  updatedAt: string;
};
```

- [ ] **Step 4: Prefer effective formatting in workbench nodes**

Replace override-only formatting derivation:

```ts
function formattingFromDraftNode(node: DraftNode): Partial<TemplateStructureFormatting> {
  return node.effectiveFormatting ?? draftOverrideToTemplateFormatting(node.formatOverride);
}

function draftOverrideToTemplateFormatting(override: DraftNodeFormatOverride): Partial<TemplateStructureFormatting> {
  return {
    fontFamily: override?.eastAsiaFont ?? null,
    eastAsiaFontFamily: override?.eastAsiaFont ?? null,
    latinFontFamily: override?.latinFont ?? null,
    fontSizeHalfPoints: override?.fontSizePt ? Math.round(override.fontSizePt * 2) : null,
    bold: override?.bold ?? null,
    alignment: override?.alignment ?? null,
    indentationFirstLine: override?.firstLineIndentTwip ?? null,
    lineSpacing: override?.lineSpacingRule || override?.lineSpacingTwip ? {
      mode: override.lineSpacingRule ?? 'AUTO',
      valueTwips: override.lineSpacingRule === 'AUTO' ? null : override.lineSpacingTwip ?? null,
      multipleHundred: override.lineSpacingRule === 'AUTO' || !override.lineSpacingRule ? override.lineSpacingTwip ?? null : null,
    } : null,
    spacingBefore: override?.spacingBeforeTwip ?? null,
    spacingAfter: override?.spacingAfterTwip ?? null,
  };
}
```

- [ ] **Step 5: Update `NodeFormatPanel` props**

Add effective formatting and compute display draft from it:

```ts
type NodeFormatPanelProps = {
  disabled: boolean;
  effectiveFormatting: TemplateStructureFormatting | null;
  error: string;
  formatOverride: DraftNodeFormatOverride | null;
  nodeLabel: string;
  onRestore: () => void;
  onSave: (formatOverride: DraftNodeFormatOverride) => void;
  previewOutdated: boolean;
  status: NodeFormatPanelStatus;
};
```

Use:

```ts
const [dirtyFields, setDirtyFields] = useState<Set<keyof DraftNodeFormatOverride>>(() => new Set());

useEffect(() => {
  setDraft(overrideWithEffectiveDefaults(formatOverride, effectiveFormatting));
  setDirtyFields(new Set());
}, [formatOverride, effectiveFormatting]);
```

Build save payload with only changed fields plus existing overrides:

```ts
function buildOverridePayload(
  currentOverride: DraftNodeFormatOverride | null,
  draft: DraftNodeFormatOverride,
  dirtyFields: Set<keyof DraftNodeFormatOverride>,
): DraftNodeFormatOverride {
  const payload = normalizeOverride(currentOverride);
  dirtyFields.forEach((field) => {
    payload[field] = draft[field] as never;
  });
  return payload;
}
```

- [ ] **Step 6: Pass effective formatting from `App.tsx`**

Use:

```tsx
<NodeFormatPanel
  disabled={selectedNodeFormatDisabled}
  effectiveFormatting={selectedDraftNode?.effectiveFormatting ?? selectedNode?.formatting ?? null}
  error={nodeFormatError}
  formatOverride={selectedDraftNode?.formatOverride ?? null}
  nodeLabel={selectedNodeFormatLabel}
  onRestore={() => void handleRestoreNodeFormatOverride()}
  onSave={(formatOverride) => void handleSaveNodeFormatOverride(formatOverride)}
  previewOutdated={renderPreviewOutdated}
  status={nodeFormatStatus}
/>
```

- [ ] **Step 7: Run frontend tests and build**

Run:

```powershell
cd frontend
npm test -- src/workbenchNodes.test.ts src/App.test.tsx
npm run build
```

Expected: PASS.

- [ ] **Step 8: Commit Task A2**

```powershell
git add frontend/src/draftTypes.ts frontend/src/workbenchNodes.ts frontend/src/components/workbench/NodeFormatPanel.tsx frontend/src/App.tsx frontend/src/App.test.tsx
git commit -m "feat: show effective node formatting in workbench"
```

---

## Task B1: Paragraph Candidate Persistence And DTOs

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__ai_paragraph_candidate.sql`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidate.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateDto.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/candidate/JdbcAiParagraphCandidateRepository.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/candidate/JdbcAiParagraphCandidateRepositoryTest.java`

- [ ] **Step 1: Add migration**

Create:

```sql
CREATE TABLE ai_paragraph_candidate (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL REFERENCES draft(id) ON DELETE CASCADE,
    target_node_id BIGINT NULL REFERENCES draft_node(id) ON DELETE SET NULL,
    target_node_role VARCHAR(80) NOT NULL DEFAULT '',
    target_node_title VARCHAR(255) NOT NULL DEFAULT '',
    outline_trace_id UUID NULL,
    paragraph_trace_id UUID NULL,
    section_index INTEGER NOT NULL,
    heading VARCHAR(500) NOT NULL DEFAULT '',
    points_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    instruction_summary VARCHAR(1000) NOT NULL DEFAULT '',
    candidate_text TEXT NOT NULL DEFAULT '',
    candidate_text_digest VARCHAR(128) NOT NULL DEFAULT '',
    status VARCHAR(40) NOT NULL,
    error_code VARCHAR(120) NOT NULL DEFAULT '',
    error_message VARCHAR(1000) NOT NULL DEFAULT '',
    accepted_at TIMESTAMPTZ NULL,
    accepted_by BIGINT NULL REFERENCES app_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_paragraph_candidate_draft ON ai_paragraph_candidate(draft_id, section_index, id);
CREATE INDEX idx_ai_paragraph_candidate_node ON ai_paragraph_candidate(target_node_id);
```

Existing migrations define the user table as `app_user`, so keep the `accepted_by` reference on `app_user(id)`.

- [ ] **Step 2: Write repository tests**

Test insert, list, update status/text, and delete:

```java
@Test
void insertsAndListsCandidatesInSectionOrder() {
    long draftId = testData.createDraft();
    repository.insert(newCandidate(draftId, 1, "一、背景"));
    repository.insert(newCandidate(draftId, 0, "标题"));

    List<AiParagraphCandidate> rows = repository.findByDraftId(draftId);

    assertThat(rows).extracting(AiParagraphCandidate::sectionIndex).containsExactly(0, 1);
    assertThat(rows.get(0).status()).isEqualTo("PENDING");
}

@Test
void updatesCandidateTextAndStatus() {
    long draftId = testData.createDraft();
    AiParagraphCandidate saved = repository.insert(newCandidate(draftId, 0, "一、背景"));

    AiParagraphCandidate updated = repository.updateTextAndStatus(
            saved.id(),
            "生成正文",
            "READY",
            "digest-1"
    ).orElseThrow();

    assertThat(updated.candidateText()).isEqualTo("生成正文");
    assertThat(updated.status()).isEqualTo("READY");
    assertThat(updated.candidateTextDigest()).isEqualTo("digest-1");
}
```

- [ ] **Step 3: Run repository tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.candidate.JdbcAiParagraphCandidateRepositoryTest"
```

Expected: FAIL because repository does not exist.

- [ ] **Step 4: Implement record, DTO, and repository**

Use immutable records:

```java
public record AiParagraphCandidate(
        long id,
        long draftId,
        Long targetNodeId,
        String targetNodeRole,
        String targetNodeTitle,
        UUID outlineTraceId,
        UUID paragraphTraceId,
        int sectionIndex,
        String heading,
        List<String> points,
        String instructionSummary,
        String candidateText,
        String candidateTextDigest,
        String status,
        String errorCode,
        String errorMessage,
        Instant acceptedAt,
        Long acceptedBy,
        Instant createdAt,
        Instant updatedAt
) {
}
```

Repository interface:

```java
public interface AiParagraphCandidateRepository {
    AiParagraphCandidate insert(AiParagraphCandidate candidate);
    List<AiParagraphCandidate> findByDraftId(long draftId);
    Optional<AiParagraphCandidate> findById(long candidateId);
    Optional<AiParagraphCandidate> updateTextAndStatus(long candidateId, String text, String status, String digest);
    Optional<AiParagraphCandidate> updateStatusAndError(long candidateId, String status, String errorCode, String errorMessage);
    Optional<AiParagraphCandidate> markAccepted(long candidateId, UUID paragraphTraceId, long acceptedBy);
    void delete(long candidateId);
}
```

- [ ] **Step 5: Run repository tests**

Run the same focused command from Step 3.

Expected: PASS.

- [ ] **Step 6: Commit Task B1**

```powershell
git add backend/src/main/resources/db/migration/V20__ai_paragraph_candidate.sql backend/src/main/java/com/gongwen/assistant/ai/candidate backend/src/test/java/com/gongwen/assistant/ai/candidate/JdbcAiParagraphCandidateRepositoryTest.java
git commit -m "feat: persist AI paragraph candidates"
```

---

## Task B2: Candidate Service, Accept, And Batch Accept

**Files:**
- Create: `backend/src/main/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateService.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateController.java`
- Create DTO request records under `backend/src/main/java/com/gongwen/assistant/ai/candidate/`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineController.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateServiceTest.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateControllerTest.java`

- [ ] **Step 1: Write service tests**

Cover no direct write on creation, single accept, and batch accept skip rules.

```java
@Test
void createBatchCandidatesDoesNotChangeDraftNodes() {
    long draftId = testData.createDraftWithNodes();
    String before = testData.bodyNodeContent(draftId);

    service.createBatch(draftId, new CreateParagraphCandidateBatchRequest(
            outlineTraceId,
            List.of(new CandidateSectionRequest(0, "一、背景", List.of("说明背景"), targetNodeId)),
            "补充要求"
    ));

    assertThat(testData.bodyNodeContent(draftId)).isEqualTo(before);
    assertThat(repository.findByDraftId(draftId)).hasSize(1);
}

@Test
void acceptCandidateWritesTargetNodeAndCompatibleBlock() {
    long draftId = testData.createDraftWithNodes();
    AiParagraphCandidate candidate = repository.insert(readyCandidate(draftId, targetNodeId, "候选正文"));

    AcceptParagraphCandidateResponse response = service.accept(draftId, candidate.id());

    assertThat(response.candidate().status()).isEqualTo("ACCEPTED");
    assertThat(testData.bodyNodeContent(draftId)).isEqualTo("候选正文");
    assertThat(testData.bodyBlockContent(draftId)).contains("候选正文");
}

@Test
void acceptBatchSkipsFailedAndDiscardedCandidates() {
    long draftId = testData.createDraftWithNodes();
    AiParagraphCandidate ready = repository.insert(readyCandidate(draftId, node1, "第一段"));
    repository.insert(errorCandidate(draftId, node2));
    repository.insert(discardedCandidate(draftId, node3));

    AcceptParagraphCandidateBatchResponse response = service.acceptBatch(draftId, List.of());

    assertThat(response.acceptedCandidateIds()).containsExactly(ready.id());
    assertThat(response.skippedCandidateIds()).hasSize(2);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.candidate.AiParagraphCandidateServiceTest"
```

Expected: FAIL because service does not exist.

- [ ] **Step 3: Implement request/response records**

Create:

```java
public record CandidateSectionRequest(
        int sectionIndex,
        String heading,
        List<String> points,
        Long targetNodeId
) {
}

public record CreateParagraphCandidateBatchRequest(
        UUID outlineTraceId,
        List<CandidateSectionRequest> sections,
        String instruction
) {
}

public record UpdateParagraphCandidateRequest(String candidateText) {
}

public record AcceptParagraphCandidateResponse(
        AiParagraphCandidateDto candidate,
        DraftDetailDto draft,
        DraftNodeDto node
) {
}

public record AcceptParagraphCandidateBatchResponse(
        List<Long> acceptedCandidateIds,
        List<Long> skippedCandidateIds,
        DraftDetailDto draft
) {
}
```

- [ ] **Step 4: Implement service rules**

Service must call `draftService.getDraft(draftId)` at the start of every public method.

Accept rule skeleton:

```java
public AcceptParagraphCandidateResponse accept(long draftId, long candidateId) {
    DraftDetailDto draft = draftService.getDraft(draftId);
    AiParagraphCandidate candidate = candidateForDraft(draftId, candidateId);
    if (!Set.of("READY", "EDITED").contains(candidate.status())) {
        throw new AiParagraphCandidateException("AI_CANDIDATE_NOT_ACCEPTABLE", "该候选正文当前不能确认替换");
    }
    DraftNode targetNode = targetNode(draftId, candidate.targetNodeId());
    if ("LOCKED".equals(targetNode.status()) || "DELETED".equals(targetNode.status())) {
        throw new AiParagraphCandidateException("AI_CANDIDATE_TARGET_UNAVAILABLE", "目标正文节点不可替换");
    }
    DraftNode updatedNode = draftNodeRepository.updateContent(
            draftId,
            targetNode.id(),
            candidate.candidateText(),
            "AI_GENERATED"
    ).orElseThrow();
    DraftDetailDto updatedDraft = draftService.updateBlocks(
            draftId,
            new UpdateDraftBlocksRequest(upsertCompatibleBlocks(draft, updatedNode, candidate.candidateText()))
    );
    AiParagraphCandidate accepted = repository.markAccepted(candidateId, candidate.paragraphTraceId(), currentUserId())
            .orElseThrow();
    return new AcceptParagraphCandidateResponse(
            AiParagraphCandidateDto.from(accepted),
            updatedDraft,
            DraftNodeDto.from(updatedNode)
    );
}
```

Use the same block materialization behavior as `AiParagraphService` so quality and export compatibility remain stable.

- [ ] **Step 5: Implement controller endpoints**

Use a dedicated controller:

```java
@RestController
@RequestMapping("/api/drafts/{draftId}/ai/paragraph-candidates")
public class AiParagraphCandidateController {
    @GetMapping
    public ApiResponse<List<AiParagraphCandidateDto>> list(@PathVariable long draftId) {
        return ApiResponse.ok(service.list(draftId));
    }

    @PostMapping("/batch")
    public ApiResponse<List<AiParagraphCandidateDto>> createBatch(
            @PathVariable long draftId,
            @RequestBody CreateParagraphCandidateBatchRequest request
    ) {
        return ApiResponse.ok(service.createBatch(draftId, request));
    }

    @PutMapping("/{candidateId}")
    public ApiResponse<AiParagraphCandidateDto> updateText(
            @PathVariable long draftId,
            @PathVariable long candidateId,
            @RequestBody UpdateParagraphCandidateRequest request
    ) {
        return ApiResponse.ok(service.updateText(draftId, candidateId, request));
    }

    @PostMapping("/{candidateId}/accept")
    public ApiResponse<AcceptParagraphCandidateResponse> accept(
            @PathVariable long draftId,
            @PathVariable long candidateId
    ) {
        return ApiResponse.ok(service.accept(draftId, candidateId));
    }

    @PostMapping("/accept-batch")
    public ApiResponse<AcceptParagraphCandidateBatchResponse> acceptBatch(@PathVariable long draftId) {
        return ApiResponse.ok(service.acceptBatch(draftId));
    }
}
```

- [ ] **Step 6: Run service and controller tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.candidate.AiParagraphCandidateServiceTest" --tests "com.gongwen.assistant.ai.candidate.AiParagraphCandidateControllerTest"
```

Expected: PASS.

- [ ] **Step 7: Commit Task B2**

```powershell
git add backend/src/main/java/com/gongwen/assistant/ai/candidate backend/src/test/java/com/gongwen/assistant/ai/candidate
git commit -m "feat: accept AI paragraph candidates"
```

---

## Task B3: Streaming Model Adapter And Candidate Generation

**Files:**
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/ModelAdapter.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/DeepSeekModelAdapter.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/MockModelAdapter.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/PromptBuilder.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateService.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateGenerationServiceTest.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/DeepSeekModelAdapterTest.java`

- [ ] **Step 1: Write candidate generation tests**

Assert generating candidates updates candidate text, does not write draft nodes, and records errors.

```java
@Test
void generateCandidateStoresTextWithoutWritingDraft() {
    long draftId = testData.createDraftWithNodes();
    AiParagraphCandidate candidate = repository.insert(pendingCandidate(draftId, targetNodeId));
    String before = testData.bodyNodeContent(draftId);

    service.generateCandidate(draftId, candidate.id());

    AiParagraphCandidate updated = repository.findById(candidate.id()).orElseThrow();
    assertThat(updated.status()).isEqualTo("READY");
    assertThat(updated.candidateText()).contains("模拟生成正文");
    assertThat(testData.bodyNodeContent(draftId)).isEqualTo(before);
}

@Test
void failedGenerationMarksCandidateError() {
    modelAdapter.failNextParagraphCandidate("AI_MODEL_UNAVAILABLE");
    long draftId = testData.createDraftWithNodes();
    AiParagraphCandidate candidate = repository.insert(pendingCandidate(draftId, targetNodeId));

    service.generateCandidate(draftId, candidate.id());

    AiParagraphCandidate updated = repository.findById(candidate.id()).orElseThrow();
    assertThat(updated.status()).isEqualTo("ERROR");
    assertThat(updated.errorCode()).isEqualTo("AI_MODEL_UNAVAILABLE");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.candidate.AiParagraphCandidateGenerationServiceTest"
```

Expected: FAIL because generation is not implemented.

- [ ] **Step 3: Extend `ModelAdapter`**

Add:

```java
default AiParagraphModelResponse generateParagraphCandidate(ParagraphPrompt prompt) {
    return generateParagraph(prompt);
}

default void streamParagraphCandidate(ParagraphPrompt prompt, AiTextStreamSink sink) {
    AiParagraphModelResponse response = generateParagraphCandidate(prompt);
    sink.onDelta(response.content());
    sink.onComplete(response.content());
}
```

Create sink:

```java
public interface AiTextStreamSink {
    void onDelta(String delta);
    void onComplete(String fullText);
    void onError(String errorCode, String message);
    boolean isCancelled();
}
```

- [ ] **Step 4: Add text-only prompt builder**

Add method:

```java
public ParagraphPrompt buildParagraphCandidatePrompt(
        DraftDetailDto draft,
        List<MaterialPromptSummary> materials,
        AiParagraphRequest request,
        AiNodeContext nodeContext
) {
    ParagraphPrompt prompt = buildParagraphPrompt(draft, materials, request, nodeContext);
    return prompt.withOutputConstraint("只输出正文段落文本，不输出 JSON、Markdown、解释或标题以外的额外说明。");
}
```

If `ParagraphPrompt` is a record without `withOutputConstraint`, add an `outputConstraint` field and update existing builders/tests with `""` as default.

- [ ] **Step 5: Implement DeepSeek text-only streaming**

Add non-stream candidate generation with text response parsing. For streaming:

```java
String body = objectMapper.writeValueAsString(Map.of(
        "model", config.model(),
        "messages", messages,
        "stream", true,
        "temperature", 0.2
));
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(config.baseUrl() + "/chat/completions"))
        .timeout(Duration.ofSeconds(config.timeoutSeconds()))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + config.apiKey())
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
    StringBuilder full = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
        if (sink.isCancelled()) {
            break;
        }
        if (!line.startsWith("data: ")) {
            continue;
        }
        String payload = line.substring("data: ".length()).trim();
        if ("[DONE]".equals(payload)) {
            break;
        }
        String delta = extractDeltaContent(payload);
        if (!delta.isBlank()) {
            full.append(delta);
            sink.onDelta(delta);
        }
    }
    sink.onComplete(full.toString());
}
```

- [ ] **Step 6: Generate one candidate**

In `AiParagraphCandidateService.generateCandidate`, load draft/materials, build prompt, mark status `STREAMING`, invoke non-stream adapter for now, normalize content, and mark `READY`.

```java
public AiParagraphCandidateDto generateCandidate(long draftId, long candidateId) {
    DraftDetailDto draft = draftService.getDraft(draftId);
    AiParagraphCandidate candidate = candidateForDraft(draftId, candidateId);
    repository.updateStatusAndError(candidateId, "STREAMING", "", "");
    try {
        ParagraphPrompt prompt = promptForCandidate(draft, candidate);
        AiParagraphModelResponse response = modelAdapter.generateParagraphCandidate(prompt);
        String normalized = normalizeParagraphContent(response.content(), candidate.heading());
        AiParagraphCandidate ready = repository.updateTextAndStatus(
                candidateId,
                normalized,
                "READY",
                digest(normalized)
        ).orElseThrow();
        return AiParagraphCandidateDto.from(ready);
    } catch (ModelAdapterException exception) {
        AiParagraphCandidate failed = repository.updateStatusAndError(
                candidateId,
                "ERROR",
                exception.errorCode(),
                exception.getMessage()
        ).orElseThrow();
        return AiParagraphCandidateDto.from(failed);
    }
}
```

- [ ] **Step 7: Run focused backend tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.candidate.AiParagraphCandidateGenerationServiceTest" --tests "com.gongwen.assistant.ai.DeepSeekModelAdapterTest" --tests "com.gongwen.assistant.ai.PromptBuilderTest"
```

Expected: PASS.

- [ ] **Step 8: Commit Task B3**

```powershell
git add backend/src/main/java/com/gongwen/assistant/ai backend/src/main/java/com/gongwen/assistant/ai/candidate backend/src/test/java/com/gongwen/assistant/ai
git commit -m "feat: generate paragraph candidates without applying"
```

---

## Task B4: Candidate Batch Job And SSE Events

**Files:**
- Create: `backend/src/main/java/com/gongwen/assistant/ai/candidate/ParagraphCandidateJobService.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/candidate/ParagraphCandidateEvent.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateController.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/candidate/ParagraphCandidateJobServiceTest.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/candidate/AiParagraphCandidateControllerTest.java`

- [ ] **Step 1: Write job tests**

Use a fake sink to capture event order:

```java
@Test
void streamsBatchEventsInSectionOrder() {
    long draftId = testData.createDraftWithNodes();
    UUID jobId = jobService.createJob(draftId, requestWithTwoSections()).jobId();

    CapturingEmitter emitter = new CapturingEmitter();
    jobService.streamJob(draftId, jobId, emitter);

    assertThat(emitter.eventNames()).containsSubsequence(
            "batch_started",
            "candidate_started",
            "candidate_delta",
            "candidate_ready",
            "candidate_started",
            "candidate_ready",
            "batch_done"
    );
}

@Test
void cancelStopsRemainingCandidatesAndKeepsReadyOnes() {
    long draftId = testData.createDraftWithNodes();
    UUID jobId = jobService.createJob(draftId, requestWithThreeSections()).jobId();

    jobService.cancel(draftId, jobId);

    List<AiParagraphCandidate> rows = repository.findByDraftId(draftId);
    assertThat(rows).anyMatch(row -> "CANCELLED".equals(row.status()) || "READY".equals(row.status()));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.candidate.ParagraphCandidateJobServiceTest"
```

Expected: FAIL because job service does not exist.

- [ ] **Step 3: Implement event model**

```java
public record ParagraphCandidateEvent(
        String type,
        UUID jobId,
        Long candidateId,
        Integer sectionIndex,
        String delta,
        AiParagraphCandidateDto candidate,
        String errorCode,
        String message
) {
    public static ParagraphCandidateEvent delta(UUID jobId, long candidateId, int sectionIndex, String delta) {
        return new ParagraphCandidateEvent("candidate_delta", jobId, candidateId, sectionIndex, delta, null, "", "");
    }
}
```

- [ ] **Step 4: Implement create job and stream**

Use in-memory job cancellation state for MVP:

```java
private final Map<UUID, AtomicBoolean> cancellations = new ConcurrentHashMap<>();

public ParagraphCandidateJobResponse createJob(long draftId, CreateParagraphCandidateBatchRequest request) {
    draftService.getDraft(draftId);
    UUID jobId = UUID.randomUUID();
    cancellations.put(jobId, new AtomicBoolean(false));
    List<AiParagraphCandidateDto> candidates = candidateService.createBatch(draftId, request);
    return new ParagraphCandidateJobResponse(jobId, candidates);
}

public SseEmitter streamJob(long draftId, UUID jobId) {
    draftService.getDraft(draftId);
    SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
    executor.execute(() -> streamCandidates(draftId, jobId, emitter));
    return emitter;
}
```

Stream each candidate through `modelAdapter.streamParagraphCandidate`, append deltas to an in-memory buffer, persist complete text as `READY`, and emit `candidate_error` if the adapter fails.

- [ ] **Step 5: Add endpoints**

```java
@PostMapping("/jobs")
public ApiResponse<ParagraphCandidateJobResponse> createJob(
        @PathVariable long draftId,
        @RequestBody CreateParagraphCandidateBatchRequest request
) {
    return ApiResponse.ok(jobService.createJob(draftId, request));
}

@GetMapping("/jobs/{jobId}/events")
public SseEmitter streamJob(@PathVariable long draftId, @PathVariable UUID jobId) {
    return jobService.streamJob(draftId, jobId);
}

@PostMapping("/jobs/{jobId}/cancel")
public ApiResponse<Void> cancelJob(@PathVariable long draftId, @PathVariable UUID jobId) {
    jobService.cancel(draftId, jobId);
    return ApiResponse.ok(null);
}
```

- [ ] **Step 6: Run focused backend tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.ai.candidate.ParagraphCandidateJobServiceTest" --tests "com.gongwen.assistant.ai.candidate.AiParagraphCandidateControllerTest"
```

Expected: PASS.

- [ ] **Step 7: Commit Task B4**

```powershell
git add backend/src/main/java/com/gongwen/assistant/ai/candidate backend/src/test/java/com/gongwen/assistant/ai/candidate
git commit -m "feat: stream paragraph candidate jobs"
```

---

## Task B5: Frontend Candidate Types, API, And Canvas

**Files:**
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/api.ts`
- Create: `frontend/src/components/workbench/ParagraphCandidateCanvas.tsx`
- Modify: `frontend/src/styles/app.css`
- Test: `frontend/src/components/workbench/ParagraphCandidateCanvas.test.tsx`

- [ ] **Step 1: Write component tests**

```tsx
it('renders streaming ready error and accepted candidates', async () => {
  render(
    <ParagraphCandidateCanvas
      candidates={[
        readyCandidate({ id: 1, heading: '一、背景', candidateText: '背景正文' }),
        streamingCandidate({ id: 2, heading: '二、事项', candidateText: '正在生成' }),
        errorCandidate({ id: 3, heading: '三、要求', errorMessage: 'AI 服务不可用' }),
      ]}
      disabled={false}
      isGenerating={true}
      onAccept={vi.fn()}
      onAcceptBatch={vi.fn()}
      onDiscard={vi.fn()}
      onEdit={vi.fn()}
      onGenerateAll={vi.fn()}
      onRetry={vi.fn()}
      onStop={vi.fn()}
    />
  );

  expect(screen.getByText('一、背景')).toBeInTheDocument();
  expect(screen.getByText('背景正文')).toBeInTheDocument();
  expect(screen.getByText('正在生成')).toBeInTheDocument();
  expect(screen.getByText('AI 服务不可用')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '批量确认' })).toBeEnabled();
  expect(screen.getByRole('button', { name: '停止生成' })).toBeEnabled();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd frontend
npm test -- src/components/workbench/ParagraphCandidateCanvas.test.tsx
```

Expected: FAIL because component does not exist.

- [ ] **Step 3: Add types**

```ts
export type AiParagraphCandidateStatus =
  | 'PENDING'
  | 'STREAMING'
  | 'READY'
  | 'EDITED'
  | 'ACCEPTING'
  | 'ACCEPTED'
  | 'RETRYING'
  | 'ERROR'
  | 'DISCARDED'
  | 'CANCELLED';

export type AiParagraphCandidate = {
  id: number;
  draftId: number;
  targetNodeId: number | null;
  targetNodeRole: string;
  targetNodeTitle: string;
  outlineTraceId: string | null;
  paragraphTraceId: string | null;
  sectionIndex: number;
  heading: string;
  points: string[];
  instructionSummary: string;
  candidateText: string;
  status: AiParagraphCandidateStatus;
  errorCode: string;
  errorMessage: string;
  acceptedAt: string | null;
  acceptedBy: number | null;
  createdAt: string;
  updatedAt: string;
};

export type ParagraphCandidateEvent = {
  type: string;
  jobId: string;
  candidateId: number | null;
  sectionIndex: number | null;
  delta: string | null;
  candidate: AiParagraphCandidate | null;
  errorCode: string;
  message: string;
};
```

- [ ] **Step 4: Add API helpers**

```ts
export function listParagraphCandidates(draftId: number) {
  return requestJson<AiParagraphCandidate[]>(`/api/drafts/${draftId}/ai/paragraph-candidates`);
}

export function createParagraphCandidateJob(
  draftId: number,
  outlineTraceId: string | null,
  sections: Array<{ sectionIndex: number; heading: string; points: string[]; targetNodeId?: number | null }>,
  instruction: string,
) {
  return requestJson<ParagraphCandidateJobResponse>(`/api/drafts/${draftId}/ai/paragraph-candidates/jobs`, {
    method: 'POST',
    body: JSON.stringify({ outlineTraceId, sections, instruction }),
  });
}

export function openParagraphCandidateEvents(draftId: number, jobId: string) {
  return new EventSource(`${API_BASE_URL}/api/drafts/${draftId}/ai/paragraph-candidates/jobs/${jobId}/events`, {
    withCredentials: true,
  });
}

export function acceptParagraphCandidate(draftId: number, candidateId: number) {
  return requestJson<AcceptParagraphCandidateResponse>(
    `/api/drafts/${draftId}/ai/paragraph-candidates/${candidateId}/accept`,
    { method: 'POST' },
  );
}

export function acceptParagraphCandidatesBatch(draftId: number) {
  return requestJson<AcceptParagraphCandidateBatchResponse>(
    `/api/drafts/${draftId}/ai/paragraph-candidates/accept-batch`,
    { method: 'POST' },
  );
}
```

- [ ] **Step 5: Create `ParagraphCandidateCanvas`**

Implement props:

```ts
type ParagraphCandidateCanvasProps = {
  candidates: AiParagraphCandidate[];
  disabled: boolean;
  isGenerating: boolean;
  onAccept: (candidateId: number) => void;
  onAcceptBatch: () => void;
  onDiscard: (candidateId: number) => void;
  onEdit: (candidateId: number, text: string) => void;
  onGenerateAll: () => void;
  onRetry: (candidateId: number) => void;
  onStop: () => void;
};
```

Use existing UI primitives `Button`, `StatusMessage`, and `TextareaField`. Keep the canvas in one panel:

```tsx
<section className="paragraph-candidate-canvas" aria-label="正文候选画布">
  <div className="local-operation-header">
    <div>
      <div className="outline-title">正文候选</div>
      <div className="panel-kicker">{summaryText(candidates, isGenerating)}</div>
    </div>
  </div>
  <div className="paragraph-candidate-actions">
    <Button disabled={disabled || isGenerating} onClick={onGenerateAll} variant="secondary">
      生成全部正文候选
    </Button>
    <Button disabled={!isGenerating} onClick={onStop} variant="ghost">
      停止生成
    </Button>
    <Button disabled={!hasAcceptableCandidates(candidates)} onClick={onAcceptBatch} variant="primary">
      批量确认
    </Button>
  </div>
  {candidates.map((candidate) => (
    <CandidateCard key={candidate.id} candidate={candidate} onAccept={onAccept} onEdit={onEdit} onRetry={onRetry} onDiscard={onDiscard} />
  ))}
</section>
```

- [ ] **Step 6: Add CSS using design tokens**

Add classes under existing workbench panel styles:

```css
.paragraph-candidate-canvas {
  display: grid;
  gap: var(--space-3);
}

.paragraph-candidate-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.paragraph-candidate-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-2);
  background: var(--color-surface);
  padding: var(--space-3);
}

.paragraph-candidate-text {
  min-height: 9rem;
  max-height: 18rem;
  overflow: auto;
}
```

Use actual token names from `DESIGN.md` and existing `frontend/src/styles/app.css`; do not introduce new colors.

- [ ] **Step 7: Run component tests and build**

Run:

```powershell
cd frontend
npm test -- src/components/workbench/ParagraphCandidateCanvas.test.tsx
npm run build
```

Expected: PASS.

- [ ] **Step 8: Commit Task B5**

```powershell
git add frontend/src/draftTypes.ts frontend/src/api.ts frontend/src/components/workbench/ParagraphCandidateCanvas.tsx frontend/src/components/workbench/ParagraphCandidateCanvas.test.tsx frontend/src/styles/app.css
git commit -m "feat: add paragraph candidate canvas"
```

---

## Task B6: Workbench Candidate Integration

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write integration tests**

Replace direct all-paragraph assertions with candidate generation assertions.

```tsx
it('generates all paragraphs as candidates without replacing workbench text', async () => {
  const originalText = '原正文';
  mockDraftDetail({ bodyText: originalText });
  mockCandidateJob({
    jobId: 'job-1',
    candidates: [
      candidate({ id: 10, sectionIndex: 0, heading: '一、背景', status: 'PENDING', candidateText: '' }),
    ],
  });

  render(<App />);
  await generateOutline();
  await userEvent.click(screen.getByRole('button', { name: '生成全部正文候选' }));
  emitCandidateEvent({ type: 'candidate_ready', candidate: candidate({ id: 10, status: 'READY', candidateText: '候选正文' }) });

  expect(screen.getByText('候选正文')).toBeInTheDocument();
  expect(screen.getByDisplayValue(originalText)).toBeInTheDocument();
});

it('accepts a candidate and replaces the target node', async () => {
  mockAcceptCandidateResponse({ candidateId: 10, nodeContent: '候选正文' });

  render(<App />);
  await openReadyCandidate();
  await userEvent.click(screen.getByRole('button', { name: '确认替换' }));

  expect(screen.getByDisplayValue('候选正文')).toBeInTheDocument();
  expect(screen.getByText('真实预览待刷新')).toBeInTheDocument();
});

it('batch accept asks for confirmation and skips failed candidates', async () => {
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  mockAcceptBatchResponse({ acceptedCandidateIds: [10], skippedCandidateIds: [11] });

  render(<App />);
  await openCandidatesWithReadyAndError();
  await userEvent.click(screen.getByRole('button', { name: '批量确认' }));

  expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('将替换 1 段正文'));
  expect(screen.getByText(/已确认 1 段/)).toBeInTheDocument();
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
cd frontend
npm test -- src/App.test.tsx
```

Expected: FAIL because `App.tsx` still uses direct paragraph generation.

- [ ] **Step 3: Add candidate state to `App.tsx`**

Add:

```ts
const [paragraphCandidates, setParagraphCandidates] = useState<AiParagraphCandidate[]>([]);
const [candidateJobId, setCandidateJobId] = useState<string | null>(null);
const [candidateStatus, setCandidateStatus] = useState<'idle' | 'generating' | 'cancelling' | 'partial-ready' | 'ready' | 'error'>('idle');
const candidateEventSourceRef = useRef<EventSource | null>(null);
```

- [ ] **Step 4: Load candidates when opening a draft**

After draft and nodes load:

```ts
const loadedCandidates = loadedDraft.id
  ? await listParagraphCandidates(loadedDraft.id).catch(() => [])
  : [];
setParagraphCandidates(loadedCandidates);
```

- [ ] **Step 5: Replace all-paragraph direct generation**

Change `handleGenerateAllParagraphs` to create a job:

```ts
async function handleGenerateAllParagraphCandidates() {
  if (!draft || !outline || outline.sections.length === 0) {
    return;
  }
  setCandidateStatus('generating');
  const sections = outline.sections.map((section, index) => ({
    sectionIndex: index,
    heading: section.heading,
    points: section.points,
    targetNodeId: bodySectionNodes[index]?.draftNodeId ?? null,
  }));
  const job = await createParagraphCandidateJob(draft.id, outline.traceId, sections, outlineInstruction);
  setCandidateJobId(job.jobId);
  setParagraphCandidates(job.candidates);
  openCandidateEventStream(draft.id, job.jobId);
}
```

- [ ] **Step 6: Apply SSE events**

```ts
function applyCandidateEvent(event: ParagraphCandidateEvent) {
  if (event.candidate) {
    setParagraphCandidates((current) => upsertCandidate(current, event.candidate as AiParagraphCandidate));
  }
  if (event.type === 'candidate_delta' && event.candidateId && event.delta) {
    setParagraphCandidates((current) => current.map((candidate) => (
      candidate.id === event.candidateId
        ? { ...candidate, status: 'STREAMING', candidateText: `${candidate.candidateText}${event.delta}` }
        : candidate
    )));
  }
  if (event.type === 'batch_done') {
    setCandidateStatus('ready');
  }
  if (event.type === 'batch_error') {
    setCandidateStatus('error');
  }
}
```

- [ ] **Step 7: Wire accept and batch accept**

Single accept:

```ts
async function handleAcceptParagraphCandidate(candidateId: number) {
  if (!draft) {
    return;
  }
  const response = await acceptParagraphCandidate(draft.id, candidateId);
  setDraft(response.draft);
  setBlocks(response.draft.blocks);
  syncGeneratedDraftNodes(response.draft.nodes, response.node);
  setParagraphCandidates((current) => upsertCandidate(current, response.candidate));
  markRenderPreviewOutdated();
}
```

Batch accept:

```ts
async function handleAcceptParagraphCandidatesBatch() {
  if (!draft) {
    return;
  }
  const acceptableCount = paragraphCandidates.filter((candidate) => ['READY', 'EDITED'].includes(candidate.status)).length;
  if (!window.confirm(`将替换 ${acceptableCount} 段正文，失败和已丢弃候选会跳过。是否继续？`)) {
    return;
  }
  const response = await acceptParagraphCandidatesBatch(draft.id);
  setDraft(response.draft);
  setBlocks(response.draft.blocks);
  setParagraphCandidates(await listParagraphCandidates(draft.id));
  markRenderPreviewOutdated();
}
```

- [ ] **Step 8: Render `ParagraphCandidateCanvas`**

Place it after outline summary and before quality/export panels:

```tsx
<ParagraphCandidateCanvas
  candidates={paragraphCandidates}
  disabled={!draft || status === 'loading'}
  isGenerating={candidateStatus === 'generating'}
  onAccept={(candidateId) => void handleAcceptParagraphCandidate(candidateId)}
  onAcceptBatch={() => void handleAcceptParagraphCandidatesBatch()}
  onDiscard={(candidateId) => void handleDiscardParagraphCandidate(candidateId)}
  onEdit={(candidateId, text) => void handleEditParagraphCandidate(candidateId, text)}
  onGenerateAll={() => void handleGenerateAllParagraphCandidates()}
  onRetry={(candidateId) => void handleRetryParagraphCandidate(candidateId)}
  onStop={() => void handleStopParagraphCandidateJob()}
/>
```

- [ ] **Step 9: Run frontend tests and build**

Run:

```powershell
cd frontend
npm test -- src/components/workbench/ParagraphCandidateCanvas.test.tsx src/App.test.tsx
npm run build
```

Expected: PASS.

- [ ] **Step 10: Commit Task B6**

```powershell
git add frontend/src/App.tsx frontend/src/App.test.tsx
git commit -m "feat: review AI paragraph candidates before applying"
```

---

## Task C1: Documentation And Verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/PROJECT_TASKS.md`
- Modify: this plan file

- [ ] **Step 1: Update project docs**

Update `AGENTS.md` current state with:

```text
工作台节点格式面板现在读取后端返回的 base/effective formatting，用户看到的是当前生效样式；保存只写草稿节点 formatOverride。
正文生成从直接写入改为正文候选画布：按提纲批量生成候选，候选可流式展示、停止、重试、编辑、逐段确认和批量确认；确认前不替换 DraftNode。
```

Update `docs/PROJECT_TASKS.md` with a concise completion note and focused verification commands.

- [ ] **Step 2: Run backend focused tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
backend\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.draft.node.DraftNodeServiceTest" --tests "com.gongwen.assistant.ai.candidate.*" --tests "com.gongwen.assistant.ai.DeepSeekModelAdapterTest" --tests "com.gongwen.assistant.ai.PromptBuilderTest"
```

Expected: PASS.

- [ ] **Step 3: Run frontend focused tests and build**

Run:

```powershell
cd frontend
npm test -- src/workbenchNodes.test.ts src/components/workbench/ParagraphCandidateCanvas.test.tsx src/App.test.tsx
npm run build
```

Expected: PASS.

- [ ] **Step 4: Run diff and migration checks**

Run:

```powershell
git diff --check
Get-ChildItem backend/src/main/resources/db/migration | Select-Object Name
git status --short --branch
```

Expected:

- `git diff --check` returns no whitespace errors.
- Migration list includes `V20__ai_paragraph_candidate.sql` after `V19`.
- Only intended files are modified.

- [ ] **Step 5: Browser verification**

Start the app using the repo's normal frontend/backend flow. If a local backend is already running on `18081`, use it for manual verification.

Check:

```text
1. Open an existing draft with mapped DraftNodes.
2. Select a body node.
3. Verify node format panel shows existing font/size/alignment.
4. Generate outline.
5. Generate all paragraph candidates.
6. Confirm generated text appears in the right panel, not in the center document.
7. Accept one candidate and verify the center document changes.
8. Generate or retain at least one error candidate and verify batch accept skips it.
9. Verify real preview is marked stale after accept.
```

- [ ] **Step 6: Commit Task C1**

```powershell
git add AGENTS.md docs/PROJECT_TASKS.md docs/superpowers/plans/2026-06-02-node-format-backfill-and-paragraph-candidates.md
git commit -m "docs: record paragraph candidate workflow"
```

---

## Self-Review

Spec coverage:

- Node format backfill: Tasks A1 and A2.
- Candidate persistence: Task B1.
- Create, edit, retry, discard, accept, batch accept API: Tasks B2, B4, B6.
- Streaming generation and stop: Tasks B3, B4, B6.
- Confirmation-before-replacement: Tasks B2 and B6.
- Security and permission boundary: Task B2 service calls `DraftService.getDraft`, Task B1 draft-scoped table, Task C1 verification.
- Tests and docs: Task C1 plus focused tests in every implementation task.

Placeholder scan:

- The plan intentionally leaves long-term retention policy out of implementation; candidates persist until draft deletion in this plan.
- The plan uses `app_user` in the migration because `V10__organization_auth_foundation.sql` defines `app_user`.

Type consistency:

- `AiParagraphCandidate`, `AiParagraphCandidateDto`, `ParagraphCandidateEvent`, and frontend `AiParagraphCandidate` use matching status names.
- `baseFormatting` and `effectiveFormatting` use the existing `TemplateStructureFormattingProfile` / `TemplateStructureFormatting` shape.
