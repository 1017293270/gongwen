# DOCX 原貌预览与结构化映射设计

日期：2026-05-30

状态：设计稿，待评审后进入实施计划

关联阶段：P10B 模板结构维度增强、P10C 工作台结构节点持久化、P11 导出体验增强、P12 部署与环境

## 1. 背景

公文助手当前已经具备 `.docx` 模板上传、`TemplateProfile` 解析、结构维度展示、工作台类 Word 预览、基础质检、草稿绑定模板导出等能力。当前解析链路主要是：

```text
DOCX 文件
  -> TemplateProfileParser
  -> TemplateProfile.structures/styles/placeholders/sections/tables/media
  -> 前端模板解析弹窗与工作台预览
  -> 质检与导出复用 effective formatting
```

这条链路适合 MVP 内的标准占位符模板、简单样式模板、常见通知/请示/报告范文。但在真实企业公文环境中，Word 文件来源复杂，样式写法高度不一致：

- 有些文件是标准模板，包含 `{{标题}}`、`{{正文}}` 等显式占位符。
- 有些文件是完整范文，没有占位符，需要识别标题、主送、正文、落款、日期等语义槽位。
- 有些文件是公文制度、手册、写作指南，不应进入套版模板流程。
- 有些文件由 WPS、Word、OA、复制粘贴或人工排版生成，直接格式、样式继承、编号、表格、文本框、页眉页脚混用。
- 有些公文把红头、版记、页码、附件、印章、发文机关标识藏在页眉、文本框、表格或图片里。

近期用 `高新发展公文使用手册 V1—1031.docx` 验证时暴露出几个典型问题：

- 该文件被 DeepSeek 正确识别为 `ORDINARY_DOCUMENT`，即“公文写作使用手册，并非公文模板或范文，无法用于自动套版”。
- 前端仍按模板结构维度展示它，导致用户误以为“模板解析失败”。
- 大量段落被标记为 `UNKNOWN`，因为它们是手册章节标题、条目和说明文字，而不是公文套版槽位。
- 许多中文段落显示为 `Times New Roman`，因为当前解析器只取 `run.getFontFamily()`，没有优先读取 OOXML `w:rFonts/@w:eastAsia`。
- 文本中出现“标题”“正文”“附件”“单位”等字样时，会被当前关键词规则误判为公文槽位。例如“1.标题：方正小标宋简体（二号）”只是手册说明，不应成为 `TITLE`。

这说明当前问题不是“多补几个关键词”能根治，而是解析架构需要从“直接猜业务语义”升级为“先还原 Word 事实，再识别文件类型，再生成可校正的结构映射”。

## 2. 目标

本设计采用用户确认的“第二档”方案：

```text
真实 DOCX 渲染 + 结构化事实提取 + 公文语义建议 + 人工校正面板
```

目标不是重造 Word，而是做一个可靠的“Word 结构标注器”：

- 用户看到的预览尽量接近真实 Word。
- 系统提取并保存 Word 的结构事实，不急于丢信息或硬猜。
- 系统给出公文结构建议，但不把建议当成不可更改事实。
- 用户可以在结构面板中确认、修正、忽略结构节点。
- AI 生成、质检、导出优先消费用户确认后的结构映射。
- 对暂不支持的复杂 OOXML 结构给出显式风险，不静默忽略。

### 2.1 产品目标

- 模板管理员上传 `.docx` 后，能看到接近 Word 原貌的预览。
- 模板管理员能看到结构树，而不是只能看到扁平段落列表。
- 模板管理员能知道文件类型：占位符模板、样式模板、范文、手册、普通 Word、混合文件。
- 模板管理员能把段落、表格、页眉页脚中的结构标注为标题、主送、正文、附件、落款、日期、版记、忽略等。
- 起草人工作台绑定模板后，使用确认后的结构映射，而不是每次重新推断。
- 导出失败或格式复现风险能追溯到具体结构节点。

### 2.2 工程目标

- 新增一个稳定的 `DocumentStructureProfile`，作为 Word 事实层合同。
- 保留现有 `TemplateProfile` 能力，但逐步让它消费 `DocumentStructureProfile`，不要继续在 `TemplateProfileParser` 中混合事实提取和公文语义判断。
- 引入 `DocumentRenderPreview`，保存真实渲染预览产物及其状态。
- 引入 `StructureMapping`，保存系统建议和人工确认后的结构语义。
- 新增样本库和回归测试，避免针对单个文件临时修规则。

### 2.3 稳定性目标

- 常见通知、请示、报告、函、会议纪要、通报、批复、意见类公文：可自动建议主要槽位，并允许人工确认。
- 手册、制度、普通文档：稳定识别为不适合自动套版，不误导用户进入模板配置。
- 复杂 Word：预览可见、结构可查、风险可提示，即使不能全自动复现，也不能静默丢失。
- 同一文件重复上传或重新解析，结构节点 key 要尽量稳定，避免用户映射频繁失效。

### 2.4 工作台产品原则：结构不丢，AI 增强

起草人工作台不应演化成“纯 AI 写作器”，也不应演化成“完整 Word 在线编辑器”。更适合本项目的定位是：

```text
结构化公文工作台 + AI 辅助扩写/润色/质检
```

核心原则是“结构不丢，AI 增强”：

- 左侧负责确定性结构：文种、模板、标题、主送、正文节点、附件、落款、日期、材料。
- 中间负责 Word 感知的编辑与预览：用户看到当前草稿如何按模板结构组织。
- 右侧负责智能增强：提纲生成、局部扩写、正式化、压缩、改写、补充依据、基础质检、导出前检查。

这三个区域的职责必须清晰，不要互相抢职责：

```text
左边 = 公文结构与输入事实
中间 = Word 原貌预览与结构化编辑
右边 = AI 操作、质检、导出
```

这种分工让用户心智更稳定：

- 用户先确认结构，再让 AI 补内容。
- AI 围绕已确认结构工作，不重新打散结构。
- 质检和导出消费同一套结构，不再各自猜测。
- 模板管理员确认的映射、起草人填写的字段、AI 生成的正文都能追溯到结构节点。

AI 操作必须以结构节点为上下文：

- 选中标题节点时，右侧显示标题优化、标题正式化、标题压缩等能力。
- 选中主送节点时，右侧显示主送规范检查、单位名称补全等能力。
- 选中正文节点时，右侧显示正式化、压缩、扩写、改写、补充、补材料依据等能力。
- 选中附件节点时，右侧显示附件说明补全、附件序号检查等能力。
- 未选中节点时，右侧显示全局提纲、全局质检、导出前检查。

AI 默认只能“建议、填充、扩写、替换当前节点内容”，不能默认破坏结构：

- 不能把主送单位写进正文。
- 不能把附件说明混进正文段落。
- 不能自动删除落款和日期。
- 不能把手册类文件误当成模板结构。
- 用户确认过的结构节点，AI 默认只对该节点给建议；跨节点改动必须明确提示并让用户确认。

