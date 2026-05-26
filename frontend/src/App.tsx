import {
  AlertCircle,
  Archive,
  CheckCircle2,
  FileDown,
  FileText,
  FolderOpen,
  LayoutDashboard,
  LibraryBig,
  Save,
  Settings,
  Sparkles,
  Upload,
} from 'lucide-react';
import { ChangeEvent, useEffect, useMemo, useState } from 'react';
import {
  createDraft,
  getAiProviderSettings,
  generateDraftOutline,
  generateDraftParagraph,
  getDraft,
  listDocumentTypes,
  listDraftMaterials,
  saveDraftBlocks,
  testAiProviderConnection,
  updateAiProviderSettings,
  uploadDraftMaterial,
} from './api';
import { ToastProvider, useToast } from './components/feedback/ToastProvider';
import type {
  AiOutline,
  AiProviderSettings,
  AiProviderStatus,
  DocumentType,
  DraftBlock,
  DraftBlockUpdate,
  DraftDetail,
  Material,
} from './draftTypes';

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
type ParagraphStatus = 'idle' | 'generating' | 'success' | 'error';
type AppView = 'overview' | 'workbench' | 'drafts' | 'templates' | 'materials' | 'exports' | 'ai-tasks' | 'settings';
type AiSettingsStatus = 'loading' | 'idle' | 'saving' | 'testing' | 'error';

const DEFAULT_AI_SETTINGS: AiProviderSettings = {
  provider: 'mock',
  deepSeekEnabled: false,
  deepSeekBaseUrl: 'https://api.deepseek.com',
  deepSeekModel: 'deepseek-v4-flash',
  deepSeekApiKeyConfigured: false,
  maskedDeepSeekApiKey: '',
  deepSeekTimeoutSeconds: 60,
};

const NAV_ITEMS: Array<{
  view: AppView;
  label: string;
  description: string;
  icon: typeof LayoutDashboard;
}> = [
  { view: 'overview', label: '总览', description: '近期工作与状态', icon: LayoutDashboard },
  { view: 'workbench', label: '工作台', description: '起草与 AI 生成', icon: FileText },
  { view: 'drafts', label: '草稿列表', description: '待补列表接口', icon: FolderOpen },
  { view: 'templates', label: '模板管理', description: 'P10 管理后台', icon: LibraryBig },
  { view: 'materials', label: '材料库', description: '材料归集入口', icon: Archive },
  { view: 'exports', label: '导出记录', description: 'Word 导出追踪', icon: FileDown },
  { view: 'ai-tasks', label: 'AI 任务', description: '生成与质检队列', icon: Sparkles },
  { view: 'settings', label: '系统设置', description: '权限与配置预留', icon: Settings },
];

export function App() {
  return (
    <ToastProvider>
      <Workbench />
    </ToastProvider>
  );
}

