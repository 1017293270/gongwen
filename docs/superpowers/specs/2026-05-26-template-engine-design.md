# 公文模板引擎设计规格

日期：2026-05-26  
状态：已确认方向，待实施计划  
项目目录：`D:\gongwen`

## 1. 背景

当前项目已经具备 `.docx` 模板占位符解析、基础字段替换和 Word 导出能力，但模板模块仍停留在最小闭环：后端内置模板、单 run 占位符替换、模板后台尚未闭环、版式规则没有被结构化理解。

后续如果只在现有占位符模型上继续补功能，等到需要支持字体、字号、缩进、行距、页边距、页眉页脚、多级编号、表格、图片或印章占位时，会产生较大的模型重构。因此模板模块需要从“占位符替换器”升级为“Word 样式体系优先”的模板引擎。

本设计采用一步到位的 C 方案：底层直接按 Word 样式体系建模，首版 UI 和业务能力分阶段释放。

## 2. 设计目标

- Word 模板是最终版式权威，系统导出结果应尽量继承原模板样式。
- 系统必须结构化理解模板，而不是只保存占位符列表。
- 模板后台、质检、导出必须共用同一份模板事实来源。
- 底层模型必须覆盖未来扩展到完整 Word 样式体系的需要。
- 首版不做网页 Word 编辑器，只做样式理解、映射确认、关键规则覆盖和导出前质检。
- 缺字段、缺映射、模板解析失败等错误必须在导出前暴露并阻断。
- 页眉页脚、页码、纸张、页边距、分节、多级编号、表格、图片和印章占位进入底层解析与存储；首版后台主要只读展示和风险提示。

## 3. 总体架构

模板模块改造为五层：

```text
Word .docx 文件
-> Raw Word Parser
-> Template Profiler
-> Template Profile + Mapping + Rules
-> Quality Check / Export Plan / Template Admin
-> Docx Renderer
```

### 3.1 Raw Word Parser

只负责读取 Word 原始事实，不做业务判断。

解析范围：

- styles：`styleId`、`styleName`、类型、继承关系、字体、字号、粗体、颜色、对齐、行距、缩进、段前段后。
- paragraphs：段落文本、所属 style、run 列表、占位符位置、编号信息。
- runs：run 文本、字符样式、字体、字号、粗体、斜体、颜色。
- sections：纸张大小、页边距、分节、页眉页脚、页码。
- numbering：多级编号定义和段落绑定关系。
- tables：表格、单元格、单元格内占位符。
- media anchors：图片、印章或图片占位符。
- placeholders：占位符文本、所在段落、所在 run 范围、是否跨 run。

### 3.2 Template Profiler

把 Raw Word 信息转换成系统可消费的 `TemplateProfile`。

职责：

- 生成模板结构快照。
- 识别占位符清单。
- 提取可映射的 Word 样式候选。
- 推断公文语义块候选，例如标题、主送、正文、一级标题、落款、日期。
- 生成默认 `TemplateRule`。
- 生成模板自身校验结果。
- 标记复杂结构风险，例如跨 run 占位符、复杂多级编号、页眉页脚、表格嵌套和图片锚点。

### 3.3 Template Profile / Mapping / Rules

这是模板模块的唯一事实来源。模板后台、质检和导出都读取它，不各自维护一套规则。

核心能力：

- 保存完整 Word 结构摘要。
- 保存公文语义块到 Word style 或占位符的映射。
- 保存解析出的规则和管理员覆盖规则。
- 保存模板自身校验结果。
- 记录 profile hash，用于判断模板文件是否变化。

### 3.4 Quality Check / Export Plan / Template Admin

三个业务入口共用模板事实：

- 模板后台负责展示解析结果、确认映射、配置字段和关键规则。
- 质检读取模板规则，检查缺字段、缺结构、缺映射、样式风险和导出阻断项。
- 导出前由 Template Binder 生成 export plan，明确每个草稿块填入哪里、使用哪个 Word style、是否允许导出。

