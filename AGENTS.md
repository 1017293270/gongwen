# 公文助手项目 AGENTS.md

本文件是公文助手项目的 AI 协同入口。任何 AI Agent、Codex 会话或开发者进入本项目时，必须先阅读本文件，再阅读设计规格和当前任务文件。

本文件的目标是让 AI 以最快、最准的速度理解项目定位、架构、模块边界、开发规则、验收标准和最新状态。

重要约束：每次完成开发任务、架构调整、模块边界变化、接口变化、数据模型变化、部署方式变化或关键决策变化后，必须更新本文件。不要让 AGENTS.md 落后于代码。

## 1. 项目定位

公文助手是面向大型企业、集团、政府及事业单位的 AI 公文起草与套版工作台。

它不是泛写作工具，也不是完整 OA 系统。MVP 要先完成严肃公文生产闭环：

选择文种 -> 填写字段 -> 上传 Word/PDF 材料 -> AI 生成提纲 -> 逐段生成正文 -> Word 版式预览分块编辑 -> 基础质检 -> 套用 Word 模板导出 `.docx`

项目优先服务：

- 企业内部行政公文
- 政企/机关风格公文
- 大型企业/集团
- 政府及事业单位相关团队

MVP 采用私有化单单位 Web 应用。长期预留 SaaS、多租户、审核流、知识库、OA/WPS/Word 插件和私有化模型。

## 2. 关键文档

进入项目后优先阅读：

1. `AGENTS.md`
2. `DESIGN.md`
3. `docs/superpowers/specs/2026-05-25-gongwen-assistant-design.md`
4. `docs/PROJECT_TASKS.md`
5. 当前任务相关的 issue、task 或计划文档
6. 代码中的 README、模块说明和测试

如果实现与文档不一致，先判断是代码落后还是文档落后。修复后必须同步更新文档。

## 3. 技术栈决策

当前目标技术栈：

- 前端：React + TypeScript
- 后端：Spring Boot
- 数据库：PostgreSQL
- 文件存储：本地存储抽象，后续可切 MinIO
- AI 接入：模型适配层，MVP 使用云端大模型 API
- 文档处理：后端负责 `.docx` 模板解析、占位符填充、导出
- 部署形态：单单位私有化 Web 应用

PostgreSQL 是默认数据库。优先使用关系表保存核心业务数据，使用 JSONB 保存适合半结构化的数据，例如草稿块快照、质检结果和 AI trace 元数据。

## 4. MVP 范围

MVP 包含：

- 起草人工作台
- 模板管理员后台
- 通知、请示、报告三个文种
- Word/PDF 材料上传
- AI 提纲生成
- AI 逐段正文生成
- 局部段落 AI 操作
- Word 风格预览
- 分块编辑
- 基础质检
- `.docx` 模板上传和占位符解析
- `.docx` 导出
- 登录和基础权限

MVP 不包含：

- 完整审核流界面
- 多租户 SaaS 运营后台
- 完整在线 Word 编辑器
- PDF 导出
- 图片/扫描件 OCR
- 复杂格式细检
- 涉密/越权完整风控
- 部门级知识库权限过滤
- OA/WPS/Word 插件

实现时不要擅自扩大 MVP 范围。必要的架构预留可以做，但不要让预留变成完整实现。

## 5. 角色与权限

MVP 只暴露两个角色：

- 起草人
- 模板管理员

权限规则：

- 起草人只能创建、编辑、查看自己的草稿、材料和导出记录。
- 起草人可以调用生成、质检和导出。
- 模板管理员可以上传、配置、启用、停用模板。
- 模板管理员可以配置文种字段和结构。
- 模板、材料、导出文件下载必须校验权限。

数据模型应预留 RBAC、tenant_id、department_id、审核状态和版本字段。

## 6. 架构边界

必须保持这些模块边界：

