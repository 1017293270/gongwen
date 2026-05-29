# Workbench Structure Node Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unified workbench structure node layer so template body sections, draft editing, AI actions, quality checks, and draft-local style overrides all target the same business object.

**Architecture:** Start with a frontend adapter that derives `WorkbenchNode[]` from the existing `DraftDetail`, `TemplateProfile`, and structure formatting overrides, then move the persistence boundary to backend node APIs without breaking the current `DraftBlock` flow. AI and quality endpoints keep their existing routes but accept node-aware fields during the transition.

**Tech Stack:** React + TypeScript frontend, Spring Boot backend, PostgreSQL/Flyway, Vitest/React Testing Library, JUnit.

---

## File Structure

- Create: `frontend/src/workbenchNodes.ts`
  - Pure frontend model and adapter for deriving `WorkbenchNode[]`.
- Create: `frontend/src/workbenchNodes.test.ts`
  - Focused tests for BODY heading/content splitting and draft override behavior.
- Modify: `frontend/src/draftTypes.ts`
  - Add shared frontend types for `WorkbenchNode`, `WorkbenchNodeType`, `WorkbenchNodePart`, and node formatting overrides.
- Modify: `frontend/src/App.tsx`
  - Replace `selectedBodyBlockId`-first workbench state with `selectedNodeId`.
  - Render left body directory from nodes.
  - Render center paper from nodes.
  - Route right-panel local operation and style controls through selected node.
- Modify: `frontend/src/api.ts`
  - Add optional node-aware request fields for paragraph generation and local operation.
- Modify: `frontend/src/App.test.tsx`
  - Add interaction coverage for selecting a body heading/content and seeing right-panel context update.
- Create: `backend/src/main/resources/db/migration/V10__draft_node_foundation.sql`
  - Add draft node persistence while keeping `draft_block`.
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeDto.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeUpdateRequest.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/JdbcDraftNodeRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeService.java`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/DraftNodeController.java`
  - Backend node API with DraftBlock compatibility.
- Create: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeServiceTest.java`
- Create: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeControllerTest.java`
  - Backend focused tests.
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiParagraphRequest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiParagraphService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationRequest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationService.java`
  - Accept `nodeId` and `nodePart` while preserving `sortOrder` and `targetBlockId`.
- Modify: `backend/src/main/java/com/gongwen/assistant/quality/QualityCheckService.java`
  - Attach node targets where derivable.
- Modify: `AGENTS.md`
- Modify: `docs/PROJECT_TASKS.md`
  - Record the implemented node workflow and remaining P11 export tie-in.

## Task 1: Frontend Node Model And Adapter

**Files:**
- Modify: `frontend/src/draftTypes.ts`
- Create: `frontend/src/workbenchNodes.ts`
- Create: `frontend/src/workbenchNodes.test.ts`

- [ ] **Step 1: Add node types**

Add these types to `frontend/src/draftTypes.ts`:

```ts
export type WorkbenchNodeType =
  | 'TITLE'
  | 'RECIPIENT'
  | 'BODY_SECTION'
  | 'ATTACHMENT'
  | 'SIGNATURE'
  | 'DATE'
  | 'STATIC_TEMPLATE_TEXT'
  | 'HEADER'
  | 'FOOTER';

export type WorkbenchNodePart = 'whole' | 'heading' | 'content';

export type WorkbenchNodeSource = 'TEMPLATE' | 'DRAFT' | 'AI' | 'USER';

export interface WorkbenchNode {
  nodeId: string;
  nodeType: WorkbenchNodeType;
  templateStructureKey?: string;
  draftBlockId?: number;
  sortOrder: number;
  label: string;
  heading?: string;
  content: string;
  source: WorkbenchNodeSource;
  locked: boolean;
  formatting?: TemplateStructureFormattingProfile;
}
```

- [ ] **Step 2: Write the BODY split test**

Create `frontend/src/workbenchNodes.test.ts` with this first test:

```ts
import { describe, expect, it } from 'vitest';
import { deriveWorkbenchNodes } from './workbenchNodes';
import type { DraftDetail, TemplateProfile } from './draftTypes';

function draft(blocks: DraftDetail['blocks']): DraftDetail {
  return {
    id: 1,
    documentTypeCode: 'NOTICE',
    title: '通知',
    status: 'DRAFT',
    templateVersionId: 9,
    blocks,
  };
}