### 3.5 Docx Renderer

只执行 export plan，不自己猜业务含义。

职责：

- 跨 run 替换占位符。
- 按 export plan 填充字段。
- 将正文块展开为多个 Word 段落，而不是在单个 run 内只插入换行。
- 复制模板中的样式锚点段落。
- 应用 `styleId/styleName`。
- 尽量保留页眉页脚、页边距、编号、表格和图片结构。
- 失败时返回结构化错误并写入 export record。

## 4. 数据模型

### 4.1 DocumentTemplate

模板主表。

字段建议：

- `id`
- `template_name`
- `document_type_code`
- `status`
- `default_template`
- `tenant_id`
- `department_id`
- `created_by`
- `created_at`
- `updated_at`

职责：

- 表示一个逻辑模板。
- 可以拥有多个版本。
- 可以绑定文种。
- 可以启用、停用、设为默认。

### 4.2 TemplateVersion

模板版本表。每次上传 `.docx` 都生成不可变版本。

字段建议：

- `id`
- `template_id`
- `version_no`
- `original_file_name`
- `file_path`
- `content_type`
- `file_size_bytes`
- `profile_hash`
- `parse_status`
- `parse_error_code`
- `parse_error_message`
- `created_by`
- `created_at`

职责：

- 保存原始 Word 文件。
- 绑定解析结果。
- 被 export record 引用，保证历史导出可追溯。

### 4.3 TemplateProfile

模板解析快照，建议 JSONB 保存，并带 schema version。

字段建议：

- `id`
- `template_version_id`
- `schema_version`
- `profile_json`
- `created_at`

`profile_json` 建议包含：

- `styles`
- `paragraphs`
- `runs`
- `sections`
- `numbering`
- `tables`
- `mediaAnchors`
- `placeholders`
- `styleCandidates`
- `warnings`

### 4.4 TemplateBlockMapping

公文语义块到 Word 模板结构的映射。

字段建议：

- `id`
- `template_version_id`
- `block_type`
- `paragraph_role`
- `placeholder_key`
- `style_id`
- `style_name`
- `anchor_paragraph_id`
- `repeat_mode`
- `required`
- `sort_order`
- `created_at`
- `updated_at`

示例：

- `TITLE -> styleId: official_title`
- `RECIPIENT -> placeholder: 主送`
- `BODY_PARAGRAPH -> styleId: body_text, repeat_mode: paragraph`
- `BODY_HEADING_1 -> styleId: heading_1`
- `SIGNATURE -> placeholder: 落款`
- `DATE -> placeholder: 日期`

### 4.5 TemplateRule

模板规则和覆盖项。

字段建议：

- `id`
- `template_version_id`
- `target_type`
- `target_key`
- `rule_type`
- `expected_value`
- `severity`
- `source`
- `enabled`
- `created_at`
- `updated_at`

`source` 取值：

- `PARSED`
- `ADMIN_OVERRIDE`
- `SYSTEM_DEFAULT`

首版关键规则：

- 字体
- 字号
- 行距
- 首行缩进
- 段前段后
- 对齐方式
- 必填字段
- 必需结构块
- 必需映射

### 4.6 TemplateValidationResult

模板自身校验结果。

字段建议：

- `id`
- `template_version_id`
- `severity`
- `code`
- `message`
- `target_type`
- `target_key`
- `details_json`
- `created_at`

示例：

- `MISSING_REQUIRED_PLACEHOLDER`
- `PLACEHOLDER_SPLIT_ACROSS_RUNS`
- `BODY_STYLE_NOT_FOUND`
- `COMPLEX_NUMBERING_READ_ONLY`
- `HEADER_FOOTER_READ_ONLY`
- `TABLE_PLACEHOLDER_DETECTED`

## 5. 模板后台