function Workbench() {
  const { showToast } = useToast();
  const [activeView, setActiveView] = useState<AppView>('overview');
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [draft, setDraft] = useState<DraftDetail | null>(null);
  const [blocks, setBlocks] = useState<DraftBlock[]>([]);
  const [materials, setMaterials] = useState<Material[]>([]);
  const [outline, setOutline] = useState<AiOutline | null>(null);
  const [outlineStatus, setOutlineStatus] = useState<OutlineStatus>('idle');
  const [outlineError, setOutlineError] = useState('');
  const [outlineInstruction, setOutlineInstruction] = useState('');
  const [paragraphStatuses, setParagraphStatuses] = useState<Record<string, ParagraphStatus>>({});
  const [paragraphErrors, setParagraphErrors] = useState<Record<string, string>>({});
  const [aiSettings, setAiSettings] = useState<AiProviderSettings>(DEFAULT_AI_SETTINGS);
  const [aiSettingsApiKey, setAiSettingsApiKey] = useState('');
  const [aiSettingsStatus, setAiSettingsStatus] = useState<AiSettingsStatus>('loading');
  const [aiSettingsMessage, setAiSettingsMessage] = useState('正在加载 AI 配置');
  const [aiProviderStatus, setAiProviderStatus] = useState<AiProviderStatus | null>(null);
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

  useEffect(() => {
    if (activeView !== 'settings') {
      return undefined;
    }

    let mounted = true;

    async function loadAiSettings() {
      try {
        setAiSettingsStatus('loading');
        const settings = await getAiProviderSettings();
        if (!mounted) {
          return;
        }
        setAiSettings(settings);
        setAiSettingsStatus('idle');
        setAiSettingsMessage('AI 配置已载入');
      } catch (error) {
        if (!mounted) {
          return;
        }
        setAiSettingsStatus('error');
        setAiSettingsMessage(error instanceof Error ? error.message : 'AI 配置加载失败');
      }
    }

    void loadAiSettings();

    return () => {
      mounted = false;
    };
  }, [activeView]);

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
  const body = blocks
    .filter((block) => block.blockType === 'BODY_PARAGRAPH')
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((block) => block.content)
    .filter(Boolean)
    .join('\n\n');
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
      setParagraphStatuses({});
      setParagraphErrors({});
      setOutlineStatus('success');
      showToast({ title: '提纲已生成', description: generatedOutline.titleSuggestion, tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '提纲生成失败';
      setOutlineStatus('error');
      setOutlineError(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleGenerateParagraph(sectionIndex: number) {
    if (!draft || !outline) {
      return;
    }
    const section = outline.sections[sectionIndex];
    const key = section.heading;
    const sortOrder = 30 + sectionIndex;

    try {
      setParagraphStatuses((current) => ({ ...current, [key]: 'generating' }));
      setParagraphErrors((current) => ({ ...current, [key]: '' }));
      const generated = await generateDraftParagraph(draft.id, section, outlineInstruction, sortOrder);
      setDraft(generated.draft);
      setBlocks(generated.draft.blocks);
      setParagraphStatuses((current) => ({ ...current, [key]: 'success' }));
      setStatus('saved');
      setStatusMessage('正文已生成并保存');
      showToast({ title: '正文已生成', description: section.heading, tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '正文生成失败';
      setParagraphStatuses((current) => ({ ...current, [key]: 'error' }));
      setParagraphErrors((current) => ({ ...current, [key]: message }));
      showToast({ title: message, tone: 'error' });
    }
  }

  function updateAiSettingsDraft(patch: Partial<AiProviderSettings>) {
    setAiSettings((current) => ({ ...current, ...patch }));
    setAiSettingsStatus('idle');
    setAiSettingsMessage('AI 配置有未保存修改');
    setAiProviderStatus(null);
  }

  async function handleSaveAiSettings() {
    try {
      setAiSettingsStatus('saving');
      setAiSettingsMessage('正在保存 AI 配置');
      const saved = await updateAiProviderSettings({
        provider: aiSettings.provider,
        deepSeekEnabled: aiSettings.deepSeekEnabled,
        deepSeekBaseUrl: aiSettings.deepSeekBaseUrl,
        deepSeekModel: aiSettings.deepSeekModel,
        deepSeekApiKey: aiSettingsApiKey,
        clearDeepSeekApiKey: false,
        deepSeekTimeoutSeconds: aiSettings.deepSeekTimeoutSeconds,
      });
      setAiSettings(saved);
      setAiSettingsApiKey('');
      setAiSettingsStatus('idle');
      setAiSettingsMessage('AI 配置已保存');
      showToast({ title: 'AI 配置已保存', tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'AI 配置保存失败';
      setAiSettingsStatus('error');
      setAiSettingsMessage(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleTestAiConnection() {
    try {
      setAiSettingsStatus('testing');
      setAiSettingsMessage('正在保存并测试 AI 连接');
      const saved = await updateAiProviderSettings({
        provider: aiSettings.provider,
        deepSeekEnabled: aiSettings.deepSeekEnabled,
        deepSeekBaseUrl: aiSettings.deepSeekBaseUrl,
        deepSeekModel: aiSettings.deepSeekModel,
        deepSeekApiKey: aiSettingsApiKey,
        clearDeepSeekApiKey: false,
        deepSeekTimeoutSeconds: aiSettings.deepSeekTimeoutSeconds,
      });
      setAiSettings(saved);
      setAiSettingsApiKey('');
      const result = await testAiProviderConnection();
      setAiProviderStatus(result);
      setAiSettingsStatus('idle');
      setAiSettingsMessage(result.message);
      showToast({ title: result.message, tone: result.available ? 'success' : 'error' });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'AI 连接测试失败';
      setAiProviderStatus(null);
      setAiSettingsStatus('error');
      setAiSettingsMessage(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  const bodyParagraphs = body
    .split(/\n+/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean);

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-mark">文</div>
          <div>
            <div className="brand-title">公文助手</div>
            <div className="brand-subtitle">AI 公文工作台</div>
          </div>
        </div>
        <nav className="sidebar-nav" aria-label="主导航">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
              <button
                aria-current={activeView === item.view ? 'page' : undefined}
                aria-label={item.label}
                className="nav-item"
                key={item.view}
                onClick={() => setActiveView(item.view)}
                type="button"
              >
                <Icon aria-hidden="true" className="nav-icon" />
                <span className="nav-copy">
                  <span className="nav-label">{item.label}</span>
                  <span className="nav-description">{item.description}</span>
                </span>
              </button>
            );
          })}
        </nav>
      </aside>

      <div className="app-main">
        <header className="app-header">
          <div>
            <h1 className="page-title">{activeView === 'workbench' ? '公文工作台' : viewTitle(activeView)}</h1>
            <p className="brand-subtitle">{activeView === 'workbench' ? '通知 / 请示 / 报告起草工作台' : viewSubtitle(activeView)}</p>
          </div>
          <div className="header-actions">
            {activeView === 'overview' && (
              <button className="btn" onClick={() => setActiveView('workbench')} type="button">
                <FileText aria-hidden="true" className="btn-icon" />
                进入工作台
              </button>
            )}
            {activeView === 'workbench' && (
              <>
                <button
                  className="btn secondary"
                  disabled={!draft || outlineStatus === 'generating' || status === 'loading'}
                  onClick={() => void handleGenerateOutline()}
                  type="button"
                >
                  <Sparkles aria-hidden="true" className="btn-icon" />
                  {outlineStatus === 'generating' ? '正在生成提纲' : outlineStatus === 'error' ? '重试生成提纲' : '生成提纲'}
                </button>
                <button className="btn" type="button">
                  <FileDown aria-hidden="true" className="btn-icon" />
                  导出 Word
                </button>
                <button className="btn" disabled={!draft || status === 'saving'} onClick={handleSave} type="button">
                  <Save aria-hidden="true" className="btn-icon" />
                  保存草稿
                </button>
              </>
            )}
          </div>
        </header>

        {activeView === 'overview' && (
          <OverviewPage
            blocks={blocks}
            draft={draft}
            materials={materials}
            onOpenWorkbench={() => setActiveView('workbench')}
            status={status}
            statusMessage={statusMessage}
          />
        )}

        {activeView === 'workbench' && (
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
                {outline.sections.map((section, index) => (
                  <div className="outline-section" key={section.heading}>
                    <div className="outline-heading">{section.heading}</div>
                    <ul>
                      {section.points.map((point) => <li key={point}>{point}</li>)}
                    </ul>
                    <button
                      className="btn secondary outline-action"
                      disabled={!draft || paragraphStatuses[section.heading] === 'generating'}
                      onClick={() => void handleGenerateParagraph(index)}
                      type="button"
                    >
                      <Sparkles aria-hidden="true" className="btn-icon" />
                      {paragraphStatuses[section.heading] === 'generating'
                        ? `正在生成：${section.heading}`
                        : paragraphStatuses[section.heading] === 'error'
                          ? `重试正文：${section.heading}`
                          : `生成正文：${section.heading}`}
                    </button>
                    {paragraphStatuses[section.heading] === 'error' && (
                      <div className="check-item warning">{paragraphErrors[section.heading]}</div>
                    )}
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
        )}

        {activeView !== 'overview' && activeView !== 'workbench' && (
          activeView === 'settings' ? (
            <AiSettingsPage
              apiKey={aiSettingsApiKey}
              message={aiSettingsMessage}
              onApiKeyChange={setAiSettingsApiKey}
              onSave={() => void handleSaveAiSettings()}
              onSettingsChange={updateAiSettingsDraft}
              onTest={() => void handleTestAiConnection()}
              providerStatus={aiProviderStatus}
              settings={aiSettings}
              status={aiSettingsStatus}
            />
          ) : (
            <PlaceholderPage view={activeView} />
          )
        )}
      </div>
    </div>
  );
}

function OverviewPage({
  blocks,
  draft,
  materials,
  onOpenWorkbench,
  status,
  statusMessage,
}: {
  blocks: DraftBlock[];
  draft: DraftDetail | null;
  materials: Material[];
  onOpenWorkbench: () => void;
  status: WorkbenchStatus;
  statusMessage: string;
}) {
  const readyMaterials = materials.filter((material) => material.status === 'READY').length;
  const bodyCount = blocks.filter((block) => block.blockType === 'BODY_PARAGRAPH' && block.content.trim()).length;
  const isLoading = status === 'loading';
  const hasError = status === 'error';

  return (
    <main aria-busy={isLoading} className="overview-page" aria-label="总览">
      <section className="overview-hero">
        <div>
          <div className="eyebrow">MVP 起草闭环</div>
          <h2>今日状态</h2>
          <p>从这里进入公文起草、模板管理、材料与导出记录。当前先聚焦起草工作台，其余入口按阶段逐步接入真实数据。</p>
        </div>
        <button className="btn" onClick={onOpenWorkbench} type="button">
          <FileText aria-hidden="true" className="btn-icon" />
          继续起草
        </button>
      </section>

      <section className="metric-grid" aria-label="工作状态">
        <div className="metric-tile">
          <span className="metric-label">当前草稿</span>
          <strong>{isLoading ? '-' : draft ? '1' : '0'}</strong>
          <span aria-live="polite">{isLoading ? '正在载入' : statusMessage}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-label">正文段落</span>
          <strong>{isLoading ? '-' : bodyCount}</strong>
          <span>{isLoading ? '等待草稿块' : '已写入草稿块'}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-label">可用材料</span>
          <strong>{isLoading ? '-' : readyMaterials}</strong>
          <span>{isLoading ? '等待材料列表' : 'READY 材料可参与生成'}</span>
        </div>
        <div className="metric-tile">
          <span className="metric-label">待接入模块</span>
          <strong>4</strong>
          <span>质检、权限、模板、导出</span>
        </div>
      </section>

      <section className="overview-grid">
        <div className="overview-panel">
          <div className="overview-panel-header">
            <h3>最近草稿</h3>
            <button className="text-button" onClick={onOpenWorkbench} type="button">打开</button>
          </div>
          <div className={isLoading ? 'draft-row skeleton-row' : 'draft-row'}>
            <FileText aria-hidden="true" className="row-icon" />
            <div>
              <div className="row-title">{isLoading ? '正在加载草稿' : draft?.title ?? '暂无草稿'}</div>
              <div className="row-meta">{draft ? `${draft.documentTypeCode} · ${draft.status}` : hasError ? statusMessage : '请稍候'}</div>
            </div>
          </div>
        </div>

        <div className="overview-panel">
          <div className="overview-panel-header">
            <h3>质检提醒</h3>
          </div>
          <div className={blocks.length > 0 ? 'check-item success' : 'check-item warning'} aria-live="polite">
            {isLoading ? '正在等待草稿结构。' : blocks.length > 0 ? '结构化草稿已载入，后续 P8 接入真实质检结果。' : '草稿载入后显示结构检查。'}
          </div>
        </div>

        <div className="overview-panel">
          <div className="overview-panel-header">
            <h3>材料状态</h3>
          </div>
          {isLoading ? (
            <p className="empty-note" aria-live="polite">正在加载材料</p>
          ) : materials.length === 0 ? (
            <p className="empty-note">尚未上传材料</p>
          ) : materials.map((material) => (
            <div className="draft-row" key={material.id}>
              <FileText aria-hidden="true" className="row-icon" />
              <div>
                <div className="row-title">{material.originalFileName}</div>
                <div className="row-meta">{material.status} · 提取 {material.extractedTextLength} 字</div>
              </div>
            </div>
          ))}
        </div>

        <div className="overview-panel">
          <div className="overview-panel-header">
            <h3>AI 任务</h3>
          </div>
          <div className="check-item success">提纲生成与逐段正文生成已接入。</div>
        </div>
      </section>
    </main>
  );
}

function PlaceholderPage({ view }: { view: AppView }) {
  return (
    <main className="placeholder-page">
      <section className="overview-panel placeholder-panel">
        <div className="overview-panel-header">
          <h2>{viewTitle(view)}</h2>
        </div>
        <p>{viewSubtitle(view)}。当前入口已预留，后续阶段会接入真实列表、权限和操作。</p>
      </section>
    </main>
  );
}

function AiSettingsPage({
  apiKey,
  message,
  onApiKeyChange,
  onSave,
  onSettingsChange,
  onTest,
  providerStatus,
  settings,
  status,
}: {
  apiKey: string;
  message: string;
  onApiKeyChange: (value: string) => void;
  onSave: () => void;
  onSettingsChange: (patch: Partial<AiProviderSettings>) => void;
  onTest: () => void;
  providerStatus: AiProviderStatus | null;
  settings: AiProviderSettings;
  status: AiSettingsStatus;
}) {
  const busy = status === 'loading' || status === 'saving' || status === 'testing';
  const deepSeekActive = settings.provider === 'deepseek' && settings.deepSeekEnabled;

  return (
    <main className="settings-page" aria-label="系统设置">
      <section className="settings-panel">
        <div className="settings-header">
          <div>
            <div className="eyebrow">AI Provider</div>
            <h2>AI 配置</h2>
            <p>DeepSeek 按 OpenAI 兼容接口接入。API Key 仅保存在当前后端运行时，重启后会回到环境变量配置。</p>
          </div>
          <span className={`status-chip ${deepSeekActive ? 'success' : ''}`}>
            {deepSeekActive ? 'DeepSeek' : 'Mock'}
          </span>
        </div>

        <div className={status === 'error' ? 'check-item warning' : 'check-item success'} aria-live="polite">
          {message}
        </div>

        <div className="settings-grid">
          <label className="field-group">
            <span className="field-label">供应商</span>
            <select
              aria-label="AI 供应商"
              className="field"
              disabled={busy}
              onChange={(event) => onSettingsChange({ provider: event.target.value as 'mock' | 'deepseek' })}
              value={settings.provider}
            >
              <option value="mock">Mock 本地演示</option>
              <option value="deepseek">DeepSeek</option>
            </select>
          </label>

          <label className="toggle-row">
            <input
              aria-label="启用 DeepSeek"
              checked={settings.deepSeekEnabled}
              disabled={busy}
              onChange={(event) => onSettingsChange({ deepSeekEnabled: event.target.checked })}
              type="checkbox"
            />
            <span>
              <strong>启用 DeepSeek</strong>
              <small>启用后提纲和正文生成会走真实模型；未配置 Key 时自动保持 Mock。</small>
            </span>
          </label>

          <label className="field-group">
            <span className="field-label">Base URL</span>
            <input
              aria-label="DeepSeek Base URL"
              className="field"
              disabled={busy}
              onChange={(event) => onSettingsChange({ deepSeekBaseUrl: event.target.value })}
              value={settings.deepSeekBaseUrl}
            />
          </label>

          <label className="field-group">
            <span className="field-label">模型</span>
            <select
              aria-label="DeepSeek 模型"
              className="field"
              disabled={busy}
              onChange={(event) => onSettingsChange({ deepSeekModel: event.target.value })}
              value={settings.deepSeekModel}
            >
              <option value="deepseek-v4-flash">deepseek-v4-flash</option>
              <option value="deepseek-v4-pro">deepseek-v4-pro</option>
              <option value="deepseek-chat">deepseek-chat（兼容旧配置）</option>
              <option value="deepseek-reasoner">deepseek-reasoner（兼容旧配置）</option>
            </select>
          </label>

          <label className="field-group">
            <span className="field-label">API Key</span>
            <input
              aria-label="DeepSeek API Key"
              autoComplete="off"
              className="field"
              disabled={busy}
              onChange={(event) => onApiKeyChange(event.target.value)}
              placeholder={settings.deepSeekApiKeyConfigured ? settings.maskedDeepSeekApiKey : 'sk-...'}
              type="password"
              value={apiKey}
            />
          </label>

          <label className="field-group">
            <span className="field-label">超时秒数</span>
            <input
              aria-label="DeepSeek 超时秒数"
              className="field"
              disabled={busy}
              min={10}
              onChange={(event) => onSettingsChange({ deepSeekTimeoutSeconds: Number(event.target.value) })}
              type="number"
              value={settings.deepSeekTimeoutSeconds}
            />
          </label>
        </div>

        <div className="settings-actions">
          <button className="btn" disabled={busy} onClick={onSave} type="button">
            <Save aria-hidden="true" className="btn-icon" />
            {status === 'saving' ? '正在保存' : '保存配置'}
          </button>
          <button className="btn secondary" disabled={busy} onClick={onTest} type="button">
            <Sparkles aria-hidden="true" className="btn-icon" />
            {status === 'testing' ? '正在测试' : '测试连接'}
          </button>
        </div>

        {providerStatus && (
          <div className={providerStatus.available ? 'check-item success' : 'check-item warning'}>
            {providerStatus.provider} · {providerStatus.model} · {providerStatus.message}
            {providerStatus.latencyMs > 0 ? ` · ${providerStatus.latencyMs}ms` : ''}
          </div>
        )}
      </section>
    </main>
  );
}

function viewTitle(view: AppView) {
  return NAV_ITEMS.find((item) => item.view === view)?.label ?? '总览';
}

function viewSubtitle(view: AppView) {
  return NAV_ITEMS.find((item) => item.view === view)?.description ?? '近期工作与状态';
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
