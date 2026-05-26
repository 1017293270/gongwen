# AI 配置与 DeepSeek 接入说明

本文记录当前 AI 模型配置页、后端模型适配层和 DeepSeek 接入方式，供后续开发者和 AI Agent 接手。

## 当前完成状态

状态：已完成基础接入。

已经完成：

- 系统设置页新增真实 AI 配置表单。
- 支持在运行时切换 `mock` / `deepseek`。
- 支持配置 DeepSeek Base URL、模型、超时秒数和 API Key。
- 支持保存配置和测试连接。
- 测试连接会先保存当前表单，再调用后端测试接口，避免前端表单与后端运行时状态不一致。
- 后端新增模型路由层，提纲生成和逐段正文生成都通过统一 `ModelAdapter` 入口。
- DeepSeek 通过 OpenAI 兼容 `/chat/completions` 接入。
- DeepSeek 输出要求为 JSON，并在后端解析成已有提纲和段落响应结构。
- API Key 只存在当前后端运行时内存和环境变量中，不写入数据库，不写入 trace，不出现在响应明文里。

当前没有完成：

- AI 配置持久化到数据库。
- 多用户/多角色下的系统设置权限控制。
- DeepSeek 账单、额度、模型列表远程拉取。
- 流式输出。
- 取消正在进行的 DeepSeek HTTP 请求。
- 生产级密钥管理和密钥轮换。

## 前端入口

入口：系统设置 -> AI 配置。

相关文件：

- `frontend/src/App.tsx`
- `frontend/src/api.ts`
- `frontend/src/draftTypes.ts`
- `frontend/src/styles/app.css`
- `frontend/src/App.test.tsx`

页面能力：

- 读取当前 AI 配置。
- 切换供应商。
- 启用或停用 DeepSeek。
- 配置 Base URL。
- 选择模型。
- 输入 API Key。
- 设置超时秒数。
- 保存配置。
- 保存当前表单并测试连接。

交互注意事项：

- 用户选择 DeepSeek 但没有保存时，前端只是表单态；测试连接已改成自动保存当前表单后再测试。
- API Key 输入框不会回显明文。后端只返回是否已配置和脱敏值。
- 未配置 Key 时，启用 DeepSeek 保存会被后端拒绝。
- 未启用 DeepSeek 或未配置 Key 时，模型路由会保持 Mock。

## 后端接口

当前核心接口：

- `GET /api/ai/settings`
- `PUT /api/ai/settings`
- `POST /api/ai/settings/test`

返回仍遵循项目既有响应包装：

```json
{
  "success": true,
  "data": {},
  "errorCode": null,
  "message": null
}
```

`GET /api/ai/settings` 返回：

- `provider`
- `deepSeekEnabled`
- `deepSeekBaseUrl`
- `deepSeekModel`
- `deepSeekApiKeyConfigured`
- `maskedDeepSeekApiKey`
- `deepSeekTimeoutSeconds`

`PUT /api/ai/settings` 入参：

- `provider`
- `deepSeekEnabled`
- `deepSeekBaseUrl`
- `deepSeekModel`
- `deepSeekApiKey`
- `clearDeepSeekApiKey`
- `deepSeekTimeoutSeconds`

`POST /api/ai/settings/test` 行为：

- 如果当前运行时未启用 DeepSeek，则返回 Mock 可用状态。
- 如果当前运行时启用了 DeepSeek，则发起一次最小 DeepSeek JSON 调用。
- 调用成功返回 provider、model、available、message、latencyMs。
- 调用失败返回统一错误。

## 后端模块

相关文件：

- `backend/src/main/java/com/gongwen/assistant/ai/AiRuntimeProperties.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiConfigurationState.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiProviderSettings.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiProviderSettingsUpdateRequest.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiProviderStatus.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiSettingsController.java`
- `backend/src/main/java/com/gongwen/assistant/ai/AiSettingsService.java`
- `backend/src/main/java/com/gongwen/assistant/ai/DeepSeekRuntimeConfig.java`
- `backend/src/main/java/com/gongwen/assistant/ai/DeepSeekModelAdapter.java`
- `backend/src/main/java/com/gongwen/assistant/ai/RoutingModelAdapter.java`
- `backend/src/main/java/com/gongwen/assistant/ai/MockModelAdapter.java`
- `backend/src/main/java/com/gongwen/assistant/ai/ModelAdapter.java`

调用路径：

```text
Controller
-> AiOutlineService / AiParagraphService
-> ModelAdapter
-> RoutingModelAdapter
-> MockModelAdapter 或 DeepSeekModelAdapter
-> ai_generation_trace
```