节点状态也应成为工作台的一等信息：

```text
EMPTY：结构存在但内容为空。
USER_FILLED：用户已填写。
AI_GENERATED：AI 已生成。
USER_MODIFIED_AFTER_AI：AI 生成后用户已修改。
NEEDS_REVIEW：需要人工确认。
QUALITY_WARNING：质检有警告。
QUALITY_ERROR：质检有错误。
EXPORT_BLOCKED：会阻断导出。
```

这些状态应同时服务左侧结构树、中间预览标记和右侧 AI/质检面板。这样用户可以一眼知道“结构还在、内容到哪一步了、哪里需要 AI 帮忙、哪里会影响导出”。

## 3. 非目标

第一期明确不做：

- 不做完整在线 Word 编辑器。
- 不在浏览器里直接编辑原始 `.docx` 的所有排版细节。
- 不承诺任意 Word 文件 100% 自动理解。
- 不承诺复杂表格、文本框、图片印章、域代码、修订痕迹第一期全部可编辑和可导出复现。
- 不把 AI 判断作为唯一依据。
- 不用截图 OCR 反推结构，除非后续作为补充能力。
- 不在 MVP 阶段接入完整 OnlyOffice、Collabora、WPS 在线编辑内核。

第一期的核心是：

```text
看得像 Word
提取得足够完整
建议可解释
用户能校正
后续链路消费确认结果
```

## 4. 总体架构

目标架构分为六层：

```text
DOCX 原文件
  -> OOXML 事实提取层
  -> 版式归一层
  -> 文件类型识别层
  -> 公文/文档语义建议层
  -> 人工校正与映射层
  -> AI / 质检 / 导出消费层

并行：

DOCX 原文件
  -> 真实渲染预览层
  -> 前端页面预览
```

### 4.1 OOXML 事实提取层

职责：忠实记录 Word 文件里有什么。

这一层不判断“这是不是标题”，只记录：

- 文档结构：body、paragraph、run、table、row、cell、header、footer、footnote、endnote、textbox、drawing、picture。
- 文本：完整段落文本、run 文本、字段文本、隐藏文本标记。
- 位置：文档顺序、所在 section、所在页眉页脚、表格坐标、父节点路径。
- 样式：styleId、styleName、basedOn、nextStyle、linkedStyle。
- 直接格式：段落级、run 级、表格级、单元格级格式。
- 编号：numId、ilvl、abstractNumId、编号格式、编号文本模板。
- 字体：ascii、hAnsi、eastAsia、cs、hint、theme 字体。
- 页面：section、纸张、方向、页边距、页眉页脚引用、页码域。
- 媒体：图片文件名、类型、尺寸、锚点、inline/anchor。
- 风险结构：文本框、形状、复杂 DrawingML、域代码、目录、批注、修订、宏、嵌入对象。

输出：`DocumentStructureProfile`。

### 4.2 版式归一层

职责：把 Word 多来源格式合并为可展示、可比较、可质检的 effective formatting。

输入：

- 直接格式。
- 段落样式。
- 字符样式。
- 基准样式链。
- 编号样式。
- 文档默认样式。
- 主题字体和 theme color。

输出：

- `declaredFormatting`：直接写在节点上的格式。
- `inheritedFormatting`：从样式链继承的格式。
- `numberingFormatting`：编号系统提供的缩进、编号格式等。
- `effectiveFormatting`：最终用于展示/映射/质检/导出的生效格式。

关键规则：

- 中文文本优先使用 `eastAsia` 字体。
- 英文和数字优先使用 `ascii` / `hAnsi` 字体。
- 混合文本应同时保存 `latinFontFamily` 和 `eastAsiaFontFamily`，前端展示时可以优先显示中文字体。
- 字号使用 half-points 存储，前端显示转换为 pt。
- 缩进、间距、页边距统一保存 twips，同时提供可读摘要。
- `lineRule=exact` 与 `auto` 要区分，不能都压成一个数字。
- `spacingBetween` 当前的 `*100` 方案不足以表达 Word 固定值行距，应改为结构化字段。

### 4.3 文件类型识别层

职责：先判断文件是什么，再决定进入哪种展示和后续流程。

文档类型枚举建议：

```text
PLACEHOLDER_TEMPLATE
STYLE_TEMPLATE
REFERENCE_DOCUMENT
OFFICIAL_DOCUMENT
MANUAL_OR_GUIDE
POLICY_OR_REGULATION
ORDINARY_DOCUMENT
MIXED_DOCUMENT
UNKNOWN_DOCUMENT
```

说明：

- `PLACEHOLDER_TEMPLATE`：包含显式占位符，可直接进入套版模板配置。
- `STYLE_TEMPLATE`：空白或半空白格式模板，无完整正文，适合人工映射后用于套版。
- `REFERENCE_DOCUMENT`：完整范文，可提取结构和格式作为模板参考。
- `OFFICIAL_DOCUMENT`：真实已发公文，不一定适合作为模板，但结构具有参考价值。
- `MANUAL_OR_GUIDE`：公文使用手册、写作指南、培训材料，不应作为套版模板。
- `POLICY_OR_REGULATION`：制度、办法、规范，可能可作为知识材料，不适合套版。
- `ORDINARY_DOCUMENT`：普通 Word 文件，不进入公文模板流程。
- `MIXED_DOCUMENT`：前半说明、后半范例，或多个文档拼接。
- `UNKNOWN_DOCUMENT`：信号不足，需要人工确认。

识别依据：

- 占位符数量和位置。
- 文档标题与文件名关键词。
- 正文样本。
- 段落结构特征。
- 是否存在典型公文槽位。
- 是否存在大量章节结构、目录、手册式标题。
- 是否包含多份范例。
- 表格和图片比例。
- AI 分析结果。

输出：

- `documentKind`
- `confidence`
- `reasonCodes`
- `recommendedWorkflow`
- `blockingWarnings`

### 4.4 语义建议层

职责：基于事实和文档类型，给出可解释的结构建议。

对于公文模板/范文/真实公文，建议语义槽位：

```text
ISSUING_ORGAN
RED_HEADER
DOC_NUMBER
SIGNER
TITLE
RECIPIENT
BODY
BODY_HEADING_LEVEL_1
BODY_HEADING_LEVEL_2
BODY_HEADING_LEVEL_3
ATTACHMENT_NOTE
ATTACHMENT_CONTENT
SIGNATURE
DATE
COPY_TO
PRINT_ORGAN
PRINT_DATE
PAGE_NUMBER
SEAL_OR_IMAGE
TABLE_ATTACHMENT
IGNORE
UNKNOWN
```

对于手册/制度/普通文档，建议文档结构：

```text
COVER_TITLE
COVER_ORGAN
COVER_DATE
PART_TITLE
CHAPTER_TITLE
SECTION_TITLE
SUBSECTION_TITLE
ITEM_HEADING
BODY_TEXT
NOTE
EXAMPLE
TABLE
IMAGE
APPENDIX
IGNORE
UNKNOWN
```

