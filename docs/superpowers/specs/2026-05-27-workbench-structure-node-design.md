# 工作台结构节点统一模型设计

日期：2026-05-27
状态：方案 A 已确认，待实施计划
项目目录：`D:\gongwen`

## 1. 背景

当前工作台把模板、草稿、AI 和质检拆成了几套不同对象：

- 模板解析产出 `TemplateProfile.structures`，能看到标题、正文段落、页眉、落款等结构。
- 草稿保存仍主要依赖 `DraftBlock`，正文只有 `BODY_PARAGRAPH`。
- AI 提纲、逐段生成、局部段落建议和质检结果分别按 section、sortOrder、blockId、blockType 工作。
- 工作台中间纸张能展示一部分模板正文，但模板分段没有真正成为可选择、可编辑、可生成、可质检的业务对象。

这导致模板里的正文小标题和正文内容无法稳定同步到左栏目录、中间纸张、右栏 AI 操作、提纲生成和质检结果。后续继续补展示会越来越乱。

## 2. 目标

建立工作台统一结构节点模型，让模板分段、草稿编辑、AI 生成、质检、局部建议和样式覆盖都绑定到同一套节点。

核心目标：

- 正文必须拆成“正文标题 + 正文内容”，例如 `一、会议时间` 和 `2026年6月3日...` 是同一个正文节点的两个字段，而不是混在一个段落字符串里。
- 工作台左栏、中间纸张和右栏始终围绕当前选中的结构节点联动。
- 工作台允许对当前草稿节点做元素和样式覆盖，但不修改模板本体。
- AI 提纲、逐段生成、局部建议和质检结果都能挂到具体节点。
- 保持 MVP 边界，不做完整 Word 编辑器，不在本阶段追求完整 OOXML 可视化编辑。

## 3. 核心概念

新增前端/后端共同理解的工作台节点模型 `WorkbenchNode`。它不是替代 Word 模板，而是模板结构进入草稿工作台后的业务视图。

推荐字段：

```text
nodeId                稳定节点标识，优先来自模板 structureKey，草稿新增节点生成 draft-scoped key
nodeType              TITLE / RECIPIENT / BODY_SECTION / ATTACHMENT / SIGNATURE / DATE / STATIC_TEMPLATE_TEXT / HEADER / FOOTER
templateStructureKey  来源模板结构 key，可为空
draftBlockId          关联草稿块 id，可为空
sortOrder             工作台顺序
label                 左栏和右栏显示名
heading               正文小标题，仅 BODY_SECTION 使用
content               正文内容或普通结构内容
source                TEMPLATE / DRAFT / AI / USER
locked                是否只读模板静态结构
formatting            生效维度，按“模板默认 + 模板覆盖 + 草稿节点覆盖”合并
qualityItems          当前节点质检项摘要
```

正文节点必须使用 `BODY_SECTION`：

```text
BODY_SECTION
  heading: 一、会议时间
  content: 2026年6月3日（星期三）上午9:30。
```

## 4. 数据流

模板进入工作台时，先把 `TemplateProfile.structures` 转换为 `WorkbenchNode[]`：

- `TITLE`、`RECIPIENT`、`ATTACHMENT`、`SIGNATURE`、`DATE` 映射到同名业务节点。
- 连续的模板 `BODY` 结构按规则合并为 `BODY_SECTION`：
  - 形如 `一、会议时间`、`二、会议地点` 的短段落作为 `heading`。
  - 紧随其后的正文段落作为该 section 的 `content`。
  - 如果正文开头没有小标题，则生成一个无 heading 的正文节点。
- `HEADER`、`FOOTER` 和无法映射的结构保留为静态节点。
- 草稿已有内容时，草稿内容覆盖模板默认内容；模板结构作为缺省底稿和样式来源。

保存草稿时，短期可以继续落到 `DraftBlock`：

- `TITLE`、`RECIPIENT`、`ATTACHMENT`、`SIGNATURE`、`DATE` 仍保存为对应 block。
- `BODY_SECTION` 可以先序列化为多个 `BODY_PARAGRAPH`，但前端必须保留 heading/content 分离视图。
- 后续迁移为真正的 `DraftNode` 表时，保持 API 语义不变。

## 5. API 与持久化契约

节点化不应一次性打断现有 `DraftBlock` 链路。推荐先增加节点语义 API，再逐步把底层存储从 block 迁到 node：

```text
GET /api/drafts/{draftId}/nodes
PUT /api/drafts/{draftId}/nodes
PUT /api/drafts/{draftId}/nodes/{nodeId}/overrides
POST /api/drafts/{draftId}/ai/paragraph       增加 nodeId / nodePart，兼容 sortOrder
POST /api/drafts/{draftId}/ai/local-operation 增加 nodeId / nodePart，兼容 targetBlockId
POST /api/drafts/{draftId}/quality-check      返回 targetNodeId / targetNodePart
```

后端节点存储推荐字段：

```text
draft_node
  id
  draft_id
  template_version_id
  template_structure_key
  parent_node_id
  node_type
  node_part
  heading
  content
  source
  sort_order
  formatting_override_json
  mapping_override_json
  status
  created_at
  updated_at
```

迁移护栏：