模板管理员后台首版包含四个区域。

### 5.1 模板概览

展示：

- 模板名称
- 文种
- 当前版本
- 状态
- 是否默认
- 解析状态
- 占位符数量
- 样式数量
- 风险数量

操作：

- 上传新模板
- 创建新版本
- 启用
- 停用
- 设为默认

### 5.2 占位符与字段

展示解析出的占位符。

支持配置：

- 字段标签
- 字段类型
- 是否必填
- 默认值
- 说明
- 排序
- 绑定草稿块类型

### 5.3 段落类型与 Word 样式映射

展示 Word 样式列表和系统推断的候选映射。

首版支持确认或修改以下映射：

- 标题
- 主送
- 正文段落
- 正文一级标题
- 附件说明
- 落款
- 日期

后续可以扩展：

- 二级标题
- 编号段落
- 附件列表
- 表格行
- 印章区域

### 5.4 版式规则与风险

展示从 Word 解析出的关键规则：

- 字体
- 字号
- 行距
- 缩进
- 对齐
- 段前段后
- 页边距
- 页眉页脚
- 页码
- 编号
- 表格
- 图片或印章占位

首版允许覆盖少数关键规则：

- 标题样式
- 正文样式
- 正文首行缩进
- 正文行距
- 落款对齐
- 必需结构块

复杂项首版只读展示和风险提示，不做复杂编辑。

## 6. 质检联动

P8 质检应读取模板规则，而不是独立维护一套硬编码检查。

检查结果分级：

- `ERROR`：必须修复，阻止导出。
- `WARNING`：允许导出，但提示人工复核。
- `SUGGESTION`：优化建议。
- `PASS`：检查通过。

首版检查项：

### 6.1 字段完整性

- 必填字段为空。
- 模板占位符没有对应草稿值。
- 字段类型不符合规则。
- 日期格式异常。

### 6.2 结构完整性

- 缺标题。
- 缺主送。
- 缺正文。
- 缺落款。
- 缺日期。
- 文种要求的结构块缺失。
- 模板要求的正文段落或附件说明缺失。

### 6.3 模板映射

- 草稿块没有可用占位符或 Word style。
- 正文段落无法展开到模板正文区域。
- 落款或日期映射位置缺失。
- 模板解析失败。
- 模板未启用。

### 6.4 版式风险

- 关键样式缺失。
- 正文字体、字号、行距或缩进和模板规则不一致。
- 标题样式缺失或异常。
- 落款对齐规则缺失。
- 页眉页脚、编号、表格或图片结构复杂，需要人工复核。

### 6.5 导出前阻断

以下情况必须阻断导出：

- 模板未启用。
- 模板版本解析失败。
- 必填字段缺失。
- 必需结构块缺失。
- 必需映射缺失。
- Docx renderer 无法生成 export plan。

## 7. 导出流程

```text
用户点击导出
-> 加载 draft + draft blocks
-> 加载 template version + profile + mappings + rules
-> 执行导出前质检
-> ERROR 存在则阻断
-> Template Binder 生成 export plan
-> Docx Renderer 渲染 docx
-> 保存导出文件
-> 写入 export record
-> 返回下载
```

Export record 必须记录：

- draft id
- template id
- template version id
- template name
- template version
- file name
- file path
- status
- error code
- error message
- validation summary
- exported by
- created at

## 8. 实施阶段

### T1 模板引擎底座

目标：后端模型和解析能力先稳定。

范围：

- 新增模板版本、profile、mapping、rule、validation result 数据模型。
- 上传模板保存原始文件。
- 解析 styles、placeholders、sections、numbering、tables、headers/footers 摘要。
- 生成 `TemplateProfile` JSONB。
- 生成模板解析风险。
- 后端测试覆盖跨 run 占位符、样式解析、页边距、页眉页脚和表格摘要。

### T2 导出引擎升级

目标：导出真正消费模板 profile。

范围：

