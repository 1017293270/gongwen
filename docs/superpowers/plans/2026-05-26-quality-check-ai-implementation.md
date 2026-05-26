# P8 Quality Check AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first visible P8 quality-check loop with deterministic rules, DeepSeek-backed writing suggestions, persisted results, and a workbench right-panel UI.

**Architecture:** Add a `quality` backend module for rule checks, AI review orchestration, persistence, and controller endpoints. Reuse the existing `PromptBuilder`, `ModelAdapter`, and `ai_generation_trace` chain for DeepSeek calls; do not put prompts in the controller or frontend. Frontend adds a compact quality panel inside the existing right column and keeps AI suggestions read-only for this phase.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, PostgreSQL JSONB, React, TypeScript, Vite, existing toast/ui components.

---

## Scope

- Rules: required field checks, basic structure checks, and body paragraph presence.
- AI: DeepSeek/mock quality suggestions returned as structured JSON.
- Persistence: latest quality check result saved in `quality_check_result`.
- API: run quality check and fetch latest result for a draft.
- Frontend: right-panel states for idle, checking, success, warning, error, AI failure, retry.

Deferred:

- Automatic text rewriting or one-click AI application.
- Template-version-driven placeholder checks beyond current draft fields.
- Export hard-block integration; P8 exposes `exportBlocked` for P11.

## Tasks

- [ ] Task 1: Add `quality_check_result` migration and backend DTOs.
- [ ] Task 2: Extend AI prompt/model adapter for quality review.
- [ ] Task 3: Add `QualityCheckService`, repository, and controller.
- [ ] Task 4: Add lightweight backend tests for service and controller.
- [ ] Task 5: Add frontend types/API and right-panel quality UI.
- [ ] Task 6: Update `AGENTS.md` and `docs/PROJECT_TASKS.md`.
- [ ] Task 7: Run focused backend tests and frontend build/test smoke.

## Lightweight Verification

Run only focused tests unless a compile failure requires broader checks:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.quality.QualityCheckServiceTest"
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.quality.QualityCheckControllerTest"
cd frontend
npm test -- --run
npm run build
```
