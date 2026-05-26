# P5 AI 生成提纲设计规格

日期：2026-05-26  
状态：已确认，进入实施

## 1. 目标

P5 目标是在现有草稿、文种和材料能力基础上，建立可测试、可替换、可追踪的 AI 提纲生成链路。

本阶段先打通“基于草稿字段和材料摘要生成结构化提纲”的最小闭环，为 P6 逐段正文生成提供稳定输入。默认实现使用本地 deterministic 模拟模型适配器，确保没有云模型密钥时也能完成测试、联调和演示。云模型接入只做配置和接口预留，不在本阶段绑定具体供应商。

## 2. 范围

包含：

- 新增 `ai_generation_trace` 表，记录 AI 调用元数据和结果状态。
- 新增后端 `ai` 模块。
- 新增 `PromptBuilder`，集中构建提纲生成提示词输入。
- 新增 `ModelAdapter` 接口和本地模拟实现。
- 新增提纲结构化响应模型。
- 新增 `POST /api/drafts/{draftId}/ai/outline`。
- 基于文种、草稿块和材料摘要生成提纲。
- 输出标题建议、正文结构、段落要点、缺失信息提示。
- 失败时返回归一化错误。
- trace 不保存完整敏感正文或完整材料提取文本。

不包含：

- 真实云模型 API 调用。
- 流式输出。
- 前端逐字流式渲染。
- 将提纲保存回草稿块。
- 逐段正文生成。
- 局部改写、正式化、压缩、扩写。
- 复杂 prompt injection 防护和完整权限体系。

## 3. 后端设计

新增包：`com.gongwen.assistant.ai`。

核心单元：

- `AiOutlineController`：提供 `POST /api/drafts/{draftId}/ai/outline`。
- `AiOutlineService`：编排草稿读取、材料摘要、prompt 构建、模型调用、结构化解析和 trace。
- `PromptBuilder`：根据文种、草稿块、材料摘要生成提纲任务输入，不散落在 controller。
- `ModelAdapter`：模型调用接口，返回规范化结果。
- `MockModelAdapter`：默认模拟模型，按草稿内容生成稳定提纲。
- `AiGenerationTraceRepository` / `JdbcAiGenerationTraceRepository`：记录 AI 调用 trace。

API 请求：

```json
{
  "instruction": "请突出会议安排和执行要求"
}
```

`instruction` 可为空，最大 1000 字。它只作为用户补充要求，不直接覆盖系统约束。

API 响应：

```json
{
  "success": true,
  "data": {
    "traceId": "uuid",
    "titleSuggestion": "关于开展季度经营分析会的通知",
    "sections": [
      {
        "heading": "一、背景与目的",
        "points": ["说明会议背景", "明确分析目标"]
      }
    ],
    "missingInformation": ["会议时间", "参会范围"]
  },
  "error": null
}
```

错误策略：

- 草稿不存在：沿用 `404 DRAFT_NOT_FOUND`。
- 请求过长：返回 `400 AI_OUTLINE_INSTRUCTION_TOO_LONG`。
- 模型失败：返回 `502 AI_MODEL_UNAVAILABLE`。
- 结构化输出无效：返回 `502 AI_RESPONSE_INVALID`。

## 4. Trace 与安全

`ai_generation_trace` 保存：

- `id`
- `draft_id`
- `task_type`
- `provider`
- `model_name`
- `status`
- `prompt_version`
- `input_summary`
- `output_summary`
- `error_code`
- `error_message`
- `latency_ms`
- `created_at`
- `tenant_id`
- `department_id`
- `version`

安全约束：

- `input_summary` 只保存文种、草稿块数量、材料数量、材料摘要总长度和补充要求长度。
- `output_summary` 只保存标题建议、章节数和缺失信息数量。
- 不保存完整草稿正文、完整材料提取文本或完整 prompt。
- 模型适配器配置通过环境变量或 Spring 配置注入，不硬编码密钥。

## 5. Prompt 设计

本阶段不把大 prompt 写入前端或 controller。`PromptBuilder` 生成一个结构化 `OutlinePrompt`：

- 文种名称。
- 草稿字段摘要。
- 正文块摘要。
- 材料摘要，单条材料截断到固定长度。
- 用户补充要求。
- 输出格式约束。
- 安全约束。

后续接入真实模型时，云模型 adapter 可以把 `OutlinePrompt` 渲染为 provider 需要的消息格式。

## 6. 前端设计

右栏“AI 建议与质检”增加提纲生成入口：

- 默认态：显示“生成提纲”按钮。
- 生成中：按钮禁用，显示处理中状态，并提供取消按钮预留。
- 成功态：显示标题建议、章节和要点、缺失信息提示。
- 失败态：显示错误摘要和重试按钮。
- 空态：没有草稿或草稿加载失败时禁用。

本阶段前端可以先接非流式 API。取消按钮先中止前端请求；后端取消和异步任务管理留到后续阶段。

## 7. 测试

后端：

- PromptBuilder 不泄露完整材料正文。
- MockModelAdapter 返回稳定结构化提纲。
- Service 生成提纲并写入成功 trace。
- Service 在 adapter 失败时写入失败 trace 并返回归一化错误。
- Controller 覆盖成功、草稿不存在、请求过长。

前端：

- 点击生成提纲后调用 API。
- 生成中禁用按钮。
- 成功后展示标题建议和章节。
- 失败后展示错误并可重试。

验证命令：

- 后端：Docker Gradle `test`。
- 前端：`npm test` 和 `npm run build`。

