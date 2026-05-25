# MVP Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the first runnable foundation for the Gongwen Assistant: Spring Boot backend, React TypeScript frontend, PostgreSQL local environment, design-token CSS, and basic health checks.

**Architecture:** This plan establishes a two-app monorepo: `backend/` for Spring Boot and `frontend/` for React. The backend owns API, persistence, and future document/AI services; the frontend owns the Anthropic-inspired workbench shell and design system consumption. PostgreSQL runs through Docker Compose for local development.

**Tech Stack:** Java 21, Spring Boot 3.x, Gradle, PostgreSQL 16, Flyway, React, TypeScript, Vite, Vitest, CSS variables from `DESIGN.md`, Docker Compose.

---

## Scope Check

The product spec covers several independent subsystems: workbench UI, template management, material parsing, AI generation, quality checks, `.docx` export, permissions, and deployment. This plan intentionally implements only the shared foundation needed by all later plans.

Follow-up plans should be created separately for:

- Template management and `.docx` export
- Draft/workbench UI and structured block editing
- Material upload and Word/PDF text extraction
- AI generation and quality checks
- Auth/RBAC and file access control

## File Structure

Create this structure:

```text
D:\gongwen
├── AGENTS.md
├── DESIGN.md
├── docker-compose.yml
├── .env.example
├── backend
│   ├── build.gradle
│   ├── settings.gradle
│   └── src
│       ├── main
│       │   ├── java
│       │   │   └── com
│       │   │       └── gongwen
│       │   │           └── assistant
│       │   │               ├── GongwenAssistantApplication.java
│       │   │               ├── common
│       │   │               │   └── api
│       │   │               │       └── ApiResponse.java
│       │   │               └── health
│       │   │                   └── HealthController.java
│       │   └── resources
│       │       ├── application.yml
│       │       └── db
│       │           └── migration
│       │               └── V1__init_foundation.sql
│       └── test
│           └── java
│               └── com
│                   └── gongwen
│                       └── assistant
│                           └── health
│                               └── HealthControllerTest.java
├── frontend
│   ├── package.json
│   ├── index.html
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── src
│       ├── main.tsx
│       ├── App.tsx
│       ├── App.test.tsx
│       ├── styles
│       │   ├── tokens.css
│       │   └── app.css
│       └── test
│           └── setup.ts
└── docs
    └── superpowers
        └── plans
            └── 2026-05-25-mvp-foundation.md
```

Responsibilities:

- `backend/common/api/ApiResponse.java`: common response envelope for simple API consistency.
- `backend/health/HealthController.java`: first API endpoint and smoke test target.
- `backend/resources/db/migration/V1__init_foundation.sql`: verifies Flyway and PostgreSQL wiring with a small app metadata table.
- `frontend/src/styles/tokens.css`: concrete implementation of `DESIGN.md` design variables.
- `frontend/src/App.tsx`: first static shell proving the workbench visual structure.

---

### Task 1: Repository Configuration

**Files:**
- Modify: `D:\gongwen\.gitignore`
- Create: `D:\gongwen\.env.example`
- Modify: `D:\gongwen\AGENTS.md`

- [ ] **Step 1: Add environment example**

Create `D:\gongwen\.env.example`:

```dotenv
POSTGRES_DB=gongwen
POSTGRES_USER=gongwen
POSTGRES_PASSWORD=gongwen_dev_password
POSTGRES_PORT=5432

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/gongwen
SPRING_DATASOURCE_USERNAME=gongwen
SPRING_DATASOURCE_PASSWORD=gongwen_dev_password

VITE_API_BASE_URL=http://localhost:8080
```

- [ ] **Step 2: Ensure `.gitignore` covers generated outputs**

Modify `D:\gongwen\.gitignore` so it contains these lines:

```gitignore
.superpowers/

# OS and editor noise
.DS_Store
Thumbs.db
.idea/
.vscode/

# Logs and temp files
*.log
*.tmp

# Java / Spring Boot
target/
build/
.gradle/
backend/.gradle/
backend/build/

# Node / frontend
node_modules/
frontend/node_modules/
frontend/dist/
coverage/
frontend/coverage/
.env
.env.*
!.env.example
```