const profile = {
  structures: [
    {
      structureKey: 'body-1',
      structureType: 'BODY',
      label: '正文段落',
      text: '一、会议时间',
      sortOrder: 30,
      source: 'paragraph',
      formatting: { fontFamily: 'FangSong', fontSizePt: 16 },
    },
    {
      structureKey: 'body-2',
      structureType: 'BODY',
      label: '正文段落',
      text: '2026年6月3日（星期三）上午9:30。',
      sortOrder: 31,
      source: 'paragraph',
      formatting: { fontFamily: 'FangSong', fontSizePt: 16 },
    },
  ],
} as TemplateProfile;

describe('deriveWorkbenchNodes', () => {
  it('groups a body heading and following paragraph into one BODY_SECTION node', () => {
    const nodes = deriveWorkbenchNodes(draft([]), profile, {});

    expect(nodes).toHaveLength(1);
    expect(nodes[0]).toMatchObject({
      nodeType: 'BODY_SECTION',
      heading: '一、会议时间',
      content: '2026年6月3日（星期三）上午9:30。',
      templateStructureKey: 'body-1',
    });
  });
});
```

- [ ] **Step 3: Run the focused test to verify it fails**

Run:

```powershell
cd frontend
npm test -- --run src/workbenchNodes.test.ts
```

Expected: fail because `deriveWorkbenchNodes` does not exist.

- [ ] **Step 4: Implement the adapter**

Create `frontend/src/workbenchNodes.ts`:

```ts
import type {
  DraftBlock,
  DraftDetail,
  TemplateProfile,
  TemplateStructureFormattingProfile,
  TemplateStructureOverrideMap,
  WorkbenchNode,
} from './draftTypes';

const BODY_HEADING_PATTERN = /^([一二三四五六七八九十]+、|（[一二三四五六七八九十]+）|\d+[.．、])\s*\S+/;

export function deriveWorkbenchNodes(
  draft: DraftDetail | null,
  profile: TemplateProfile | null,
  overrides: TemplateStructureOverrideMap,
): WorkbenchNode[] {
  if (!draft) return [];
  const draftBlocks = draft.blocks ?? [];
  const nodesFromTemplate = profile?.structures?.length
    ? nodesFromProfile(draftBlocks, profile, overrides)
    : [];
  if (nodesFromTemplate.length > 0) return nodesFromTemplate;
  return nodesFromBlocks(draftBlocks);
}

