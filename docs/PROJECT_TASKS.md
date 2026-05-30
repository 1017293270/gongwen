# 公文助手项目长期任务总表

本文件是公文助手从当前工程地基走向完整产品落地的长期任务入口。它用于人类开发者和 AI Agent 协同排期、选取下一阶段任务、判断依赖关系和验收边界。

使用规则：

- 每次开始新任务前，先阅读 `AGENTS.md`、`DESIGN.md`、本文件和当前阶段的实施计划。
- 每次只推进一个阶段或一个阶段内的清晰子任务，不要把后续预留做成完整实现。
- 阶段完成后必须更新本文件的状态、验收结果和下一步建议。
- 涉及架构边界、数据模型、接口契约、权限、安全、部署方式变化时，同步更新 `AGENTS.md`。

## 当前状态

当前分支：`p2-p3-draft-workbench`

已完成：

- P0 工程地基。
- P1 模板与 Word 导出最小闭环。
- Spring Boot 后端骨架。
- React + TypeScript + Vite 前端骨架。
- PostgreSQL Docker Compose。
- Flyway 初始化迁移。
- `/api/health`。
- `.docx` 模板占位符解析。
- Word 模板填充导出。
- 模板、模板字段、导出记录数据库表。
- 最小模板解析和 Word 导出 API。
- 三栏公文工作台静态壳子。
- 前端测试和构建验证。
- 后端测试、Docker 化 `bootRun`、Flyway v2、健康检查和导出烟测验证。
- 文种、草稿、草稿块数据模型。
- 草稿创建、读取、块保存 API。
- 工作台接入真实草稿数据，支持编辑、预览和保存。
- 材料上传、Word/PDF 文本提取、材料状态保存和工作台材料列表。
- 前端统一轻反馈组件，基于 `@radix-ui/react-toast` 并使用 `DESIGN.md` token。
- 前端通过 `VITE_API_BASE_URL` 联调映射到 18080 的后端容器。
- 后端 CORS 允许本地 Vite 端口访问 `/api/**`。
- 应用总览页和左侧侧边栏信息架构壳子，工作台、草稿列表、模板管理、材料库、导出记录、AI 任务和系统设置入口。
- 草稿列表首版真实闭环：按文种查询草稿、展示草稿卡片、空状态、新建空白草稿、点击草稿进入工作台，并在工作台内继续选择同文种模板。
- 系统设置 AI 配置页、运行时 Mock / DeepSeek 切换、DeepSeek 模型适配和连接测试 API。
- P8A 模板引擎后端底座：模板版本、`TemplateProfile`、解析风险、上传解析 API 和 profile 查询 API。
- P8 基础质检首版闭环：规则质检、模板适配质检、DeepSeek/Mock 质检建议、结果落库和右栏质检面板。
- P10 模板管理员后台首版：文种文件夹、模板卡片、新增模板、上传新版本、模板列表 API 和 profile 解析结果展示。
- P10 模板智能识别首版：Word 没有 `{{字段名}}` 占位符时不再直接按解析失败处理，而是用 AI/Mock 识别文件类型、文种倾向和建议占位符。
- P10B 结构维度闭环已形成首个后端共享合同：profile 输出可识别结构、原文片段、位置、来源和字体、字号、加粗、颜色、对齐、首行缩进、行距、段前段后，维度编辑保存到 `template_rule`，同一套生效格式已被工作台预览、基础质检和 `.docx` 导出复用。
- P10C 工作台结构节点前端切片：新增 `WorkbenchNode` 派生层，左栏正文目录、中间类 Word 纸张和右栏局部操作上下文统一围绕结构节点；正文小标题和正文内容分开展示、选择和编辑，底层仍兼容现有 `DraftBlock`。
- P11 导出体验阻断切片：新增草稿绑定模板版本后的 Word 导出 API 和工作台右栏入口，导出前会自动保存、运行基础质检，后端按最近一次质检结果阻断 `exportBlocked=true` 的草稿。
- P9 账号与组织底座第三档首版：Spring Security 会话登录、CSRF、系统管理员 bootstrap、部门树、账号与角色表、部门管理 UI、账号管理 UI、文种 CRUD UI，以及草稿/模板/文种的当前用户归属过滤。

当前推荐下一阶段：

- P11 导出体验继续增强，补导出记录列表、历史文件下载和失败记录追溯。
- P9 权限继续收口，补材料/导出文件下载鉴权、模板管理员细粒度授权、审计日志和权限不足 UI。
- P10C 结构节点持久化，新增后端节点 API/存储，并把 AI/质检/导出从草稿块逐步迁到 `nodeId`。
- P10B 后续增强，继续补 richer style inheritance 和 unsupported OOXML 结构显式提示。
- P10 模板管理员后台后续增强，继续做字段映射、启停和版本详情。

## 全局落地原则

- 后端优先打通严肃公文生产闭环，再补管理 UI 和增强体验。
- MVP 保持单单位私有化 Web 应用，不提前实现完整 SaaS、多租户、审核流和插件生态。
- 草稿、模板、材料、AI trace、质检结果、导出记录必须结构化保存。
- 模型调用、Prompt、材料解析、模板填充、导出、质检必须保持服务边界清晰。
- 所有文件上传、下载、导出、删除、权限变更都必须考虑鉴权、校验和审计预留。
- 前端必须遵循 `DESIGN.md` token 和三栏工作台结构。
- 常规 UI 必须优先复用全局组件和全局样式；页面局部 CSS 只能用于特殊业务布局，不能覆盖按钮、表单、状态提示、空状态等全局组件的颜色、字号、图标尺寸、padding、对齐和交互状态。
- 每个阶段完成前必须运行相关测试，不能验证时必须记录具体原因。