- 从草稿块生成 export plan。
- 支持正文段落展开成多个 Word 段落。
- 支持按 `styleId/styleName` 应用样式。
- 支持跨 run 替换。
- 导出失败写入结构化错误。
- export record 绑定模板版本。
- 测试覆盖标题、正文多段、落款日期、缺映射和缺字段。

### T3 模板管理后台

目标：管理员能维护模板。

范围：

- 上传模板。
- 查看解析结果。
- 配置字段和必填规则。
- 确认块类型到 Word style 的映射。
- 启用、停用、设为默认。
- 查看复杂版式风险。
- 复杂项只读展示，不做完整在线编辑。

### T4 质检与工作台联动

目标：起草人导出前就能看到模板驱动的问题。

范围：

- 工作台选择启用模板。
- 右侧质检读取模板规则。
- 展示错误、警告和建议。
- 错误阻断导出。
- 警告允许导出但提示复核。
- 导出成功记录模板版本和检查结果摘要。

## 9. 验收标准

- 可以上传一个真实 `.docx` 公文模板。
- 可以解析出占位符和关键 Word 样式。
- 可以解析并保存页眉页脚、页码、纸张、页边距、分节、多级编号、表格、图片或印章占位的摘要。
- 可以把标题、主送、正文多段、附件、落款、日期正确套入模板。
- 导出的 Word 继承模板字体、字号、缩进、行距和页边距。
- 跨 run 占位符可以被识别和替换。
- 多段正文导出为多个 Word 段落。
- 缺字段或缺映射时阻止导出。
- 模板管理员能看到解析风险并完成基本映射配置。
- 起草人在工作台能选择模板并看到模板驱动的质检结果。
- 导出记录能追溯具体模板版本。

## 10. 非目标

首版不做：

- 完整在线 Word 编辑器。
- 页面级所见即所得排版编辑。
- 复杂页眉页脚编辑。
- 多级编号可视化编辑。
- 表格结构可视化编辑器。
- 图片、印章拖拽排版。
- PDF 导出。
- 模板审核流。

这些能力进入底层模型和风险展示，但不在首版 UI 中完整编辑。

## 11. 风险与策略

### 11.1 Word 结构复杂

风险：不同单位模板可能有复杂样式、分节、表格和 run 拆分。

策略：

- Raw parser 尽量保留事实。
- Profiler 生成风险，不静默失败。
- 复杂项先只读展示和人工确认。
- 导出失败必须有明确错误码。

### 11.2 MVP 范围膨胀

风险：一步到位按 C 建模容易滑向在线 Word 编辑器。

策略：

- 底层模型完整，UI 能力分阶段释放。
- 首版只开放关键映射和少数规则覆盖。
- 复杂编辑延后。

### 11.3 质检与导出规则分裂

风险：质检说通过，但导出失败。

策略：

- 质检和导出共用 `TemplateProfile`、`TemplateBlockMapping` 和 `TemplateRule`。
- 导出前必须重新执行阻断级检查。
- Export plan 生成失败也要进入质检结果。

### 11.4 历史导出不可追溯

风险：模板更新后无法解释历史文件来源。

策略：

- `TemplateVersion` 不可变。
- Export record 绑定具体模板版本。
- 保存 validation summary 和 profile hash。

## 12. 对现有路线的调整

当前任务队列中 P8、P10、P11 需要重新对齐：

- P8 不再只是基础质检，而是第一版模板驱动质检。
- P10 不只是模板上传页面，而是模板 profile、映射和规则管理后台。
- P11 导出体验增强必须依赖模板版本和 export plan。

推荐先新增一个模板引擎阶段，放在 P8 或 P10 前：

```text
P8A 模板引擎底座
P8B 模板驱动质检
P10 模板管理后台
P11 模板版本化导出体验
```

这样可以保持 C 方案底层一步到位，同时让功能按可验收阶段推进。