配置路径：

```text
application.yml / environment
-> AiRuntimeProperties
-> AiConfigurationState
-> AiSettingsController
-> RoutingModelAdapter
```

## 环境变量

`.env.example` 已包含：

```env
GONGWEN_AI_PROVIDER=mock
GONGWEN_DEEPSEEK_ENABLED=false
GONGWEN_DEEPSEEK_BASE_URL=https://api.deepseek.com
GONGWEN_DEEPSEEK_MODEL=deepseek-v4-flash
GONGWEN_DEEPSEEK_TIMEOUT_SECONDS=60
DEEPSEEK_API_KEY=
```

开发建议：

- 本地无 Key 时保持 `GONGWEN_AI_PROVIDER=mock`。
- 需要真实 DeepSeek 时，设置 `DEEPSEEK_API_KEY`，或在系统设置页运行时输入。
- 系统设置页写入的是后端内存态配置；后端重启后会回到环境变量。
- 不要把真实 Key 写入仓库、测试 fixture、日志或文档示例。

## 支持模型

当前页面和后端允许：

- `deepseek-v4-flash`
- `deepseek-v4-pro`
- `deepseek-chat`
- `deepseek-reasoner`

如需新增模型：

1. 更新 `AiConfigurationState` 的允许列表。
2. 更新 `frontend/src/App.tsx` 的模型下拉。
3. 更新 `.env.example` 默认值或说明。
4. 更新本说明文件。
5. 跑前后端相关测试。

## 安全边界

必须保持：

- API Key 不明文返回前端。
- API Key 不写入数据库。
- API Key 不写入 `ai_generation_trace`。
- trace 仍只记录 provider、model、任务类型、prompt 版本、输入摘要、输出摘要、错误摘要和耗时。
- DeepSeek prompt 不应包含完整敏感正文或完整材料全文。
- 日志不要打印请求 Authorization header。

后续生产化建议：

- 增加系统设置权限校验，仅模板管理员或系统管理员可改 AI 配置。
- 将 Key 改为受保护的密钥存储，而不是普通数据库字段。
- 增加密钥轮换、清除 Key、连接审计记录。
- 增加成本、耗时和错误率统计。

## 验证命令

后端 focused 测试：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test-focused.ps1 "com.gongwen.assistant.ai.AiSettingsServiceTest"
```

后端全量测试：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backend-test.ps1
```

前端测试：

```powershell
cd frontend
npm test -- --run
```

前端构建：

```powershell
cd frontend
npm run build
```

接口烟测：

```powershell
Invoke-RestMethod -Uri http://127.0.0.1:8080/api/ai/settings
Invoke-RestMethod -Uri http://127.0.0.1:8080/api/ai/settings/test -Method Post
```

## 本轮验证结果

本轮已验证：

- `AiSettingsServiceTest` 通过。
- 后端全量测试通过。
- 前端 `npm test -- --run` 通过。
- 前端 `npm run build` 通过。
- 本地后端 `GET /api/ai/settings` 可返回当前运行时配置。
- 本地后端 `PUT /api/ai/settings` 可保存运行时配置。
- 本地后端 `POST /api/ai/settings/test` 可返回 Mock 或 DeepSeek 测试状态。
- 本地前端 `http://127.0.0.1:5175` 可打开系统设置页。

## 已知注意点

- Codex Browser 插件在本机曾因 `node_repl` 内核报 `failed to write kernel assets` 不可用；需要浏览器验证时可使用终端 Playwright 或手动浏览器。
- Vite 通过 `npm run dev -- --host 127.0.0.1 --port 5175` 在本机 PowerShell 后台启动时曾出现参数转发异常；必要时可直接使用 `node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5175`。
- 如果页面显示 `Failed to fetch`，优先检查后端 8080 是否在监听，以及 `VITE_API_BASE_URL` 是否指向当前后端。
- 如果页面表单显示 DeepSeek，但测试结果显示 Mock，说明后端运行时状态仍未启用 DeepSeek；当前代码已将“测试连接”改为自动保存后测试，刷新前端即可使用新逻辑。

## 下一步建议

短期：

1. P7 局部段落 AI 操作。
2. P8 基础质检。
3. 给系统设置增加“清除 API Key”操作。
4. 给 AI 配置 API 增加 controller 层测试。

中期：

1. 将系统设置权限纳入 RBAC。
2. 将 AI 配置持久化到受保护配置表或密钥服务。
3. 接入流式输出、取消和更完整的超时处理。
4. 增加 AI 调用成本、耗时、失败率和 provider 状态总览。
