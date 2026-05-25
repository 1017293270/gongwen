# 模板与 Word 导出最小闭环设计规格

日期：2026-05-25
状态：已确认范围，进入实现

## 1. 目标

P1 阶段打通后端 `.docx` 模板占位符解析和结构化草稿导出 Word 的核心能力。该阶段不做完整前端模板管理页，不做完整权限系统，只为后续模板后台、草稿工作台和导出体验建立稳定后端边界。

## 2. 范围

包含：

- 使用 Apache POI 读取和写入 `.docx`。
- 解析 Word 模板中的 `{{字段名}}` 占位符。
- 支持基础字段：`标题`、`主送`、`正文`、`附件`、`落款`、`日期`。
- 新增模板解析服务。
- 新增 Word 模板填充导出服务。
- 新增模板、模板字段、导出记录数据库表。
- 新增最小 API：解析模板、导出 Word。
- 使用测试代码生成 `.docx` 模板并验证解析和导出。

不包含：

- 前端模板管理页面。
- 完整登录和权限控制。
- 模板启停 UI。
- 模板审核流。
- PDF 导出。
- 复杂 Word 样式精修。

## 3. 架构边界

新增后端模块：

- `template`：模板相关入口和 DTO。
- `template.parser`：只负责 `.docx` 占位符识别，不关心数据库和导出记录。
- `exporting`：导出入口、DTO 和导出记录。
- `exporting.word`：只负责 Word 模板填充，不关心 HTTP 表单和权限。

服务职责：

- `DocxPlaceholderParser`：输入 `.docx` 字节，输出去重后的占位符列表。
- `DocxTemplateRenderer`：输入 `.docx` 字节和字段值，输出填充后的 `.docx` 字节。
- `WordExportService`：校验必填值、调用 renderer、保存导出记录。
- `TemplateController`：接收 multipart 模板文件，返回占位符列表。
- `WordExportController`：接收 JSON 请求，返回 `.docx` 文件。

## 4. 占位符规则

占位符格式固定为：

```text
{{字段名}}
```

解析规则：

- 字段名 trim 后不能为空。
- 同名字段去重。
- 输出按首次出现顺序排序。
- P1 支持段落和表格内文本。
- P1 不保证处理被 Word 拆成多个 run 的占位符，后续可增强。测试模板必须让单个占位符保持在同一段文本中。

填充规则：

- 字段值来源于结构化 JSON。
- `正文` 可包含换行，导出时换行转换为 Word 段内换行。
- 缺少模板中任一占位符对应的字段值时，导出失败并返回明确错误。
- 不在日志记录完整正文。

## 5. 数据模型

新增表：

- `document_template`：模板元数据和版本。
- `template_field`：模板解析出的字段。
- `export_record`：导出记录和模板版本追溯。

P1 API 可先不实现完整模板上传落库流程，但迁移必须提前建立表结构，供后续阶段复用。

## 6. API

### 解析模板

`POST /api/templates/parse`

请求：

- `multipart/form-data`
- 字段：`file`

响应：

```json
{
  "success": true,
  "data": {
    "placeholders": ["标题", "主送", "正文", "落款", "日期"]
  },
  "errorCode": null,
  "message": null
}
```

### 导出 Word

`POST /api/exports/word`

请求：

```json
{
  "templateName": "通知模板",
  "templateVersion": 1,
  "values": {
    "标题": "关于开展年度档案整理工作的通知",
    "主送": "各部门、各直属单位",
    "正文": "为进一步规范年度档案管理工作...",
    "落款": "办公室",
    "日期": "2026年5月25日"
  }
}
```

P1 为了避免前端上传和持久化耦合，导出 API 使用内置测试模板生成 Word 文件。后续 P10 模板后台完成后，再改为基于模板 ID 导出。

响应：

- 成功：`application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- 失败：JSON 错误响应。

## 7. 测试

必须覆盖：

- `.docx` 段落占位符解析。
- `.docx` 表格占位符解析。
- 重复占位符去重。
- 缺失字段导出失败。
- 成功导出的 `.docx` 可由 Apache POI 读取，且占位符已替换。
- API 解析模板返回占位符。

## 8. 后续衔接

P1 完成后进入：

1. P2 草稿结构与文种模型。
2. P3 工作台前端接真实草稿数据。
3. P10 模板管理员后台接入真实模板上传和字段配置。
