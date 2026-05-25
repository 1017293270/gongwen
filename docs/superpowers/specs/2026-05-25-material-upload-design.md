# P4 材料上传与统一反馈设计规格

日期：2026-05-25  
状态：已确认，进入实施  

## 1. 目标

P4 目标是在现有草稿工作台基础上补齐 Word/PDF 材料上传、文本提取、材料状态保存和前端材料列表，为 P5 AI 提纲生成提供可消费的材料上下文。

本阶段同时引入轻量组件库作为统一反馈基础，先覆盖保存成功、上传成功、上传失败和加载状态，后续 AI 生成、质检、导出复用同一反馈入口。

## 2. 范围

包含：

- 新增 `material` 表，材料归属于草稿。
- 新增本地文件存储抽象，默认保存到 `storage/materials`。
- 支持 `.docx` 和 `.pdf` 上传。
- 校验扩展名、内容类型和大小，默认最大 20MB。
- `.docx` 使用 Apache POI 提取文本。
- `.pdf` 使用 Apache PDFBox 提取文本。
- 提取成功保存 `READY` 状态和提取文本。
- 提取失败保存 `FAILED` 状态和错误摘要。
- 新增材料上传和列表 API。
- 前端左栏接入真实材料上传、列表、加载、成功、失败状态。
- 前端引入 Radix UI Toast primitives，并用 `DESIGN.md` CSS 变量封装项目自己的统一反馈组件。

不包含：

- 材料下载、删除、批量管理。
- 图片/扫描件 OCR。
- 材料级权限完整实现。
- 将材料接入 AI prompt。
- 完整模板管理员 UI。

## 3. 后端设计

新增包：`com.gongwen.assistant.material`。

核心单元：

- `MaterialController`：提供 `POST /api/drafts/{draftId}/materials` 和 `GET /api/drafts/{draftId}/materials`。
- `MaterialService`：负责校验、存储、提取、状态编排。
- `MaterialRepository` / `JdbcMaterialRepository`：保存材料元数据、提取文本和错误摘要。
- `MaterialStorage` / `LocalMaterialStorage`：封装本地文件落盘路径。
- `MaterialTextExtractor`：统一 Word/PDF 文本提取入口。

上传流程：

1. Controller 接收 `MultipartFile`。
2. Service 校验草稿存在、文件非空、大小不超过限制、类型为 Word/PDF。
3. 文件写入本地存储目录。
4. 根据类型提取文本。
5. 成功则保存 `READY`，失败则保存 `FAILED` 和错误摘要。
6. 返回材料 DTO，前端展示材料状态。

错误策略：

- 不允许类型：返回 `400 MATERIAL_TYPE_NOT_ALLOWED`。
- 文件过大：返回 `400 MATERIAL_FILE_TOO_LARGE`。
- 空文件：返回 `400 MATERIAL_FILE_EMPTY`。
- 草稿不存在：沿用 `404 DRAFT_NOT_FOUND`。
- 提取失败：材料记录保存为 `FAILED`，接口仍返回成功响应和失败状态，让用户能看到该材料失败原因。

## 4. 前端设计

左栏“文种、模板与材料”继续保持现有三栏结构，不新增页面。

新增 UI：

- 上传按钮绑定隐藏文件输入，接受 `.docx,.pdf`。
- 上传中按钮禁用并显示统一加载文案。
- 材料列表显示文件名、类型、大小、状态、提取字数或错误摘要。
- 保存草稿、上传成功、上传失败使用统一 Toast。

组件库选择：

- 使用 `@radix-ui/react-toast`。
- 不直接采用成套视觉组件库，避免引入与 `DESIGN.md` 冲突的视觉语言。
- 项目封装 `ToastProvider` / `useToast`，样式全部使用现有 CSS 变量。

## 5. 配置

新增配置：

- `gongwen.material.storage-dir`，默认 `storage/materials`。
- `gongwen.material.max-file-size-bytes`，默认 `20971520`。
- Spring multipart 默认最大文件和请求大小设置为 20MB。

`.env.example` 增加本地材料存储目录说明。

## 6. 测试

后端：

- Controller 上传 `.docx` 成功。
- Controller 拒绝不支持文件类型。
- Service 保存提取成功材料。
- Service 保存提取失败材料为 `FAILED`。
- Word/PDF 提取器至少覆盖 Word 文本提取；PDF 通过小型测试文档覆盖。

前端：

- 加载草稿后请求材料列表。
- 上传成功后刷新材料列表并显示 Toast。
- 上传失败后显示统一错误 Toast。

验证命令：

- 后端：Docker Gradle `test`。
- 前端：`npm test` 和 `npm run build`。