- UI 工作台：只负责交互、状态展示、编辑体验和 API 调用。
- AI interaction service：编排生成、局部操作、质检调用。
- Prompt builder：集中构建提示词，按任务和文种版本化。
- Material service：上传、解析、提取文本、管理材料来源。
- Template service：上传模板、解析占位符、字段配置、版本管理。
- Draft service：管理草稿、草稿块、状态和编辑保存。
- Quality check service：字段、结构、表达、导出前检查。
- Export service：将结构化草稿填入 `.docx` 模板并生成文件。
- Model adapter：集中管理模型供应商、模型名、参数、超时、重试、错误归一化和 trace。

不要把大 prompt 写进前端组件。不要把模型调用散落在 controller 或 UI 中。

## 7. AI 功能要求

AI 生成采用“两段式 + 局部操作”：

1. 生成提纲。
2. 用户确认或调整提纲。
3. 逐段生成正文。
4. 支持选中段落后正式化、压缩、扩写、改写、补充要求。
5. 质检模块生成建议和导出前检查结果。

所有 AI 调用必须考虑：

- 处理中状态
- 取消
- 失败重试
- 超时
- 错误归一化
- trace 记录
- 不在日志保存完整敏感正文
- 高风险操作预留确认机制

Prompt 必须集中管理。结构化输出必须解析和校验。

## 8. UI 设计标准

UI 必须遵循根目录 `DESIGN.md`。`DESIGN.md` 是本项目具体设计系统，包含 Anthropic-inspired token、颜色、字体、间距、组件状态、布局和响应式规则。前端实现不得绕过它另起一套视觉语言。

UI 使用 Anthropic-inspired 风格：

- 克制、清晰、低噪声
- 温和中性色、米白或暖灰背景
- 深色正文、低饱和强调色
- 不使用花哨科技感、强渐变、大面积高饱和色
- 公文工作台要显得可信、专业、安静

工作台布局：

- 左侧：文种、模板、字段表单、材料上传
- 中间：Word 风格预览和分块编辑
- 右侧：AI 建议、基础质检、导出前检查

必须覆盖：

- 默认、悬停、聚焦、禁用、加载、成功、错误、空状态
- 生成中、取消中、重试中
- 权限不足
- 文件上传失败
- AI 服务不可用
- 导出失败

移动端至少可用，桌面端优先打磨。

前端代码要求：

- 所有颜色必须来自 `DESIGN.md` 定义的 CSS 变量。
- 所有组件状态必须覆盖 default、hover、focus-visible、active、disabled、loading、error。
- 工作台必须保持三栏主结构：左侧字段/材料/模板，中间 Word 风格预览，右侧 AI 建议/质检/导出。
- 不允许使用大面积蓝紫渐变、AI 光晕、装饰 blob、玻璃拟态或营销页式 hero。
- 如果 UI 需求和 `DESIGN.md` 冲突，先更新 `DESIGN.md` 并说明原因。

## 9. 数据模型基线

核心实体：

- User
- Role
- Permission
- DocumentType
- Template
- TemplateField
- Draft
- DraftBlock
- Material
- AiGenerationTrace
- QualityCheckResult
- ExportRecord

草稿必须结构化保存，不只保存一整段 HTML 或纯文本。正文块、标题、主送、附件、落款、日期等应有明确结构。

导出记录必须保存模板版本，确保以后能追溯某个文件由哪个模板版本生成。

## 10. 安全要求

默认安全规则：

- 不硬编码密钥。
- 新增环境变量必须更新示例配置。
- 输入必须校验和规范化。
- 文件上传必须校验类型、大小和访问权限。
- SQL 必须参数化。
- HTML/Markdown 渲染必须消毒。
- AI trace 不记录完整敏感正文。
- 下载模板、材料和导出文件时必须鉴权。
- 删除、批量修改、权限变更、生产发布等高风险操作必须确认并审计。

如果发现密钥泄露，停止开发并明确报告，先处理密钥轮换。

## 11. 测试要求

测试深度按风险决定。

MVP 必测：