## 阶段路线图

### P0 工程地基

状态：已完成。

目标：建立可运行的前后端和数据库基础。

已完成任务：

- 创建 Spring Boot 后端工程。
- 创建 React + TypeScript + Vite 前端工程。
- 添加 PostgreSQL Docker Compose。
- 添加 `.env.example`。
- 添加 Flyway 初始迁移。
- 添加 `/api/health`。
- 添加前端工作台静态壳子。
- 添加后端和前端 smoke test。

验收状态：

- `npm test` 通过。
- `npm run build` 通过。
- `docker compose up -d postgres` 后 PostgreSQL healthy。
- 后端通过 `gradle:8.10.2-jdk21` 容器执行 `gradle test` 成功。
- 后端通过容器 `bootRun` 成功，`/api/health` 返回 `success: true`。

后续维护：

- 可补 Gradle Wrapper，减少对本机 Gradle 或 Docker Gradle 镜像的依赖。
- 可补后端 Dockerfile 和一键开发脚本。

### P1 模板与 Word 导出最小闭环

状态：已完成。

目标：先打通后端 `.docx` 模板占位符解析和结构化草稿导出 Word 的核心能力，不做完整前端模板管理页面。

范围：

- 使用 Apache POI 处理 `.docx`。
- 解析 `{{字段名}}` 格式的 Word 模板占位符。
- 支持基础字段：`标题`、`主送`、`正文`、`附件`、`落款`、`日期`。
- 新增模板、模板字段、导出记录数据表。
- 新增模板解析服务。
- 新增 Word 导出服务。
- 使用测试资源里的 `.docx` 模板验证功能。
- 提供最小 API 供后续前端和测试使用。

建议后端模块：

- `com.gongwen.assistant.template`
- `com.gongwen.assistant.template.parser`
- `com.gongwen.assistant.export`
- `com.gongwen.assistant.export.word`

建议数据表：

- `document_template`
- `template_field`
- `export_record`

验收标准：

- 已能从 `.docx` 测试模板中解析出所有 `{{字段名}}` 占位符。
- 已能识别缺失字段并返回明确错误。
- 已能将结构化字段数据填入 `.docx` 模板。
- 已通过 Apache POI 重新读取导出 `.docx`，确认内容已替换。
- 已保存导出记录，包含模板名称、模板版本、文件名、状态和错误信息。

已实现 API：

- `POST /api/templates/parse`
- `POST /api/exports/word`

已实现后端模块：

- `com.gongwen.assistant.template`
- `com.gongwen.assistant.template.parser`
- `com.gongwen.assistant.exporting`
- `com.gongwen.assistant.exporting.word`

验证状态：

- 后端完整测试通过。
- Flyway v2 已在 PostgreSQL 中应用。
- `/api/exports/word` 已通过运行时烟测导出 `.docx`。
- `export_record` 已写入成功记录。

不做：

- 完整模板管理 UI。
- 完整权限控制。
- 复杂 Word 样式精修。
- PDF 导出。
- 模板审核流。
- 模板启停的前端交互。

### P2 草稿结构与文种模型

状态：已完成。

目标：建立通知、请示、报告的结构化草稿模型。

范围：

- `DocumentType` 文种表和初始化数据。
- `Draft` 草稿主表。
- `DraftBlock` 草稿块表。
- 草稿状态字段。
- 标题、主送、正文段落、附件、落款、日期等块类型。
- 草稿创建、读取、保存、更新 API。

验收标准：

- 已能创建通知、请示、报告草稿。
- 已能保存和读取草稿块。
- 草稿已按 `DraftBlock` 结构化保存，不是单一 HTML 或纯文本。
- 后续导出可以直接消费草稿结构。

已实现 API：

- `GET /api/document-types`
- `POST /api/drafts`
- `GET /api/drafts?documentTypeCode={code}`
- `GET /api/drafts/{id}`
- `PUT /api/drafts/{id}/blocks`

验证状态：

- 后端完整测试通过。
- Flyway v3 已在 PostgreSQL 中应用。
- 运行时 API 烟测已验证草稿创建、保存和数据库持久化。

### P3 工作台前端接入真实数据

状态：已完成。

目标：让三栏工作台从静态壳子变成可编辑、可保存的真实工作台。

范围：

- 左侧字段表单接草稿 API。
- 中间 Word 风格预览接 `DraftBlock`。
- 支持分块编辑和保存。
- 右侧显示导出前状态。
- 添加加载、错误、空、禁用状态。
- 桌面优先，移动端至少可用。

验收标准：

- 页面已能从后端加载默认草稿。
- 编辑标题、正文、落款、日期后能保存，并由数据库持久化。
- 文本未发现溢出或遮挡。
- UI 保持 `DESIGN.md` 三栏工作台风格。

验证状态：

- `npm test` 通过。
- `npm run build` 通过。
- 浏览器联调已验证加载、编辑标题、保存和数据库回查。

### P4 材料上传与文本提取

状态：已完成。

目标：支持 Word/PDF 材料上传、文本提取和草稿上下文绑定。

范围：

- `Material` 数据表。
- Word/PDF 文件上传。
- 文件类型和大小校验。
- 本地文件存储抽象。
- Word 文本提取。
- PDF 文本提取。
- 提取失败状态。

