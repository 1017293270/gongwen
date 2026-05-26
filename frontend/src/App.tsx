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
import { ChangeEvent, MutableRefObject, useEffect, useMemo, useRef, useState } from 'react';
import {
  createDraft,
  getAiProviderSettings,
  generateDraftOutline,
  generateDraftParagraph,
  generateLocalOperation,
  getDraft,
  listDocumentTypes,
  listDraftMaterials,
  listTemplateVersions,
  runQualityCheck,
  saveDraftBlocks,
  testAiProviderConnection,
  updateAiProviderSettings,
  updateDraftTemplateVersion,
  uploadDraftMaterial,
} from './api';
import { ToastProvider, useToast } from './components/feedback/ToastProvider';
import {
  Button,
  ConfirmDialog,
  Dialog,
  SelectField,
  StatusMessage,
  TextareaField,
  TextField,
} from './components/ui';
import type {
  AiLocalOperation,
  AiLocalOperationType,
  AiOutline,
  AiProviderSettings,
  AiProviderStatus,
  DocumentType,
  DraftBlock,
  DraftBlockUpdate,
  DraftDetail,
  Material,
  QualityCheckItem,
  QualityCheckResult,
  TemplateVersionSummary,
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
type LocalOperationStatus = 'idle' | 'generating' | 'suggested' | 'saving' | 'saved' | 'error';
type QualityCheckStatus = 'idle' | 'checking' | 'success' | 'error';
type AppView = 'overview' | 'workbench' | 'drafts' | 'templates' | 'materials' | 'exports' | 'ai-tasks' | 'settings';
type AiSettingsStatus = 'loading' | 'idle' | 'saving' | 'testing' | 'error';
type AiDialog = 'outline' | 'quality' | 'local' | null;

const LOCAL_OPERATION_OPTIONS: Array<{ value: AiLocalOperationType; label: string }> = [
  { value: 'FORMALIZE', label: '正式化' },
  { value: 'COMPRESS', label: '压缩' },
  { value: 'EXPAND', label: '扩写' },
  { value: 'REWRITE', label: '改写' },
  { value: 'SUPPLEMENT', label: '补充' },
];

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
  const [templateVersions, setTemplateVersions] = useState<TemplateVersionSummary[]>([]);
  const [outline, setOutline] = useState<AiOutline | null>(null);
  const [outlineStatus, setOutlineStatus] = useState<OutlineStatus>('idle');
  const [outlineError, setOutlineError] = useState('');
  const [outlineInstruction, setOutlineInstruction] = useState('');
  const [activeAiDialog, setActiveAiDialog] = useState<AiDialog>(null);
  const [paragraphStatuses, setParagraphStatuses] = useState<Record<string, ParagraphStatus>>({});
  const [paragraphErrors, setParagraphErrors] = useState<Record<string, string>>({});
  const [allParagraphStatus, setAllParagraphStatus] = useState<ParagraphStatus>('idle');
  const [allParagraphError, setAllParagraphError] = useState('');
  const [selectedBodyBlockId, setSelectedBodyBlockId] = useState<number | null>(null);
  const [localOperationType, setLocalOperationType] = useState<AiLocalOperationType>('FORMALIZE');
  const [localOperationInstruction, setLocalOperationInstruction] = useState('');
  const [localOperationStatus, setLocalOperationStatus] = useState<LocalOperationStatus>('idle');
  const [localOperationError, setLocalOperationError] = useState('');
  const [localOperationSuggestion, setLocalOperationSuggestion] = useState<AiLocalOperation | null>(null);
  const [qualityCheck, setQualityCheck] = useState<QualityCheckResult | null>(null);
  const [qualityCheckStatus, setQualityCheckStatus] = useState<QualityCheckStatus>('idle');
  const [qualityCheckError, setQualityCheckError] = useState('');
  const [discardSuggestionConfirmOpen, setDiscardSuggestionConfirmOpen] = useState(false);
  const paragraphRefs = useRef<Record<number, HTMLElement | null>>({});
  const outlineRequestRef = useRef<AbortController | null>(null);
  const qualityRequestRef = useRef<AbortController | null>(null);
  const localOperationRequestRef = useRef<AbortController | null>(null);
  const [aiSettings, setAiSettings] = useState<AiProviderSettings>(DEFAULT_AI_SETTINGS);
  const [aiSettingsApiKey, setAiSettingsApiKey] = useState('');
  const [aiSettingsStatus, setAiSettingsStatus] = useState<AiSettingsStatus>('loading');
  const [aiSettingsMessage, setAiSettingsMessage] = useState('正在加载 AI 配置');
  const [aiProviderStatus, setAiProviderStatus] = useState<AiProviderStatus | null>(null);
  const [status, setStatus] = useState<WorkbenchStatus>('loading');
  const [materialStatus, setMaterialStatus] = useState<MaterialStatus>('loading');
  const [statusMessage, setStatusMessage] = useState('正在加载草稿');

  useEffect(() => () => {
    outlineRequestRef.current?.abort();
    qualityRequestRef.current?.abort();
    localOperationRequestRef.current?.abort();
  }, []);

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
        const loadedTemplateVersions = await listTemplateVersions(loadedDraft.documentTypeCode);
        if (!mounted) {
          return;
        }
        setDocumentTypes(types);
        setDraft(loadedDraft);
        setBlocks(loadedDraft.blocks);
        setMaterials(loadedMaterials);
        setTemplateVersions(loadedTemplateVersions);
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
  const bodyBlocks = blocks
    .filter((block) => block.blockType === 'BODY_PARAGRAPH')
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const bodyNavigationBlocks = bodyBlocks.filter((block) => block.content.trim() || bodyBlocks.length === 1);
  const attachment = blockValues.ATTACHMENT ?? '';
  const signature = blockValues.SIGNATURE ?? '';
  const date = blockValues.DATE ?? '';
  const selectedBodyBlock = bodyBlocks.find((block) => block.id === selectedBodyBlockId) ?? null;

  function selectBodyBlock(blockId: number, shouldScroll = true) {
    setSelectedBodyBlockId(blockId);
    setLocalOperationError('');
    setLocalOperationSuggestion(null);
    setLocalOperationStatus('idle');
    if (shouldScroll) {
      window.setTimeout(() => {
        const target = paragraphRefs.current[blockId];
        if (typeof target?.scrollIntoView === 'function') {
          target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
        target?.focus({ preventScroll: true });
      }, 0);
    }
  }

  function updateBlock(blockType: string, content: string) {
    setStatus('idle');
    setStatusMessage('草稿有未保存修改');
    setLocalOperationSuggestion(null);
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

  function updateBlockById(blockId: number, content: string) {
    setStatus('idle');
    setStatusMessage('草稿有未保存修改');
    setLocalOperationSuggestion(null);
    setBlocks((currentBlocks) => currentBlocks.map((block) => (
      block.id === blockId ? { ...block, content } : block
    )));
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

  function startAiRequest(requestRef: MutableRefObject<AbortController | null>) {
    requestRef.current?.abort();
    const controller = new AbortController();
    requestRef.current = controller;
    return controller;
  }

  function clearAiRequest(requestRef: MutableRefObject<AbortController | null>, controller: AbortController) {
    if (requestRef.current === controller) {
      requestRef.current = null;
    }
  }

  function isAbortError(error: unknown) {
    return error instanceof DOMException && error.name === 'AbortError';
  }

  function openOutlineDialog() {
    setActiveAiDialog('outline');
    void handleGenerateOutline();
  }

  function openQualityDialog() {
    setActiveAiDialog('quality');
    void handleRunQualityCheck();
  }

  function openLocalOperationDialog() {
    setActiveAiDialog('local');
    void handleGenerateLocalOperation();
  }

  function closeAiDialog() {
    if (activeAiDialog === 'outline' && outlineStatus === 'generating') {
      outlineRequestRef.current?.abort();
    }
    if (activeAiDialog === 'quality' && qualityCheckStatus === 'checking') {
      qualityRequestRef.current?.abort();
    }
    if (activeAiDialog === 'local' && localOperationStatus === 'generating') {
      localOperationRequestRef.current?.abort();
    }
    setActiveAiDialog(null);
  }

  async function handleTemplateVersionChange(templateVersionId: number | null) {
    if (!draft) {
      return;
    }
    try {
      const updatedDraft = await updateDraftTemplateVersion(draft.id, templateVersionId);
      setDraft(updatedDraft);
      setBlocks(updatedDraft.blocks);
      setQualityCheck(null);
      showToast({
        title: templateVersionId ? '模板已绑定到草稿' : '已取消模板绑定',
        tone: 'success',
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '模板绑定失败';
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleGenerateOutline() {
    if (!draft) {
      return;
    }

    const controller = startAiRequest(outlineRequestRef);
    try {
      setOutlineStatus('generating');
      setOutlineError('');
      const generatedOutline = await generateDraftOutline(draft.id, outlineInstruction, controller.signal);
      setOutline(generatedOutline);
      setParagraphStatuses({});
      setParagraphErrors({});
      setAllParagraphStatus('idle');
      setAllParagraphError('');
      setOutlineStatus('success');
      showToast({ title: '提纲已生成', description: generatedOutline.titleSuggestion, tone: 'success' });
    } catch (error) {
      if (isAbortError(error)) {
        setOutlineStatus('idle');
        setOutlineError('');
        showToast({ title: '提纲生成已取消', tone: 'info' });
        return;
      }
      const message = error instanceof Error ? error.message : '提纲生成失败';
      setOutlineStatus('error');
      setOutlineError(message);
      showToast({ title: message, tone: 'error' });
    } finally {
      clearAiRequest(outlineRequestRef, controller);
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
      setAllParagraphStatus('idle');
      setAllParagraphError('');
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

  async function handleGenerateAllParagraphs() {
    if (!draft || !outline || outline.sections.length === 0) {
      return;
    }

    let activeSectionHeading = '';
    try {
      setAllParagraphStatus('generating');
      setAllParagraphError('');
      setParagraphErrors({});
      let latestDraft = draft;
      for (const [index, section] of outline.sections.entries()) {
        activeSectionHeading = section.heading;
        setParagraphStatuses((current) => ({ ...current, [section.heading]: 'generating' }));
        const generated = await generateDraftParagraph(latestDraft.id, section, outlineInstruction, 30 + index);
        latestDraft = generated.draft;
        setDraft(generated.draft);
        setBlocks(generated.draft.blocks);
        setParagraphStatuses((current) => ({ ...current, [section.heading]: 'success' }));
      }
      setAllParagraphStatus('success');
      setStatus('saved');
      setStatusMessage('全部正文已生成并保存');
      showToast({ title: '全部正文已生成', description: `${outline.sections.length} 个段落已保存`, tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '全部正文生成失败';
      setAllParagraphStatus('error');
      setAllParagraphError(activeSectionHeading ? `${activeSectionHeading}：${message}` : message);
      if (activeSectionHeading) {
        setParagraphStatuses((current) => ({ ...current, [activeSectionHeading]: 'error' }));
        setParagraphErrors((current) => ({ ...current, [activeSectionHeading]: message }));
      }
      showToast({ title: '全部正文生成中断', description: activeSectionHeading || undefined, tone: 'error' });
    }
  }

  async function handleGenerateLocalOperation() {
    if (!draft || !selectedBodyBlock) {
      setActiveAiDialog('local');
      setLocalOperationStatus('error');
      setLocalOperationError('请先在预览中选择正文段落');
      return;
    }

    const controller = startAiRequest(localOperationRequestRef);
    try {
      setLocalOperationStatus('generating');
      setLocalOperationError('');
      setLocalOperationSuggestion(null);
      const suggestion = await generateLocalOperation(
        draft.id,
        selectedBodyBlock.id,
        localOperationType,
        localOperationInstruction,
        controller.signal,
      );
      setLocalOperationSuggestion(suggestion);
      setLocalOperationStatus('suggested');
      showToast({ title: '段落建议已生成', tone: 'success' });
    } catch (error) {
      if (isAbortError(error)) {
        setLocalOperationStatus('idle');
        setLocalOperationError('');
        showToast({ title: '段落建议生成已取消', tone: 'info' });
        return;
      }
      const message = error instanceof Error ? error.message : '段落建议生成失败';
      setLocalOperationStatus('error');
      setLocalOperationError(message);
      showToast({ title: message, tone: 'error' });
    } finally {
      clearAiRequest(localOperationRequestRef, controller);
    }
  }

  async function handleRunQualityCheck() {
    if (!draft) {
      return;
    }

    const controller = startAiRequest(qualityRequestRef);
    try {
      setQualityCheckStatus('checking');
      setQualityCheckError('');
      const result = await runQualityCheck(draft.id, controller.signal);
      setQualityCheck(result);
      setQualityCheckStatus('success');
      showToast({
        title: result.exportBlocked ? '质检发现需处理问题' : '质检完成',
        description: qualitySummary(result),
        tone: result.exportBlocked ? 'error' : 'success',
      });
    } catch (error) {
      if (isAbortError(error)) {
        setQualityCheckStatus('idle');
        setQualityCheckError('');
        showToast({ title: '质检已取消', tone: 'info' });
        return;
      }
      const message = error instanceof Error ? error.message : '质检失败';
      setQualityCheckStatus('error');
      setQualityCheckError(message);
      showToast({ title: message, tone: 'error' });
    } finally {
      clearAiRequest(qualityRequestRef, controller);
    }
  }

  async function handleAcceptLocalOperation() {
    if (!draft || !localOperationSuggestion) {
      return;
    }

    try {
      setLocalOperationStatus('saving');
      const payload: DraftBlockUpdate[] = blocks
        .map((block) => ({
          blockType: block.blockType,
          content: block.id === localOperationSuggestion.targetBlockId
            ? localOperationSuggestion.suggestionText
            : block.content,
          sortOrder: block.sortOrder,
        }))
        .sort((a, b) => a.sortOrder - b.sortOrder);
      const updatedDraft = await saveDraftBlocks(draft.id, payload);
      setDraft(updatedDraft);
      setBlocks(updatedDraft.blocks);
      setSelectedBodyBlockId(localOperationSuggestion.targetBlockId);
      setLocalOperationSuggestion(null);
      setLocalOperationStatus('saved');
      setStatus('saved');
      setStatusMessage('局部建议已采纳并保存');
      showToast({ title: '建议已采纳', tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '采纳建议失败';
      setLocalOperationStatus('error');
      setLocalOperationError(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  function handleDiscardLocalOperation() {
    setLocalOperationSuggestion(null);
    setLocalOperationStatus('idle');
    setLocalOperationError('');
    setDiscardSuggestionConfirmOpen(false);
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
              <Button icon={<FileText aria-hidden="true" />} onClick={() => setActiveView('workbench')}>
                进入工作台
              </Button>
            )}
            {activeView === 'workbench' && (
              <>
                <Button
                  disabled={!draft || outlineStatus === 'generating' || status === 'loading'}
                  icon={<Sparkles aria-hidden="true" />}
                  isLoading={outlineStatus === 'generating'}
                  loadingLabel="正在生成提纲"
                  onClick={openOutlineDialog}
                  variant="secondary"
                >
                  {outlineStatus === 'error' ? '重试生成提纲' : '生成提纲'}
                </Button>
                <Button icon={<FileDown aria-hidden="true" />}>
                  导出 Word
                </Button>
                <Button
                  disabled={!draft || status === 'saving'}
                  icon={<Save aria-hidden="true" />}
                  isLoading={status === 'saving'}
                  loadingLabel="正在保存"
                  onClick={handleSave}
                >
                  保存草稿
                </Button>
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
            <SelectField disabled label="文种" value={draft?.documentTypeCode ?? 'NOTICE'}>
              {documentTypes.length === 0 ? <option value="NOTICE">通知</option> : documentTypes.map((type) => (
                <option key={type.code} value={type.code}>{type.name}</option>
              ))}
            </SelectField>

            <SelectField
              disabled={!draft || templateVersions.length === 0}
              hint={templateVersions.length === 0 ? '暂无已解析模板，先通过模板 API 上传版本。' : '质检会按所选模板检查占位符适配。'}
              label="套版模板"
              onChange={(event) => void handleTemplateVersionChange(event.target.value ? Number(event.target.value) : null)}
              value={draft?.templateVersionId ? String(draft.templateVersionId) : ''}
            >
              <option value="">未选择模板</option>
              {templateVersions.map((template) => (
                <option key={template.templateVersionId} value={template.templateVersionId}>
                  {template.templateName} v{template.versionNo}
                </option>
              ))}
            </SelectField>

            <TextField label="标题" onChange={(event) => updateBlock('TITLE', event.target.value)} value={title} />

            <TextField label="主送" onChange={(event) => updateBlock('RECIPIENT', event.target.value)} value={recipient} />

            <section className="paragraph-index" aria-label="正文段落目录">
              <div className="paragraph-index-header">
                <span className="field-label">正文</span>
                <span className="paragraph-count">{bodyNavigationBlocks.length} 段</span>
              </div>
              {bodyNavigationBlocks.length > 0 ? (
                <div className="paragraph-index-list">
                  {bodyNavigationBlocks.map((block, index) => (
                    <button
                      aria-current={selectedBodyBlockId === block.id ? 'true' : undefined}
                      className={`paragraph-index-item ${selectedBodyBlockId === block.id ? 'selected' : ''}`}
                      key={block.id}
                      onClick={() => selectBodyBlock(block.id)}
                      type="button"
                    >
                      <span className="paragraph-index-number">{index + 1}</span>
                      <span className="paragraph-index-copy">
                        <span className="paragraph-index-title">{paragraphDisplayTitle(block, index)}</span>
                        <span className="paragraph-index-preview">{paragraphPreview(block.content)}</span>
                      </span>
                    </button>
                  ))}
                </div>
              ) : (
                <p className="empty-note">暂无正文段落，先生成或填写正文。</p>
              )}
            </section>

            <TextField label="附件" onChange={(event) => updateBlock('ATTACHMENT', event.target.value)} value={attachment} />

            <TextField label="落款" onChange={(event) => updateBlock('SIGNATURE', event.target.value)} value={signature} />

            <TextField label="日期" onChange={(event) => updateBlock('DATE', event.target.value)} value={date} />

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
            </section>

            <section aria-label="公文预览">
          <div className="document-stage">
            <article className="document-paper">
              <h2 className="document-title">{title}</h2>
              <p>{recipient}：</p>
              {bodyNavigationBlocks.length > 0 ? bodyNavigationBlocks
                .map((block) => (
                selectedBodyBlockId === block.id ? (
                  <textarea
                    aria-label={`编辑段落：${block.content.trim().slice(0, 18)}`}
                    className="document-paragraph-editor"
                    key={block.id}
                    onChange={(event) => {
                      syncParagraphEditorHeight(event.currentTarget);
                      updateBlockById(block.id, event.target.value);
                    }}
                    ref={(node) => {
                      paragraphRefs.current[block.id] = node;
                      if (node) {
                        syncParagraphEditorHeight(node);
                      }
                    }}
                    value={block.content}
                  />
                ) : (
                  <button
                    aria-pressed={false}
                    className="document-paragraph"
                    key={block.id}
                    onClick={() => selectBodyBlock(block.id, false)}
                    ref={(node) => {
                      paragraphRefs.current[block.id] = node;
                    }}
                    type="button"
                  >
                    <span className="visually-hidden">选择段落：</span>
                    {block.content.trim() || '点击填写正文段落'}
                  </button>
                )
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
            <StatusMessage title={statusMessage} tone={status === 'error' ? 'warning' : 'success'} />
            <StatusMessage title={`结构化草稿块 ${blocks.length} 项`} tone="success" />
            <StatusMessage title={`参考材料 ${materials.length} 项`} tone={materials.length > 0 ? 'success' : 'warning'} />
            <TextareaField
              aria-label="提纲补充要求"
              className="outline-instruction"
              disabled={!draft || outlineStatus === 'generating'}
              label="补充要求"
              maxLength={1000}
              onChange={(event) => setOutlineInstruction(event.target.value)}
              placeholder="可补充会议重点、语气、必须覆盖的信息"
              value={outlineInstruction}
            />
            <Button
              disabled={!draft || outlineStatus === 'generating' || allParagraphStatus === 'generating' || status === 'loading'}
              icon={<Sparkles aria-hidden="true" />}
              isLoading={outlineStatus === 'generating'}
              loadingLabel="正在生成提纲"
              onClick={openOutlineDialog}
              variant="secondary"
            >
              {outlineStatus === 'error' ? '重试生成提纲' : '生成提纲'}
            </Button>
            {outlineStatus === 'error' && <StatusMessage title={outlineError} tone="warning" />}
            {outline && (
              <div className="ai-task-summary" aria-label="提纲摘要">
                <span>{outline.titleSuggestion}</span>
                <button className="summary-link" onClick={() => setActiveAiDialog('outline')} type="button">
                  查看提纲
                </button>
              </div>
            )}
            <div className="quality-check" aria-label="基础质检">
              <div className="quality-check-header">
                <div>
                  <div className="outline-title">基础质检</div>
                  <div className="panel-kicker">
                    {qualityCheck ? qualitySummary(qualityCheck) : '规则检查 + AI 表达建议'}
                  </div>
                </div>
                {qualityCheck && (
                  <span className={`quality-badge ${qualityCheck.status.toLowerCase()}`}>
                    {qualityStatusLabel(qualityCheck.status)}
                  </span>
                )}
              </div>
              <Button
                disabled={!draft || qualityCheckStatus === 'checking' || status === 'loading'}
                icon={<CheckCircle2 aria-hidden="true" />}
                isLoading={qualityCheckStatus === 'checking'}
                loadingLabel="正在质检"
                onClick={openQualityDialog}
                variant="secondary"
              >
                {qualityCheckStatus === 'error' ? '重试质检' : '运行质检'}
              </Button>
              {qualityCheckStatus === 'error' && <StatusMessage title={qualityCheckError} tone="warning" />}
              {qualityCheck && (
                <div className="ai-task-summary" aria-label="质检摘要">
                  <span>{qualitySummary(qualityCheck)}</span>
                  <button className="summary-link" onClick={() => setActiveAiDialog('quality')} type="button">
                    查看结果
                  </button>
                </div>
              )}
            </div>
            <div className="local-operation" aria-label="局部段落操作">
              <div className="local-operation-header">
                <div>
                  <div className="outline-title">局部段落操作</div>
                  <div className="panel-kicker">
                    {selectedBodyBlock ? `已选择段落 #${selectedBodyBlock.sortOrder}` : '请先在预览中选择正文段落'}
                  </div>
                </div>
              </div>
              <div className="operation-grid" role="group" aria-label="局部操作类型">
                {LOCAL_OPERATION_OPTIONS.map((option) => (
                  <button
                    aria-pressed={localOperationType === option.value}
                    className={`operation-choice ${localOperationType === option.value ? 'selected' : ''}`}
                    key={option.value}
                    onClick={() => setLocalOperationType(option.value)}
                    type="button"
                  >
                    {option.label}
                  </button>
                ))}
              </div>
              <TextareaField
                aria-label="局部补充要求"
                className="outline-instruction"
                disabled={!draft || localOperationStatus === 'generating' || localOperationStatus === 'saving'}
                label="局部补充要求"
                maxLength={1000}
                onChange={(event) => setLocalOperationInstruction(event.target.value)}
                placeholder="可补充语气、长度、必须保留或强化的信息"
                value={localOperationInstruction}
              />
              <Button
                disabled={!draft || !selectedBodyBlock || localOperationStatus === 'generating' || localOperationStatus === 'saving'}
                icon={<Sparkles aria-hidden="true" />}
                isLoading={localOperationStatus === 'generating'}
                loadingLabel="正在生成建议"
                onClick={openLocalOperationDialog}
                variant="secondary"
              >
                生成段落建议
              </Button>
              {localOperationError && <StatusMessage title={localOperationError} tone="warning" />}
              {localOperationSuggestion && (
                <div className="ai-task-summary" aria-label="段落建议摘要">
                  <span>已生成 {localOperationLabel(localOperationSuggestion.operationType)} 建议</span>
                  <button className="summary-link" onClick={() => setActiveAiDialog('local')} type="button">
                    查看建议
                  </button>
                </div>
              )}
            </div>
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

        <Dialog
          actions={(
            <Button onClick={closeAiDialog} variant="secondary">
              {outlineStatus === 'generating' ? '取消生成' : '关闭'}
            </Button>
          )}
          className="ai-task-dialog"
          description="提纲结果、缺失信息和正文生成入口集中在这里，不再撑高右侧面板。"
          onClose={closeAiDialog}
          open={activeAiDialog === 'outline'}
          title="生成提纲"
        >
          <div className="ai-dialog-stack">
            {outlineStatus === 'generating' && (
              <AiProgress detail="正在分析文种字段、草稿块和参考材料" label="生成提纲进度" />
            )}
            {outlineStatus === 'error' && (
              <StatusMessage title={outlineError} tone="warning">
                <Button icon={<Sparkles aria-hidden="true" />} onClick={() => void handleGenerateOutline()} variant="secondary">
                  重试生成提纲
                </Button>
              </StatusMessage>
            )}
            {outline && (
              <div className="outline-result" aria-label="AI 提纲结果">
                <div className="outline-result-header">
                  <div className="outline-title">{outline.titleSuggestion}</div>
                  <Button
                    className="outline-generate-all"
                    disabled={!draft || allParagraphStatus === 'generating' || outline.sections.length === 0}
                    icon={<Sparkles aria-hidden="true" />}
                    isLoading={allParagraphStatus === 'generating'}
                    loadingLabel="正在生成全部正文"
                    onClick={() => void handleGenerateAllParagraphs()}
                    variant="secondary"
                  >
                    {allParagraphStatus === 'error' ? '重试生成全部正文' : '生成全部正文'}
                  </Button>
                </div>
                {allParagraphStatus === 'generating' && (
                  <AiProgress detail="按提纲顺序逐段保存到 Word 预览" label="正文生成进度" />
                )}
                {allParagraphStatus === 'error' && <StatusMessage title={allParagraphError} tone="warning" />}
                {outline.sections.map((section, index) => (
                  <div className="outline-section" key={section.heading}>
                    <div className="outline-heading">{section.heading}</div>
                    <ul>
                      {section.points.map((point) => <li key={point}>{point}</li>)}
                    </ul>
                    <Button
                      className="outline-action"
                      disabled={!draft || allParagraphStatus === 'generating' || paragraphStatuses[section.heading] === 'generating'}
                      icon={<Sparkles aria-hidden="true" />}
                      isLoading={paragraphStatuses[section.heading] === 'generating'}
                      loadingLabel={`正在生成：${section.heading}`}
                      onClick={() => void handleGenerateParagraph(index)}
                      variant="secondary"
                    >
                      {paragraphStatuses[section.heading] === 'error'
                        ? `重试正文：${section.heading}`
                        : `生成正文：${section.heading}`}
                    </Button>
                    {paragraphStatuses[section.heading] === 'error' && (
                      <StatusMessage title={paragraphErrors[section.heading]} tone="warning" />
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
            {!outline && outlineStatus !== 'generating' && outlineStatus !== 'error' && (
              <StatusMessage title="点击右栏生成提纲后，结果会显示在这里。" />
            )}
          </div>
        </Dialog>

        <Dialog
          actions={(
            <Button onClick={closeAiDialog} variant="secondary">
              {qualityCheckStatus === 'checking' ? '取消质检' : '关闭'}
            </Button>
          )}
          className="ai-task-dialog"
          description="规则检查和 AI 表达建议集中展示，避免右侧面板被长列表拉高。"
          onClose={closeAiDialog}
          open={activeAiDialog === 'quality'}
          title="运行质检"
        >
          <div className="ai-dialog-stack">
            {qualityCheckStatus === 'checking' && (
              <AiProgress detail="正在检查必填字段、正文结构、材料依据和表达风险" label="运行质检进度" />
            )}
            {qualityCheckStatus === 'error' && (
              <StatusMessage title={qualityCheckError} tone="warning">
                <Button icon={<CheckCircle2 aria-hidden="true" />} onClick={() => void handleRunQualityCheck()} variant="secondary">
                  重试质检
                </Button>
              </StatusMessage>
            )}
            {qualityCheck?.exportBlocked && (
              <StatusMessage title="存在 ERROR 项，后续导出前需要先处理。" tone="warning" />
            )}
            {qualityCheck && qualityCheck.items.length === 0 && (
              <StatusMessage title="未发现阻断问题，AI 暂无额外建议。" tone="success" />
            )}
            {qualityCheck && qualityCheck.items.length > 0 && (
              <div className="quality-list">
                {qualityCheck.items.map((item) => (
                  <QualityCheckItemView item={item} key={`${item.code}-${item.targetBlockId ?? 'draft'}`} />
                ))}
              </div>
            )}
            {!qualityCheck && qualityCheckStatus !== 'checking' && qualityCheckStatus !== 'error' && (
              <StatusMessage title="点击右栏运行质检后，检查项会显示在这里。" />
            )}
          </div>
        </Dialog>

        <Dialog
          actions={(
            <Button onClick={closeAiDialog} variant="secondary">
              {localOperationStatus === 'generating' ? '取消生成' : '关闭'}
            </Button>
          )}
          className="ai-task-dialog"
          description={selectedBodyBlock ? `目标段落 #${selectedBodyBlock.sortOrder}` : '请先在预览中选择正文段落。'}
          onClose={closeAiDialog}
          open={activeAiDialog === 'local'}
          title="生成段落建议"
        >
          <div className="ai-dialog-stack">
            {localOperationStatus === 'generating' && (
              <AiProgress detail={`正在生成${localOperationLabel(localOperationType)}建议，不会直接覆盖原文`} label="生成段落建议进度" />
            )}
            {localOperationError && (
              <StatusMessage title={localOperationError} tone="warning">
                {selectedBodyBlock ? (
                  <Button icon={<Sparkles aria-hidden="true" />} onClick={() => void handleGenerateLocalOperation()} variant="secondary">
                    重试生成建议
                  </Button>
                ) : null}
              </StatusMessage>
            )}
            {localOperationSuggestion && (
              <div className="local-suggestion" aria-label="段落建议">
                <div className="local-suggestion-text">{localOperationSuggestion.suggestionText}</div>
                <div className="suggestion-actions">
                  <Button
                    disabled={localOperationStatus === 'saving'}
                    isLoading={localOperationStatus === 'saving'}
                    loadingLabel="正在采纳"
                    onClick={() => void handleAcceptLocalOperation()}
                    variant="secondary"
                  >
                    采纳建议
                  </Button>
                  <Button
                    disabled={localOperationStatus === 'saving'}
                    onClick={() => setDiscardSuggestionConfirmOpen(true)}
                    variant="ghost"
                  >
                    放弃
                  </Button>
                </div>
              </div>
            )}
            {!localOperationSuggestion && localOperationStatus !== 'generating' && !localOperationError && (
              <StatusMessage title="点击右栏生成段落建议后，建议文本会显示在这里。" />
            )}
          </div>
        </Dialog>

        <ConfirmDialog
          cancelLabel="继续编辑"
          confirmLabel="放弃建议"
          description="这只会移除右侧建议，不会修改当前草稿正文。"
          onCancel={() => setDiscardSuggestionConfirmOpen(false)}
          onConfirm={handleDiscardLocalOperation}
          open={discardSuggestionConfirmOpen}
          title="放弃这条段落建议？"
        />
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
        <Button icon={<FileText aria-hidden="true" />} onClick={onOpenWorkbench}>
          继续起草
        </Button>
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
            <Button onClick={onOpenWorkbench} variant="ghost">打开</Button>
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
          <StatusMessage
            title={isLoading ? '正在等待草稿结构。' : blocks.length > 0 ? '结构化草稿已载入，后续 P8 接入真实质检结果。' : '草稿载入后显示结构检查。'}
            tone={blocks.length > 0 ? 'success' : 'warning'}
          />
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
          <StatusMessage title="提纲生成与逐段正文生成已接入。" tone="success" />
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

        <StatusMessage title={message} tone={status === 'error' ? 'warning' : 'success'} />

        <div className="settings-grid">
          <SelectField
            disabled={busy}
            label="AI 供应商"
            onChange={(event) => onSettingsChange({ provider: event.target.value as 'mock' | 'deepseek' })}
            value={settings.provider}
          >
            <option value="mock">Mock 本地演示</option>
            <option value="deepseek">DeepSeek</option>
          </SelectField>

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

          <TextField
            disabled={busy}
            label="DeepSeek Base URL"
            onChange={(event) => onSettingsChange({ deepSeekBaseUrl: event.target.value })}
            value={settings.deepSeekBaseUrl}
          />

          <SelectField
            disabled={busy}
            label="DeepSeek 模型"
            onChange={(event) => onSettingsChange({ deepSeekModel: event.target.value })}
            value={settings.deepSeekModel}
          >
            <option value="deepseek-v4-flash">deepseek-v4-flash</option>
            <option value="deepseek-v4-pro">deepseek-v4-pro</option>
            <option value="deepseek-chat">deepseek-chat（兼容旧配置）</option>
            <option value="deepseek-reasoner">deepseek-reasoner（兼容旧配置）</option>
          </SelectField>

          <TextField
            autoComplete="off"
            disabled={busy}
            label="DeepSeek API Key"
            onChange={(event) => onApiKeyChange(event.target.value)}
            placeholder={settings.deepSeekApiKeyConfigured ? settings.maskedDeepSeekApiKey : 'sk-...'}
            type="password"
            value={apiKey}
          />

          <TextField
            disabled={busy}
            label="DeepSeek 超时秒数"
            min={10}
            onChange={(event) => onSettingsChange({ deepSeekTimeoutSeconds: Number(event.target.value) })}
            type="number"
            value={settings.deepSeekTimeoutSeconds}
          />
        </div>

        <div className="settings-actions">
          <Button
            disabled={busy}
            icon={<Save aria-hidden="true" />}
            isLoading={status === 'saving'}
            loadingLabel="正在保存"
            onClick={onSave}
          >
            保存配置
          </Button>
          <Button
            disabled={busy}
            icon={<Sparkles aria-hidden="true" />}
            isLoading={status === 'testing'}
            loadingLabel="正在测试"
            onClick={onTest}
            variant="secondary"
          >
            测试连接
          </Button>
        </div>

        {providerStatus && (
          <StatusMessage
            title={`${providerStatus.provider} · ${providerStatus.model} · ${providerStatus.message}${providerStatus.latencyMs > 0 ? ` · ${providerStatus.latencyMs}ms` : ''}`}
            tone={providerStatus.available ? 'success' : 'warning'}
          />
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

function QualityCheckItemView({ item }: { item: QualityCheckItem }) {
  const Icon = item.severity === 'ERROR' ? AlertCircle : CheckCircle2;
  return (
    <div className={`quality-item ${item.severity.toLowerCase()}`}>
      <Icon aria-hidden="true" className="quality-item-icon" />
      <div>
        <div className="quality-item-title">{item.message}</div>
        {item.suggestion && <div className="quality-item-suggestion">{item.suggestion}</div>}
        <div className="quality-item-meta">
          {qualitySeverityLabel(item.severity)} · {qualityCategoryLabel(item.category)}
        </div>
      </div>
    </div>
  );
}

function AiProgress({ detail, label }: { detail: string; label: string }) {
  return (
    <div className="ai-progress" role="progressbar" aria-label={label} aria-valuetext={detail}>
      <div className="ai-progress-header">
        <span>{detail}</span>
        <span>进行中</span>
      </div>
      <div className="ai-progress-track" aria-hidden="true">
        <span className="ai-progress-bar" />
      </div>
    </div>
  );
}

function qualitySummary(result: QualityCheckResult) {
  const errorCount = result.items.filter((item) => item.severity === 'ERROR').length;
  const warningCount = result.items.filter((item) => item.severity === 'WARNING').length;
  const infoCount = result.items.filter((item) => item.severity === 'INFO').length;
  return `错误 ${errorCount} · 警告 ${warningCount} · 建议 ${infoCount}`;
}

function qualityStatusLabel(status: QualityCheckResult['status']) {
  return status === 'PASS' ? '通过' : status === 'WARNING' ? '有建议' : '需处理';
}

function qualitySeverityLabel(severity: QualityCheckItem['severity']) {
  return severity === 'ERROR' ? '错误' : severity === 'WARNING' ? '警告' : '建议';
}

function qualityCategoryLabel(category: string) {
  const labels: Record<string, string> = {
    REQUIRED_FIELD: '必填字段',
    STRUCTURE: '结构完整性',
    MATERIAL: '材料依据',
    TEMPLATE: '模板适配',
    AI_EXPRESSION: 'AI 表达建议',
    AI_STRUCTURE: 'AI 结构建议',
    AI_RISK: 'AI 风险建议',
    AI_MATERIAL: 'AI 材料建议',
    AI_SERVICE: 'AI 服务',
  };
  return labels[category] ?? category;
}

function localOperationLabel(operationType: AiLocalOperationType) {
  return LOCAL_OPERATION_OPTIONS.find((option) => option.value === operationType)?.label ?? '段落';
}

function paragraphDisplayTitle(block: DraftBlock, index: number) {
  const content = block.content.trim().replace(/\s+/g, ' ');
  if (!content) {
    return `第 ${index + 1} 段`;
  }
  const headingMatch = content.match(/^([一二三四五六七八九十]+[、.．]\s*[^：:。；;，,]{2,28})[：:。；;，,]?/);
  if (headingMatch) {
    return headingMatch[1].trim();
  }
  const colonIndex = content.search(/[：:]/);
  if (colonIndex > 1 && colonIndex <= 24) {
    return content.slice(0, colonIndex).trim();
  }
  return `第 ${index + 1} 段`;
}

function paragraphPreview(content: string) {
  const normalized = content.trim().replace(/\s+/g, ' ');
  if (!normalized) {
    return '点击后在中间填写正文';
  }
  return normalized.length > 34 ? `${normalized.slice(0, 34)}...` : normalized;
}

function syncParagraphEditorHeight(textarea: HTMLTextAreaElement) {
  textarea.style.height = 'auto';
  textarea.style.height = `${textarea.scrollHeight}px`;
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
