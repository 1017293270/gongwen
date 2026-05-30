# P12 组织账号与权限体系 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build real account login, department tree, role management, document type management, and ownership-based filtering for drafts, templates, materials, and exports.

**Architecture:** Spring Security owns authentication with HttpOnly session cookies and CSRF protection. A new `security` package exposes the current user to services, while `organization` manages departments and accounts. Existing document, draft, template, material, and export services receive the current user and enforce ownership rules at service/repository boundaries.

**Tech Stack:** Spring Boot 3.3.5, Spring Security, JDBC repositories, Flyway, React 18, TypeScript, Vitest, Testing Library.

---

## File Map

- Create `backend/src/main/resources/db/migration/V10__organization_auth_foundation.sql`: organization/auth schema and ownership columns.
- Modify `backend/build.gradle`: add Spring Security and security test dependencies.
- Create `backend/src/main/java/com/gongwen/assistant/security/**`: security config, principal, current user provider, auth controller, bootstrap admin.
- Create `backend/src/main/java/com/gongwen/assistant/organization/**`: department and user admin DTOs, services, repositories, controllers.
- Modify existing backend document/draft/template/material/export classes: enforce current-user visibility and ownership.
- Modify backend tests under `backend/src/test/java/com/gongwen/assistant/**`: add auth, organization, and ownership tests.
- Modify `frontend/src/api.ts` and `frontend/src/draftTypes.ts`: add auth/organization types and credentials/CSRF support.
- Modify `frontend/src/App.tsx`: add login gate, current-user shell, organization/document-type management views.
- Modify `frontend/src/App.test.tsx`: cover login and ownership-aware admin screens.
- Modify `frontend/src/styles/app.css`: token-based styles for login and management pages.
- Modify `AGENTS.md` and `docs/PROJECT_TASKS.md`: record P12 behavior and API.

## Task 1: Backend Security Skeleton

- [ ] Write failing controller tests for `/api/auth/login`, `/api/auth/me`, and unauthenticated `/api/drafts`.
- [ ] Add Spring Security dependencies.
- [ ] Add `SecurityConfiguration` with session auth, CSRF cookie repository, `/api/auth/login`, `/api/auth/csrf`, `/api/health` permit-all, and `/api/**` authenticated.
- [ ] Add `GongwenUserPrincipal`, `CurrentUser`, and `CurrentUserProvider`.
- [ ] Add `AuthController`, `AuthService`, and JSON login/logout/me behavior.
- [ ] Run focused auth tests and verify they pass.

## Task 2: Organization Schema And Repositories

- [ ] Write failing repository/service tests for department tree and user creation.
- [ ] Add Flyway V10 schema for `department`, `app_user`, `app_role`, `app_user_role`, and ownership columns.
- [ ] Add `DepartmentRepository`, `UserRepository`, DTOs, and service validation.
- [ ] Add bootstrap roles and default root department.
- [ ] Add admin bootstrap user with BCrypt password from env/property or generated one-time local password.
- [ ] Run focused organization tests and verify they pass.

## Task 3: Organization Admin APIs

- [ ] Write failing MVC tests for department CRUD and user CRUD with admin vs non-admin access.
- [ ] Add `DepartmentController` and `UserAdminController`.
- [ ] Enforce `SYSTEM_ADMIN` on account and department management endpoints.
- [ ] Return safe DTOs without password hashes.
- [ ] Run focused controller tests and verify they pass.

## Task 4: Ownership Filtering

- [ ] Write failing service/repository tests proving user A cannot read, update, delete, upload material to, or export user B's draft.
- [ ] Update `DraftRepository` and `DraftService` to create drafts with `created_by` and `department_id`.
- [ ] Update draft list/get/update/delete to enforce current user visibility.
- [ ] Update material service to rely on draft ownership before list/upload.
- [ ] Update export service to enforce draft ownership and save `exported_by`.
- [ ] Run focused draft/material/export tests and verify they pass.

## Task 5: Document Type And Template Ownership

- [ ] Write failing tests for visible document types and templates by current user.
- [ ] Update `DocumentTypeRepository/Service` to support system + own visible records and admin-all view.
- [ ] Update document type create/update/delete ownership checks.
- [ ] Update `TemplateRepository`, upload service, and version repository to bind and filter by current user.
- [ ] Run focused document/template tests and verify they pass.

## Task 6: Frontend Auth Gate

- [ ] Write failing Vitest tests for login page and `/api/auth/me` boot behavior.
- [ ] Update API client to send credentials and CSRF header for unsafe requests.
- [ ] Add auth types and calls.
- [ ] Add login page, logout action, current-user header, and unauthorized handling.
- [ ] Run focused frontend tests and verify they pass.

## Task 7: Frontend Organization Management

- [ ] Write failing Vitest tests for account, department, and document type management pages.
- [ ] Add organization API calls and types.
- [ ] Add navigation entries visible to system admin.
- [ ] Add department tree, account table/form, and document type management view using existing global components and token styles.
- [ ] Run focused frontend tests and verify they pass.

## Task 8: Full Verification And Docs

- [ ] Update `AGENTS.md` and `docs/PROJECT_TASKS.md`.
- [ ] Run backend test script.
- [ ] Run frontend tests.
- [ ] Run frontend build.
- [ ] Run `git diff --check`.
- [ ] Summarize changed files, verification, and residual risks.
