# P12 组织账号与权限体系设计

日期：2026-05-30
状态：已确认，进入实施
项目目录：`D:\gongwen`

## 1. 背景

公文助手已经具备文种、模板、草稿、材料、质检和 Word 导出的主链路，但当前仍是单人本地工作台形态。数据库里虽然预留了 `created_by`、`department_id`、`tenant_id`，但没有真实账号、部门树、登录态和服务端权限过滤。

P12 的目标是把系统升级为可供组织内部多人使用的基础版本。账号、部门、角色和登录必须真实落库；草稿、模板、文种、材料和导出记录必须按当前账号归属过滤；系统管理员可以管理账号、部门和文种。

## 2. 目标

- 建立真实账号密码登录，不使用前端假用户。
- 建立部门树，账号归属到部门。
- 建立角色体系：系统管理员、模板管理员、起草人。
- 建立账号管理、部门管理、文种管理 UI。
- 草稿、材料、模板、导出记录绑定创建人和部门。
- 起草人只能访问自己的草稿、材料和导出记录。
- 模板管理员可以管理自己创建的模板和文种。
- 系统管理员可以管理全部账号、部门、文种，并查看全部业务数据。
- 保留现有工作台、模板管理、草稿列表和导出能力。

## 3. 认证方案

采用本地账号密码 + Spring Security + HttpOnly Session Cookie。

选择理由：

- 私有化 Web 应用更适合服务端 session 管理。
- 不把 token 存入 localStorage，降低 XSS 后凭证泄漏风险。
- 后续接入 OA、LDAP、统一身份认证时，后端认证边界更清晰。
- 前端通过 `credentials: 'include'` 发送 Cookie。

CSRF 使用 Spring Security `CookieCsrfTokenRepository`。前端登录前请求 `/api/auth/csrf` 获取 token，后续非 GET 请求带 `X-XSRF-TOKEN`。

## 4. 数据模型

新增表：

- `department`
  - `id`
  - `parent_id`
  - `code`
  - `name`
  - `status`
  - `sort_order`
  - `created_at`
  - `updated_at`
- `app_user`
  - `id`
  - `username`
  - `display_name`
  - `password_hash`
  - `department_id`
  - `status`
  - `last_login_at`
  - `created_at`
  - `updated_at`
- `app_role`
  - `code`
  - `name`
  - `description`
- `app_user_role`
  - `user_id`
  - `role_code`

迁移补充：

- `document_type` 增加 `created_by`、`department_id`。
- `document_template` 使用已有 `created_by`、`department_id`。
- `draft` 使用已有 `created_by`、`department_id`。
- `document_template_version` 使用已有 `created_by`。
- `export_record` 使用已有 `exported_by`，补 `department_id`。

现有历史数据：

- 旧文种、模板、草稿默认保留可用。
- `created_by is null` 的文种视为系统内置文种，对所有账号可见。
- `created_by is null` 的模板视为系统内置模板，对所有账号可见。
- 首次启动如没有账号，应用创建一个系统管理员账号；密码来自环境变量，未配置时生成一次性本地密码并写入启动日志。

## 5. API 契约

认证：

```text
GET  /api/auth/csrf
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/logout
```

组织管理：

```text
GET    /api/departments
POST   /api/departments
PUT    /api/departments/{id}
DELETE /api/departments/{id}
```

账号管理：

```text
GET    /api/users
POST   /api/users
PUT    /api/users/{id}
PUT    /api/users/{id}/password
DELETE /api/users/{id}
```

已有 API 权限化：

- `GET /api/document-types` 返回系统内置 + 当前账号可见文种；系统管理员返回全部。
- `POST/PUT/DELETE /api/document-types` 绑定和校验当前账号。
- `GET/POST/DELETE /api/templates` 绑定和校验当前账号。
- `GET/POST/PUT/DELETE /api/drafts/**` 只能访问当前账号可见草稿。
- `GET/POST /api/drafts/{draftId}/materials` 先校验草稿归属。
- `POST /api/exports/drafts/{draftId}/word` 先校验草稿归属，并记录导出人。

## 6. 前端交互

新增登录页：

- 账号、密码输入。
- 登录中、失败、禁用状态。
- 未登录时不渲染工作台主壳。

应用壳新增：

- 当前用户展示：姓名、部门、角色。
- 退出登录。
- 系统设置下拆出账号管理、部门管理、文种管理入口。

部门管理：

- 树级列表。
- 新建子部门、重命名、删除。
- 仅允许删除没有子部门、账号或业务数据引用的空部门；删除为物理删除，前端必须二次确认。

账号管理：

- 列表展示账号、姓名、部门、角色、状态。
- 新建账号、编辑姓名/部门/角色/状态、重置密码。
- 不展示密码哈希。

文种管理：

- 使用已有文种 CRUD API。
- 展示系统内置文种和当前账号创建的文种。
- 删除前二次确认。
- 有草稿或模板引用时后端阻断删除。

## 7. 权限规则

角色：

- `SYSTEM_ADMIN`：账号、部门、文种、模板、草稿全量管理。
- `TEMPLATE_ADMIN`：管理自己创建的模板和文种，查看系统内置文种和模板。
- `DRAFTER`：创建和管理自己的草稿、材料、导出记录；查看系统内置文种和模板。

通用规则：

- 所有 `/api/**` 除健康检查、CSRF 和登录外都要求登录。
- 高风险操作包括删除账号、删除部门、删除文种、删除模板、删除草稿，前端二次确认，后端权限校验。
- API 不返回密码哈希、session id、CSRF 内部信息。
- 错误响应使用稳定 `errorCode` 和安全 message。

## 8. 验收标准

- 未登录访问业务 API 返回 401。
- 使用初始化管理员可以登录并获取当前用户。
- 系统管理员可以创建部门、创建账号、给账号分配部门和角色。
- 新账号可以登录。
- 起草人 A 创建的草稿，起草人 B 不可读取、修改、删除或导出。
- 文种 CRUD 有前端管理界面，删除有引用的文种被后端阻断。
- 模板和草稿列表按当前账号可见范围过滤。
- 前端登录、加载、错误、空、禁用和权限不足状态可见。
- 后端测试、前端测试和前端构建通过。

## 9. 非目标

- 不做多租户 SaaS 运营后台。
- 不做复杂 OAuth/OIDC 接入。
- 不做部门级审核流。
- 不做细粒度字段级权限。
- 不做生产级限流网关；本阶段只保留服务端权限边界和后续限流接口位置。