- 模板占位符解析。
- 模板字段配置。
- 草稿块保存和读取。
- Word/PDF 材料上传和解析失败路径。
- AI 生成成功、失败、取消、重试。
- 质检成功和缺失字段路径。
- `.docx` 导出成功和缺失占位符路径。
- 起草人访问自己草稿和禁止访问他人草稿。
- 模板管理员权限。

UI 改动必须验证：

- 桌面布局。
- 移动端至少可用。
- 加载、空、错误、禁用状态。
- 生成中和失败重试。
- 文本不溢出、不遮挡。

完成开发前必须运行相关测试或明确说明无法运行的原因。

## 12. 开发流程

每个任务建议流程：

1. 阅读 `AGENTS.md` 和相关规格。
2. 明确任务属于前端、后端、AI、文档、权限、测试还是部署。
3. 查看现有代码模式，不要凭空发明风格。
4. 先设计边界，再改代码。
5. 实现时覆盖错误、空、权限和加载状态。
6. 添加或更新测试。
7. 运行验证命令。
8. 更新 `AGENTS.md` 和相关 docs。
9. 总结改动、测试和剩余风险。

任何跨模块功能都要同时考虑 UI、API、服务、数据模型、权限、日志、测试和部署影响。

## 12.1 多 Agent 协同开发规范

项目后续鼓励使用多 Agent 协同，但必须先拆边界再并行，避免多个 Agent 同时改同一组文件造成互相覆盖。

适合并行的任务：

- 前端页面实现与后端服务实现可以并行，但必须先约定 API contract 和类型字段。
- 后端核心服务与测试补充可以并行，但测试 Agent 只能基于约定行为写测试，不要重构实现。
- UI 视觉/状态打磨与文档更新可以并行，但 UI Agent 不应修改后端逻辑。
- 文档整理、验收清单、测试执行可以与开发 Agent 并行。
- P7 局部 AI 操作中，后端建议 API、前端 DraftBlock 选择 UI、测试/文档可以拆给不同 Agent。
- P8 基础质检中，规则引擎、右栏质检 UI、测试 fixture、文档可以拆给不同 Agent。

不适合并行的任务：

- 同时修改同一个 React 大组件，例如 `frontend/src/App.tsx` 的同一区域。
- 同时修改 `ModelAdapter`、`PromptBuilder`、`AiOutlineService`、`AiParagraphService` 等 AI 调用核心链路。
- 未先约定接口就并行开发前后端。
- 未先冻结 CSS 命名就并行改同一页面布局。
- 一个 Agent 做大范围重构，另一个 Agent 基于旧结构继续实现功能。

并行前必须产出一个简短分工：

- 目标阶段，例如 P7 或 P8。
- 本轮要交付的用户可见能力。
- API contract 或前端类型草案。
- 每个 Agent 的文件边界。
- 每个 Agent 的验收命令。
- 最终由一个集成 Agent 统一跑全量验证。

推荐角色拆分：

- 架构/集成 Agent：读 `AGENTS.md`、`DESIGN.md`、`docs/PROJECT_TASKS.md`，拆任务、定接口、最后合并检查。
- 后端 Agent：实现 controller、service、repository、model adapter、trace 和后端测试。
- 前端 Agent：实现页面、状态机、API client、交互反馈和前端测试。
- QA Agent：补错误路径、权限路径、空状态、失败重试和验收脚本，不做无关重构。
- 文档 Agent：同步 `AGENTS.md`、`docs/PROJECT_TASKS.md` 和专题说明文档。

文件边界建议：

- 后端 Agent 优先只改 `backend/src/main/java/**` 和 `backend/src/test/java/**`。
- 前端 Agent 优先只改 `frontend/src/**`。
- 文档 Agent 优先只改 `AGENTS.md`、`docs/**`、`.env.example`。
- 需要跨边界时，先在主会话说明原因，并由集成 Agent 统一处理冲突。

合并与验收规则：