- [ ] **Step 3: Update AGENTS current status**

In `D:\gongwen\AGENTS.md`, update the "当前状态" section to mention this plan:

```markdown
当前状态：产品设计已确认，第一份实施计划已生成：`docs/superpowers/plans/2026-05-25-mvp-foundation.md`。尚未开始业务功能代码实现。
```

- [ ] **Step 4: Verify repository state**

Run:

```powershell
git status --short
```

Expected: `.env.example`, `.gitignore`, `AGENTS.md`, and this plan file are shown as changed or untracked.

- [ ] **Step 5: Commit repository configuration**

Run:

```powershell
git add .gitignore .env.example AGENTS.md docs/superpowers/plans/2026-05-25-mvp-foundation.md
git commit -m "docs: add mvp foundation plan"
```

Expected: commit succeeds.

---

### Task 2: PostgreSQL Local Environment

**Files:**
- Create: `D:\gongwen\docker-compose.yml`

- [ ] **Step 1: Add Docker Compose**

Create `D:\gongwen\docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: gongwen-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-gongwen}
      POSTGRES_USER: ${POSTGRES_USER:-gongwen}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-gongwen_dev_password}
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-gongwen} -d ${POSTGRES_DB:-gongwen}"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  postgres_data:
```

- [ ] **Step 2: Start PostgreSQL**

Run:

```powershell
docker compose up -d postgres
```

Expected: container `gongwen-postgres` starts.

- [ ] **Step 3: Verify database health**

Run:

```powershell
docker compose ps
```

Expected: `gongwen-postgres` shows `healthy` after a short wait.

- [ ] **Step 4: Commit database environment**

Run:

```powershell
git add docker-compose.yml
git commit -m "chore: add postgres compose service"
```

Expected: commit succeeds.

---

### Task 3: Spring Boot Backend Skeleton

**Files:**
- Create: `D:\gongwen\backend\settings.gradle`
- Create: `D:\gongwen\backend\build.gradle`
- Create: `D:\gongwen\backend\src\main\java\com\gongwen\assistant\GongwenAssistantApplication.java`
- Create: `D:\gongwen\backend\src\main\java\com\gongwen\assistant\common\api\ApiResponse.java`
- Create: `D:\gongwen\backend\src\main\java\com\gongwen\assistant\health\HealthController.java`
- Create: `D:\gongwen\backend\src\main\resources\application.yml`
- Create: `D:\gongwen\backend\src\main\resources\db\migration\V1__init_foundation.sql`
- Create: `D:\gongwen\backend\src\test\java\com\gongwen\assistant\health\HealthControllerTest.java`

- [ ] **Step 1: Create Gradle settings**

Create `D:\gongwen\backend\settings.gradle`:

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'gongwen-assistant-backend'
```

- [ ] **Step 2: Create backend build file**

Create `D:\gongwen\backend\build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.gongwen'
version = '0.1.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Create Spring Boot application**

Create `D:\gongwen\backend\src\main\java\com\gongwen\assistant\GongwenAssistantApplication.java`:

```java
package com.gongwen.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GongwenAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(GongwenAssistantApplication.class, args);
    }
}
```

- [ ] **Step 4: Create response envelope**

Create `D:\gongwen\backend\src\main\java\com\gongwen\assistant\common\api\ApiResponse.java`:

```java
package com.gongwen.assistant.common.api;

public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String message
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message);
    }
}
```

- [ ] **Step 5: Create health controller**

Create `D:\gongwen\backend\src\main\java\com\gongwen\assistant\health\HealthController.java`:

```java
package com.gongwen.assistant.health;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.ok(new HealthStatus("ok", "gongwen-assistant", OffsetDateTime.now()));
    }

    public record HealthStatus(String status, String service, OffsetDateTime checkedAt) {
    }
}
```

- [ ] **Step 6: Create application config**

Create `D:\gongwen\backend\src\main\resources\application.yml`:

