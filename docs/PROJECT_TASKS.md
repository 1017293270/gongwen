# 公文助手项目长期任务总表

本文件是公文助手从当前工程地基走向完整产品落地的长期任务入口。它用于人类开发者和 AI Agent 协同排期、选取下一阶段任务、判断依赖关系和验收边界。

使用规则：

- 每次开始新任务前，先阅读 `AGENTS.md`、`DESIGN.md`、本文件和当前阶段的实施计划。
- 每次只推进一个阶段或一个阶段内的清晰子任务，不要把后续预留做成完整实现。
- 阶段完成后必须更新本文件的状态、验收结果和下一步建议。
- 涉及架构边界、数据模型、接口契约、权限、安全、部署方式变化时，同步更新 `AGENTS.md`。

## 当前状态

当前分支：`p1-template-docx-export`

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

当前推荐下一阶段：

- P2 草稿结构与文种模型。

## 全局落地原则

- 后端优先打通严肃公文生产闭环，再补管理 UI 和增强体验。
- MVP 保持单单位私有化 Web 应用，不提前实现完整 SaaS、多租户、审核流和插件生态。
- 草稿、模板、材料、AI trace、质检结果、导出记录必须结构化保存。
- 模型调用、Prompt、材料解析、模板填充、导出、质检必须保持服务边界清晰。
- 所有文件上传、下载、导出、删除、权限变更都必须考虑鉴权、校验和审计预留。
- 前端必须遵循 `DESIGN.md` token 和三栏工作台结构。
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

状态：待开始。

目标：建立通知、请示、报告的结构化草稿模型。

范围：

- `DocumentType` 文种表和初始化数据。
- `Draft` 草稿主表。
- `DraftBlock` 草稿块表。
- 草稿状态字段。
- 标题、主送、正文段落、附件、落款、日期等块类型。
- 草稿创建、读取、保存、更新 API。

验收标准：

- 能创建通知、请示、报告草稿。
- 能保存和读取草稿块。
- 草稿不是单一 HTML 或纯文本。
- 后续导出可以直接消费草稿结构。

### P3 工作台前端接入真实数据

状态：待开始。

目标：让三栏工作台从静态壳子变成可编辑、可保存的真实工作台。

范围：

- 左侧字段表单接草稿 API。
- 中间 Word 风格预览接 `DraftBlock`。
- 支持分块编辑和保存。
- 右侧显示导出前状态。
- 添加加载、错误、空、禁用状态。
- 桌面优先，移动端至少可用。

验收标准：

- 页面刷新后草稿内容不丢失。
- 编辑标题、正文、落款、日期后能保存并重新读取。
- 文本不溢出、不遮挡。
- UI 仍符合 `DESIGN.md`。

### P4 材料上传与文本提取

状态：待开始。

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

- 只能上传允许类型和大小的文件。
- 材料归属于草稿。
- 能提取 Word/PDF 文本。
- 提取失败能返回明确错误。

### P5 AI 生成提纲

状态：待开始。

目标：接入模型适配层，生成结构化公文提纲。

范围：

- `ModelAdapter`。
- `PromptBuilder`。
- `AiGenerationTrace` 数据表。
- 生成提纲 API。
- 结构化输出解析和校验。
- 超时、失败、重试、取消状态。

验收标准：

- 能基于文种、字段、材料摘要生成提纲。
- 输出包含标题建议、正文结构、段落要点、缺失信息提示。
- AI trace 不记录完整敏感正文。
- 失败时返回归一化错误。

### P6 AI 逐段正文生成

状态：待开始。

目标：基于确认后的提纲逐段生成正文块。

范围：

- 段落级生成 API。
- 单段失败单段重试。
- 生成进度状态。
- 生成 trace 绑定到草稿块。
- 前端逐段生成状态。

验收标准：

- 能按提纲生成正文块。
- 单段失败不影响已生成段落。
- 用户可重试失败段落。
- 生成结果能保存为 `DraftBlock`。

