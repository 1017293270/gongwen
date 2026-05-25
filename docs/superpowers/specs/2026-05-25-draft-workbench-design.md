# 草稿结构与工作台真实数据设计规格

日期：2026-05-25
状态：已确认范围，进入实现

## 1. 目标

P2+P3 合并阶段把当前静态三栏工作台接入真实后端数据。完成后，用户可以创建一份默认公文草稿、编辑标题/主送/正文/附件/落款/日期，并保存到 PostgreSQL。

## 2. 范围

包含：

- 新增文种模型 `DocumentType`，内置通知、请示、报告。
- 新增草稿主表 `draft`。
- 新增草稿块表 `draft_block`。
- 新增草稿创建、读取、块保存 API。
- 前端启动后加载文种列表。
- 前端可创建默认通知草稿。
- 左侧字段表单绑定真实草稿块。
- 中间 Word 风格预览由草稿块渲染。
- 保存按钮将编辑内容写回后端。
- 右侧展示草稿保存状态。

不包含：

- 登录鉴权。
- AI 生成。
- 材料上传。
- 模板选择 UI。
- 富文本编辑器。
- 审核流。

## 3. 数据模型

### document_type

- `code`：`NOTICE`、`REQUEST`、`REPORT`
- `name`：通知、请示、报告
- `status`：`ACTIVE`
- `sort_order`

### draft

- `id`
- `document_type_code`
- `title`
- `status`：P2 使用 `DRAFT`
- `template_id`：预留
- `created_by`：预留
- `tenant_id`、`department_id`：预留
- `review_status`：预留
- `version_no`
- `created_at`、`updated_at`

### draft_block

- `id`
- `draft_id`
- `block_type`：`TITLE`、`RECIPIENT`、`BODY_PARAGRAPH`、`ATTACHMENT`、`SIGNATURE`、`DATE`
- `content`
- `sort_order`
- `metadata`：JSONB
- `created_at`、`updated_at`

## 4. API

### 获取文种

`GET /api/document-types`

返回：

```json
{
  "success": true,
  "data": [
    {"code": "NOTICE", "name": "通知", "status": "ACTIVE", "sortOrder": 1}
  ],
  "errorCode": null,
  "message": null
}
```

### 创建草稿

`POST /api/drafts`

请求：

```json
{
  "documentTypeCode": "NOTICE",
  "title": "关于开展年度档案整理工作的通知"
}
```

返回草稿详情，并自动生成默认块。

### 获取草稿

`GET /api/drafts/{id}`

返回草稿详情和块列表。

### 保存草稿块

`PUT /api/drafts/{id}/blocks`

请求：

```json
{
  "blocks": [
    {"blockType": "TITLE", "content": "标题", "sortOrder": 10},
    {"blockType": "RECIPIENT", "content": "各部门、各直属单位", "sortOrder": 20}
  ]
}
```

返回更新后的草稿详情。

## 5. 前端交互

- 首屏加载文种列表。
- 如果当前没有草稿，自动创建一份通知草稿。
- 左侧字段编辑标题、主送、背景/正文、附件、落款、日期。
- 中间预览实时反映本地编辑。
- 点击保存后调用 `PUT /api/drafts/{id}/blocks`。
- 保存成功后右侧显示最近保存状态。
- 保存失败后右侧显示错误，保留本地编辑内容。

## 6. 测试

后端：

- 文种列表包含通知、请示、报告。
- 创建草稿生成默认块。
- 获取不存在草稿返回明确错误。
- 保存草稿块后可重新读取。

前端：

- 工作台加载后显示后端草稿标题。
- 修改标题后点击保存会调用 API。
- API 失败时显示错误状态。

## 7. 后续衔接

P2+P3 完成后，P4 可以接材料上传，P5/P6 可以把 AI 生成结果保存为 `DraftBlock`，P1 的 Word 导出可以改为直接消费真实草稿块。