```yaml
server:
  port: 8080

spring:
  application:
    name: gongwen-assistant
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/gongwen}
    username: ${SPRING_DATASOURCE_USERNAME:gongwen}
    password: ${SPRING_DATASOURCE_PASSWORD:gongwen_dev_password}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 7: Create first migration**

Create `D:\gongwen\backend\src\main\resources\db\migration\V1__init_foundation.sql`:

```sql
create table app_metadata (
    id bigserial primary key,
    metadata_key varchar(100) not null unique,
    metadata_value varchar(500) not null,
    created_at timestamptz not null default now()
);

insert into app_metadata (metadata_key, metadata_value)
values ('schema_version', 'foundation-v1');
```

- [ ] **Step 8: Write health controller test**

Create `D:\gongwen\backend\src\test\java\com\gongwen\assistant\health\HealthControllerTest.java`:

```java
package com.gongwen.assistant.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsServiceStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.status", equalTo("ok")))
                .andExpect(jsonPath("$.data.service", equalTo("gongwen-assistant")));
    }
}
```

- [ ] **Step 9: Run backend tests**

Run:

```powershell
cd D:\gongwen\backend
gradle test
```

Expected: tests pass. If `gradle` is unavailable, install/use the repo wrapper in a follow-up step and record the blocker.

- [ ] **Step 10: Run backend with PostgreSQL**

Run:

```powershell
cd D:\gongwen\backend
gradle bootRun
```

Expected: app starts on port `8080` and Flyway applies `V1__init_foundation.sql`.

- [ ] **Step 11: Verify backend health**

In a second terminal, run:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected: JSON response with `success: true` and `data.status: "ok"`.

- [ ] **Step 12: Commit backend skeleton**

Run:

```powershell
git add backend
git commit -m "feat: add spring boot backend foundation"
```

Expected: commit succeeds.

---

### Task 4: React Frontend Skeleton With Design Tokens

**Files:**
- Create: `D:\gongwen\frontend\package.json`
- Create: `D:\gongwen\frontend\index.html`
- Create: `D:\gongwen\frontend\vite.config.ts`
- Create: `D:\gongwen\frontend\tsconfig.json`
- Create: `D:\gongwen\frontend\src\main.tsx`
- Create: `D:\gongwen\frontend\src\App.tsx`
- Create: `D:\gongwen\frontend\src\App.test.tsx`
- Create: `D:\gongwen\frontend\src\styles\tokens.css`
- Create: `D:\gongwen\frontend\src\styles\app.css`
- Create: `D:\gongwen\frontend\src\test\setup.ts`

- [ ] **Step 1: Create package manifest**

Create `D:\gongwen\frontend\package.json`:

```json
{
  "name": "gongwen-assistant-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "test": "vitest --run",
    "preview": "vite preview"
  },
  "dependencies": {
    "@vitejs/plugin-react": "^4.3.3",
    "lucide-react": "^0.468.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/react": "^16.1.0",
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "typescript": "^5.6.3",
    "vite": "^5.4.11",
    "vitest": "^2.1.5"
  }
}
```

- [ ] **Step 2: Create Vite HTML**

Create `D:\gongwen\frontend\index.html`:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>公文助手</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 3: Create Vite config**

Create `D:\gongwen\frontend\vite.config.ts`:

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
  },
});
```

- [ ] **Step 4: Create TypeScript config**

Create `D:\gongwen\frontend\tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["DOM", "DOM.Iterable", "ES2020"],
    "allowJs": false,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "forceConsistentCasingInFileNames": true,
    "module": "ESNext",
    "moduleResolution": "Node",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx"
  },
  "include": ["src"],
  "references": []
}
```

- [ ] **Step 5: Create design tokens CSS**

Create `D:\gongwen\frontend\src\styles\tokens.css` using the core tokens from `DESIGN.md`:

