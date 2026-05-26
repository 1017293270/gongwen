# AI Outline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build P5 AI outline generation with a traceable backend model adapter boundary and a workbench outline UI.

**Architecture:** Add a focused backend `ai` module with prompt building, model adapter, outline service, trace persistence, and controller. Use a deterministic mock model adapter by default so tests and local demos do not require cloud credentials, while keeping provider configuration injectable for later.

**Tech Stack:** Spring Boot, JDBC, PostgreSQL/Flyway, Jackson, React, TypeScript, Vitest.

---

## File Structure

- `backend/src/main/resources/db/migration/V5__ai_outline_foundation.sql`: creates `ai_generation_trace`.
- `backend/src/main/java/com/gongwen/assistant/ai/*`: AI outline request/response, prompt, model adapter, service, controller, trace repository and errors.
- `backend/src/test/java/com/gongwen/assistant/ai/*`: focused unit and controller tests.
- `frontend/src/api.ts`: add outline API helper.
- `frontend/src/draftTypes.ts`: add outline response types.
- `frontend/src/App.tsx`: add right-panel outline generation state.
- `frontend/src/styles/app.css`: add outline UI styles using existing tokens.
- `AGENTS.md` and `docs/PROJECT_TASKS.md`: update P5 status after implementation.

---

### Task 1: Backend Trace And Prompt Domain

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__ai_outline_foundation.sql`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/AiGenerationTrace.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/AiGenerationTraceRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/JdbcAiGenerationTraceRepository.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/OutlinePrompt.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/PromptBuilder.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/PromptBuilderTest.java`

- [ ] **Step 1: Write prompt builder tests**

Verify prompt input summarizes document type, draft blocks, material counts, and truncates material text.

- [ ] **Step 2: Add V5 migration**

Create `ai_generation_trace` with `draft_id`, task metadata, status, summaries, error fields, latency, tenant/department/version fields, timestamps, and indexes on `draft_id` and `task_type`.

- [ ] **Step 3: Implement trace repository and prompt domain**

Keep prompt data structured. Do not persist full prompt or full material text in trace.

- [ ] **Step 4: Run focused backend test**

Run Docker Gradle focused test for `PromptBuilderTest`.

### Task 2: Backend Model Adapter And Outline Service

**Files:**
- Create: `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineRequest.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineResponse.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineSection.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/ModelAdapter.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/ModelAdapterException.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/MockModelAdapter.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineService.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/AiOutlineServiceTest.java`

- [ ] **Step 1: Write service tests**

Cover successful outline generation and adapter failure trace recording.

- [ ] **Step 2: Implement mock adapter**

Return stable title suggestion, sections, and missing information using existing draft and material summary inputs.

- [ ] **Step 3: Implement outline service**

Read draft, summarize materials, build prompt, call adapter, validate output, write success or failed trace.

- [ ] **Step 4: Run focused service tests**

Run Docker Gradle focused test for `AiOutlineServiceTest`.

### Task 3: Backend Outline API

**Files:**
- Create: `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineController.java`
- Create: `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineException.java`
- Test: `backend/src/test/java/com/gongwen/assistant/ai/AiOutlineControllerTest.java`

- [ ] **Step 1: Write controller tests**

Cover `POST /api/drafts/{draftId}/ai/outline` success, draft missing, and instruction too long.

- [ ] **Step 2: Implement controller**

Return project `ApiResponse`, map validation errors to `400`, missing draft to `404`, model errors to `502`.

- [ ] **Step 3: Run controller tests**

Run Docker Gradle focused test for `AiOutlineControllerTest`.

### Task 4: Frontend Outline Generation UI

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/draftTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles/app.css`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: Add frontend API and types**

Add `generateDraftOutline` and outline response interfaces.

- [ ] **Step 2: Add failing frontend tests**

Expect clicking generate outline to show loading state, render returned title and sections, and show retry on failure.

- [ ] **Step 3: Implement right-panel outline UI**

Use existing toast feedback and CSS variables. Include default, loading, success, error, retry, and disabled states.

- [ ] **Step 4: Run frontend tests and build**

Run `npm test` and `npm run build` in `frontend`.

### Task 5: Documentation And Full Verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/PROJECT_TASKS.md`

- [ ] **Step 1: Update docs**

Record P5 API, trace table, mock adapter default, frontend outline UI, and verification status.

- [ ] **Step 2: Run full backend tests**

Run the Docker Gradle full backend test command from `docs/PROJECT_TASKS.md`.

- [ ] **Step 3: Review git diff**

Confirm changes are limited to P5 AI outline backend/frontend/docs.

- [ ] **Step 4: Commit P5**

Commit with `feat: add ai outline generation foundation`.

---

## Self-Review

Spec coverage:

- ModelAdapter and PromptBuilder: covered by Tasks 1 and 2.
- AiGenerationTrace data table: covered by Task 1.
- Outline API and normalized failures: covered by Task 3.
- Structured output: covered by Tasks 2 and 3.
- Frontend AI states: covered by Task 4.
- Documentation and verification: covered by Task 5.

Placeholder scan:

- No TBD/TODO placeholders are used.
- Real cloud model integration is explicitly excluded from P5 and left behind the adapter boundary.

