import { useState } from 'react';
import type { ChangeEvent, ReactNode } from 'react';
import { AlertCircle, CheckCircle2, ChevronDown, FileText, Upload } from 'lucide-react';
import type { DocumentType, DraftDetail, Material, TemplateVersionSummary } from '../../draftTypes';
import { SelectField, TextField } from '../ui';

type WorkbenchContextPanelProps = {
  attachment: string;
  blockCount: number;
  bodySectionCount: number;
  currentDocumentTypeCode: string;
  currentDocumentTypeName: string;
  date: string;
  documentTypes: DocumentType[];
  draft: DraftDetail | null;
  latestTemplateVersions: TemplateVersionSummary[];
  materialStatus: string;
  materials: Material[];
  nodeCount: number;
  onDocumentTypeChange: (documentTypeCode: string) => void;
  onFieldChange: (blockType: string, value: string) => void;
  onMaterialUpload: (event: ChangeEvent<HTMLInputElement>) => void;
  onTemplateVersionChange: (templateVersionId: number | null) => void;
  recipient: string;
  signature: string;
  status: string;
  structureTree: ReactNode;
  title: string;
};

export function WorkbenchContextPanel({
  attachment,
  blockCount,
  bodySectionCount,
  currentDocumentTypeCode,
  currentDocumentTypeName,
  date,
  documentTypes,
  draft,
  latestTemplateVersions,
  materialStatus,
  materials,
  nodeCount,
  onDocumentTypeChange,
  onFieldChange,
  onMaterialUpload,
  onTemplateVersionChange,
  recipient,
  signature,
  status,
  structureTree,
  title,
}: WorkbenchContextPanelProps) {
  const [openSections, setOpenSections] = useState({
    basics: true,
    materials: false,
  });
  const selectedTemplateName = latestTemplateVersions
    .find((template) => draft?.templateVersionId === template.templateVersionId);

  function setSectionOpen(section: keyof typeof openSections, open: boolean) {
    setOpenSections((current) => ({ ...current, [section]: open }));
  }

  return (
    <section className="panel workbench-context-panel" aria-label="起草信息">
      <div className="workbench-side-header">
        <div>
          <p className="panel-kicker">草稿上下文</p>
          <h2 className="panel-title">文种、模板与结构</h2>
        </div>
        <span className={`workbench-side-state ${draft ? 'success' : 'warning'}`}>
          {draft ? `#${draft.id}` : '未载入'}
        </span>
      </div>

      <div className="workbench-side-scroll">
        <div className="workbench-context-summary" aria-label="草稿摘要">
          <span>
            <strong>{currentDocumentTypeName}</strong>
            <small>当前文种</small>
          </span>
          <span>
            <strong>{selectedTemplateName ? `v${selectedTemplateName.versionNo}` : '未选'}</strong>
            <small>{selectedTemplateName?.templateName ?? '套版模板'}</small>
          </span>
          <span>
            <strong>{nodeCount}</strong>
            <small>结构节点</small>
          </span>
          <span>
            <strong>{materials.length}</strong>
            <small>参考材料</small>
          </span>
        </div>

        <details
          className="workbench-disclosure"
          onToggle={(event) => setSectionOpen('basics', event.currentTarget.open)}
          open={openSections.basics}
        >
          <summary className="workbench-disclosure-summary">
            <span>
              <span className="workbench-disclosure-title">基础要素</span>
              <span className="workbench-disclosure-meta">文种、模板、字段与结构导航</span>
            </span>
            <ChevronDown aria-hidden="true" size={16} />
          </summary>
          <div className="workbench-disclosure-body">
            <SelectField
              disabled={documentTypes.length === 0 || status === 'loading'}
              hint="切换后进入该文种当前草稿；若暂无草稿，则返回对应目录。"
              label="文种"
              onChange={(event) => onDocumentTypeChange(event.target.value)}
              value={currentDocumentTypeCode}
            >
              {documentTypes.length === 0 ? <option value="NOTICE">通知</option> : documentTypes.map((type) => (
                <option key={type.code} value={type.code}>{type.name}</option>
              ))}
            </SelectField>

            <SelectField
              disabled={!draft || latestTemplateVersions.length === 0}
              hint={latestTemplateVersions.length === 0 ? '暂无已解析模板，先通过模板管理上传版本。' : '仅展示每个模板的最新版本。'}
              label="套版模板"
              onChange={(event) => onTemplateVersionChange(event.target.value ? Number(event.target.value) : null)}
              value={draft?.templateVersionId ? String(draft.templateVersionId) : ''}
            >
              <option value="">未选择模板</option>
              {latestTemplateVersions.map((template) => (
                <option key={template.templateVersionId} value={template.templateVersionId}>
                  {template.templateName} v{template.versionNo}
                </option>
              ))}
            </SelectField>

            <div className="workbench-context-field-grid">
              <TextField label="标题" onChange={(event) => onFieldChange('TITLE', event.target.value)} value={title} />
              <TextField label="主送" onChange={(event) => onFieldChange('RECIPIENT', event.target.value)} value={recipient} />
              <TextField label="附件" onChange={(event) => onFieldChange('ATTACHMENT', event.target.value)} value={attachment} />
              <TextField label="落款" onChange={(event) => onFieldChange('SIGNATURE', event.target.value)} value={signature} />
              <TextField label="日期" onChange={(event) => onFieldChange('DATE', event.target.value)} value={date} />
            </div>

            <div className="workbench-context-structure">
              <div className="paragraph-index-header">
                <span className="field-label">结构导航</span>
                <span className="paragraph-count">{bodySectionCount} 正文 · {blockCount} 兼容块</span>
              </div>
              {structureTree}
            </div>
          </div>
        </details>

        <details
          className="workbench-disclosure"
          onToggle={(event) => setSectionOpen('materials', event.currentTarget.open)}
          open={openSections.materials || materialStatus === 'uploading'}
        >
          <summary className="workbench-disclosure-summary">
            <span>
              <span className="workbench-disclosure-title">参考材料</span>
              <span className="workbench-disclosure-meta">{materials.length > 0 ? `${materials.length} 份材料可用于生成` : 'Word/PDF 上传与解析状态'}</span>
            </span>
            <ChevronDown aria-hidden="true" size={16} />
          </summary>
          <div className="workbench-disclosure-body">
            <div className="material-upload">
              <input
                accept=".docx,.pdf,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                aria-label="上传材料文件"
                className="visually-hidden"
                disabled={!draft || materialStatus === 'uploading'}
                id="material-upload"
                onChange={onMaterialUpload}
                type="file"
              />
              <label
                aria-disabled={!draft || materialStatus === 'uploading'}
                className="ui-button ui-button-secondary upload-label"
                htmlFor="material-upload"
              >
                <Upload aria-hidden="true" />
                {materialStatus === 'uploading' ? '正在上传材料' : '上传 Word/PDF 材料'}
              </label>
            </div>

            <div className="material-list" aria-label="材料列表">
              {materials.length === 0 ? (
                <p className="empty-note">
                  {materialStatus === 'loading' ? '正在加载材料' : '尚未上传材料'}
                </p>
              ) : materials.map((material) => (
                <div className="material-item" key={material.id}>
                  <FileText aria-hidden="true" className="material-icon" />
                  <div className="material-copy">
                    <div className="material-name">{material.originalFileName}</div>
                    <div className="material-meta">
                      {material.fileExtension.toUpperCase()} · {formatFileSize(material.fileSizeBytes)} · {material.status === 'READY' ? `提取 ${material.extractedTextLength} 字` : material.errorMessage}
                    </div>
                  </div>
                  <span className={`status-chip ${material.status === 'READY' ? 'success' : 'danger'}`}>
                    {material.status === 'READY' ? (
                      <CheckCircle2 aria-hidden="true" className="status-icon" />
                    ) : (
                      <AlertCircle aria-hidden="true" className="status-icon" />
                    )}
                    {material.status === 'READY' ? '已就绪' : '失败'}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </details>
      </div>
    </section>
  );
}

function formatFileSize(size: number) {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