- 读取时优先返回 `draft_node`；没有节点数据时，从 `DraftBlock + TemplateProfile` 派生节点。
- 保存节点时同步更新现有 `DraftBlock`，保证旧导出和旧预览短期仍能工作。
- 用户手工编辑过的节点不被模板重新解析自动覆盖，除非用户明确选择“按模板重置结构”。
- 节点样式覆盖是草稿局部配置；模板版本维度覆盖仍属于模板后台配置。
- 导出记录必须保存使用的 `templateVersionId` 和节点快照摘要，保证后续追溯。

## 6. 工作台交互

三栏结构不变，但每栏职责调整：

- 左栏：显示节点目录和基础字段。正文区显示正文小标题列表，而不是“第 1 段”。
- 中栏：按节点渲染类 Word 纸张。点击标题、正文内容、附件、落款等任意结构都会选中对应节点。
- 右栏：只显示当前节点相关的 AI 操作、补充要求、质检结果和样式覆盖。

节点选中后，右栏提供两类配置：

- 内容配置：节点标题、节点正文、补充要求、生成/重写/扩写等操作。
- 样式覆盖：字体、字号、对齐、首行缩进、行距、段前段后、加粗等，仅影响当前草稿节点，不修改模板默认值。

样式优先级：

```text
草稿节点覆盖 > 模板版本维度覆盖 > 模板解析默认维度 > 系统默认 Word 预览样式
```

## 7. AI 与质检联动

所有 AI/质检入口逐步迁移到节点语义：

- 生成提纲：输出 `BODY_SECTION[]`，包含 heading、points、missingInformation。
- 逐段生成：目标是某个 `BODY_SECTION.nodeId`，生成结果写入该节点 `content`。
- 生成全部正文：按 `BODY_SECTION.sortOrder` 串行生成。
- 局部建议：目标从 `blockId` 升级为 `nodeId`，支持对 heading 或 content 分别操作。
- 质检结果：每条问题挂 `nodeId`，能区分“正文小标题格式问题”和“正文内容缺失信息”。
- 右栏展示：只展示当前节点相关建议，同时保留全局质检入口和汇总状态。

## 8. 分阶段实施

第一阶段：前端工作台节点视图

- 在前端从 `DraftDetail + TemplateProfile + formattingOverrides` 派生 `WorkbenchNode[]`。
- 左栏正文目录改为节点标题列表。
- 中栏纸张按节点渲染，正文标题和正文内容分开。
- 右栏局部操作读取当前选中节点，而不是只读 `selectedBodyBlockId`。

第二阶段：AI 与质检节点化

- 提纲结果转换为 `BODY_SECTION`。
- 单段/全局正文生成写入对应节点。
- 局部操作 request 增加 `nodeId`、`nodePart`，兼容旧 `targetBlockId`。
- 质检结果返回 `targetNodeId`，前端按节点聚合展示。

第三阶段：持久化与导出复现

- 后端新增或演进草稿节点存储，避免长期用 `BODY_PARAGRAPH` 承载 heading/content。
- 草稿节点样式覆盖落库。
- `.docx` 导出读取同一套节点内容和生效维度。

## 9. 关键决策

当前推荐采用单个 `BODY_SECTION` 节点承载 `heading + content`，而不是一开始拆成 `BODY_HEADING` 和 `BODY_PARAGRAPH` 两个兄弟节点。

原因：

- 用户在业务上选择的是一个完整正文分段，右栏补充要求、质检、逐段生成都天然围绕该分段。
- 标题和内容需要能分别操作时，用 `nodePart=heading/content` 即可，不必立即扩大节点树复杂度。
- 后续若出现正文标题独立编号、独立样式或独立 AI 操作的强需求，再把 `BODY_SECTION` 拆为父子节点，API 仍可保持 `nodeId + nodePart` 兼容。

## 10. 非目标

- 不做完整在线 Word 编辑器。
- 不直接修改上传的 Word 模板文件。
- 不在本阶段支持复杂表格单元格级编辑、图片/印章拖拽、页码域编辑、宏或脚注尾注。
- 不把右栏做成全局审计后台，右栏只服务当前节点和必要全局摘要。

## 11. 验收标准

- 选择模板后，模板正文中的小标题和正文内容能在工作台中作为结构节点出现。
- 左栏正文目录显示真实小标题，例如“会议时间”“会议地点”，不再只显示“第 1 段”。
- 中间纸张中正文标题和正文内容分开渲染，点击任一部分能选中对应节点。
- 右栏局部操作、补充要求、质检项和样式覆盖随选中节点切换。
- 生成提纲和逐段正文不会破坏模板原有正文分段。
- 工作台节点样式覆盖不修改模板默认配置。
- 刷新页面后，用户对节点内容和节点样式的草稿级修改仍然存在。
- 旧草稿没有节点数据时仍可正常加载，并能派生出可编辑节点视图。

## 12. 测试要求

- 前端 focused 测试覆盖模板 BODY 结构转 `BODY_SECTION` 的规则。
- 前端交互测试覆盖点击正文标题/正文内容后右栏选中节点变化。
- 后端服务测试覆盖后续节点化 API 的兼容路径。
- 保留轻量验证节奏：优先 focused tests 和 `npm run build`，风险升高时再跑更广测试。