```css
:root {
  --color-ivory-light: #faf9f5;
  --color-ivory-medium: #f0eee6;
  --color-ivory-dark: #e8e6dc;
  --color-oat: #e3dacc;
  --color-manilla: #ebdbbc;
  --color-slate-dark: #141413;
  --color-slate-medium: #3d3d3a;
  --color-slate-light: #5e5d59;
  --color-cloud-dark: #87867f;
  --color-cloud-medium: #b0aea5;
  --color-cloud-light: #d1cfc5;
  --color-accent: #c6613f;
  --color-clay: #d97757;
  --color-olive: #788c5d;
  --color-cactus: #bcd1ca;
  --color-sky: #6a9bcc;
  --color-heather: #cbcadb;
  --color-fig: #c46686;
  --color-coral: #ebcece;

  --color-bg: var(--color-ivory-light);
  --color-bg-subtle: var(--color-ivory-medium);
  --color-bg-muted: var(--color-ivory-dark);
  --color-surface: #ffffff;
  --color-surface-warm: #fffdf8;
  --color-text: var(--color-slate-dark);
  --color-text-muted: var(--color-slate-light);
  --color-text-subtle: var(--color-cloud-dark);
  --color-text-inverse: var(--color-ivory-light);
  --color-border: rgba(20, 20, 19, 0.12);
  --color-border-strong: rgba(20, 20, 19, 0.2);
  --color-border-subtle: rgba(20, 20, 19, 0.07);
  --color-primary: var(--color-slate-dark);
  --color-primary-hover: var(--color-slate-medium);
  --color-success: var(--color-olive);
  --color-success-bg: #eef3e8;
  --color-warning: var(--color-clay);
  --color-warning-bg: #fbefe9;
  --color-danger: #b53333;
  --color-danger-bg: #f7e4e1;
  --color-info: var(--color-sky);
  --color-info-bg: #eaf2fa;

  --font-sans: "Inter", "Noto Sans SC", "Microsoft YaHei", "PingFang SC", Arial, sans-serif;
  --font-serif: "Source Serif 4", "Noto Serif SC", "Songti SC", SimSun, Georgia, serif;
  --font-mono: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;

  --text-xs: 0.75rem;
  --text-sm: 0.875rem;
  --text-md: 1rem;
  --text-lg: 1.125rem;
  --text-xl: 1.25rem;
  --text-2xl: 1.5rem;
  --text-3xl: 2rem;

  --space-1: 0.25rem;
  --space-2: 0.5rem;
  --space-3: 0.75rem;
  --space-4: 1rem;
  --space-5: 1.5rem;
  --space-6: 2rem;
  --space-7: 2.5rem;
  --space-8: 3rem;

  --shadow-xs: 0 1px 2px rgba(20, 20, 19, 0.06);
  --shadow-sm: 0 4px 10px rgba(20, 20, 19, 0.08);
  --shadow-md: 0 12px 28px rgba(20, 20, 19, 0.12);

  color: var(--color-text);
  background: var(--color-bg);
  font-family: var(--font-sans);
}
```

- [ ] **Step 6: Create app CSS**

Create `D:\gongwen\frontend\src\styles\app.css`:

```css
@import "./tokens.css";

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  min-width: 320px;
  background: var(--color-bg);
}

button,
input,
textarea,
select {
  font: inherit;
}

.app-shell {
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 0 var(--space-5);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg);
}

.brand-title {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 600;
}

.brand-subtitle {
  margin: 2px 0 0;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}

.workbench {
  display: grid;
  grid-template-columns: minmax(280px, 320px) minmax(520px, 1fr) minmax(300px, 340px);
  gap: var(--space-4);
  padding: var(--space-4);
  min-height: calc(100vh - 64px);
}

.panel {
  background: var(--color-surface-warm);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}

.panel-header {
  padding: var(--space-4);
  border-bottom: 1px solid var(--color-border-subtle);
}

.panel-title {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 600;
}

.panel-body {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
}

.field {
  min-height: 38px;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-text);
  padding: 8px 10px;
  font-size: var(--text-sm);
}

.field:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgba(198, 97, 63, 0.12);
  outline: none;
}

.btn {
  min-height: 36px;
  padding: 0 14px;
  border-radius: 6px;
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: var(--color-text-inverse);
  font-size: var(--text-sm);
  font-weight: 500;
}

.btn.secondary {
  background: transparent;
  color: var(--color-text);
  border-color: var(--color-border-strong);
}

.btn:focus-visible {
  outline: 2px solid rgba(198, 97, 63, 0.45);
  outline-offset: 2px;
}

.document-stage {
  background: var(--color-bg-muted);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: var(--space-5);
  overflow: auto;
}

.document-paper {
  width: min(100%, 720px);
  min-height: 760px;
  margin: 0 auto;
  background: var(--color-surface);
  color: var(--color-text);
  box-shadow: var(--shadow-md);
  padding: var(--space-8);
}

.document-title {
  text-align: center;
  font-size: var(--text-2xl);
  line-height: 1.35;
}

.document-paper p {
  font-size: var(--text-md);
  line-height: 1.9;
}

.check-item {
  border: 1px solid var(--color-border-subtle);
  border-left-width: 4px;
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--color-surface);
  font-size: var(--text-sm);
}

.check-item.success {
  border-left-color: var(--color-success);
  background: var(--color-success-bg);
}

.check-item.warning {
  border-left-color: var(--color-warning);
  background: var(--color-warning-bg);
}

@media (max-width: 1180px) {
  .workbench {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 7: Create React entry point**

Create `D:\gongwen\frontend\src\main.tsx`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import './styles/app.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

- [ ] **Step 8: Create workbench shell**

Create `D:\gongwen\frontend\src\App.tsx`:

```tsx
export function App() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1 className="brand-title">公文助手</h1>
          <p className="brand-subtitle">通知 / 请示 / 报告起草工作台</p>
        </div>
        <button className="btn">导出 Word</button>
      </header>

      <main className="workbench">
        <section className="panel" aria-label="起草信息">
          <div className="panel-header">
            <h2 className="panel-title">文种、模板与材料</h2>
          </div>
          <div className="panel-body">
            <select className="field" aria-label="文种">
              <option>通知</option>
              <option>请示</option>
              <option>报告</option>
            </select>
            <input className="field" aria-label="标题" defaultValue="关于开展年度档案整理工作的通知" />
            <textarea className="field" aria-label="事项背景" defaultValue="背景：年度资料归档不完整，需要统一整理。" />
            <button className="btn secondary">上传 Word/PDF 材料</button>
          </div>
        </section>

        <section aria-label="公文预览">
          <div className="document-stage">
            <article className="document-paper">
              <h2 className="document-title">关于开展年度档案整理工作的通知</h2>
              <p>各部门、各直属单位：</p>
              <p>为进一步规范年度档案管理工作，提升资料归集、整理和归档质量，现就开展年度档案整理工作有关事项通知如下。</p>
              <p><strong>一、整理范围</strong><br />各部门在本年度形成的会议材料、制度文件、项目资料、台账记录及其他应归档资料。</p>
              <p><strong>二、工作要求</strong><br />各部门应指定专人负责，按照统一目录完成资料整理，确保材料完整、分类准确、命名规范。</p>
              <p style={{ textAlign: 'right', marginTop: '2rem' }}>办公室<br />2026年5月25日</p>
            </article>
          </div>
        </section>

        <section className="panel" aria-label="AI 建议和质检">
          <div className="panel-header">
            <h2 className="panel-title">AI 建议与质检</h2>
          </div>
          <div className="panel-body">
            <div className="check-item success">基础字段完整</div>
            <div className="check-item warning">建议补充验收标准</div>
            <div className="check-item warning">建议引用档案管理制度</div>
            <button className="btn secondary">优化选中段落</button>
          </div>
        </section>
      </main>
    </div>
  );
}
```

- [ ] **Step 9: Create test setup**

Create `D:\gongwen\frontend\src\test\setup.ts`:

```ts
import '@testing-library/jest-dom/vitest';
```

- [ ] **Step 10: Create frontend smoke test**

Create `D:\gongwen\frontend\src\App.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';