每个建议必须包含：

- `suggestedRole`
- `confidence`
- `reasonCodes`
- `evidence`
- `source`：RULE、AI、USER、IMPORT、SYSTEM
- `alternatives`

示例：

```json
{
  "nodeKey": "body-p-20",
  "suggestedRole": "BODY_HEADING_LEVEL_1",
  "confidence": 0.92,
  "source": "RULE",
  "reasonCodes": ["CHINESE_LEVEL_1_NUMBERING", "FONTS_HEITI", "BODY_INDENTED"],
  "evidence": {
    "textPreview": "一、公文类别",
    "numberingPattern": "一、",
    "eastAsiaFont": "方正黑体",
    "fontSizeHalfPoints": 32
  },
  "alternatives": [
    {"role": "BODY_TEXT", "confidence": 0.25}
  ]
}
```

### 4.5 人工校正与映射层

职责：把系统建议变成用户确认的结构合同。

核心原则：

- 系统建议不等于最终事实。
- 用户确认优先级高于规则和 AI。
- 每个映射都要绑定稳定的 `nodeKey`。
- 文件重新解析后，应尽量通过 `nodeKey`、文本 hash、位置、样式特征找回旧映射。
- 人工映射需要版本化，便于回滚和追溯。

映射类型：

- 标记节点角色。
- 合并多个节点为同一槽位。
- 拆分一个节点为标题和正文。
- 忽略说明性节点。
- 标记重复结构。
- 标记表格为附件表格或普通表格。
- 标记页眉/页脚节点为保留、替换或忽略。

示例：

```json
{
  "nodeKey": "body-p-42",
  "confirmedRole": "TITLE",
  "confidence": 1.0,
  "source": "USER",
  "slotKey": "title",
  "mappingStatus": "CONFIRMED",
  "notes": "用户确认该段为公文标题"
}
```

### 4.6 消费层

后续模块不再直接消费粗糙的 `TemplateProfile.structures` 推断结果，而是消费：

```text
DocumentStructureProfile + StructureMappingProfile
```

模块消费方式：

- 工作台预览：使用真实渲染预览作为视觉参考，使用结构映射作为编辑锚点。
- AI 生成：向已确认的 `TITLE`、`BODY`、`ATTACHMENT_NOTE` 等节点写入内容。
- 质检：检查已确认槽位是否缺失、是否可替换、是否存在复杂结构风险。
- 导出：优先替换确认节点；不确定节点保留原样或阻断。
- 导出记录：保存 `structureProfileVersion` 和 `mappingVersion`。

## 5. 数据模型设计

### 5.1 新增核心实体

建议新增这些逻辑实体：

- `DocumentStructureProfile`
- `DocumentNode`
- `DocumentNodeFormatting`
- `DocumentRenderPreview`
- `DocumentKindAnalysis`
- `StructureSuggestion`
- `StructureMappingProfile`
- `StructureMappingItem`
- `StructureRiskItem`
- `StructureSampleFixture`

第一期可复用现有 `template_profile.profile_json` 存储一部分字段，但设计上应明确新合同，避免继续把所有内容塞进旧 `TemplateProfile`。

### 5.2 表结构建议

#### `document_structure_profile`

用于保存 Word 事实层和归一层。

字段建议：

```text
id bigserial primary key
template_version_id bigint references document_template_version(id)
source_file_hash varchar(128) not null
schema_version integer not null
extractor_version varchar(40) not null
node_count integer not null
risk_count integer not null
profile_json jsonb not null
created_at timestamptz not null default now()
updated_at timestamptz not null default now()
```

约束：

- `template_version_id` 可唯一，也可以允许多版本重算。第一期建议唯一，重算时覆盖或新建 version 字段。
- `profile_json` 必须保留原始节点和 effective formatting。

#### `document_render_preview`

用于保存 DOCX 原貌预览产物。

字段建议：

```text
id bigserial primary key
template_version_id bigint references document_template_version(id)
source_file_hash varchar(128) not null
renderer varchar(40) not null
renderer_version varchar(80)
status varchar(30) not null
page_count integer
storage_dir text
manifest_json jsonb
error_code varchar(80)
error_message text
created_at timestamptz not null default now()
updated_at timestamptz not null default now()
```

`manifest_json` 示例：

```json
{
  "pages": [
    {
      "pageIndex": 0,
      "imagePath": "storage/previews/template-version-12/page-1.png",
      "width": 1240,
      "height": 1754,
      "dpi": 150
    }
  ],
  "pdfPath": "storage/previews/template-version-12/document.pdf"
}
```

#### `document_kind_analysis`

用于保存文件类型识别结果。

字段建议：

```text
id bigserial primary key
template_version_id bigint references document_template_version(id)
document_kind varchar(60) not null
confidence numeric(5,4) not null
recommended_workflow varchar(80) not null
source varchar(40) not null
analysis_json jsonb not null
created_at timestamptz not null default now()
```

#### `structure_mapping_profile`

用于保存用户确认后的映射版本。

字段建议：

```text
id bigserial primary key
template_version_id bigint references document_template_version(id)
structure_profile_id bigint references document_structure_profile(id)
version_no integer not null
status varchar(30) not null
created_by bigint references app_user(id)
department_id bigint references department(id)
mapping_json jsonb not null
created_at timestamptz not null default now()
updated_at timestamptz not null default now()
```

#### `structure_mapping_audit`

用于记录人工校正历史。

字段建议：

```text
id bigserial primary key
mapping_profile_id bigint references structure_mapping_profile(id)
actor_user_id bigint references app_user(id)
action varchar(60) not null
node_key varchar(160)
before_json jsonb
after_json jsonb
created_at timestamptz not null default now()
```

### 5.3 `DocumentStructureProfile` JSON

顶层结构建议：

```json
{
  "schemaVersion": 1,
  "source": {
    "fileName": "example.docx",
    "fileHash": "sha256...",
    "createdBy": null,
    "modifiedBy": null
  },
  "documentDefaults": {
    "font": {},
    "paragraph": {},
    "section": {}
  },
  "styles": [],
  "numbering": [],
  "sections": [],
  "nodes": [],
  "risks": [],
  "stats": {}
}
```

### 5.4 `DocumentNode`

字段建议：

```json
{
  "nodeKey": "body-p-20",
  "nodeType": "PARAGRAPH",
  "location": {
    "part": "BODY",
    "sectionIndex": 0,
    "paragraphIndex": 20,
    "tableIndex": null,
    "rowIndex": null,
    "cellIndex": null,
    "headerFooterIndex": null
  },
  "text": {
    "plainText": "一、公文类别",
    "normalizedText": "一、公文类别",
    "textHash": "sha256...",
    "charCount": 6
  },
  "style": {
    "styleId": null,
    "styleName": null,
    "basedOnChain": []
  },
  "numbering": {
    "numId": "0",
    "ilvl": 0,
    "format": "chineseCounting",
    "levelText": "%1、",
    "renderedNumber": "一、"
  },
  "formatting": {
    "declared": {},
    "inherited": {},
    "numbering": {},
    "effective": {}
  },
  "runs": [],
  "children": [],
  "riskCodes": []
}
```

