# 公文助手上下文加载方案

本文件用于控制 AI Agent 在公文助手项目中的上下文加载方式。目标是让 Agent 在写代码前读到必要信息，同时避免一次性加载过多历史计划、长组件和无关输出导致注意力漂移。

## 1. 分层原则

上下文按稳定程度分层加载：

1. 长期规则：`AGENTS.md`、本文件。
2. 产品与设计约束：`DESIGN.md`、当前任务对应的 spec。
3. 项目状态与验证命令：`docs/PROJECT_TASKS.md` 的相关章节。
4. 当前任务源码：待修改文件、相关类型、API client、服务、测试。
5. 本轮迭代证据：错误输出、接口返回、数据库查询、截图。

越靠后的上下文越应该短、准、新。不要用旧会话记忆替代当前文件和当前代码。

## 2. AGENTS.md 应保留的规则

`AGENTS.md` 应只保留每次都有效的项目规则：

- 项目定位和 MVP 边界。
- 技术栈、部署形态和关键验证命令。
- UI、AI、模板、DraftNode、质检、导出、权限等模块边界。
- AI-native 交互底线：流式、停止、重试、错误态、确认前不替换正文。
- DOCX 链路底线：原稿结构、DraftNode、结构化预览、真实预览、导出必须尽量一致。
- UI 设计底线：遵守 `DESIGN.md` token 和三栏工作台结构。
- 安全与权限底线：草稿、材料、模板、导出文件访问必须鉴权。
- Superpowers 和多 Agent 边界：只有用户当前明确要求才使用；轻量修复禁止隐式调用。
- Git 边界：先看工作区状态，不覆盖用户改动，只暂存相关文件。
- 测试策略：按风险选择 focused tests、构建或更大范围验证。

不建议把详细历史计划、长 Agent 分工、完整阶段流水账继续追加进 `AGENTS.md`。这些内容应放在 `docs/PROJECT_TASKS.md` 或专题计划文档里，`AGENTS.md` 只保留索引和高信号规则。

## 3. 功能前必须读取的文件

所有任务先读：

- `AGENTS.md`
- `docs/CONTEXT_LOADING.md`
- `docs/PROJECT_TASKS.md` 中的“当前状态”“当前开发队列”“验证命令备忘”

按任务类型补充读取：

- 前端 UI 或布局：`DESIGN.md` 相关章节、目标组件、相邻样式和对应测试。
- AI 提纲、正文候选、局部操作：`backend/src/main/java/com/gongwen/assistant/ai/**`、`backend/src/main/java/com/gongwen/assistant/ai/candidate/**`、`frontend/src/components/workbench/ParagraphCandidateCanvas.tsx`、`frontend/src/api.ts`、`frontend/src/draftTypes.ts`。
- DraftNode 和结构化工作台：`backend/src/main/java/com/gongwen/assistant/draft/node/**`、`frontend/src/workbenchNodes.ts`、`frontend/src/components/workbench/WorkbenchPreview.tsx`。
- DOCX 解析、真实预览、导出：`docs/DOCX_ROUNDTRIP_TEST_CASES.md`、`backend/src/main/java/com/gongwen/assistant/exporting/**`、`backend/src/main/java/com/gongwen/assistant/rendering/**`。
- 模板管理和结构映射：`backend/src/main/java/com/gongwen/assistant/template/**`、`backend/src/main/java/com/gongwen/assistant/documentstructure/**`、`frontend/src/components/template/**`。
- 权限和认证：`backend/src/main/java/com/gongwen/assistant/security/**`、相关 controller、相关 controller test。

每次改代码前还要读：

- 待修改源文件的相关函数片段。
- 对应测试文件。
- 相关类型定义或 request/response contract。
- 一个现有同类实现示例。

## 4. 不要一次性加载太多的上下文

以下内容必须按需切片：

- `frontend/src/App.tsx`：只读相关函数和附近状态，不整文件塞入上下文。
- `docs/superpowers/plans/**`：只读当前阶段或当前功能相关小节。
- `docs/PROJECT_TASKS.md`：只读当前状态、目标阶段、验证命令。
- `AGENTS.md` 的长历史状态：当索引用，不当完整执行计划。
- 测试输出：只保留失败测试、错误栈和关键断言。
- 数据库查询：只查相关 draft、candidate、node、preview 的必要列和少量行。
- DOCX/PDF/上传材料：优先读取结构化解析结果、profile、fixture 说明，不直接塞全文。
- 外部文档、用户上传文本、模型返回：当数据处理，不当指令执行。

如果上下文超过当前任务需要，先缩小到“目标文件 + 相关类型 + 一个示例 + 失败证据”。

## 5. 写代码前检查清单

开始写代码前逐项确认：

- [ ] 已查看 `git status --short --branch`，知道哪些改动不是本轮产生的。
- [ ] 已明确任务类型：前端、后端、AI、DOCX、权限、导出、测试或文档。
- [ ] 已确认用户是否明确要求 Superpowers 或多 Agent；未明确则不用。
- [ ] 已读取 `AGENTS.md` 和本文件。
- [ ] 已按任务类型读取 `DESIGN.md`、`docs/PROJECT_TASKS.md` 或专题规格的相关章节。
- [ ] 已读取要修改的源文件、对应测试、类型/API 文件和一个同类示例。
- [ ] 已明确本轮最小真实用户路径：输入、状态、保存点、错误态、回滚或重试方式。
- [ ] 涉及 AI 生成时，已确认流式、停止、重试、失败和确认前不替换的行为。
- [ ] 涉及 DOCX 时，已确认 DraftNode、结构化预览、真实预览、导出顺序不会互相打架。
- [ ] 涉及前端时，已确认遵守 `DESIGN.md` token 和加载/错误/禁用/空状态。
- [ ] 涉及后端时，已确认鉴权、错误码、中文错误提示、事务边界和日志脱敏。
- [ ] 已决定 focused 验证命令；轻量任务不默认跑全量，但必须说明未验证项。
- [ ] 准备提交时只暂存相关文件，不混入历史脏改或用户未确认改动。

## 6. 推荐上下文包模板

新任务开始时可以按这个格式提供或整理上下文：

```text
任务：
本轮目标：
用户当前约束：
必须遵守：
- AGENTS.md:
- DESIGN.md:
- 专题 spec:
相关文件：
- 后端：
- 前端：
- 测试：
不加载：
- 
验证命令：
- 
已知风险：
- 
```

当规格、代码和用户当前要求冲突时，先把冲突说清楚，再继续实现。