验收标准：

- 已只能上传允许类型和大小的文件。
- 已将材料归属于草稿。
- 已能提取 Word/PDF 文本。
- 已能在提取失败时保存 `FAILED` 状态和明确错误。

已实现 API：

- `GET /api/drafts/{draftId}/materials`
- `POST /api/drafts/{draftId}/materials`

已实现后端模块：

- `com.gongwen.assistant.material`
- 本地材料存储抽象 `MaterialStorage` / `LocalMaterialStorage`
- Word/PDF 提取器 `DocxPdfMaterialTextExtractor`

已实现前端能力：

- 工作台左栏材料上传。
- 材料列表、状态、提取字数、失败原因展示。
- 基于 `@radix-ui/react-toast` 的统一轻反馈组件。
- 基于提纲章节的单段正文生成按钮、生成中状态、失败重试状态和成功后预览刷新。

验证状态：

- 后端完整测试通过。
- 前端 `npm test` 通过。
- 前端 `npm run build` 通过。

### P5 AI 生成提纲

状态：已完成。

目标：接入模型适配层，生成结构化公文提纲。

范围：

- `ModelAdapter`。
- `PromptBuilder`。
- `AiGenerationTrace` 数据表。
- 生成提纲 API。
- 结构化输出解析和校验。
- 超时、失败、重试、取消状态。

验收标准：

- 已能基于文种、草稿字段和 READY 材料摘要生成提纲。
- 输出包含标题建议、正文结构、段落要点、缺失信息提示。
- AI trace 不记录完整敏感正文、完整材料提取文本或完整 prompt。
- 失败时返回归一化错误。

已实现 API：

- `POST /api/drafts/{draftId}/ai/outline`

已实现后端模块：

- `com.gongwen.assistant.ai`
- `PromptBuilder`
- `ModelAdapter`
- `MockModelAdapter`
- `AiOutlineService`
- `AiGenerationTraceRepository` / `JdbcAiGenerationTraceRepository`

已实现前端能力：

- 右栏提纲补充要求输入。
- 生成提纲、生成中禁用、失败重试。
- 标题建议、章节要点和缺失信息展示。
- 成功和失败 Toast 反馈。

验证状态：

- 后端 P5 focused 测试通过。
- 前端 `npm test` 通过。
- 前端 `npm run build` 通过。

### P6 AI 逐段正文生成

状态：已完成。

目标：基于确认后的提纲逐段生成正文块。

范围：

- 段落级生成 API。
- 单段失败单段重试。
- 基于提纲一键串行生成全部正文。
- 生成进度状态。
- 生成 trace 绑定到草稿块。
- 前端逐段生成状态。

验收标准：

- 已能按提纲章节生成单个正文块。
- 已能从提纲入口按章节顺序生成全部正文。
- 生成正文会保留提纲章节标题，避免段落标题显示不一致。
- 单段失败不影响已生成段落。
- 用户可重试失败段落。
- 生成结果已保存为 `DraftBlock`。

已实现 API：

- `POST /api/drafts/{draftId}/ai/paragraph`

已实现后端模块：

- `AiParagraphService`
- `AiParagraphRequest` / `AiParagraphResponse`
- `ParagraphPrompt`
- `AiParagraphModelResponse`
- `MockModelAdapter.generateParagraph`
- `DeepSeekModelAdapter.generateParagraph`

已实现前端能力：

- 提纲章节内“生成正文 / 正在生成 / 重试正文”按钮。
- 提纲结果顶部“生成全部正文”按钮，按章节顺序复用单段接口生成并保存。
- 成功后刷新草稿块和 Word 风格预览。
- 失败时显示右栏错误和 Toast 反馈。

验证状态：

- 后端全量测试通过。
- 前端 `npm test -- --run` 通过。
- 前端 `npm run build` 通过。
- 本地浏览器打开 `http://127.0.0.1:5175` 验证工作台渲染，无控制台错误。

### P6.5 应用总览与侧边栏壳子

状态：已完成。

目标：在继续 P7/P8 前补齐全局信息架构入口，让工作台、模板管理、材料、导出记录和 AI 任务有清晰入口。

范围：

- 默认总览页。
- 左侧侧边栏主导航。
- 工作台入口。
- 草稿列表、模板管理、材料库、导出记录、AI 任务、系统设置预留页面。
- 总览展示当前草稿、正文段落、READY 材料和阶段提醒。
- 总览加载、空、错误和可操作状态。
- 草稿列表已从预留页升级为真实首版，支持按通知/请示/报告筛选、加载草稿卡片、空状态、新建空白草稿并进入工作台。

验收标准：

- 默认进入总览页。
- 侧边栏可进入工作台。
- 工作台仍保持三栏主结构。
- 预留页面不抢占 MVP 范围，不提前实现完整管理后台。
- 桌面和窄屏布局可用，交互控件保持可访问名称和 focus-visible 状态。

验证状态：

- 前端 `npm test -- --run` 通过。
- 前端 `npm run build` 通过。
- 终端 Playwright 打开 `http://127.0.0.1:5175` 验证总览、主导航、最近草稿、工作台切换和右栏 AI 面板，控制台错误为 0。
- Codex Browser 插件因底层 `node_repl` 内核报 `failed to write kernel assets` 未能使用，已用 bundled Playwright 完成等价浏览器验证。

### P6.6 系统设置 AI 配置与 DeepSeek 适配

状态：已完成。