### 5.5 Formatting 字段建议

`effective` 示例：

```json
{
  "alignment": "BOTH",
  "indentation": {
    "firstLineTwips": 640,
    "leftTwips": 0,
    "rightTwips": 0
  },
  "spacing": {
    "beforeTwips": 0,
    "afterTwips": 157,
    "line": 590,
    "lineRule": "exact",
    "linePoints": 29.5
  },
  "font": {
    "eastAsia": "方正黑体",
    "latin": "Times New Roman",
    "display": "方正黑体",
    "sizeHalfPoints": 32,
    "sizePt": 16,
    "bold": false,
    "colorHex": null
  }
}
```

关键点：

- 不再只保存 `fontFamily` 一个字段。
- `display` 字段由归一层计算，中文文本优先 `eastAsia`。
- 保留原始字体字段，便于后续导出复现。

## 6. 后端模块设计

### 6.1 建议包结构

```text
com.gongwen.assistant.documentstructure
  DocumentStructureProfile
  DocumentStructureExtractor
  DocxOoxmlStructureExtractor
  EffectiveFormattingResolver
  NumberingResolver
  StyleInheritanceResolver
  DocumentKindClassifier
  StructureSuggestionService
  StructureMappingService
  DocumentRenderPreviewService
  DocumentStructureController
```

现有 `com.gongwen.assistant.template.profile` 保留，但应逐步降级为兼容层。

### 6.2 `DocumentStructureExtractor`

职责：

- 从 `.docx` 字节流读取 WordprocessingML。
- 提取 body 段落、表格、页眉、页脚、文本框、图片、section。
- 生成稳定节点 key。
- 保留原始 XML 指纹或路径。

节点 key 策略：

```text
part + section + structural path + local index + short text hash
```

示例：

```text
body-p-20-a8f3d2
body-table-1-r-2-c-3-p-0-b71a0c
header-0-p-2-91ab2f
```

节点 key 不应只靠 index，因为重新保存 Word 后 index 可能变化。也不应只靠文本 hash，因为重复段落常见。第一期可用“路径 + 文本 hash”组合。

### 6.3 `EffectiveFormattingResolver`

职责：

- 解析文档默认样式。
- 解析 style basedOn 链。
- 合并段落样式、字符样式、直接格式。
- 解析 numbering 缩进和编号文本。
- 输出 normalized formatting。

优先级建议：

```text
run direct formatting
  > character style
  > paragraph direct formatting
  > paragraph style
  > basedOn style chain
  > numbering level style
  > document defaults
```

需要注意 Word 实际规则更复杂，第一期以可解释和可测试为准。

### 6.4 `DocumentKindClassifier`

职责：

- 基于规则和 AI 共同判断文件类型。
- 规则结果和 AI 结果都保存。
- 最终输出一个稳定 `documentKind`。

第一期推荐“规则先行，AI 辅助”：

- 有显式占位符且字段命中公文字段：`PLACEHOLDER_TEMPLATE`。
- 文本很少但样式丰富：`STYLE_TEMPLATE`。
- 有典型公文结构，正文完整：`REFERENCE_DOCUMENT` 或 `OFFICIAL_DOCUMENT`。
- 文件名或标题含“手册、指南、规范、制度、办法、培训、写作基础”：倾向 `MANUAL_OR_GUIDE` 或 `POLICY_OR_REGULATION`。
- 章节标题密集，正文包含大量“标题/正文/附件格式说明”：倾向 `MANUAL_OR_GUIDE`。
- 无明显公文结构：`ORDINARY_DOCUMENT`。

AI 仅用于补充：

- 辅助解释为什么是手册/范文/模板。
- 给出建议 workflow。
- 不能直接覆盖显式占位符事实。

### 6.5 `StructureSuggestionService`

职责：

- 对 `DocumentStructureProfile.nodes` 生成角色建议。
- 根据 `documentKind` 选择不同规则集。
- 给出置信度和理由。

规则集：

```text
OfficialDocumentRules
ManualDocumentRules
TemplateDocumentRules
GenericWordRules
```

对公文文档：

- 居中、大字号、短文本、靠前：可能是 `TITLE`。
- 标题后第一个以冒号结尾的短行：可能是 `RECIPIENT`。
- 首行缩进、仿宋、三号、两端对齐：可能是 `BODY`。
- `附件：` 开头：`ATTACHMENT_NOTE`。
- 右对齐短行，下一行是中文日期：`SIGNATURE`。
- 中文日期右对齐：`DATE`。
- 页脚中页码域：`PAGE_NUMBER`。
- 页眉中红色大字号或图片：`RED_HEADER` 或 `ISSUING_ORGAN`。

对手册/制度文档：

- 封面中连续单字居中大字号可以合并为 `COVER_TITLE`。
- `第X部分`：`PART_TITLE`。
- `一、`：`CHAPTER_TITLE` 或 `SECTION_TITLE`，依据上下文层级判断。
- `（一）`：`SECTION_TITLE`。
- `1.`：`ITEM_HEADING` 或 `BODY_TEXT`，依据长度和后续正文判断。
- 普通长段落：`BODY_TEXT`。
- 表格：`TABLE`。

### 6.6 `StructureMappingService`

职责：

- 创建初始映射草稿：建议角色 + 用户未确认状态。
- 保存用户确认结果。
- 支持局部更新。
- 支持重新解析后映射迁移。
- 提供给质检和导出稳定读取接口。

映射状态：

```text
SUGGESTED
CONFIRMED
REJECTED
IGNORED
STALE
NEEDS_REVIEW
```

重新解析迁移策略：

- 同 nodeKey 命中：直接继承。
- nodeKey 失效但 textHash + 位置邻近命中：继承并标记 `NEEDS_REVIEW`。
- 文本变化较大：不继承，标记需要重新确认。

### 6.7 `DocumentRenderPreviewService`

职责：

- 生成真实 DOCX 预览。
- 保存页面图片或 PDF。
- 前端按页加载。
- 记录渲染失败原因。

渲染实现选项：

第一期推荐：

```text
LibreOffice headless -> PDF -> PNG pages
```

原因：

- 比 POI 自己还原视觉更可靠。
- 能覆盖页眉页脚、表格、分页、字体替换等大部分真实效果。
- 可用于人工标注时对照原貌。

注意：

- 私有化部署需要明确 LibreOffice 依赖。
- Windows 本地和 Docker/Linux 生产环境路径不同。
- 字体缺失会导致预览差异，必须有字体审计。
- 渲染任务应异步执行，避免上传接口长时间阻塞。

渲染状态：

```text
PENDING
RENDERING
READY
FAILED
UNSUPPORTED
```

