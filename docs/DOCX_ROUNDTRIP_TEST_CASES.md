# DOCX 结构闭环测试用例

本文件用于验证 DOCX 原稿、工作台结构化编辑预览、导出 Word 三条链路是否闭合。

## 测试文件

- `docs/examples/公文助手_讲话稿范文_示例.docx`
- `docs/examples/公文助手_占位符通知模板_示例.docx`

## 必须保持的链路合同

后续 Agent 修改 DOCX 结构、映射、草稿节点、工作台预览或导出时，必须保持以下合同：

- `DocumentStructureProfile` 是原稿事实层，必须尽量保留 DOCX 中所有可用事实节点，包括正文段落、表格单元格段落、页眉和页脚。
- `StructureMappingProfile` 是语义覆盖层，`UNKNOWN` 不是丢弃含义，只表示需要人工确认。
- `IGNORE` 才表示明确不进入草稿节点，并在原位导出时清空对应原始节点文本。
- `DraftNode` 初始化和重建必须以源节点文本为主，不得把旧 `BODY_PARAGRAPH` 复制到多个正文节点。
- 未编辑草稿导出无占位符参考文档时，必须走 `ORIGINAL_NODE_REPLACEMENT`，不能退回清空重组的 `GENERATED_SNAPSHOT`。
- 工作台中间区域是结构化编辑预览，应按持久化节点顺序展示可追溯内容；像素级排版验收以 LibreOffice 真预览或导出 DOCX 为准。
- 任何修改如果导致原稿、工作台结构化预览、导出 Word 三者在未编辑场景下内容节点不一致，视为阻断级回归。

## 自动化用例

### 用例 1：无编辑参考范文闭环

目标：验证无占位符讲话稿范文在未编辑时不丢节点、不乱序、不重组。

覆盖链路：

- 原稿 DOCX -> `DocumentStructureProfile`
- `DocumentStructureProfile` -> `DraftNode`
- `DraftNode` -> 原 DOCX 节点原位替换导出

断言：

- 每个非忽略源节点都生成一个 `DraftNode`。
- `DraftNode.templateNodeKey` 顺序与源结构节点顺序一致。
- `DraftNode.content` 与源节点文本一致。
- 导出策略为 `ORIGINAL_NODE_REPLACEMENT`。
- 导出 DOCX 的正文段落、表格文本、页眉和页脚文本与源 DOCX 完全一致。

自动化测试：

```powershell
cd backend
$env:JAVA_HOME='C:\Users\10172\.jdks\ms-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.integration.DocxCompleteStructurePipelineTest.noEditSpeechReferenceKeepsSourceNodesDraftNodesAndExportAligned"
```

### 用例 2：编辑单个节点后只替换对应节点

目标：验证用户只编辑标题和一段正文时，静态事实仍保留。

断言：

- 导出策略为 `ORIGINAL_NODE_REPLACEMENT`。
- 导出 DOCX 包含新标题和新正文。
- 副标题、表格、页眉、页脚仍保留原稿内容。
- 旧标题不再出现在导出 DOCX 中。

自动化测试：

```powershell
cd backend
$env:JAVA_HOME='C:\Users\10172\.jdks\ms-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.integration.DocxCompleteStructurePipelineTest.editedSpeechReferenceReplacesOnlyMappedEditableNodesAndKeepsStaticFacts"
```

### 用例 3：忽略节点只清空目标节点

目标：验证模板管理员把某个节点标为 `IGNORE` 后，不影响其他结构。

断言：

- 被忽略节点不会生成 `DraftNode`。
- 导出 DOCX 不再包含被忽略文本。
- 标题和未忽略正文仍保留。
- 导出 DOCX 的结构数量保持一致，被忽略位置为空而不是整篇重组。

自动化测试：

```powershell
cd backend
$env:JAVA_HOME='C:\Users\10172\.jdks\ms-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon --console=plain test --tests "com.gongwen.assistant.integration.DocxCompleteStructurePipelineTest.ignoredSpeechReferenceNodeIsSkippedFromDraftNodesAndClearedOnlyInExport"
```

### 用例 4：工作台结构化预览按节点顺序渲染

目标：验证中间纸面预览不再用固定“标题-主送-正文-落款-日期”顺序重组。

断言：

- 固定文本节点会出现在结构化编辑预览中。
- 标题、固定文本、日期、主送、正文按 `sortOrder` 顺序渲染。

自动化测试：

```powershell
cd frontend
npm test -- src/components/workbench/WorkbenchPreview.test.tsx
```

## 浏览器人工验收

1. 刷新 `http://localhost:5173/`，登录后进入模板管理。
2. 上传或选择 `公文助手_讲话稿范文_示例.docx`。
3. 在模板解析工作台确认并发布映射。
4. 进入绑定该模板的草稿工作台。
5. 点击左侧结构树的 `从原稿重建结构`。
6. 不做任何编辑，直接保存草稿并导出 Word。
7. 对比原稿、工作台结构化编辑预览、导出 Word：
   - 标题、副标题、日期、主送、正文、表格、页眉、页脚均应可追溯。
   - 未编辑的导出 Word 不应出现正文前置、标题后移、静态信息丢失。
   - 新导出记录应记录 `ORIGINAL_NODE_REPLACEMENT`。

注意：旧导出的 `.docx` 不会自动变新，需要在后端重启后重新导出。

## Agent 接力建议

适合后端 Agent 继续补强：

- 增加请示、报告、通知等真实样本 fixtures。
- 扩展 `DocumentSemanticSuggester` 的文种识别规则，但不得改变事实层节点。
- 补充图片、编号列表、跨页表格等 OOXML 风险节点。
- 增加导出后 DOCX XML 级别 diff 工具，标注被替换、被清空和保留节点。

适合前端 Agent 继续补强：

- 将 `WorkbenchPreview` 继续拆分为节点渲染子组件，减少单组件体积。
- 在结构树中增加“源节点/映射角色/草稿状态/导出动作”快速筛选。
- 在人工确认映射时增加“未确认但保留”的显式说明，避免用户以为 `UNKNOWN` 会丢失。
- 做登录后的浏览器 E2E：上传样例、发布映射、重建结构、导出、下载并回读。

适合 QA/文档 Agent 继续补强：

- 每新增一种文种样例，都在本文件新增一个“无编辑闭环”和“编辑单节点”验收用例。
- 每次修改 DOCX 导出或工作台预览，都至少运行本文自动化用例 1、2、4。
- 若本机没有 LibreOffice，应记录为视觉预览未验，不可把结构测试等同于像素级预览验收。
