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

当前状态：P5 AI 生成提纲已完成基础实现。仓库包含 Spring Boot 后端骨架、React 前端骨架、PostgreSQL Docker Compose、本项目 `DESIGN.md` token 落地、基础健康检查、`.docx` 模板占位符解析、Word 模板填充导出、模板/字段/导出记录表、文种/草稿/草稿块数据表、材料表、AI trace 表，以及工作台真实草稿加载、编辑、预览、保存、材料上传、材料列表和 AI 提纲生成能力。PostgreSQL 已通过 Docker Compose 启动并健康，Flyway 已应用到 v5。当前本机已安装 JDK 21，并已落地 Gradle Wrapper、本地 Gradle 8.10.2 工具目录和后端测试脚本，后续后端验证优先使用本机脚本，避免反复启动 Docker Gradle 冷环境。

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

AI 提纲生成当前约定：

- 默认使用 `MockModelAdapter`，无需云模型密钥即可本地测试和演示。
- `PromptBuilder` 集中构建结构化提纲输入，不在 controller 或前端散落 prompt。
- `ai_generation_trace` 记录任务类型、provider、model、状态、prompt 版本、输入摘要、输出摘要、错误摘要和耗时。
- trace 不保存完整草稿正文、完整材料提取文本或完整 prompt。
- 提纲 API 返回标题建议、正文结构、段落要点和缺失信息提示。
- 前端右栏已接入生成中、成功、失败和重试状态。

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

1. 推进 P6：AI 逐段正文生成，基于已生成提纲生成并保存 `DraftBlock`。
2. 在进入更大功能前补首页/侧边栏信息架构壳子，让工作台、模板管理、材料、导出记录和 AI 任务有清晰入口。
3. 后续开发默认先跑 focused tests，提交前再跑全量后端测试、前端测试和前端构建。