目标：把 AI 供应商配置放入系统设置，让本地 Mock 演示和 DeepSeek 云模型接入可以在运行时切换。

范围：

- 系统设置中的 AI 配置页面。
- DeepSeek Base URL、模型、超时和 API Key 运行时配置。
- AI 配置数据库持久化，后端重启后保留供应商和 DeepSeek 配置。
- 后端模型路由适配层。
- OpenAI 兼容 `/chat/completions` 调用。
- AI 配置读取、保存和连接测试 API。
- `.env.example` 新增 DeepSeek 相关环境变量。
- 测试连接前自动保存当前表单，避免表单状态和后端运行时状态不一致。
- 新增专题说明文档 `docs/AI_CONFIGURATION.md`。

验收标准：

- 未配置云模型密钥时仍默认使用 Mock，便于本地开发和演示。
- 启用 DeepSeek 时必须配置 API Key。
- API Key 响应只返回脱敏状态；数据库只保存加密密文，不保存明文，也不写入 trace。
- 后端重启后优先读取 `ai_provider_settings`，避免系统设置回到 Mock。
- DeepSeek 调用仍复用集中 PromptBuilder 和既有 trace 链路。
- 系统设置页面覆盖加载、保存中、测试中、成功、失败和禁用状态。
- 不改动侧边栏样式，仅将 AI 配置内容放入系统设置。
- 用户在表单中选择 DeepSeek 后，可以直接点测试连接；前端会先保存配置再测试，不会拿旧 Mock 状态测试。

已实现 API：

- `GET /api/ai/settings`
- `PUT /api/ai/settings`
- `POST /api/ai/settings/test`

已实现后端模块：

- `AiRuntimeProperties`
- `AiConfigurationState`
- `RoutingModelAdapter`
- `DeepSeekModelAdapter`
- `AiSettingsService`
- `AiSettingsController`
- `AiSettingsException`
- `AiProviderStatus`
- `AiSettingsRepository` / `JdbcAiSettingsRepository`
- `AiSettingsSecretCodec`
- `AiSettingsSnapshot`

已实现前端能力：

- 系统设置页 AI 配置表单。
- Mock / DeepSeek 切换。
- DeepSeek Base URL、模型、API Key 和超时配置。
- 保存配置和连接测试。
- 成功、失败和加载状态反馈。
- 测试连接时自动保存当前配置。

实现细节：

- `RoutingModelAdapter` 是 `ModelAdapter` 主入口。
- provider 为 `deepseek`、启用 DeepSeek 且已有 API Key 时，提纲生成和段落生成走 `DeepSeekModelAdapter`。
- provider 为 `mock`、未启用 DeepSeek 或未配置 API Key 时，继续走 Mock。
- `DeepSeekModelAdapter` 调用 OpenAI 兼容 `/chat/completions`，要求 JSON 输出。
- DeepSeek 提纲输出解析为 `AiOutlineResponse`。
- DeepSeek 段落输出解析为 `AiParagraphModelResponse`。
- 配置保存到 `ai_provider_settings`，`AiConfigurationState` 启动时优先读取数据库；没有持久化记录时使用环境变量。
- DeepSeek API Key 使用本机密钥文件加密后保存，默认密钥文件为 `storage/ai-settings.key`。
- API Key 响应只暴露 `deepSeekApiKeyConfigured` 和 `maskedDeepSeekApiKey`。
- `.env.example` 已新增 `GONGWEN_AI_PROVIDER`、`GONGWEN_AI_SETTINGS_KEY_FILE`、`GONGWEN_DEEPSEEK_ENABLED`、`GONGWEN_DEEPSEEK_BASE_URL`、`GONGWEN_DEEPSEEK_MODEL`、`GONGWEN_DEEPSEEK_TIMEOUT_SECONDS`、`DEEPSEEK_API_KEY`。

相关文件：

- `docs/AI_CONFIGURATION.md`
- `.env.example`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/gongwen/assistant/ai/AiRuntimeProperties.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiConfigurationState.java`
- `backend/src/main/java/com/gongwen/assistant/ai/RoutingModelAdapter.java`
- `backend/src/main/java/com/gongwen/assistant/ai/DeepSeekModelAdapter.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiSettingsController.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiSettingsService.java`
- `backend/src/test/java/com/gongwen/assistant/ai/AiSettingsServiceTest.java`
- `frontend/src/App.tsx`
- `frontend/src/api.ts`
- `frontend/src/draftTypes.ts`
- `frontend/src/App.test.tsx`
- `frontend/src/styles/app.css`

已知注意点：

- 系统设置已写入 `ai_provider_settings` 持久表，后端重启后优先读取数据库；没有数据库记录时才使用环境变量。
- 生产化前需要补权限控制、密钥清除、审计和更完整的密钥轮换策略。
- Codex Browser 插件在本机曾不可用，原因是底层 `node_repl` 内核报 `failed to write kernel assets`；浏览器验证可使用终端 Playwright 或人工刷新。
- 如果系统设置页显示 `Failed to fetch`，优先检查后端 8080 是否运行，以及 `VITE_API_BASE_URL` 是否指向当前后端。

验证状态：

- focused 后端测试 `AiSettingsServiceTest` 通过。
- 后端完整测试通过。
- 前端 `npm test -- --run` 通过。
- 前端 `npm run build` 通过。
- 本地接口烟测 `GET /api/ai/settings`、`PUT /api/ai/settings`、`POST /api/ai/settings/test` 通过。
- 本地 Vite `http://127.0.0.1:5175` 可打开系统设置页。