## 7. API 设计

第一期建议新增 API，避免污染现有模板上传接口。

### 7.1 获取结构总览

```http
GET /api/templates/versions/{versionId}/document-structure
```

返回：

```json
{
  "success": true,
  "data": {
    "templateVersionId": 12,
    "structureProfileId": 31,
    "schemaVersion": 1,
    "documentKind": {
      "kind": "MANUAL_OR_GUIDE",
      "confidence": 0.95,
      "recommendedWorkflow": "VIEW_AS_REFERENCE_ONLY",
      "message": "该文件为公文写作使用手册，不建议作为套版模板。"
    },
    "stats": {
      "nodeCount": 535,
      "paragraphCount": 423,
      "tableCount": 12,
      "riskCount": 4
    },
    "nodes": [],
    "suggestions": [],
    "mapping": {
      "mappingProfileId": 18,
      "versionNo": 1,
      "status": "DRAFT"
    },
    "risks": []
  }
}
```

### 7.2 获取渲染预览

```http
GET /api/templates/versions/{versionId}/render-preview
```

返回：

```json
{
  "success": true,
  "data": {
    "status": "READY",
    "pageCount": 8,
    "pages": [
      {
        "pageIndex": 0,
        "imageUrl": "/api/templates/versions/12/render-preview/pages/0",
        "width": 1240,
        "height": 1754
      }
    ],
    "errorCode": null,
    "errorMessage": null
  }
}
```

### 7.3 下载单页预览图

```http
GET /api/templates/versions/{versionId}/render-preview/pages/{pageIndex}
```

权限：

- 系统管理员可访问所有。
- 模板管理员可访问有权限的模板版本。
- 普通起草人只能访问自己可用模板的预览，后续权限收口。

### 7.4 重新解析

```http
POST /api/templates/versions/{versionId}/document-structure/reparse
```

用途：

- 修改解析器后重算。
- 字体包变化后重算。
- 用户手动触发。

### 7.5 保存映射

```http
PUT /api/templates/versions/{versionId}/structure-mapping
```

请求：

```json
{
  "baseMappingProfileId": 18,
  "items": [
    {
      "nodeKey": "body-p-20-a8f3d2",
      "role": "BODY_HEADING_LEVEL_1",
      "slotKey": "body.heading.1",
      "status": "CONFIRMED",
      "notes": "一级正文标题"
    }
  ]
}
```

返回：

```json
{
  "success": true,
  "data": {
    "mappingProfileId": 19,
    "versionNo": 2,
    "status": "DRAFT",
    "confirmedCount": 12,
    "needsReviewCount": 0
  }
}
```

### 7.6 发布映射

```http
POST /api/templates/versions/{versionId}/structure-mapping/publish
```

发布后：

- 工作台绑定该模板版本时优先读取已发布映射。
- 质检和导出消费已发布映射。
- 草稿已经绑定旧映射时，后续是否自动升级要按版本策略处理。

## 8. 前端交互设计

### 8.1 入口位置

模板解析结果弹窗升级为“文档解析工作台”。

现有弹窗可以继续作为轻量入口，但复杂文件应进入独立视图或大弹窗：

```text
模板管理
  -> 模板卡片
  -> 查看解析
  -> 文档解析工作台
```

### 8.2 页面布局

第一期推荐三栏：

```text
左栏：页面缩略图 / 结构树 / 风险筛选
中间：DOCX 原貌预览
右栏：节点详情 / 角色建议 / 映射编辑
```

保持项目 Anthropic-inspired 风格，避免做成炫技式编辑器。

#### 左栏

提供：

- 文档类型和置信度。
- 页面缩略图。
- 结构树。
- 筛选：
  - 全部
  - 未确认
  - 已确认
  - 风险
  - 公文槽位
  - 手册章节
  - 表格
  - 页眉页脚

结构树示例：

```text
封面
  公文使用手册
  办公室
  2024年10月
第一部分 公文写作基础知识
  一、公文类别
  二、公文写作规范
    （一）用语标准
      1.规范严谨
      正文说明...
```

#### 中间

提供：

- 按页展示预览图。
- 缩放。
- 页码跳转。
- 选中结构节点时，滚动到对应页。
- 如果第一期无法精确高亮原图位置，可先在旁边结构树选中；第二期再做坐标高亮。

坐标高亮的后续方案：

- LibreOffice 渲染 PNG 只能给视觉结果，不直接给段落坐标。
- 可用 docx4j/LibreOffice PDF text layer/PDFBox 获取文本坐标。
- 第一阶段不强依赖坐标，先完成预览 + 结构面板。

#### 右栏

选中节点后显示：

- 原文预览。
- 当前建议角色。
- 置信度。
- 理由。
- 格式摘要：
  - 中文字体
  - 西文字体
  - 字号
  - 对齐
  - 首行缩进
  - 行距
  - 段前段后
  - 编号层级
- 位置摘要：
  - 第几页
  - 正文/表格/页眉/页脚
  - 段落 index
- 操作：
  - 标记为标题
  - 标记为主送
  - 标记为正文
  - 标记为附件
  - 标记为落款
  - 标记为日期
  - 标记为章节标题
  - 忽略
  - 清除确认

### 8.3 文件类型差异化展示

#### 占位符模板

主提示：

```text
已识别显式占位符，可进入套版配置。
```

展示重点：

- 占位符列表。
- 占位符所在节点。
- 是否跨 run。
- 是否缺少常用字段。
- 生效格式。

#### 样式模板

主提示：

```text
该文件像样式模板，需要配置字段映射后用于套版。
```

展示重点：

- 候选标题/正文/落款/日期样式。
- 用户确认映射。
- 缺少槽位提示。

#### 范文/真实公文

主提示：

```text
该文件像完整公文，可提取结构与格式作为模板参考。
```

展示重点：

- 自动建议公文槽位。
- 允许用户确认后转换为模板映射。
- 原文内容是否作为默认样例保留。

#### 手册/制度/普通 Word

主提示：

```text
该文件不是套版模板。可以作为参考资料或知识材料，不建议用于自动套版。
```

展示重点：

- 文档大纲。
- 章节结构。
- 风险说明。
- 不显示“字段映射必填”压力。
- 提供“转为知识材料/仅保存参考”而不是“进入模板配置”。

### 8.4 空、错、加载状态

必须覆盖：

- 结构解析中。
- 渲染预览生成中。
- 渲染失败。
- 文件类型待确认。
- 结构建议失败但事实层可用。
- AI 不可用时只显示规则建议。
- 字体缺失导致预览可能不同。
- 映射有未确认风险。
- 用户无权限编辑映射。

### 8.5 起草人工作台三栏职责

解析工作台面向模板管理员，起草人工作台面向日常写作。两者应共享同一套结构合同，但交互目标不同：

- 模板管理员工作台解决“这个 Word 文件是什么、结构如何映射、能不能套版”。
- 起草人工作台解决“基于已确认结构，如何把这份公文写完整、改规范、检查并导出”。