### P7 局部 AI 操作

状态：待开始。

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

### P8 基础质检

状态：待开始。

目标：提供字段、结构、表达和导出前基础检查。

范围：

- `QualityCheckResult` 数据表。
- 必填字段检查。
- 文种结构检查。
- 模板占位符填充检查。
- 标题、主送、正文、附件、落款、日期完整性检查。
- 表达风格建议。
- 导出前检查。

验收标准：

- 缺失必填字段能被发现。
- 缺失模板占位符填充值能阻止导出。
- 质检结果以结构化 JSONB 保存。
- 前端右侧能展示成功、警告、错误状态。

### P9 登录与基础权限

状态：待开始。

目标：实现 MVP 起草人和模板管理员权限。

范围：

- `User`、`Role`、`Permission` 数据表。
- 登录认证。
- 起草人权限。
- 模板管理员权限。
- 草稿、材料、模板、导出文件访问控制。

验收标准：

- 起草人只能访问自己的草稿、材料和导出记录。
- 模板管理能力仅模板管理员可用。
- 下载模板、材料、导出文件时校验权限。

### P10 模板管理员后台

状态：待开始。

目标：让模板管理员通过 UI 上传和配置 Word 模板。

范围：

- 模板上传页面。
- 占位符解析结果展示。
- 字段名称、类型、必填、默认值、排序配置。
- 绑定文种。
- 模板启用、停用。
- 模板版本管理。

验收标准：

- 管理员能上传 `.docx` 模板。
- 管理员能看到解析出的占位符。
- 管理员能配置字段并保存。
- 起草工作台能选择启用模板。

### P11 导出体验增强

状态：待开始。

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

1. P2 草稿结构与文种模型。
2. P3 工作台前端接入真实数据。
3. P4 材料上传与文本提取。
4. P5 AI 生成提纲。
5. P6 AI 逐段正文生成。
6. P8 基础质检。
7. P9 登录与基础权限。
8. P10 模板管理员后台。
9. P11 导出体验增强。

## AI 接力清单

新 AI Agent 接手时必须确认：

- 当前分支和 `git status --short --branch`。
- `AGENTS.md` 与本文件是否反映当前代码。
- 当前阶段是否已有 `docs/superpowers/plans/*` 实施计划。
- 是否存在未提交用户改动，不能随意覆盖。
- 如果改后端，优先用 Docker Gradle 镜像验证 Java 21 构建，除非本机已安装 Java 21 和 Gradle。
- 如果改前端，必须运行 `npm test` 和 `npm run build`。
- 如果改 UI，必须用浏览器检查桌面布局，必要时检查移动视口。

## 验证命令备忘

前端：

```powershell
cd E:\gongwen\frontend
npm test
npm run build
```

后端测试（本机无 Java 21/Gradle 时）：

```powershell
cd E:\gongwen
docker run --rm -v "E:\gongwen\backend:/workspace" -w /workspace -e GRADLE_USER_HOME=/tmp/gradle-home gradle:8.10.2-jdk21 gradle --project-cache-dir /tmp/gradle-project-cache test
```

数据库：

```powershell
cd E:\gongwen
docker compose up -d postgres
docker compose ps
docker exec gongwen-postgres pg_isready -U gongwen -d gongwen
```

后端运行验证（8080 被占用时映射到 18080）：

```powershell
cd E:\gongwen
docker rm -f gongwen-backend-dev 2>$null
docker run -d --name gongwen-backend-dev --network gongwen_default -p 18080:8080 -v "E:\gongwen\backend:/workspace" -w /workspace -e SPRING_DATASOURCE_URL="jdbc:postgresql://postgres:5432/gongwen" -e SPRING_DATASOURCE_USERNAME="gongwen" -e SPRING_DATASOURCE_PASSWORD="gongwen_dev_password" gradle:8.10.2-jdk21 gradle bootRun
Invoke-RestMethod http://127.0.0.1:18080/api/health
```