### P7 局部 AI 操作

状态：已完成基础实现。

目标：支持选中段落后的 AI 辅助改写。

范围：

- 正式化。
- 压缩。
- 扩写。
- 改写。
- 补充要求。
- 结果先作为建议出现，确认后替换原文。

验收标准：

- 局部操作不直接覆盖原文。
- 用户确认后才替换。
- 保留操作 trace 和失败状态。

已实现 API：

- `POST /api/drafts/{draftId}/ai/local-operation`

已实现后端能力：

- `AiLocalOperationService`
- `AiLocalOperationRequest` / `AiLocalOperationResponse`
- `LocalOperationPrompt`
- `AiLocalOperationModelResponse`
- `AiLocalOperationType`
- `ModelAdapter.generateLocalOperation`
- `MockModelAdapter.generateLocalOperation`
- `DeepSeekModelAdapter.generateLocalOperation`

已实现前端能力：

- Word 风格预览中按 `BODY_PARAGRAPH` 草稿块选择单个段落。
- 左栏正文区域已从大 textarea 改为正文段落目录，点击目录项会定位并选中中间对应段落。
- 选中正文段落后可直接在 Word 风格预览中编辑，并继续复用草稿保存链路。
- 右栏局部操作面板，支持正式化、压缩、扩写、改写和补充。
- 建议生成中、错误、建议展示、采纳和放弃状态。
- 采纳后复用 `PUT /api/drafts/{id}/blocks` 保存替换后的草稿块。

验证状态：

- focused 后端测试 `AiLocalOperationServiceTest` 通过。
- focused 后端 controller 测试 `AiOutlineControllerTest` 通过。
- 前端 `npm test -- --run` 通过。
- 前端 `npm run build` 通过。

实施建议：

- 先修正前端段落目标模型，按 `DraftBlock` 选择单个 `BODY_PARAGRAPH`，不要基于合并后的正文 textarea 做局部操作。
- 新增建议型 API，返回 `traceId`、目标段落标识、操作类型和建议文本，不直接保存草稿。
- 用户点采用后复用 `PUT /api/drafts/{id}/blocks` 保存替换后的草稿块。
- 后端 prompt 使用 `local-operation-v1`，trace 只记录段落标识、操作类型、字符数和摘要，不保存完整原文或完整建议。

建议多 Agent 拆分：

- Agent A 后端：新增局部 AI 建议 request/response、service、controller、prompt builder 和 trace；不改前端。
- Agent B 前端：在 Word 风格预览中支持选择单个 `BODY_PARAGRAPH`，右栏出现局部操作面板；不改后端业务逻辑。
- Agent C QA/文档：补测试场景和文档，包括未选段落、生成失败、建议未采纳不覆盖原文、采纳后保存。
- 集成 Agent：统一对齐 API 字段、处理冲突、跑后端 focused/full tests、前端 tests/build 和浏览器验证。

### P8A 模板引擎底座

状态：已完成 T1 后端基础，并已被 P8B 质检和 P10 模板后台首版复用。

目标：按 Word 样式体系优先建立模板版本、`TemplateProfile`、解析风险和上传解析 API，为后续模板管理、质检和导出升级提供稳定底座。

已实现范围：

- `document_template_version`、`template_profile`、`template_block_mapping`、`template_rule`、`template_validation_result` 数据表。
- 本地模板文件存储和模板版本持久化。
- `TemplateProfile` 解析，占位符、样式、section、表格、页眉页脚、媒体和校验项可结构化保存。
- `.docx` 模板上传服务，包含类型、大小、空文件校验，解析失败会标记版本失败。
- profile JSONB 持久化。

覆盖等级约定：

- 模板能力必须按 L0-L5 标记覆盖程度：L0 解析保存，L1 后台展示，L2 风险识别，L3 映射或规则配置，L4 质检，L5 导出复现。
- 占位符和关键样式映射最终要达到 L5；页眉页脚、编号、复杂表格、图片或印章锚点首版至少达到 L1-L2，不能静默忽略。
- 当前 P8A 已完成基础 L0-L2 的一部分，但字体、字号、对齐、行距、缩进、段前段后等样式细节解析仍需 P10B 继续补齐。

已实现 API：

- `GET /api/templates`
- `POST /api/templates`
- `GET /api/templates/versions`
- `POST /api/templates/{templateId}/versions`
- `GET /api/templates/versions/{versionId}/profile`

后续依赖：

- P8 基础质检可复用 `TemplateProfile` 做占位符填充和结构检查。
- P10 模板管理员后台已复用上传和 profile 查询能力展示解析结果；后续继续扩展字段映射、启停和版本详情。
- P11 导出体验增强需要绑定具体模板版本，保证历史导出可追溯。

### P8 基础质检

状态：首版已完成。

目标：提供字段、结构、表达和导出前基础检查。

已完成范围：

- `quality_check_result` 数据表，结果以 JSONB 保存。
- `POST /api/drafts/{draftId}/quality-check`。
- `GET /api/drafts/{draftId}/quality-check/latest`。
- 必填字段检查：标题、主送、落款、日期。
- 基础结构检查：正文段落不能为空。
- READY 材料缺失提示。
- DeepSeek/Mock AI 质检建议，覆盖表达、结构衔接、事实风险和材料依据。
- `QUALITY_CHECK` AI trace，记录摘要、建议数量、错误摘要和耗时，不保存完整正文。
- 工作台右栏“基础质检”面板，支持检查中、成功、警告、错误、重试和 AI 服务失败提示。

