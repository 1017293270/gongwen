# 多 Agent 协同开发工作流

本文用于提高公文助手后续开发效率。目标是让多个 AI Agent 可以并行工作，但不互相覆盖、不制造接口漂移，最后能由一个集成 Agent 收口验证。

## 什么时候使用多 Agent

适合使用：

- 任务横跨前端、后端、测试、文档。
- 前后端可以先通过明确 API contract 解耦。
- 有清晰文件边界。
- 有一个最终集成者负责合并和验证。

不适合使用：

- 需求还没想清楚。
- 主要改动集中在同一个文件同一段代码。
- 需要连续调试同一个 bug。
- 还没有接口字段约定。

## 标准启动流程

1. 主会话阅读 `AGENTS.md`、`DESIGN.md`、`docs/PROJECT_TASKS.md`。
2. 主会话明确本轮阶段，例如 P7 或 P8。
3. 主会话写出 API contract 或状态模型草案。
4. 主会话按文件边界派发 Agent。
5. 各 Agent 独立实现并报告改动、测试和风险。
6. 集成 Agent 查看 `git status --short`、关键 diff 和冲突区域。
7. 集成 Agent 跑 focused tests。
8. 集成 Agent 跑全量后端测试、前端测试和前端构建。
9. 集成 Agent 更新 `AGENTS.md` 和相关 docs。
10. 主会话总结完成情况和下一步。

## 推荐 Agent 角色

### 架构/集成 Agent

职责：

- 拆任务。
- 定 API contract。
- 维护模块边界。
- 处理冲突。
- 跑最终验证。
- 更新接力文档。

不要做：

- 不要在未读代码前大改架构。
- 不要把每个 Agent 的分支思路混在一起直接重构。

### 后端 Agent

职责：

- Controller。
- Service。
- Repository。
- DTO/request/response。
- Prompt builder。
- Model adapter。
- Trace。
- 后端测试。

文件范围：

- `backend/src/main/java/**`
- `backend/src/test/java/**`
- 必要时更新 `backend/src/main/resources/application.yml`

交付说明必须包含：

- 新增或修改 API。
- 错误码。
- 安全边界。
- trace 字段。
- 跑过的测试。

### 前端 Agent

职责：

- 页面和组件。
- 状态机。
- API client。
- 类型定义。
- loading/error/empty/disabled/success 状态。
- 前端测试。

文件范围：

- `frontend/src/**`

交付说明必须包含：

- 用户可见变化。
- 交互状态。
- API 调用点。
- 响应式或可访问性注意事项。
- 跑过的测试。

### QA Agent

职责：

- 找失败路径。
- 补测试。
- 补验收脚本。
- 做浏览器或接口验证。

不要做：

- 不要顺手重构业务逻辑。
- 不要改视觉风格。

### 文档 Agent

职责：

- 更新 `AGENTS.md`。
- 更新 `docs/PROJECT_TASKS.md`。
- 更新专题文档。
- 记录已知问题和下一步。

不要做：

- 不要把流水账写进 AGENTS。
- 不要记录真实密钥、敏感正文或材料全文。

## 文件冲突规则

红区文件，同一时间只能一个 Agent 主改：

- `frontend/src/App.tsx`
- `frontend/src/styles/app.css`
- `backend/src/main/java/com/gongwen/assistant/ai/ModelAdapter.java`
- `backend/src/main/java/com/gongwen/assistant/ai/PromptBuilder.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiOutlineService.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiParagraphService.java`
- `AGENTS.md`
- `docs/PROJECT_TASKS.md`

黄区文件，可以并行但要提前约定字段：

- `frontend/src/api.ts`
- `frontend/src/draftTypes.ts`
- `backend/src/main/java/**/Controller.java`
- `backend/src/main/java/**/Request.java`
- `backend/src/main/java/**/Response.java`

绿区文件，通常可并行：

- 新增后端测试文件。
- 新增前端测试用例，但注意同一测试文件冲突。
- 新增专题文档。
- 新增独立 service/helper。

## P7 推荐并行方案

目标：局部段落 AI 操作。

### Agent A 后端

范围：

- 新增局部操作类型：正式化、压缩、扩写、改写、补充要求。
- 新增建议型 API。
- 不直接覆盖原文。
- 写 trace。
- 写后端测试。

建议 API：

- `POST /api/drafts/{draftId}/ai/local-operation`

建议 request：

```json
{
  "blockId": 123,
  "operation": "FORMALIZE",
  "instruction": "更正式一些"
}
```

建议 response：

```json
{
  "traceId": "uuid",
  "draftId": 1,
  "blockId": 123,
  "operation": "FORMALIZE",
  "suggestion": "建议文本",
  "status": "READY"
}
```

### Agent B 前端

范围：

- Word 风格预览按 `DraftBlock` 渲染正文段落。
- 支持选择单个 `BODY_PARAGRAPH`。
- 右栏显示局部操作按钮。
- 建议生成后显示采纳/放弃。
- 采纳后复用 `PUT /api/drafts/{id}/blocks`。

状态必须覆盖：

- 未选择段落。
- 已选择段落。
- 生成中。
- 生成失败。
- 建议待采纳。
- 采纳保存中。
- 采纳成功。

### Agent C QA/文档

范围：

- 前端测试：未选择段落时禁用、生成建议、失败重试、采纳后保存。
- 后端测试：无权限草稿、block 不存在、非正文块、模型失败、成功 trace。
- 文档：更新 P7 状态和验收说明。

### 集成 Agent

范围：

- 对齐 API 字段。
- 检查 trace 不保存完整敏感正文。
- 跑验证。
- 浏览器检查工作台仍保持三栏结构。
- 更新 `AGENTS.md` 和 `docs/PROJECT_TASKS.md`。

## P8 推荐并行方案

目标：基础质检。

### Agent A 后端规则检查

- 必填字段检查。
- 文种结构检查。
- 导出前检查。
- `QualityCheckResult` 持久化或 JSONB 快照。

### Agent B 前端质检面板

- 右栏新增质检结果列表。
- 按严重程度展示。
- 支持重新检查。
- 支持空、失败、加载、成功状态。

### Agent C 测试/文档

- 缺字段 fixture。
- 结构缺失 fixture。
- 导出前阻断测试。
- 文档和任务状态更新。

## 每个 Agent 的交付格式

```text
Agent:
阶段:
范围:
改动文件:
新增/修改 API:
已覆盖状态:
测试:
未验证:
风险:
需要集成处理:
```

## 最终验收清单

- `git status --short` 已查看。
- 没有误改用户明确要求不要动的区域。
- 后端 focused tests 通过。
- 后端全量测试通过。
- 前端 `npm test -- --run` 通过。
- 前端 `npm run build` 通过。
- 关键页面浏览器验证通过。
- `AGENTS.md` 已更新。
- `docs/PROJECT_TASKS.md` 已更新。
- 新增环境变量已同步 `.env.example`。