describe('App', () => {
  it('renders the gongwen workbench shell', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: '公文助手' })).toBeInTheDocument();
    expect(screen.getByLabelText('起草信息')).toBeInTheDocument();
    expect(screen.getByLabelText('公文预览')).toBeInTheDocument();
    expect(screen.getByLabelText('AI 建议和质检')).toBeInTheDocument();
  });
});
```

- [ ] **Step 11: Install frontend dependencies**

Run:

```powershell
cd D:\gongwen\frontend
npm install
```

Expected: `package-lock.json` is created and dependencies install successfully.

- [ ] **Step 12: Run frontend tests**

Run:

```powershell
cd D:\gongwen\frontend
npm test
```

Expected: test passes.

- [ ] **Step 13: Build frontend**

Run:

```powershell
cd D:\gongwen\frontend
npm run build
```

Expected: build succeeds and writes `frontend/dist`.

- [ ] **Step 14: Commit frontend skeleton**

Run:

```powershell
git add frontend package-lock.json
git commit -m "feat: add react workbench foundation"
```

Expected: commit succeeds. If `package-lock.json` is inside `frontend/`, use `git add frontend/package-lock.json`.

---

### Task 5: Local Full-Stack Smoke Verification

**Files:**
- Modify: `D:\gongwen\AGENTS.md`

- [ ] **Step 1: Start database**

Run:

```powershell
cd D:\gongwen
docker compose up -d postgres
```

Expected: PostgreSQL is running.

- [ ] **Step 2: Start backend**

Run:

```powershell
cd D:\gongwen\backend
gradle bootRun
```

Expected: backend starts on `http://localhost:8080`.

- [ ] **Step 3: Start frontend**

In another terminal, run:

```powershell
cd D:\gongwen\frontend
npm run dev
```

Expected: frontend starts on `http://localhost:5173`.

- [ ] **Step 4: Verify backend health**

Run:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected:

```powershell
success data
------- ----
   True @{status=ok; service=gongwen-assistant; checkedAt=...}
```

- [ ] **Step 5: Verify frontend page**

Open:

```text
http://localhost:5173
```

Expected:

- Header displays `公文助手`.
- Left panel displays文种、模板与材料 controls.
- Center document preview is visible.
- Right panel displays AI 建议与质检.
- Colors match `DESIGN.md`: ivory background, slate text, restrained clay/olive semantic accents.

- [ ] **Step 6: Update AGENTS current status**

Modify the "当前状态" section in `D:\gongwen\AGENTS.md`:

```markdown
当前状态：MVP Foundation 已完成。仓库包含 Spring Boot 后端骨架、React 前端骨架、PostgreSQL Docker Compose、本项目 DESIGN.md token 落地，以及基础健康检查。
```

- [ ] **Step 7: Run final checks**

Run:

```powershell
cd D:\gongwen\backend
gradle test
cd D:\gongwen\frontend
npm test
npm run build
cd D:\gongwen
git status --short
```

Expected:

- Backend tests pass.
- Frontend tests pass.
- Frontend build passes.
- `AGENTS.md` is modified.

- [ ] **Step 8: Commit final status update**

Run:

```powershell
git add AGENTS.md
git commit -m "docs: record mvp foundation status"
```

Expected: commit succeeds.

- [ ] **Step 9: Push branch**

Run:

```powershell
git push
```

Expected: all commits are pushed to `origin/main`.

---

## Self-Review

Spec coverage:

- Product workbench shell: covered by Task 4.
- Anthropic-inspired UI system: covered by Task 4 using `DESIGN.md` tokens.
- Java Spring Boot backend: covered by Task 3.
- PostgreSQL: covered by Task 2 and Task 3 migration.
- API response consistency: covered by `ApiResponse`.
- Health/smoke verification: covered by Task 3 and Task 5.
- AI, template, material, quality check, export, and RBAC: intentionally excluded from this foundation plan and listed as follow-up plans.

Placeholder scan:

- No unfinished marker patterns or vague future-work instructions are used.
- Every code creation step includes concrete content.

Type consistency:

- Backend package root is consistently `com.gongwen.assistant`.
- Frontend component export is consistently `App`.
- Test labels match the JSX `aria-label` values.