起草人工作台三栏推荐职责：

```text
左栏：文种 / 模板 / 字段 / 结构树 / 材料
中间：Word 风格结构化编辑与预览
右栏：AI 建议 / 质检 / 导出 / 当前节点操作
```

左栏不应只是字段表单，应逐步升级为“结构与输入事实中心”：

- 文种：通知、请示、报告等。
- 模板：当前绑定模板版本、映射状态、风险状态。
- 基础字段：标题、主送、附件、落款、日期等。
- 正文结构：一级标题、二级标题、正文段落、附件说明。
- 材料：已上传材料、解析状态、是否可用于 AI。

正文结构树应展示节点状态，例如：

```text
标题                         USER_FILLED
主送                         USER_FILLED
一、工作背景                  AI_GENERATED
  正文段落 1                  USER_MODIFIED_AFTER_AI
二、重点任务                  EMPTY
附件说明                      QUALITY_WARNING
落款                         USER_FILLED
日期                         USER_FILLED
```

中间区域负责让用户编辑“结构化内容”，而不是暴露全部 Word 底层排版细节：

- 一个结构节点对应一个可编辑块或一组可编辑块。
- 节点被选中后，中间预览、左侧结构树、右侧 AI 操作同步高亮。
- 用户在中间编辑正文，只改变该节点内容，不改变节点角色。
- 如果用户需要拆分或合并节点，应走明确操作，而不是靠回车、删除等文本行为隐式改变结构。
- 排版优先来自模板映射和 effective formatting，用户不需要在起草阶段手动调整每段字体行距。

右栏保留现有 AI、质检、导出功能，但要按选中上下文变化：

| 上下文 | 右栏主能力 |
| --- | --- |
| 未选节点 | 生成提纲、全局补全、运行质检、导出 |
| 标题节点 | 标题正式化、标题压缩、标题风险检查 |
| 主送节点 | 单位名称检查、主送格式检查 |
| 正文标题节点 | 小标题优化、层级一致性检查 |
| 正文段落节点 | 正式化、压缩、扩写、改写、补充依据 |
| 附件节点 | 附件序号检查、附件说明补全 |
| 落款/日期节点 | 落款日期一致性检查、格式检查 |

AI 结果的应用方式建议分为三档：

```text
建议：只显示在右栏，不修改正文。
替换当前节点：用户明确点击采纳后，仅替换当前节点。
跨节点改写：必须弹出影响范围确认，展示将改动哪些节点。
```

这条规则尤其重要：AI 可以帮助扩展内容，但不能把已确认结构打散。结构是严肃公文生产的主干，AI 是围绕主干工作的辅助能力。

## 9. 与现有模块的关系

### 9.1 与 `TemplateProfile`

短期：

- 保留现有 `TemplateProfile`。
- 新增 `DocumentStructureProfile`。
- `TemplateProfile.structures` 继续用于兼容旧前端和现有导出。
- 上传后同时生成 `TemplateProfile` 和 `DocumentStructureProfile`。

中期：

- `TemplateProfile.structures` 改为由 `DocumentStructureProfile + StructureMapping` 派生。
- 旧 `TemplateProfileParser` 只作为兼容层或迁移入口。

长期：

- 结构事实统一由 `DocumentStructureProfile` 提供。
- `TemplateProfile` 只保留模板业务摘要：占位符、映射、能力矩阵、导出合同。

### 9.2 与工作台 `WorkbenchNode`

当前 `WorkbenchNode` 是前端派生层。

新设计中：

- `WorkbenchNode` 应逐步绑定 `DocumentNode.nodeKey` 和 `StructureMappingItem`。
- 工作台不再从旧草稿块和模板结构临时推断所有节点。
- 草稿节点持久化时，应保存 `templateNodeKey`、`role`、`slotKey`、`content`。
- 草稿节点应保存内容来源和状态，例如用户填写、AI 生成、AI 后人工修改、质检警告、导出阻断。
- 前端左侧结构树、中间预览选区、右侧 AI 操作应共享同一个 `selectedWorkbenchNodeId`，避免三个区域状态割裂。

建议新增或演进为后端持久化节点合同：

```json
{
  "id": "draft-node-1001",
  "draftId": "draft-1",
  "templateNodeKey": "body-p-42",
  "slotKey": "body.section.1.paragraph.1",
  "role": "BODY",
  "title": "一、工作背景",
  "content": "为深入贯彻落实...",
  "contentSource": "AI_GENERATED",
  "reviewStatus": "NEEDS_REVIEW",
  "qualityStatus": "WARNING",
  "sortOrder": 100,
  "version": 3
}
```

### 9.3 与 AI 生成

AI 输入不应直接使用完整 Word 原文。

应使用：

- 已确认结构映射摘要。
- 文档类型。
- 必要格式约束。
- READY 材料摘要。
- 用户字段。

AI 不应重新决定哪个节点是标题，除非用户请求“重新建议映射”。

AI 输出也应回写到明确节点：

- 全局提纲生成：生成或更新正文结构节点，但不覆盖用户已确认的标题、主送、附件、落款和日期。
- 单段正文生成：只写入目标正文节点。
- 局部段落操作：只返回目标节点建议文本，用户采纳后替换目标节点内容。
- 跨节点补全：必须返回影响范围摘要，前端展示确认后再批量写入。
- 质检建议：绑定到节点或全局，不直接改正文。

### 9.4 与基础质检

质检优先级：

```text
已发布人工映射
  > 已确认但未发布映射
  > 系统建议
  > 旧 TemplateProfile fallback
```

质检新增项：

- 模板未完成映射确认。
- 必要槽位缺失。
- 节点已确认但原结构包含复杂风险。
- 渲染预览失败。
- 字体缺失可能影响导出。
- 文件类型不适合作为套版模板。

### 9.5 与导出

导出规则：

- 只有 `PLACEHOLDER_TEMPLATE` 和映射确认后的 `STYLE_TEMPLATE/REFERENCE_DOCUMENT/OFFICIAL_DOCUMENT` 可以进入自动导出。
- `MANUAL_OR_GUIDE/ORDINARY_DOCUMENT` 默认阻断套版导出。
- 如果用户强行把普通文件转为模板，必须完成必要槽位映射并确认风险。

导出记录新增追溯字段：

- `structure_profile_id`
- `structure_mapping_profile_id`
- `render_preview_id` 可选

## 10. 风险检测

### 10.1 结构风险

风险代码建议：

```text
TEXT_BOX_DETECTED
FLOATING_SHAPE_DETECTED
PICTURE_ANCHOR_DETECTED
TRACKED_CHANGES_DETECTED
COMMENTS_DETECTED
FIELD_CODE_DETECTED
TOC_DETECTED
COMPLEX_TABLE_DETECTED
MERGED_CELLS_DETECTED
NESTED_TABLE_DETECTED
MULTI_SECTION_DETECTED
HEADER_FOOTER_CONTENT_DETECTED
MISSING_FONT_DETECTED
UNKNOWN_NUMBERING_FORMAT
PLACEHOLDER_SPLIT_ACROSS_RUNS
MACRO_OR_EMBEDDED_OBJECT_DETECTED
```

