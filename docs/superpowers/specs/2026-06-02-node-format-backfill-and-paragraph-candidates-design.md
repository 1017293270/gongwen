# 节点格式回填与正文候选画布设计规格

日期：2026-06-02
状态：已确认设计方向，待用户审阅后进入实施计划
项目目录：`D:\gongwen`

## 1. 背景

工作台已经完成 `DraftNode` 结构化编辑、节点格式覆盖、真实预览刷新和 Word 导出追溯。当前仍有两个体验断点：

1. 节点格式面板只显示草稿节点的 `formatOverride`，没有把模板原稿或结构映射中的现有样式回填给用户。
2. 从提纲生成正文时，现有 `/api/drafts/{draftId}/ai/paragraph` 会直接写入 `DraftNode` 和兼容 `DraftBlock`，用户缺少先审阅候选、再确认替换的安全缓冲。

本设计把两个问题合并处理：先让用户看清当前节点真实生效样式，再让 AI 正文生成进入“候选池 -> 审阅 -> 逐段或批量采纳”的可控流程。

## 2. 目标

- 节点格式面板默认展示当前节点的生效样式，而不是空白覆盖值。
- 用户能区分“模板/原稿样式”和“草稿覆盖样式”。
- 提纲生成后，用户可以一次生成全部正文候选。
- 正文候选先进入右侧临时画布，不直接替换工作台正文。
- 用户可以逐段确认、编辑、重试、丢弃，也可以批量确认已完成候选。
- 停止生成后，已经生成的候选保留，失败段可单独重试。
- AI trace 不记录完整敏感正文，候选存储和确认写入均复用草稿权限边界。

## 3. 非目标

- 不做完整在线 Word 编辑器。
- 不把候选画布扩展成审计后台。
- 不改变导出的格式合并优先级。
- 不强制所有模型都支持流式；不支持流式的模型可降级为分段非流式候选。
- 不在第一版做多人协同候选合并。

## 4. 节点格式回填

### 4.1 后端合同

给 `DraftNodeDto` 增加只读格式字段，推荐命名：

```text
baseFormatting
effectiveFormatting
```

- `baseFormatting`：模板原稿结构节点、结构映射覆盖、文种默认、系统默认合并后的默认样式，不含草稿节点覆盖。
- `effectiveFormatting`：`baseFormatting + formatOverride` 的最终生效样式。

格式字段沿用现有 `TemplateStructureFormattingProfile` 合同，继续包含：

- `eastAsiaFontFamily`
- `latinFontFamily`
- `fontSizeHalfPoints`
- `bold`
- `alignment`
- `indentationFirstLine`
- `lineSpacing`
- `spacingBefore`
- `spacingAfter`
- 兼容字段 `fontFamily`、`spacingBetween`

后端解析来源优先级保持现有规则：

```text
草稿节点覆盖 > 结构映射/模板覆盖 > 原 DOCX effective formatting > 文种默认 > 系统默认
```

### 4.2 前端行为

`NodeFormatPanel` 展示“当前生效样式”，但保存时只提交用户明确改过的覆盖字段。

推荐交互：

- 每个输入框默认显示 `effectiveFormatting` 对应值。
- 字段旁或输入状态标识是否来自“模板默认”或“草稿覆盖”。
- 用户修改字段后，该字段进入覆盖状态。
- “恢复模板默认”只清空 `formatOverride`，不修改模板或结构映射。
- 结构化编辑预览使用 `effectiveFormatting` 渲染近似样式。
- 真实预览仍以后端 LibreOffice 刷新结果为准。

### 4.3 兼容策略

如果后端暂未返回 `effectiveFormatting`，前端可以临时回退：

```text
template profile structure formatting + template structure override + formatOverride
```

但正式实现应以后端返回为准，避免前端和导出逻辑漂移。

## 5. 正文候选画布

### 5.1 用户流程

1. 用户生成或查看提纲。
2. 用户点击“生成全部正文候选”。
3. 右侧显示候选画布，按提纲段落列出候选项。
4. 系统按段落顺序生成，当前段流式输出。
5. 已完成段落进入待确认状态。
6. 用户可以逐段编辑候选、重试、丢弃、确认替换。
7. 用户可以点击“批量确认”，一次采纳所有已完成且未丢弃的候选。
8. 失败段保留错误状态，批量确认时跳过失败段。
9. 停止生成后，已完成候选继续保留，未生成段落可稍后继续。