function nodesFromProfile(
  draftBlocks: DraftBlock[],
  profile: TemplateProfile,
  overrides: TemplateStructureOverrideMap,
): WorkbenchNode[] {
  const nodes: WorkbenchNode[] = [];
  const structures = [...(profile.structures ?? [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  let pendingBody: WorkbenchNode | null = null;

  for (const structure of structures) {
    const text = (structure.text ?? '').trim();
    const formatting = mergeFormatting(structure.formatting, overrides[structure.structureKey]);
    if (structure.structureType === 'BODY') {
      if (BODY_HEADING_PATTERN.test(text)) {
        if (pendingBody) nodes.push(pendingBody);
        pendingBody = {
          nodeId: `template:${structure.structureKey}`,
          nodeType: 'BODY_SECTION',
          templateStructureKey: structure.structureKey,
          sortOrder: structure.sortOrder ?? nodes.length,
          label: stripBodyMarker(text),
          heading: text,
          content: '',
          source: 'TEMPLATE',
          locked: false,
          formatting,
        };
      } else if (pendingBody) {
        pendingBody = {
          ...pendingBody,
          content: [pendingBody.content, text].filter(Boolean).join('\n'),
        };
      } else {
        nodes.push({
          nodeId: `template:${structure.structureKey}`,
          nodeType: 'BODY_SECTION',
          templateStructureKey: structure.structureKey,
          sortOrder: structure.sortOrder ?? nodes.length,
          label: '正文',
          content: text,
          source: 'TEMPLATE',
          locked: false,
          formatting,
        });
      }
      continue;
    }

    if (pendingBody) {
      nodes.push(pendingBody);
      pendingBody = null;
    }

    nodes.push({
      nodeId: `template:${structure.structureKey}`,
      nodeType: mapStructureType(structure.structureType),
      templateStructureKey: structure.structureKey,
      sortOrder: structure.sortOrder ?? nodes.length,
      label: structure.label || text || '模板结构',
      content: text,
      source: 'TEMPLATE',
      locked: structure.structureType === 'HEADER' || structure.structureType === 'FOOTER',
      formatting,
    });
  }

  if (pendingBody) nodes.push(pendingBody);
  return applyDraftBlocks(nodes, draftBlocks);
}

function nodesFromBlocks(blocks: DraftBlock[]): WorkbenchNode[] {
  return blocks.map((block) => ({
    nodeId: `block:${block.id ?? block.sortOrder}`,
    nodeType: block.blockType === 'BODY_PARAGRAPH' ? 'BODY_SECTION' : mapStructureType(block.blockType),
    draftBlockId: block.id,
    sortOrder: block.sortOrder,
    label: block.blockType === 'BODY_PARAGRAPH' ? deriveBodyLabel(block.content, block.sortOrder) : block.blockType,
    heading: block.blockType === 'BODY_PARAGRAPH' ? extractHeading(block.content) : undefined,
    content: block.blockType === 'BODY_PARAGRAPH' ? stripHeading(block.content) : block.content,
    source: 'DRAFT',
    locked: false,
  }));
}

function applyDraftBlocks(nodes: WorkbenchNode[], blocks: DraftBlock[]): WorkbenchNode[] {
  const bodyBlocks = blocks.filter((block) => block.blockType === 'BODY_PARAGRAPH' && block.content.trim());
  let bodyIndex = 0;
  return nodes.map((node) => {
    if (node.nodeType !== 'BODY_SECTION') return node;
    const block = bodyBlocks[bodyIndex++];
    if (!block) return node;
    return {
      ...node,
      draftBlockId: block.id,
      heading: extractHeading(block.content) || node.heading,
      content: stripHeading(block.content) || node.content,
      source: 'DRAFT',
    };
  });
}

function mergeFormatting(
  base: TemplateStructureFormattingProfile | undefined,
  override: TemplateStructureFormattingProfile | undefined,
) {
  return { ...(base ?? {}), ...(override ?? {}) };
}

function mapStructureType(type: string): WorkbenchNode['nodeType'] {
  switch (type) {
    case 'TITLE':
      return 'TITLE';
    case 'RECIPIENT':
      return 'RECIPIENT';
    case 'ATTACHMENT':
      return 'ATTACHMENT';
    case 'SIGNATURE':
      return 'SIGNATURE';
    case 'DATE':
      return 'DATE';
    case 'HEADER':
      return 'HEADER';
    case 'FOOTER':
      return 'FOOTER';
    default:
      return 'STATIC_TEMPLATE_TEXT';
  }
}

function extractHeading(content: string) {
  const firstLine = content.split('\n')[0]?.trim() ?? '';
  return BODY_HEADING_PATTERN.test(firstLine) ? firstLine : undefined;
}

function stripHeading(content: string) {
  const lines = content.split('\n');
  const firstLine = lines[0]?.trim() ?? '';
  if (BODY_HEADING_PATTERN.test(firstLine)) {
    return lines.slice(1).join('\n').trim();
  }
  return content.trim();
}

function stripBodyMarker(text: string) {
  return text.replace(/^([一二三四五六七八九十]+、|（[一二三四五六七八九十]+）|\d+[.．、])\s*/, '');
}

function deriveBodyLabel(content: string, sortOrder: number) {
  return stripBodyMarker(extractHeading(content) ?? `正文 ${sortOrder}`);
}
```

- [ ] **Step 5: Run focused frontend test**

Run:

```powershell
cd frontend
npm test -- --run src/workbenchNodes.test.ts
```

Expected: pass.

- [ ] **Step 6: Commit Task 1**

```powershell
git add frontend/src/draftTypes.ts frontend/src/workbenchNodes.ts frontend/src/workbenchNodes.test.ts
git commit -m "feat: derive workbench structure nodes"
```

## Task 2: Workbench UI Uses Selected Node

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles/app.css`

- [ ] **Step 1: Write interaction test**

Add a test in `frontend/src/App.test.tsx` that loads a template profile with `BODY` heading/content, clicks the rendered body heading, and expects the right panel to show that node label:

```ts
it('selects a body section node from the paper and updates the right panel context', async () => {
  render(<App />);

  await screen.findByText('一、会议时间');
  await userEvent.click(screen.getByText('一、会议时间'));

  expect(screen.getByText('已选择：会议时间')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
cd frontend
npx vitest run src/App.test.tsx -t "selects a body section node"
```

Expected: fail because right panel is still block-oriented.

- [ ] **Step 3: Replace selected body block state**

In `frontend/src/App.tsx`, replace:

```ts
const [selectedBodyBlockId, setSelectedBodyBlockId] = useState<number | null>(null);
```

with:

```ts
const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
```

Then derive:

```ts
const workbenchNodes = useMemo(
  () => deriveWorkbenchNodes(draft, selectedTemplateProfile, selectedTemplateOverrides),
  [draft, selectedTemplateProfile, selectedTemplateOverrides],
);
const selectedNode = workbenchNodes.find((node) => node.nodeId === selectedNodeId) ?? null;
const bodySectionNodes = workbenchNodes.filter((node) => node.nodeType === 'BODY_SECTION');
```

- [ ] **Step 4: Render left body directory from nodes**

Replace body block list rendering with:

```tsx
{bodySectionNodes.map((node, index) => (
  <button
    key={node.nodeId}
    type="button"
    aria-current={selectedNodeId === node.nodeId ? 'true' : undefined}
    className={`paragraph-index-item ${selectedNodeId === node.nodeId ? 'selected' : ''}`}
    onClick={() => setSelectedNodeId(node.nodeId)}
  >
    <span className="paragraph-index-number">{index + 1}</span>
    <span>
      <strong>{node.label}</strong>
      <small>{node.content || '尚未生成正文'}</small>
    </span>
  </button>
))}
```

- [ ] **Step 5: Render center paper body heading and content separately**

In the paper preview loop, render `BODY_SECTION` as:

```tsx
<section
  key={node.nodeId}
  className={`draft-preview-node ${selectedNodeId === node.nodeId ? 'selected' : ''}`}
  onClick={() => setSelectedNodeId(node.nodeId)}
>
  {node.heading ? <h3>{node.heading}</h3> : null}
  <p>{node.content || '待生成正文内容'}</p>
</section>
```

- [ ] **Step 6: Update right-panel wording**

Replace selected block copy with:

```tsx
<p>{selectedNode ? `已选择：${selectedNode.label}` : '请先选择正文结构'}</p>
```

Disable local operation unless:

```ts
selectedNode?.nodeType === 'BODY_SECTION'
```

- [ ] **Step 7: Run UI focused tests**

Run:

```powershell
cd frontend
npx vitest run src/App.test.tsx -t "selects a body section node"
npm run build
```

Expected: both pass.

- [ ] **Step 8: Commit Task 2**

```powershell
git add frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/styles/app.css
git commit -m "feat: drive workbench selection by structure node"
```

## Task 3: Backend Draft Node Persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__draft_node_foundation.sql`
- Create: `backend/src/main/java/com/gongwen/assistant/draft/node/*.java`
- Create: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeServiceTest.java`
- Create: `backend/src/test/java/com/gongwen/assistant/draft/node/DraftNodeControllerTest.java`

- [ ] **Step 1: Add migration**

Create `V10__draft_node_foundation.sql`:

```sql
create table draft_node (
    id bigserial primary key,
    draft_id bigint not null references draft(id) on delete cascade,
    template_version_id bigint references document_template_version(id),
    template_structure_key varchar(160),
    parent_node_id bigint references draft_node(id) on delete cascade,
    node_type varchar(64) not null,
    node_part varchar(32) not null default 'whole',
    heading text,
    content text not null default '',
    source varchar(32) not null default 'DRAFT',
    sort_order integer not null,
    formatting_override_json jsonb not null default '{}'::jsonb,
    mapping_override_json jsonb not null default '{}'::jsonb,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index idx_draft_node_draft_order on draft_node(draft_id, sort_order, id);
create index idx_draft_node_template_structure on draft_node(template_version_id, template_structure_key);
```

- [ ] **Step 2: Write service test for block fallback**

Create `DraftNodeServiceTest` with:

```java
@Test
void derivesBodySectionNodesFromDraftBlocksWhenNoPersistedNodesExist() {
    DraftNodeService service = new DraftNodeService(nodeRepository, draftRepository);
    when(nodeRepository.findByDraftId(1L)).thenReturn(List.of());
    when(draftRepository.findById(1L)).thenReturn(Optional.of(new DraftDetailDto(
            1L, "NOTICE", "通知", "DRAFT", null,
            List.of(new DraftBlockDto(3L, "BODY_PARAGRAPH", "一、会议时间\n2026年6月3日。", 30))
    )));

    List<DraftNodeDto> nodes = service.listNodes(1L);

    assertThat(nodes).singleElement().satisfies(node -> {
        assertThat(node.nodeType()).isEqualTo("BODY_SECTION");
        assertThat(node.heading()).isEqualTo("一、会议时间");
        assertThat(node.content()).isEqualTo("2026年6月3日。");
    });
}
```

- [ ] **Step 3: Implement DTOs and repository interface**

Create:

```java
public record DraftNodeDto(
        long id,
        long draftId,
        Long templateVersionId,
        String templateStructureKey,
        Long parentNodeId,
        String nodeType,
        String nodePart,
        String heading,
        String content,
        String source,
        int sortOrder,
        Map<String, Object> formattingOverride,
        Map<String, Object> mappingOverride,
        String status
) {}
```

and:

```java
public interface DraftNodeRepository {
    List<DraftNodeDto> findByDraftId(long draftId);
    void replaceDraftNodes(long draftId, List<DraftNodeUpdateRequest> nodes);
}
```

- [ ] **Step 4: Implement `DraftNodeService.listNodes` fallback**

Implement fallback from `BODY_PARAGRAPH` with the same heading regex used by the frontend:

```java
private static final Pattern BODY_HEADING = Pattern.compile("^([一二三四五六七八九十]+、|（[一二三四五六七八九十]+）|\\d+[.．、])\\s*\\S+");
```

The fallback `DraftNodeDto` should use `id = -block.id()`, `nodeType = BODY_SECTION`, `source = DRAFT`, and `sortOrder = block.sortOrder()`.

- [ ] **Step 5: Add controller**

Create `DraftNodeController`:

```java
@RestController
@RequestMapping("/api/drafts/{draftId}/nodes")
class DraftNodeController {
    private final DraftNodeService service;

    DraftNodeController(DraftNodeService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<List<DraftNodeDto>> list(@PathVariable long draftId) {
        return ApiResponse.ok(service.listNodes(draftId));
    }

    @PutMapping
    ApiResponse<List<DraftNodeDto>> replace(@PathVariable long draftId, @RequestBody List<DraftNodeUpdateRequest> request) {
        return ApiResponse.ok(service.replaceNodes(draftId, request));
    }
}
```

- [ ] **Step 6: Run backend focused tests**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.draft.node.DraftNodeServiceTest"
```

Expected: pass.

- [ ] **Step 7: Commit Task 3**

```powershell
git add backend/src/main/resources/db/migration/V10__draft_node_foundation.sql backend/src/main/java/com/gongwen/assistant/draft/node backend/src/test/java/com/gongwen/assistant/draft/node
git commit -m "feat: add draft structure node persistence"
```

## Task 4: Node-Aware AI Requests

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiParagraphRequest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiParagraphService.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationRequest.java`
- Modify: `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationService.java`
- Modify: AI tests under `backend/src/test/java/com/gongwen/assistant/ai/`

- [ ] **Step 1: Extend frontend request payloads**

In `frontend/src/api.ts`, add optional fields:

```ts
nodeId?: string;
nodePart?: 'heading' | 'content' | 'whole';
```

Send `nodeId: selectedNode.nodeId` and `nodePart: 'content'` for paragraph generation and local operation.

- [ ] **Step 2: Extend backend records compatibly**

Update Java request records to include nullable node fields while preserving existing fields:

```java
public record AiParagraphRequest(
        String sectionTitle,
        List<String> points,
        String extraRequirement,
        Integer sortOrder,
        String nodeId,
        String nodePart
) {}
```

```java
public record AiLocalOperationRequest(
        long targetBlockId,
        String nodeId,
        String nodePart,
        String operationType,
        String extraRequirement
) {}
```

- [ ] **Step 3: Keep old behavior as fallback**

In services, resolve target in this order:

```java
// 1. nodeId if provided and resolvable
// 2. targetBlockId or sortOrder legacy path
// 3. stable validation error if neither is usable
```

- [ ] **Step 4: Add tests**

Add one paragraph test asserting `nodeId` request still saves a `BODY_PARAGRAPH` during transition.
Add one local-operation test asserting legacy `targetBlockId` still works.

- [ ] **Step 5: Run AI focused tests**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.ai.AiParagraphServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.ai.AiLocalOperationServiceTest"
```

Expected: both pass.

- [ ] **Step 6: Commit Task 4**

```powershell
git add frontend/src/api.ts frontend/src/App.tsx backend/src/main/java/com/gongwen/assistant/ai backend/src/test/java/com/gongwen/assistant/ai
git commit -m "feat: target ai operations by workbench node"
```

## Task 5: Node-Aware Quality Display

**Files:**
- Modify: `backend/src/main/java/com/gongwen/assistant/quality/QualityCheckService.java`
- Modify: quality DTOs if needed.
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: quality tests.

- [ ] **Step 1: Add quality target fields**

Extend frontend quality item type with:

```ts
targetNodeId?: string;
targetNodePart?: WorkbenchNodePart;
```

- [ ] **Step 2: Attach node target in backend where possible**

When a quality item targets `BODY_PARAGRAPH`, attach the first active `BODY_SECTION` node id during the transition. For missing title/recipient/signature/date, attach the matching node type if present.

- [ ] **Step 3: Filter right-panel node quality**

In `App.tsx`, derive:

```ts
const selectedNodeQualityItems = qualityResult?.items.filter((item) => item.targetNodeId === selectedNode?.nodeId) ?? [];
```

Render selected node items before global summary.

- [ ] **Step 4: Run focused checks**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.quality.QualityCheckServiceTest"
cd frontend
npx vitest run src/App.test.tsx -t "quality"
```

Expected: pass.

- [ ] **Step 5: Commit Task 5**

```powershell
git add backend/src/main/java/com/gongwen/assistant/quality backend/src/test/java/com/gongwen/assistant/quality frontend/src/draftTypes.ts frontend/src/App.tsx
git commit -m "feat: bind quality feedback to workbench nodes"
```

## Task 6: Verification And Documentation

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/PROJECT_TASKS.md`

- [ ] **Step 1: Update project status**

Record that P10C has a node-oriented workbench model, with remaining P11 export replay work still pending.

- [ ] **Step 2: Run lightweight verification**

Run:

```powershell
git diff --check
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.draft.node.DraftNodeServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.ai.AiParagraphServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.quality.QualityCheckServiceTest"
cd frontend
npm test -- --run src/workbenchNodes.test.ts
npx vitest run src/App.test.tsx -t "selects a body section node"
npm run build
```

Expected: all pass.

- [ ] **Step 3: Browser sanity check**

Open `http://localhost:54716/` or the active Vite URL and verify:

- The left body directory shows real body headings.
- The center paper separates body heading and content.
- Clicking heading or content selects one node.
- The right panel shows the selected node name and node-specific operations.
- Refresh does not lose persisted node edits after Task 3 is complete.

- [ ] **Step 4: Commit docs**

```powershell
git add AGENTS.md docs/PROJECT_TASKS.md
git commit -m "docs: record workbench node workflow"
```

## Recommended Execution Order

1. Task 1 and Task 2 first: they produce the user-visible cleanup fastest and validate the product direction.
2. Task 3 next: it prevents refresh-loss and stops the node model from being frontend-only.
3. Task 4 and Task 5 after persistence: AI and quality become node-aware without breaking old draft blocks.
4. Task 6 last: verification and project status update.

## Self-Review

- Spec coverage: the plan covers unified nodes, BODY heading/content split, left/center/right synchronization, draft-local overrides, persistence, AI targeting, quality targeting, and compatibility with existing `DraftBlock`.
- Placeholder scan: no task relies on "TBD" behavior; each task has concrete files, commands, and expected results.
- Type consistency: `WorkbenchNode`, `nodeId`, `nodePart`, and `BODY_SECTION` are used consistently across frontend, backend, AI, and quality tasks.
