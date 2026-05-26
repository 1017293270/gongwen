import { AlertCircle, CheckCircle2, FileDown, FileText, Save, Sparkles, Upload } from 'lucide-react';
import { ChangeEvent, useEffect, useMemo, useState } from 'react';
import {
  createDraft,
  generateDraftOutline,
  getDraft,
  listDocumentTypes,
  listDraftMaterials,
  saveDraftBlocks,
  uploadDraftMaterial,
} from './api';
import { ToastProvider, useToast } from './components/feedback/ToastProvider';
import type { AiOutline, DocumentType, DraftBlock, DraftBlockUpdate, DraftDetail, Material } from './draftTypes';

const DEFAULT_TITLE = '关于开展年度档案整理工作的通知';
const CURRENT_DRAFT_ID_KEY = 'gongwen.currentDraftId';

const BLOCK_SORT_ORDER: Record<string, number> = {
  TITLE: 10,
  RECIPIENT: 20,
  BODY_PARAGRAPH: 30,
  ATTACHMENT: 40,
  SIGNATURE: 50,
  DATE: 60,
};

type WorkbenchStatus = 'loading' | 'idle' | 'saving' | 'saved' | 'error';
type MaterialStatus = 'loading' | 'idle' | 'uploading' | 'error';
type OutlineStatus = 'idle' | 'generating' | 'success' | 'error';

export function App() {
  return (
    <ToastProvider>
      <Workbench />
    </ToastProvider>
  );
}

