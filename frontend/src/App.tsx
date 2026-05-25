import { FileDown, Save, Sparkles, Upload } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { createDraft, getDraft, listDocumentTypes, saveDraftBlocks } from './api';
import type { DocumentType, DraftBlock, DraftBlockUpdate, DraftDetail } from './draftTypes';

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

export function App() {
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [draft, setDraft] = useState<DraftDetail | null>(null);
  const [blocks, setBlocks] = useState<DraftBlock[]>([]);
  const [status, setStatus] = useState<WorkbenchStatus>('loading');
  const [statusMessage, setStatusMessage] = useState('正在加载草稿');

  useEffect(() => {
    let mounted = true;

    async function loadWorkbench() {
      try {
        setStatus('loading');
        setStatusMessage('正在加载草稿');
        const types = await listDocumentTypes();
        const loadedDraft = await loadCurrentDraft();
        if (!mounted) {
          return;
        }
        setDocumentTypes(types);
        setDraft(loadedDraft);
        setBlocks(loadedDraft.blocks);
        setStatus('idle');
        setStatusMessage('草稿已载入');
      } catch (error) {
        if (!mounted) {
          return;
        }
        setStatus('error');
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
    } catch (error) {
      setStatus('error');
      setStatusMessage(error instanceof Error ? error.message : '保存失败');
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

            <button className="btn secondary" type="button">
              <Upload aria-hidden="true" className="btn-icon" />
              上传 Word/PDF 材料
            </button>
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
            <div className="check-item warning">材料上传和 AI 生成将在后续阶段接入</div>
            <button className="btn secondary" type="button">
              <Sparkles aria-hidden="true" className="btn-icon" />
              优化选中段落
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}