### 10.2 风险等级

```text
INFO：已检测，可正常展示。
WARNING：可展示但导出复现可能有差异。
ERROR：会影响套版导出，需人工处理。
BLOCKING：当前不允许自动套版。
```

### 10.3 风险展示

前端展示原则：

- 不吓人，但要明确。
- 风险绑定具体节点。
- 提供处理建议。

示例：

```text
检测到文本框内容。当前可在预览中查看，但不能自动替换。若该文本框是红头或印章，请在映射面板中标记为“保留”。
```

## 11. 渲染预览方案

### 11.1 技术选型

第一期推荐：

```text
LibreOffice headless
  -> PDF
  -> PNG pages
```

备选：

- docx4j 导 PDF：Java 集成更自然，但中文字体和复杂版式可能更麻烦。
- OnlyOffice/Collabora：兼容性更强，但部署复杂，不进入第一期。
- 前端 docx-preview：轻量，但与真实 Word 差异较大，不能作为“真实原貌”。

### 11.2 存储目录

新增配置：

```text
GONGWEN_DOCX_PREVIEW_STORAGE_DIR=storage/docx-previews
GONGWEN_DOCX_PREVIEW_RENDERER=libreoffice
GONGWEN_LIBREOFFICE_PATH=
GONGWEN_DOCX_PREVIEW_DPI=150
GONGWEN_DOCX_PREVIEW_TIMEOUT_SECONDS=60
```

### 11.3 异步任务

渲染可能耗时，应异步。

上传流程：

```text
上传模板版本
  -> 保存原文件
  -> 同步执行轻量事实提取
  -> 创建渲染任务 PENDING
  -> 返回版本 READY 或 PARSING
  -> 后台生成预览
```

前端：

- 如果预览还未完成，显示生成中。
- 如果失败，仍可展示结构树。

### 11.4 字体管理

真实预览依赖字体。

必须支持：

- 检测 DOCX 使用字体。
- 检测运行环境是否安装这些字体。
- 字体缺失时记录风险。
- 后续部署文档提供字体安装说明。

第一期可先检测常见中文字体：

- 方正小标宋简体
- 方正黑体
- 方正楷体
- 方正仿宋
- 宋体
- 黑体
- 仿宋
- 楷体
- Times New Roman

### 11.5 现成 Word 引擎边界

现成 Word/DOCX 能力大致分为三类，不能混为一谈：

```text
渲染转换引擎：负责把 DOCX 渲染成 PDF/图片/HTML。
在线 Office 引擎：负责在浏览器里查看和编辑 DOCX。
结构处理库：负责读取、修改、生成 DOCX 结构。
```

第一期建议不要直接引入完整在线 Office 引擎作为主工作台，原因是：

- 完整在线 Office 会把产品心智带向“在线 Word”，用户可能绕过左侧结构和右侧 AI。
- 在线 Office 内部编辑结果不天然等于本项目的结构化节点，需要额外同步、锁定和冲突处理。
- 部署、鉴权、文件回调、并发编辑、版本保存和安全边界都会显著变复杂。
- 公文助手的核心价值不是让用户自由排版，而是让用户保留公文结构，并用 AI 稳定补全内容。

但完整在线 Office 可以作为后续“高级编辑/打开原文件”能力存在：

```text
主工作台：结构化编辑 + AI 增强 + 导出
高级编辑：OnlyOffice/Collabora 等完整编辑器，作为受控入口
```

推荐分层策略：

| 能力 | 第一期推荐 | 后续增强 |
| --- | --- | --- |
| 原貌预览 | LibreOffice headless 转 PDF/PNG | 增加坐标高亮和字体诊断 |
| 结构提取 | Apache POI/docx4j + 自研归一层 | 增加更多 OOXML 结构覆盖 |
| 手动编辑 | 自研结构节点编辑器 | 增加拆分、合并、移动、锁定、批量映射 |
| 完整 Word 编辑 | 不进入主链路 | 作为高级编辑入口接 OnlyOffice/Collabora |
| 商业高保真转换 | 暂不依赖 | 可评估 Aspose.Words/GroupDocs 作为企业版增强 |

手动编辑的重点不是“任意编辑 Word 排版”，而是给结构节点提供灵活但受控的编辑：

- 编辑节点文本。
- 新增正文节点。
- 删除空节点。
- 拆分正文段落。
- 合并相邻正文段落。
- 调整正文节点顺序。
- 修改节点角色。
- 标记节点为忽略、保留、替换。
- 锁定标题、主送、落款、日期等关键槽位。
- 查看 AI 建议与人工修改历史。

这类编辑可以完全纳入本项目的数据模型和权限体系，适合与 AI、质检、导出联动。完整 Word 编辑器则适合作为“兜底修版”工具，不适合作为主流程。

## 12. 样本库与测试

### 12.1 样本库分类

必须建立 `backend/src/test/resources/docx-fixtures/` 或 `docs/fixtures/docx/` 样本体系。

建议分类：

```text
placeholder-template/
style-template/
reference-document/
official-document/
manual-guide/
policy-regulation/
ordinary-document/
edge-cases/
```

### 12.2 必备样本

第一批至少包含：

- 标准占位符通知模板。
- 标准占位符请示模板。
- 标准占位符报告模板。
- 无占位符通知范文。
- 无占位符请示范文。
- 带红头/文号/签发人的上行文。
- 带附件说明的公文。
- 带版记/抄送/印发机关的公文。
- 带表格附件的公文。
- 多 section 公文。
- WPS 生成公文。
- 手工直接格式化公文。
- 公文使用手册。
- 企业制度文件。
- 普通 Word 文件。
- 含文本框/图片/页眉页脚复杂结构文件。

### 12.3 Golden JSON

每个样本配套期望结果：

```text
expected-structure.json
expected-kind.json
expected-suggestions.json
expected-risks.json
```

测试不要求每个节点都完美，但必须验证关键槽位：

- 文件类型正确。
- 关键结构节点存在。
- 中文字体不被误显示为 Times New Roman。
- 常见标题层级识别正确。
- 手册不进入套版模板。
- 复杂结构风险被发现。

### 12.4 测试分层

后端测试：

- `DocumentStructureExtractorTest`
- `EffectiveFormattingResolverTest`
- `NumberingResolverTest`
- `DocumentKindClassifierTest`
- `StructureSuggestionServiceTest`
- `StructureMappingServiceTest`
- `DocumentRenderPreviewServiceTest`

前端测试：

- 文档类型提示。
- 结构树展示。
- 节点选择和详情。
- 映射保存。
- 风险状态。
- 渲染失败 fallback。

端到端验证：