暂未完成范围：

- 工作台已可绑定具体模板版本，质检会读取 `TemplateProfile` 检查占位符填充值、未映射占位符和跨 run 风险。
- 标题、主送、正文、附件、落款、日期完整性检查。
- 导出前自动阻断尚未接入 `POST /api/exports/word`，后续 P11 处理。
- AI 建议首版只展示，不自动改正文。

验收标准：

- 缺失必填字段能被发现并让 `exportBlocked=true`。
- AI 质检失败时规则质检仍返回结果，并展示 AI 服务警告。
- 质检结果以结构化 JSONB 保存。
- 前端右侧能展示成功、警告、错误和重试状态。

### P9 登录与基础权限

状态：第三档首版已完成，后续权限收口待继续。

目标：实现 MVP 起草人和模板管理员权限。

范围：

- 已完成 `department`、`app_user`、`app_role`、`app_user_role` 数据表和 Flyway v10。
- 已完成 Spring Security 会话登录、CSRF、`/api/auth/login`、`/api/auth/me`、`/api/auth/logout`。
- 已完成系统管理员 bootstrap，开发环境未配置 `GONGWEN_BOOTSTRAP_ADMIN_PASSWORD` 时生成一次性密码；生产部署必须显式配置初始密码。
- 已完成部门树管理 API/UI：查询、新增、编辑、停用；部门编号由后端按层级自动生成，前端不暴露编号输入和编号列，部门列表使用统一管理列表组件。
- 已完成账号管理 API/UI：查询、新增、编辑、重置密码、停用；账号新增/编辑使用全局弹窗，账号列表与部门列表复用统一管理列表组件。
- 已完成文种 CRUD API/UI：查询、新增、编辑、停用。
- 已完成草稿、模板、模板版本和文种的当前用户归属过滤首版：系统管理员可看全部，普通账号仅看系统内置或自己创建的数据。
- 已完成前端登录页、会话恢复、登出和按角色隐藏系统管理员入口。
- 待继续补材料下载、导出文件下载、导出记录列表、模板管理员细粒度权限、审计日志和权限不足 UI。

验收标准：

- 已验证未登录访问 `/api/drafts` 返回 401。
- 已验证登录、当前用户和登出 API contract。
- 已验证部门树构建、创建、删除阻断和账号创建/弱密码拒绝。
- 已验证草稿和文种服务会携带当前用户归属。
- 已验证前端文种管理、部门管理和账号管理新增流程。
- 待继续验证起草人材料和导出文件下载不能越权。
- 待继续验证模板管理能力仅系统管理员或模板管理员可用。

### P10 模板管理员后台

状态：首版已完成，后续增强待继续。

目标：让模板管理员通过 UI 上传和配置 Word 模板。

已完成范围：

- 模板管理入口从预留页升级为真实页面。
- 首屏按文种展示“文件夹式”卡片，文种包括通知、请示、报告。
- 进入文种后展示该文种下的模板卡片，右上角显示模板数和版本数。
- 新增模板进入独立页面，标题显示“新增模板 - 文种名”。
- 已有模板可上传新版本，标题显示“上传新版本 - 文种名”。
- 新增/上传版本时先选择 Word 文件，再点击“确认创建并解析”，避免选择文件即自动上传。
- 模板卡片可查看最新版本 `TemplateProfile` 的占位符和解析风险。
- 解析结果使用全局 `Dialog` 弹窗展示；模板列表页只保留文种、卡片和操作入口，避免长结果区撑开页面。
- 无显式占位符的 Word 文件会展示“智能识别”结果，说明当前不是解析失败，并建议 `{{标题}}`、`{{正文}}`、`{{日期}}` 等可用于自动套版的占位符。
- 后端已提供模板列表和创建 API，配合版本上传与 profile 查询形成首版闭环。
- UI 必须复用全局 `Button`、表单、状态和 token；模板页不得再用局部 class 覆盖按钮颜色、字号、图标尺寸和对齐。

已实现 API：

- `GET /api/templates?documentTypeCode=NOTICE`
- `POST /api/templates`
- `GET /api/templates/versions?documentTypeCode=NOTICE`
- `POST /api/templates/{templateId}/versions`
- `GET /api/templates/versions/{versionId}/profile`

后续范围：

- 字段名称、类型、必填、默认值、排序配置。
- 占位符到草稿块/字段的映射配置。
- Word 样式到草稿块的映射配置，例如标题、主送、正文段落、正文一级标题、附件说明、落款和日期。
- 关键版式规则配置和展示，例如标题居中、正文行距、正文首行缩进、段前段后、落款右对齐、字体和字号。
- 模板能力矩阵展示，标明占位符、样式、段落、字符、分节、页眉页脚、编号、表格、媒体和未支持结构分别达到 L0-L5 哪个等级。
- 复杂结构风险展示，例如多级编号、复杂表格、图片锚点、页眉页脚和 unsupported 结构；首版只读提示，不做完整编辑。
- 模板启用、停用。
- 模板版本详情页和历史版本列表。
- 模板管理员权限接入。
- 模板删除或批量操作如需实现，必须先补确认、审计和权限。

验收标准：

