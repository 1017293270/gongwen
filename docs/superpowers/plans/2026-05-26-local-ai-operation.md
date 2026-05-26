# P7 Local AI Operation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add paragraph-level AI suggestions for a selected `BODY_PARAGRAPH` block without directly overwriting the draft.

**Architecture:** The backend exposes a suggestion-only endpoint under the existing draft AI route and routes generation through `ModelAdapter`, so Mock and DeepSeek both work. The frontend selects one body block in the Word-style preview, requests a suggestion, then only saves the replacement through the existing `PUT /api/drafts/{id}/blocks` path when the user accepts.

**Tech Stack:** Spring Boot, Java 21, React, TypeScript, Vite, existing AI trace table.

---

## API Contract

`POST /api/drafts/{draftId}/ai/local-operation`

Request:

```json
{
  "targetBlockId": 12,
  "operationType": "FORMALIZE",
  "instruction": "语气更正式，突出执行要求"
}
```

Allowed `operationType` values:

- `FORMALIZE`
- `COMPRESS`
- `EXPAND`
- `REWRITE`
- `SUPPLEMENT`

Response:

```json
{
  "traceId": "uuid",
  "targetBlockId": 12,
  "operationType": "FORMALIZE",
  "suggestionText": "建议后的段落文本"
}
```

The endpoint must not save draft blocks. Accepting a suggestion remains a frontend action that calls `PUT /api/drafts/{id}/blocks`.

## Work Split

### Agent A: Backend Suggestion API

**Files:**

- Modify `backend/src/main/java/com/gongwen/assistant/ai/ModelAdapter.java`
- Modify `backend/src/main/java/com/gongwen/assistant/ai/MockModelAdapter.java`
- Modify `backend/src/main/java/com/gongwen/assistant/ai/DeepSeekModelAdapter.java`
- Modify `backend/src/main/java/com/gongwen/assistant/ai/PromptBuilder.java`
- Modify `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineController.java`
- Create `backend/src/main/java/com/gongwen/assistant/ai/LocalOperationPrompt.java`
- Create `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationRequest.java`
- Create `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationResponse.java`
- Create `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationModelResponse.java`
- Create `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationType.java`
- Create `backend/src/main/java/com/gongwen/assistant/ai/AiLocalOperationService.java`
- Test lightly in `backend/src/test/java/com/gongwen/assistant/ai/AiLocalOperationServiceTest.java`

**Steps:**

- [ ] Add local operation request/response records and enum.
- [ ] Add `generateLocalOperation(LocalOperationPrompt prompt)` default method to `ModelAdapter`.
- [ ] Add `buildLocalOperationPrompt(...)` with prompt version `local-operation-v1`; input summaries must record block id/type, operation type, and character counts, not full original text.
- [ ] Implement `AiLocalOperationService`: validate request, load draft, find target block, require `BODY_PARAGRAPH`, call model, save AI trace with task type `LOCAL_OPERATION`, return suggestion only.
- [ ] Implement Mock operation output with deterministic text.
- [ ] Implement DeepSeek JSON prompt returning `{ "suggestionText": "..." }`.
- [ ] Add controller route and map local-operation validation errors to `400`, model failures to `502`.
- [ ] Add one focused service test for success trace and one for non-body block rejection.

### Agent B: Frontend Selection And Adoption

**Files:**

- Modify `frontend/src/draftTypes.ts`
- Modify `frontend/src/api.ts`
- Modify `frontend/src/App.tsx`
- Modify `frontend/src/styles/app.css`
- Add or update a small focused test in `frontend/src/App.test.tsx`

**Steps:**

- [ ] Add local operation types and API client.
- [ ] Render body preview from `BODY_PARAGRAPH` blocks, each as a selectable button-like paragraph with stable focus-visible styling.
- [ ] Track selected body block id and selected operation type.
- [ ] Add right-panel local operation controls below outline generation.
- [ ] Disable generation until a body paragraph is selected.
- [ ] Show loading, error, suggestion, accept, and discard states.
- [ ] On accept, replace only the selected block content locally and call existing `saveDraftBlocks`.
- [ ] Add a focused UI test for selecting a paragraph, requesting a suggestion, accepting it, and saving via `/blocks`.

### Agent C: QA And Docs

**Files:**

- Modify `AGENTS.md`
- Modify `docs/PROJECT_TASKS.md`
- Optionally modify `docs/AI_CONFIGURATION.md` only if P7 changes DeepSeek usage notes.

**Steps:**

- [ ] After implementation, update P7 status and current API list.
- [ ] Record that P7 keeps suggestions separate from saving.
- [ ] Keep verification lightweight: focused backend test, frontend test run, frontend build.

## Verification

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.ai.AiLocalOperationServiceTest"
cd frontend
npm test -- --run
npm run build
```