### 5.2 候选状态

候选项状态：

```text
PENDING
STREAMING
READY
EDITED
ACCEPTING
ACCEPTED
RETRYING
ERROR
DISCARDED
CANCELLED
```

批量任务状态：

```text
IDLE
GENERATING
CANCELLING
PARTIAL_READY
READY
ERROR
CANCELLED
ACCEPTING
ACCEPTED
```

### 5.3 数据模型

推荐新增 `ai_paragraph_candidate` 表。

核心字段：

- `id`
- `draft_id`
- `target_node_id`
- `target_node_role`
- `target_node_title`
- `outline_trace_id`
- `paragraph_trace_id`
- `section_index`
- `heading`
- `points_json`
- `instruction_summary`
- `candidate_text`
- `candidate_text_digest`
- `status`
- `error_code`
- `error_message`
- `accepted_at`
- `accepted_by`
- `created_at`
- `updated_at`

存储原则：

- 候选正文属于草稿工作区数据，必须按草稿权限读取和写入。
- trace 元数据只保存节点 id、角色、标题、字符数、摘要和错误，不保存完整材料正文。
- `candidate_text` 可保存完整候选，因为这是用户审阅和采纳对象；如果后续进入更高安全等级，可增加脱敏或短期过期策略。

### 5.4 API 合同

推荐 API：

```text
POST /api/drafts/{draftId}/ai/paragraph-candidates/batch
GET  /api/drafts/{draftId}/ai/paragraph-candidates
POST /api/drafts/{draftId}/ai/paragraph-candidates/{candidateId}/retry
PUT  /api/drafts/{draftId}/ai/paragraph-candidates/{candidateId}
POST /api/drafts/{draftId}/ai/paragraph-candidates/{candidateId}/accept
POST /api/drafts/{draftId}/ai/paragraph-candidates/accept-batch
POST /api/drafts/{draftId}/ai/paragraph-candidates/cancel
DELETE /api/drafts/{draftId}/ai/paragraph-candidates/{candidateId}
```

流式生成可以采用 SSE：

```text
POST /api/drafts/{draftId}/ai/paragraph-candidates/batch/stream
```

事件类型：

```text
batch_started
candidate_started
candidate_delta
candidate_ready
candidate_error
candidate_cancelled
batch_done
batch_error
```

如果浏览器或部署环境不适合 POST SSE，可使用“创建任务 + GET 事件流”：

```text
POST /api/drafts/{draftId}/ai/paragraph-candidate-jobs
GET  /api/drafts/{draftId}/ai/paragraph-candidate-jobs/{jobId}/events
```

第一版推荐“创建任务 + GET SSE”，更利于重连和恢复。

### 5.5 采纳写入

采纳候选时由后端统一写入：

- 更新目标 `DraftNode.content`。
- 将目标节点状态设为 `AI_GENERATED` 或 `USER_MODIFIED_AFTER_AI`。
- 同步兼容 `DraftBlock`，保持现有质检和旧链路可用。
- 标记真实预览待刷新。
- 更新候选状态为 `ACCEPTED`。

批量采纳规则：

- 只采纳 `READY` 和 `EDITED` 候选。
- 跳过 `ERROR`、`DISCARDED`、`CANCELLED`、`ACCEPTED`。
- 对同一目标节点出现多个候选时，只允许采纳最新未废弃候选，或要求用户先明确选择。
- 返回每个候选的采纳结果，前端展示成功数、跳过数和失败数。

## 6. 模型适配

`ModelAdapter` 增加正文候选生成能力：

```text
generateParagraphCandidate(prompt)
streamParagraphCandidate(prompt, sink)
```

DeepSeek 适配：

- 非流式路径继续可用，作为降级。
- 流式路径使用 `stream=true`。
- 为了能逐字/逐段展示，正文候选流式输出可先不强制 JSON response format；完成后后端再做规范化和校验。
- 如果坚持结构化 JSON，需要实现增量缓冲，直到完整 JSON 可解析后再进入 `READY`，但用户看到的流式颗粒度会变差。

推荐第一版正文候选流式采用纯文本输出，服务端统一规范化：

```text
生成内容必须只输出正文段落文本，不输出 Markdown，不输出 JSON，不输出解释。
```

## 7. 前端设计