- 上传模板。
- 等待结构解析和预览生成。
- 打开解析工作台。
- 修改映射。
- 发布映射。
- 工作台绑定模板。
- 运行质检。
- 导出 Word。

## 13. 权限与安全

### 13.1 权限

规则：

- 系统管理员可查看、重新解析、编辑、发布所有映射。
- 模板管理员可管理自己有权限的模板版本。
- 起草人只可读取可用模板的已发布结构和预览，不可编辑映射。
- 普通用户不能下载原始模板文件，除非已有模板下载权限。
- 预览图片访问需要鉴权，不能暴露静态目录。

### 13.2 文件安全

- 只接受 `.docx`。
- 不执行宏。
- 不打开外部链接。
- 渲染进程要设置超时。
- 渲染目录隔离。
- 文件名规范化，不能直接使用用户文件名作为路径。
- 预览图片和 PDF 不提交仓库。

### 13.3 AI 安全

- AI 只接收必要文本样本和结构摘要。
- 不发送完整敏感正文，除非后续用户明确启用云模型处理完整文件。
- AI 结果必须落 `source=AI` 和置信度。
- AI 不得自动发布映射。

## 14. 迁移路线

### 14.1 第一期：事实层与预览

范围：

- 新增 `DocumentStructureProfile`。
- 修中文字体 `eastAsia` 优先。
- 提取 style inheritance 基础能力。
- 提取 numbering 基础能力。
- 新增文件类型识别结果。
- 新增渲染预览存储和 API。
- 前端展示文档类型、预览、结构树和风险。

不做：

- 复杂坐标高亮。
- 完整人工映射发布。
- 导出消费新映射。

验收：

- 公文使用手册识别为 `MANUAL_OR_GUIDE` 或 `ORDINARY_DOCUMENT`，不再误导为可套版模板。
- 中文字体显示为方正/仿宋/黑体等真实中文字体。
- 标准占位符模板仍可识别占位符。
- 预览图可加载。

### 14.2 第二期：人工映射

范围：

- 新增 `structure_mapping_profile`。
- 前端支持节点角色编辑。
- 支持保存、发布、审计。
- 质检读取发布映射。

验收：

- 用户可把某段标记为标题/正文/落款/日期。
- 保存后刷新不丢。
- 发布后工作台能读取。
- 权限不足时不可编辑。

### 14.3 第三期：导出与 AI 消费

范围：

- 导出服务消费结构映射。
- AI 生成按结构节点写入。
- 质检基于节点角色。
- 导出记录绑定 mapping version。

验收：

- 映射确认后的无占位符范文可稳定导出。
- 手册/普通 Word 默认阻断套版导出。
- 导出失败能定位到具体节点和风险。

### 14.4 第四期：坐标高亮与复杂结构

范围：

- 从 PDF text layer 或其他渲染中获得节点页码和坐标。
- 预览中高亮选中节点。
- 文本框、页眉页脚、表格复杂结构增强。

验收：

- 点击结构树，中间预览定位并高亮。
- 表格单元格节点可标注。
- 页眉页脚节点可标注保留/替换。

## 15. 与现有阶段的衔接

### P10B

当前 P10B 的结构维度闭环应升级为：

```text
TemplateProfile.structures
  -> DocumentStructureProfile.nodes
  -> StructureMappingProfile
  -> EffectiveFormattingContext
```

### P10C

当前 `WorkbenchNode` 前端派生层应在第二期后绑定后端节点：

```text
WorkbenchNode.templateNodeKey = DocumentNode.nodeKey
WorkbenchNode.role = StructureMappingItem.role
```

### P11

导出体验应新增：

- 映射未确认阻断。
- 文件类型不适合导出阻断。
- 复杂节点风险阻断或警告。
- 导出记录追溯 mapping version。

### P12

部署文档需新增：

- LibreOffice 安装。
- 中文字体安装。
- 预览存储目录。
- 渲染超时配置。

## 16. 验收标准

### 16.1 功能验收

- 上传标准占位符模板，能识别占位符和结构。
- 上传无占位符范文，能建议公文槽位。
- 上传公文手册，能识别为手册/普通文档，不进入套版配置。
- 上传复杂 Word，能显示预览并列出风险。
- 模板管理员能保存结构映射。
- 发布映射后，工作台和质检能读取。

### 16.2 稳定性验收

- 同一文件重复上传，关键节点 key 稳定。
- 同一文件重复解析，文件类型结果稳定。
- 中文字体不误报为 Times New Roman。
- “标题/正文/附件”等词出现在说明文字中时，不误判为套版槽位。
- 手册章节标题能进入手册结构，而不是公文槽位。

### 16.3 UI 验收

- 预览加载中、失败、成功状态完整。
- 结构树可滚动、可筛选。
- 节点详情文字不溢出。
- 风险提示清晰。
- 权限不足有明确状态。
- 桌面优先，窄屏至少可用。

### 16.4 性能验收

- 20MB 以内 DOCX 上传后，事实提取在可接受时间内完成。
- 渲染任务异步，不阻塞上传接口。
- 预览图片分页加载，不一次性拉取所有大图。
- 结构树大于 500 节点时仍可操作。

## 17. 开放问题

需要评审后确认：

- 预览第一期是否必须生成 PNG，还是 PDF 内嵌预览即可。
- LibreOffice 是否作为 P12 前置依赖，还是仅在本地开发环境启用。
- 手册/制度类文档后续是否进入知识库，而不是模板库。
- 是否新增独立“文档材料/知识材料库”，避免模板管理承载普通 Word。
- 人工映射是否允许起草人参与，还是仅模板管理员可编辑。
- 已绑定旧模板的草稿在映射发布后是否自动升级。

## 18. 推荐实施顺序

推荐顺序：

1. 修中文字体和 style inheritance 基础问题。
2. 新增 `DocumentStructureProfile` 和后端 extractor。
3. 新增文件类型识别结果，并让前端按类型显示不同提示。
4. 新增渲染预览异步任务和预览 API。
5. 前端新增解析工作台第一版。
6. 新增人工映射保存和发布。
7. 质检和导出消费发布映射。
8. 建立样本库和回归测试，持续增加真实文件。

不推荐：

- 直接重写现有导出服务。
- 一开始接入完整在线 Word 编辑器。
- 继续在 `TemplateProfileParser.inferStructureType` 中堆关键词。
- 让 AI 直接返回最终映射并自动发布。

## 19. 成功判断

这个设计完成后，系统面对真实 Word 文件时应有三种稳定结果：

1. **能自动套版**：标准模板或确认映射后的范文。
2. **能参考但需确认**：样式模板、范文、真实公文。
3. **不能套版但能解释**：手册、制度、普通 Word、复杂风险文件。

成功不是“所有文件都自动猜对”，而是：

```text
可识别的自动处理
不确定的请求确认
不适合的明确阻断
复杂的显式提示风险
```

这比当前“直接猜标题/正文/落款”的方式稳定得多，也更符合严肃公文生产场景。