- 管理员能按文种进入模板列表。
- 管理员能新增 `.docx` 模板并明确确认上传解析。
- 管理员能看到解析出的占位符和风险。
- 起草工作台能选择已上传的模板版本。
- 后续增强完成后，管理员能配置字段和启停模板。
- 无占位符 Word 文件能被明确区分为样式模板、范文/示例公文、普通 Word 或待人工确认，不误提示为解析失败。
- 管理员能看到模板能力矩阵，知道每个 Word 维度是已解析、可展示、可质检、可配置、可导出复现，还是仅风险提示。
- 管理员能把标题、正文、落款、日期等公文语义块映射到 Word 样式，并能查看关键版式规则。

验证状态：

- focused 后端测试 `TemplateUploadControllerTest` 通过。
- 前端 `npm run build` 通过。
- `git diff --check` 通过。
- 当前模板管理 UI 已经多轮按项目全局按钮和布局规范修正；后续修改必须先复用全局样式。

### P10B 模板结构维度配置闭环

状态：结构维度首个闭环已完成，后续增强待继续。

目标：模板解析后形成“结构 + 原文 + 可编辑维度”，并让工作台类 Word 纸张按所选模板维度渲染。能力矩阵只作为内部覆盖口径，不再作为管理员主展示。

已完成范围：

- `TemplateProfileParser` 输出 `structures`，每条结构包含结构类型、结构名称、Word 原文片段、位置、来源和默认格式维度。
- 模板解析结果弹窗主展示改为“结构与维度”，按结构展示原文片段，并提供字体、字号、对齐、首行缩进、行距、段后和加粗编辑控件；点击“应用”后通过 API 保存到 `template_rule` 的 `STRUCTURE_FORMATTING_OVERRIDE` 规则。
- 工作台绑定模板版本后会拉取 `TemplateProfile`，并将标题、主送、正文、附件、落款、日期的生效维度应用到中间类 Word 预览。
- 后端已提供共享的 effective formatting 解析合同：从 `TemplateProfile.structures` 默认值与 `STRUCTURE_FORMATTING_OVERRIDE` 覆盖合并出标题、主送、正文、落款、日期等语义槽位的最终格式。
- 基础质检与 `.docx` 导出已复用同一套 effective formatting，不再分别各自推断标题居中、正文首行缩进、正文行距、落款右对齐和日期右对齐等规则。
- 工作台前端已通过 `WorkbenchNode` 派生层把正文小标题和正文内容分开，左栏目录、中间纸张选中态和右栏局部操作上下文统一使用节点语义。
- 前端类型 `TemplateProfile` 已补齐 `structures`、styles、sections、tables 和 media 字段。
- 已补参考范文到工作台/导出的第二档还原修复：解析层和前端派生层会把主送、附件、右对齐落款和右对齐日期从 `BODY` 中分离；effective formatting 优先使用带首行缩进的正文段作为正文样式样本，避免首个主送式段落导致正文导出无缩进。
- 已补参考范文导出的第三档入口：无占位符参考模板导出时保留原 `.docx` 的 section、页边距、页脚和 styles 外壳，按 `TemplateProfile` 重建发文机关、文号、标题、主送、正文、附件、落款、日期等结构槽位；导出前清洗旧正文块中混入的主送、附件、落款和日期，减少重复输出。

后续范围：

- 从 Word style 定义本身补充更多继承属性，而不仅依赖代表段落和 run。
- 新增后端 `draft_node` 持久化或兼容节点 API，避免 `WorkbenchNode` 长期只作为前端派生层。
- 补 unsupported OOXML 结构列表，例如域、脚注尾注、复杂 DrawingML、宏等，并在质检或模板风险里显式提示。

验证状态：

- focused 后端测试 `TemplateProfileParserTest` 通过。
- focused 后端测试 `TemplateUploadServiceTest` 通过。
- 前端 `App.test.tsx` 通过。
- 前端 `npm run build` 通过。
- 浏览器自动化未完成：当前可用工具没有 Browser 导航工具，bundled Playwright 缺 `playwright-core`，已用组件测试和构建覆盖首轮 UI 验证。

### P11 导出体验增强

状态：进行中。已完成导出前阻断切片：工作台可对当前草稿调用 `POST /api/exports/drafts/{draftId}/word`，按草稿绑定的模板版本生成并下载 `.docx`；点击导出会先保存当前草稿并运行基础质检，后端导出服务会读取最近一次质检结果，未质检返回 `QUALITY_CHECK_REQUIRED`，存在阻断项返回 `QUALITY_CHECK_BLOCKED`，通过后才复用 P10B 生效格式输出 Word 结果。

目标：完善导出前检查、导出记录和下载体验。

范围：

- 导出前质检拦截。
- 导出记录列表。
- 历史导出文件下载。
- 模板版本追溯。
- 导出失败原因展示。
- 更复杂正文块填充。

验收标准：

- 未通过基础检查时阻止导出。
- 导出失败有可理解错误。
- 历史导出记录可追溯模板版本。

### P12 部署与环境

状态：待开始。

目标：形成可交付的本地和私有化部署方式。

范围：

- 后端 Dockerfile。
- 前端 Dockerfile 或 Nginx 静态部署。
- 全栈 Docker Compose。
- `.env.example` 完整化。
- 本地启动文档。
- 生产部署说明。
- 日志目录和文件存储目录配置。

验收标准：

- 新机器可按文档启动完整系统。
- 环境变量不硬编码密钥。
- 文件、日志、数据库路径清晰可配置。

### P13 第二阶段增强

状态：待开始。

目标：在 MVP 闭环稳定后增强生产可用性。

候选能力：