右侧 `AI 建议与质检` 区域新增 `ParagraphCandidateCanvas`。

画布组成：

- 批量任务工具条：生成全部、停止、继续、批量确认。
- 候选列表：按提纲顺序展示标题、状态、生成进度、错误。
- 候选详情：正文候选文本、编辑框、确认替换、重试、丢弃。
- 状态反馈：生成中、停止中、失败重试、已确认、跳过失败段。

布局原则：

- 保持三栏工作台，不新增页面级 hero 或营销式布局。
- 候选画布在右侧面板内，和质检、导出、节点格式面板保持同一设计语言。
- 长正文使用固定高度编辑区，避免撑爆右栏。
- 生成时显示当前段落的流式输出和仍在处理提示。
- 批量确认前显示确认提示，说明会替换哪些正文节点。

## 8. 安全与权限

- 所有候选 API 复用 `DraftService.getDraft` 的草稿访问过滤。
- 起草人只能查看和采纳自己的候选。
- 模板管理员不能越权访问起草人草稿候选，除非未来显式增加授权。
- 候选删除、批量采纳不是不可逆生产发布，但仍应记录操作时间和操作者。
- AI trace 不记录完整材料正文，不记录完整原文上下文。
- 错误响应继续使用统一 `ApiResponse.error(errorCode, message)`。
- 批量确认必须有前端确认，避免误覆盖多个正文节点。

## 9. 错误处理

必须覆盖：

- AI 服务不可用。
- 单段生成失败。
- 批量生成中途停止。
- SSE 连接断开。
- 目标节点不存在或已删除。
- 目标节点锁定。
- 候选已被采纳或丢弃。
- 批量确认部分成功、部分失败。
- 权限不足。

SSE 断开后，前端应重新拉取候选列表，以候选表状态为准。

## 10. 测试计划

后端 focused tests：

- 候选生成不写 `DraftNode` 和 `DraftBlock`。
- 单段采纳后写入目标 `DraftNode` 并同步兼容 `DraftBlock`。
- 批量采纳跳过失败、丢弃和已采纳候选。
- 候选权限复用草稿权限。
- 目标节点锁定或删除时采纳失败。
- 格式回填返回 `baseFormatting` 和 `effectiveFormatting`。
- `formatOverride` 仍优先于模板默认样式。

前端 tests：

- 格式面板显示生效样式而非空白覆盖。
- 修改单个字段后只提交该字段覆盖。
- 恢复默认后显示模板样式。
- 生成全部正文候选时不立即替换正文。
- 流式候选逐段进入 `READY`。
- 逐段确认替换目标正文。
- 批量确认只采纳可采纳候选。
- 停止生成后已完成候选保留。
- 错误段可重试。

人工或浏览器验证：

- 桌面三栏布局不被候选画布撑乱。
- 移动端候选画布至少可操作。
- 长正文不溢出按钮和面板。
- 真实预览在采纳后标记待刷新。

## 11. 实施建议

建议分两阶段实施：

### 阶段 A：节点格式回填

1. 后端扩展 `DraftNodeDto` 格式字段。
2. 前端 `DraftNode` 类型补字段。
3. `workbenchNodes.ts` 使用 `effectiveFormatting` 派生结构化预览样式。
4. `NodeFormatPanel` 显示生效样式和覆盖来源。
5. 补 focused 后端和前端测试。

### 阶段 B：正文候选画布

1. 新增候选表和后端候选服务。
2. 新增候选 CRUD、采纳、批量采纳 API。
3. 增加模型适配的流式正文候选能力。
4. 前端新增 `ParagraphCandidateCanvas`。
5. 将“生成全部正文”切换为生成候选，不直接写正文。
6. 补停止、重试、批量确认、失败恢复测试。

阶段 A 可独立合入。阶段 B 涉及 API、AI、前端状态和数据模型，建议按后端候选合同、前端画布、流式适配、QA 文档拆分并行。

## 12. 剩余决策

已确认：

- 第一版正文候选支持一次生成全部段落。
- 支持逐段确认。
- 支持批量确认。
- 候选在确认前不替换正文。

待实施前确认：

- 候选正文是否长期保留，还是在草稿完成导出后清理。
- 流式第一版是否允许纯文本输出，完成后由服务端规范化。
- 批量确认是否需要二次确认弹窗，推荐需要。