- 每个 Agent 完成后必须说明改了哪些文件、实现了哪些状态、跑了哪些测试、哪些没有跑。
- 集成 Agent 必须查看 `git status --short` 和关键 diff。
- 集成 Agent 必须确认没有误改用户已声明不要动的区域，例如当前用户已说明“不需要管侧边栏样式”。
- 集成 Agent 必须跑至少相关 focused tests；提交前跑全量后端测试、前端测试和前端构建。
- 如果本地浏览器插件不可用，记录原因，并使用终端 Playwright、curl/Invoke-RestMethod 或手动浏览器替代验证。

多 Agent 输出格式建议：

```text
Agent: 后端
范围: P7 局部 AI 建议 API
改动: ...
接口: ...
测试: ...
风险: ...
需要集成处理: ...
```

当前下一轮并行建议：

- Agent A 后端：实现 P7 局部段落建议 API，新增 prompt、request/response、trace，不直接覆盖原文。
- Agent B 前端：把正文预览改为可选择单个 `BODY_PARAGRAPH` 块，右栏显示局部操作面板和建议采纳状态。
- Agent C QA/文档：补 P7 成功、失败、未选择段落、采纳前不覆盖原文的测试和文档。
- 集成 Agent：对齐 API 字段、跑全量验证、检查 UI 是否仍符合 `DESIGN.md` 和三栏工作台结构。

## 13. 文档更新规则

每次开发完成后必须检查是否需要更新本文件。

必须更新 AGENTS.md 的情况：

- 技术栈变化。
- 模块边界变化。
- `DESIGN.md` 设计系统变化。
- 新增核心实体或修改数据模型。
- 新增 API 契约或重要接口行为变化。
- AI 调用链路、prompt、模型适配策略变化。
- 权限模型变化。
- 部署方式、环境变量、文件存储方式变化。
- MVP 范围变化。
- 新增重要测试或验收方式。
- 发现并固化新的项目约定。

更新 AGENTS.md 时保持高信号，不记录流水账。它应该帮助下一位 AI 快速理解项目，而不是成为冗长日志。

## 14. 当前状态

当前状态：P6 AI 逐段正文生成已完成基础实现，并已补充应用总览页、左侧侧边栏信息架构壳子和系统设置 AI 配置页。仓库包含 Spring Boot 后端骨架、React 前端骨架、PostgreSQL Docker Compose、本项目 `DESIGN.md` token 落地、基础健康检查、`.docx` 模板占位符解析、Word 模板填充导出、模板/字段/导出记录表、文种/草稿/草稿块数据表、材料表、AI trace 表，以及总览入口、工作台真实草稿加载、编辑、预览、保存、材料上传、材料列表、AI 提纲生成、基于提纲的单段正文生成能力、运行时 Mock / DeepSeek 切换和 DeepSeek 连接测试。PostgreSQL 已通过 Docker Compose 启动并健康，Flyway 已应用到 v5。当前本机已安装 JDK 21，并已落地 Gradle Wrapper、本地 Gradle 8.10.2 工具目录和后端测试脚本，后续后端验证优先使用本机脚本，避免反复启动 Docker Gradle 冷环境。

当前核心 API：

- `GET /api/health`
- `POST /api/templates/parse`
- `POST /api/exports/word`
- `GET /api/document-types`
- `POST /api/drafts`
- `GET /api/drafts/{id}`
- `PUT /api/drafts/{id}/blocks`
- `GET /api/drafts/{draftId}/materials`
- `POST /api/drafts/{draftId}/materials`
- `POST /api/drafts/{draftId}/ai/outline`
- `POST /api/drafts/{draftId}/ai/paragraph`
- `GET /api/ai/settings`
- `PUT /api/ai/settings`
- `POST /api/ai/settings/test`

AI 模型配置当前约定：