function Workbench() {
  const { showToast } = useToast();
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [draft, setDraft] = useState<DraftDetail | null>(null);
  const [blocks, setBlocks] = useState<DraftBlock[]>([]);
  const [materials, setMaterials] = useState<Material[]>([]);
  const [outline, setOutline] = useState<AiOutline | null>(null);
  const [outlineStatus, setOutlineStatus] = useState<OutlineStatus>('idle');
  const [outlineError, setOutlineError] = useState('');
  const [outlineInstruction, setOutlineInstruction] = useState('');
  const [status, setStatus] = useState<WorkbenchStatus>('loading');
  const [materialStatus, setMaterialStatus] = useState<MaterialStatus>('loading');
  const [statusMessage, setStatusMessage] = useState('正在加载草稿');

  useEffect(() => {
    let mounted = true;

    async function loadWorkbench() {
      try {
        setStatus('loading');
        setMaterialStatus('loading');
        setStatusMessage('正在加载草稿');
        const types = await listDocumentTypes();
        const loadedDraft = await loadCurrentDraft();
        const loadedMaterials = await listDraftMaterials(loadedDraft.id);
        if (!mounted) {
          return;
        }
        setDocumentTypes(types);
        setDraft(loadedDraft);
        setBlocks(loadedDraft.blocks);
        setMaterials(loadedMaterials);
        setStatus('idle');
        setMaterialStatus('idle');
        setStatusMessage('草稿已载入');
      } catch (error) {
        if (!mounted) {
          return;
        }
        setStatus('error');
        setMaterialStatus('error');
        setStatusMessage(error instanceof Error ? error.message : '草稿加载失败');
      }
    }

    void loadWorkbench();

    return () => {
      mounted = false;
    };
  }, []);

  async function loadCurrentDraft() {
    const storedDraftId = Number(window.localStorage.getItem(CURRENT_DRAFT_ID_KEY));
    if (Number.isInteger(storedDraftId) && storedDraftId > 0) {
      try {
        return await getDraft(storedDraftId);
      } catch {
        window.localStorage.removeItem(CURRENT_DRAFT_ID_KEY);
      }
    }

    const createdDraft = await createDraft('NOTICE', DEFAULT_TITLE);
    window.localStorage.setItem(CURRENT_DRAFT_ID_KEY, String(createdDraft.id));
    return createdDraft;
  }

  const blockValues = useMemo(() => {
    return blocks.reduce<Record<string, string>>((acc, block) => {
      acc[block.blockType] = block.content;
      return acc;
    }, {});
  }, [blocks]);

  const currentDocumentType = documentTypes.find((type) => type.code === draft?.documentTypeCode);
  const title = blockValues.TITLE ?? draft?.title ?? DEFAULT_TITLE;
  const recipient = blockValues.RECIPIENT ?? '';
  const body = blockValues.BODY_PARAGRAPH ?? '';
  const attachment = blockValues.ATTACHMENT ?? '';
  const signature = blockValues.SIGNATURE ?? '';
  const date = blockValues.DATE ?? '';

  function updateBlock(blockType: string, content: string) {
    setStatus('idle');
    setStatusMessage('草稿有未保存修改');
    setBlocks((currentBlocks) => {
      const existing = currentBlocks.find((block) => block.blockType === blockType);
      if (existing) {
        return currentBlocks.map((block) => block.blockType === blockType ? { ...block, content } : block);
      }
      return [
        ...currentBlocks,
        {
          id: 0,
          blockType,
          content,
          sortOrder: BLOCK_SORT_ORDER[blockType] ?? 100,
        },
      ].sort((a, b) => a.sortOrder - b.sortOrder);
    });
  }

  async function handleSave() {
    if (!draft) {
      return;
    }

    try {
      setStatus('saving');
      setStatusMessage('正在保存');
      const payload: DraftBlockUpdate[] = blocks
        .map((block) => ({
          blockType: block.blockType,
          content: block.content,
          sortOrder: block.sortOrder,
        }))
        .sort((a, b) => a.sortOrder - b.sortOrder);
      const updatedDraft = await saveDraftBlocks(draft.id, payload);
      window.localStorage.setItem(CURRENT_DRAFT_ID_KEY, String(updatedDraft.id));
      setDraft(updatedDraft);
      setBlocks(updatedDraft.blocks);
      setStatus('saved');
      setStatusMessage('已保存');
      showToast({ title: '草稿已保存', tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '保存失败';
      setStatus('error');
      setStatusMessage(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleMaterialUpload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!draft || !file) {
      return;
    }

    try {
      setMaterialStatus('uploading');
      const uploaded = await uploadDraftMaterial(draft.id, file);
      const refreshedMaterials = await listDraftMaterials(draft.id);
      setMaterials(refreshedMaterials);
      setMaterialStatus('idle');
      if (uploaded.status === 'READY') {
        showToast({ title: '材料上传成功', description: uploaded.originalFileName, tone: 'success' });
      } else {
        showToast({
          title: '材料解析失败',
          description: uploaded.errorMessage ?? uploaded.originalFileName,
          tone: 'error',
        });
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '材料上传失败';
      setMaterialStatus('error');
      showToast({ title: message, tone: 'error' });
    } finally {
      event.target.value = '';
    }
  }

  async function handleGenerateOutline() {
    if (!draft) {
      return;
    }

    try {
      setOutlineStatus('generating');
      setOutlineError('');
      const generatedOutline = await generateDraftOutline(draft.id, outlineInstruction);
      setOutline(generatedOutline);
      setOutlineStatus('success');
      showToast({ title: '提纲已生成', description: generatedOutline.titleSuggestion, tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '提纲生成失败';
      setOutlineStatus('error');
      setOutlineError(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  const bodyParagraphs = body
    .split(/\n+/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean);

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1 className="brand-title">公文助手</h1>
          <p className="brand-subtitle">通知 / 请示 / 报告起草工作台</p>
        </div>
        <div className="header-actions">
          <button className="btn secondary" type="button">
            <Sparkles aria-hidden="true" className="btn-icon" />
            生成提纲
          </button>
          <button className="btn" type="button">
            <FileDown aria-hidden="true" className="btn-icon" />
            导出 Word
          </button>
          <button className="btn" disabled={!draft || status === 'saving'} onClick={handleSave} type="button">
            <Save aria-hidden="true" className="btn-icon" />
            保存草稿
          </button>
        </div>
      </header>

      <main className="workbench">
        <section className="panel" aria-label="起草信息">
          <div className="panel-header">
            <h2 className="panel-title">文种、模板与材料</h2>
            <p className="panel-kicker">当前草稿：{currentDocumentType?.name ?? '通知'}</p>
          </div>
          <div className="panel-body">
            <label className="field-group">
              <span className="field-label">文种</span>
              <select className="field" aria-label="文种" disabled value={draft?.documentTypeCode ?? 'NOTICE'}>
                {documentTypes.length === 0 ? <option value="NOTICE">通知</option> : documentTypes.map((type) => (
                  <option key={type.code} value={type.code}>{type.name}</option>
                ))}
              </select>
            </label>

            <label className="field-group">
              <span className="field-label">标题</span>
              <input className="field" aria-label="标题" onChange={(event) => updateBlock('TITLE', event.target.value)} value={title} />
            </label>

            <label className="field-group">
              <span className="field-label">主送</span>
              <input className="field" aria-label="主送" onChange={(event) => updateBlock('RECIPIENT', event.target.value)} value={recipient} />
            </label>

            <label className="field-group">
              <span className="field-label">正文</span>
              <textarea className="field" aria-label="正文" onChange={(event) => updateBlock('BODY_PARAGRAPH', event.target.value)} value={body} />
            </label>

            <label className="field-group">
              <span className="field-label">附件</span>
              <input className="field" aria-label="附件" onChange={(event) => updateBlock('ATTACHMENT', event.target.value)} value={attachment} />
            </label>

            <label className="field-group">
              <span className="field-label">落款</span>
              <input className="field" aria-label="落款" onChange={(event) => updateBlock('SIGNATURE', event.target.value)} value={signature} />
            </label>

            <label className="field-group">
              <span className="field-label">日期</span>
              <input className="field" aria-label="日期" onChange={(event) => updateBlock('DATE', event.target.value)} value={date} />
            </label>

            <div className="material-upload">
              <input
                accept=".docx,.pdf,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                aria-label="上传材料文件"
                className="visually-hidden"
                disabled={!draft || materialStatus === 'uploading'}
                id="material-upload"
                onChange={handleMaterialUpload}
                type="file"
              />
              <label
                aria-disabled={!draft || materialStatus === 'uploading'}
                className="btn secondary upload-label"
                htmlFor="material-upload"
              >
                <Upload aria-hidden="true" className="btn-icon" />
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
        </section>

        <section aria-label="公文预览">
          <div className="document-stage">
            <article className="document-paper">
              <h2 className="document-title">{title}</h2>
              <p>{recipient}：</p>
              {bodyParagraphs.length > 0 ? bodyParagraphs.map((paragraph, index) => (
                <p key={`${paragraph}-${index}`}>{paragraph}</p>
              )) : <p>请在左侧填写正文内容。</p>}
              {attachment && <p>附件：{attachment}</p>}
              <p className="signature">
                {signature}
                <br />
                {date}
              </p>
            </article>
          </div>
        </section>

        <section className="panel" aria-label="AI 建议和质检">
          <div className="panel-header">
            <h2 className="panel-title">AI 建议与质检</h2>
            <p className="panel-kicker">{status === 'loading' ? '正在载入' : `草稿 #${draft?.id ?? '-'}`}</p>
          </div>
          <div className="panel-body">
            <div className={`check-item ${status === 'error' ? 'warning' : 'success'}`}>{statusMessage}</div>
            <div className="check-item success">结构化草稿块 {blocks.length} 项</div>
            <div className={materials.length > 0 ? 'check-item success' : 'check-item warning'}>
              参考材料 {materials.length} 项
            </div>
            <label className="field-group">
              <span className="field-label">补充要求</span>
              <textarea
                aria-label="提纲补充要求"
                className="field outline-instruction"
                disabled={!draft || outlineStatus === 'generating'}
                maxLength={1000}
                onChange={(event) => setOutlineInstruction(event.target.value)}
                placeholder="可补充会议重点、语气、必须覆盖的信息"
                value={outlineInstruction}
              />
            </label>
            <button
              className="btn secondary"
              disabled={!draft || outlineStatus === 'generating' || status === 'loading'}
              onClick={handleGenerateOutline}
              type="button"
            >
              <Sparkles aria-hidden="true" className="btn-icon" />
              {outlineStatus === 'generating' ? '正在生成提纲' : outlineStatus === 'error' ? '重试生成提纲' : '生成提纲'}
            </button>
            {outlineStatus === 'error' && <div className="check-item warning">{outlineError}</div>}
            {outline && (
              <div className="outline-result" aria-label="AI 提纲结果">
                <div className="outline-title">{outline.titleSuggestion}</div>
                {outline.sections.map((section) => (
                  <div className="outline-section" key={section.heading}>
                    <div className="outline-heading">{section.heading}</div>
                    <ul>
                      {section.points.map((point) => <li key={point}>{point}</li>)}
                    </ul>
                  </div>
                ))}
                {outline.missingInformation.length > 0 && (
                  <div className="outline-missing">
                    缺失信息：{outline.missingInformation.join('、')}
                  </div>
                )}
              </div>
            )}
          </div>
        </section>
      </main>
    </div>
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