- 历史范文库。
- PDF 导出。
- 更细格式检查。
- 版本留痕。
- 轻量审核流。
- 政策制度知识库。
- 私有化模型适配。

进入条件：

- MVP 起草、材料、AI、质检、导出、权限闭环已完成。

### P14 第三阶段增强

状态：待开始。

目标：面向多单位、复杂组织和生态集成。

候选能力：

- 多单位 / 多租户。
- 部门级权限过滤。
- OA/WPS/Word 插件。
- 图片 OCR。
- 涉密、越权、敏感风险审查。
- 完整审计后台。
- SaaS 运营后台。

进入条件：

- 第二阶段核心增强稳定，并有真实部署反馈。

## 当前开发队列

1. P11 导出体验增强。
2. P10B 结构维度配置持久化。
3. P10 模板管理员后台后续增强：字段映射、启停、版本详情。
4. P9 权限继续收口：材料/导出文件下载鉴权、模板管理员细粒度权限、审计日志和权限不足 UI。
5. P12 部署与环境。

## 当前推荐多 Agent 分工

目标阶段：P11 导出体验增强 + P10 后续增强 + P9 权限收口。

建议先由集成 Agent 冻结接口契约，再并行：

- Agent A 后端 P11：读取或触发最新质检结果，`exportBlocked=true` 时阻断导出；补导出记录模板版本追溯和稳定错误 shape。
- Agent B 前端 P11：在工作台导出入口展示质检状态、阻断原因、重试质检、导出中、导出失败和成功下载状态。
- Agent C P10B：把结构维度编辑落库，并让质检和导出复用同一套生效维度；不要再回到能力矩阵主展示。
- Agent F P10：继续字段映射、模板启停和版本详情；必须复用 P10B 的结构维度来源。
- Agent D P9：基于已落地的认证/RBAC 契约继续补材料、导出文件、模板管理员能力和审计日志；不要绕过现有 `CurrentUserProvider`。
- Agent E QA/文档：补 P10/P11/P9 验收清单、focused tests、错误/空/权限状态和文档同步。

文件边界建议：

- P11 后端优先改 `backend/src/main/java/com/gongwen/assistant/exporting/**`、`backend/src/main/java/com/gongwen/assistant/quality/**`、相关 controller/test。
- P11 前端优先改 `frontend/src/App.tsx`、`frontend/src/api.ts`、`frontend/src/draftTypes.ts`、`frontend/src/styles/app.css`，但按钮/表单必须复用全局组件。
- P10B 后续优先改 `backend/src/main/java/com/gongwen/assistant/template/profile/**`、`backend/src/main/java/com/gongwen/assistant/template/**`、`frontend/src/App.tsx` 中 `TemplateManagementPage`、`frontend/src/api.ts`、`frontend/src/draftTypes.ts`；重点是结构维度配置持久化和导出复现。
- P9 权限优先复用已落地的账号/部门/角色模型，后续改动必须带鉴权测试。
- 文档 Agent 只改 `AGENTS.md`、`DESIGN.md`、`docs/**`、必要的 `.env.example`。

## AI 接力清单

新 AI Agent 接手时必须确认：

- 当前分支和 `git status --short --branch`。
- `AGENTS.md` 与本文件是否反映当前代码。
- 最近提交：`9744a31 feat: add template management library flow`。如果工作区还有未提交 UI 微调，必须先阅读 diff，不要覆盖。
- 当前阶段是否已有 `docs/superpowers/plans/*` 实施计划。
- 是否存在未提交用户改动，不能随意覆盖。
- 当前本机已有 JDK 21、本地 Gradle 8.10.2 和脚本，后端优先用 `scripts/backend-test-focused.ps1` 或 `scripts/backend-test.ps1`，不必默认启 Docker Gradle 冷环境。
- 如果改前端，至少运行 `npm run build`；风险较高或改测试相关时再运行 `npm test -- --run`。
- 如果改 UI，必须用浏览器检查桌面布局，必要时检查移动视口；常规 UI 必须先复用全局组件和全局样式。

## 验证命令备忘

前端：

```powershell
cd D:\gongwen\frontend
npm test -- --run
npm run build
```

后端 focused 测试（优先）：

```powershell
cd D:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.template.TemplateUploadControllerTest"
```

后端全量测试：

```powershell
cd D:\gongwen
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test.ps1
```

后端测试（本机脚本不可用时再用 Docker Gradle）：

```powershell
cd D:\gongwen
docker run --rm -v "D:\gongwen\backend:/workspace" -w /workspace -e GRADLE_USER_HOME=/tmp/gradle-home gradle:8.10.2-jdk21 gradle --project-cache-dir /tmp/gradle-project-cache test
```

数据库：

```powershell
cd D:\gongwen
docker compose up -d postgres
docker compose ps
docker exec gongwen-postgres pg_isready -U gongwen -d gongwen
```

后端运行验证（8080 被占用时映射到 18080）：

```powershell
cd D:\gongwen
docker rm -f gongwen-backend-dev 2>$null
docker run -d --name gongwen-backend-dev --network gongwen_default -p 18080:8080 -v "D:\gongwen\backend:/workspace" -w /workspace -e SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/gongwen" -e SPRING_DATASOURCE_USERNAME="gongwen" -e SPRING_DATASOURCE_PASSWORD="gongwen_dev_password" gradle:8.10.2-jdk21 gradle bootRun
Invoke-RestMethod http://127.0.0.1:18080/api/health
```