- 当前已完成“系统设置 -> AI 配置 -> DeepSeek 适配”的运行时接入。系统设置不再只是预留页，已包含真实可用的 AI 供应商配置表单。
- 默认仍使用 `MockModelAdapter`，无需云模型密钥即可本地测试和演示；Mock 是保底能力，不代表正式模型已接通。
- 系统设置页可切换 `mock` / `deepseek`，可配置 DeepSeek Base URL、模型、超时和 API Key。
- “测试连接”会先保存当前表单，再调用后端测试接口；避免出现前端已选择 DeepSeek、后端仍按旧 Mock 配置测试的状态错位。
- DeepSeek 配置为后端内存态运行时配置，启动时由环境变量初始化；重启后回到环境变量配置，不写入数据库。
- API Key 只通过请求写入后端运行时状态，响应中只返回是否已配置和脱敏值，不记录到 repo、数据库或 trace。
- `RoutingModelAdapter` 是当前 `ModelAdapter` 的主入口，只在 provider 为 `deepseek`、已启用且 API Key 已配置时路由到 `DeepSeekModelAdapter`，否则继续使用 Mock。
- `DeepSeekModelAdapter` 使用 OpenAI 兼容的 `/chat/completions`，请求 `response_format: {"type":"json_object"}`，要求模型只返回 JSON，再解析成提纲或段落结构。
- DeepSeek 调用仍复用 `PromptBuilder`、`AiOutlineService`、`AiParagraphService` 和 `ai_generation_trace`，没有把 prompt 或模型调用散落到 controller 或前端。
- DeepSeek 错误会归一化为模型不可用、HTTP 错误、返回为空、结构无效、请求配置无效或调用中断，前端以统一错误/Toast 展示。
- 当前支持模型选项：`deepseek-v4-flash`、`deepseek-v4-pro`、`deepseek-chat`、`deepseek-reasoner`。默认值来自 `.env.example` 的 `GONGWEN_DEEPSEEK_MODEL`。
- 新增环境变量：`GONGWEN_AI_PROVIDER`、`GONGWEN_DEEPSEEK_ENABLED`、`GONGWEN_DEEPSEEK_BASE_URL`、`GONGWEN_DEEPSEEK_MODEL`、`GONGWEN_DEEPSEEK_TIMEOUT_SECONDS`、`DEEPSEEK_API_KEY`。
- 本轮本地运行曾验证：后端 `GET /api/ai/settings`、`PUT /api/ai/settings`、`POST /api/ai/settings/test` 均可用；前端系统设置页可展示、保存和触发测试。
- 更完整说明见 `docs/AI_CONFIGURATION.md`。

AI 提纲生成当前约定：

- `PromptBuilder` 集中构建结构化提纲输入，不在 controller 或前端散落 prompt。
- `ai_generation_trace` 记录任务类型、provider、model、状态、prompt 版本、输入摘要、输出摘要、错误摘要和耗时。
- trace 不保存完整草稿正文、完整材料提取文本或完整 prompt。
- 提纲 API 返回标题建议、正文结构、段落要点和缺失信息提示。
- 前端右栏已接入生成中、成功、失败和重试状态。

AI 逐段正文生成当前约定：

- `POST /api/drafts/{draftId}/ai/paragraph` 基于提纲章节标题、要点、补充要求和 READY 材料摘要生成单个正文段落。
- 生成结果保存为 `BODY_PARAGRAPH` 草稿块；同一 `sortOrder` 重新生成会替换原段落，支持单段重试。
- `PromptBuilder` 集中构建 `paragraph-v1` 输入摘要，不在前端或 controller 散落 prompt。
- `ai_generation_trace` 使用 `PARAGRAPH` task type 记录 provider、model、状态、prompt 版本、输入摘要、输出摘要、错误摘要和耗时。
- trace 不保存完整正文、完整材料提取文本或完整 prompt。
- 前端提纲章节已接入“生成正文 / 正在生成 / 重试正文”状态，成功后刷新 Word 风格预览。

材料上传当前约定：

- 支持 `.docx` 和 `.pdf`。
- 默认最大 20MB。
- 本地存储目录由 `GONGWEN_MATERIAL_STORAGE_DIR` 配置，默认 `storage/materials`。
- `.docx` 使用 Apache POI 提取文本，`.pdf` 使用 Apache PDFBox 提取文本。
- 提取成功保存 `READY`，提取失败保存 `FAILED` 和错误摘要。
- 当前 API 列表返回材料状态和提取字数，不直接返回完整提取文本。

前端统一反馈约定：

- 已引入 `@radix-ui/react-toast`。
- 保存、上传、失败等轻反馈通过 `ToastProvider` / `useToast` 统一展示。
- Toast 样式必须继续使用 `DESIGN.md` CSS 变量，避免引入与当前 Anthropic-inspired 风格冲突的成套视觉主题。

前端应用壳约定：

- 默认进入总览页，左侧侧边栏提供总览、工作台、草稿列表、模板管理、材料库、导出记录、AI 任务和系统设置入口。
- 侧边栏采用 Anthropic/OpenAI-like 紧凑工作区导航：导航项单行展示，隐藏解释性副文案，当前页使用暖白 active pill 和细 accent 左侧标记；移动端改为两列 44px 触控项。
- 工作台仍保持项目规定的三栏主结构；侧边栏只负责全局导航，不替代工作台内部的字段、预览和 AI 建议布局。
- 草稿列表、模板管理、材料库、导出记录和 AI 任务当前是预留入口，后续阶段接入真实列表、权限和操作。
- 系统设置当前已有真实 AI 配置页面；后续再扩展权限、账号、部署和审计配置时，不要覆盖现有 AI 配置能力。
- 总览页展示当前草稿、正文段落、READY 材料和阶段提醒，必须区分加载、空、错误和可操作状态。

本地 Vite 联调时，如果后端容器因本机 8080 被占用而映射到 18080，需要用 `VITE_API_BASE_URL=http://127.0.0.1:18080` 启动前端。后端已允许本地 Vite 端口跨域访问 `/api/**`。

本地后端测试约定：

- JDK 21 路径：`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`。
- 本地 Gradle 工具目录：`D:\gongwen\.tools\gradle-8.10.2`，该目录不提交。
- 本地 Gradle 缓存目录：`D:\gongwen\.gradle-user-home`，该目录不提交。
- focused 后端测试：`powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.ai.PromptBuilderTest"`。
- 全量后端测试：`powershell -ExecutionPolicy Bypass -File .\scripts\backend-test.ps1`。
- 前端测试：在 `frontend/` 下运行 `npm test -- --run`，构建运行 `npm run build`。

长期任务总表已建立：`docs/PROJECT_TASKS.md`。后续 AI Agent 接手时必须用该文件判断当前阶段、依赖、验收标准和下一步任务。

已确认决策：

- MVP 采用公文起草工作台路线。
- UI 采用 Anthropic-inspired 风格。
- MVP 文种为通知、请示、报告。
- MVP 支持 Word/PDF 材料上传。
- MVP 导出 `.docx`。
- 数据库选择 PostgreSQL。
- 后端优先 Java Spring Boot。
- 前端目标 React + TypeScript。
- 模型接入先用云端 API，预留私有化模型适配。
- 私有化单单位部署优先。

当前原型：

- `D:\gongwen\.superpowers\brainstorm\42868-17797250696083\content\gongwen-workbench-preview.html`

下一步建议：

1. 推进 P7：局部段落 AI 操作，先做段落块级选择，再以建议形式返回，用户确认后复用现有草稿保存链路替换原文。
2. 推进 P8：基础质检，覆盖缺失字段、结构完整性和导出前检查结果。
3. P7 前端不要基于合并后的正文 textarea 做局部操作；优先按 `DraftBlock` 渲染和选择单个 `BODY_PARAGRAPH`。
4. P7 后端建议新增建议型 API，不直接覆盖原文；返回 traceId、目标块、操作类型和建议文本，采用后再调用现有块保存接口。
5. P8 质检可以先不接真实模型，优先落地字段缺失、结构完整、导出前检查和结果展示，再逐步接 AI 表达建议。
6. 后续开发默认先跑 focused tests，提交前再跑全量后端测试、前端测试和前端构建。
