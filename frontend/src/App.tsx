import {
  AlertCircle,
  Archive,
  ArrowLeft,
  Building2,
  Check,
  CheckCircle2,
  ChevronRight,
  Eye,
  FileDown,
  FileText,
  FolderOpen,
  LayoutDashboard,
  LibraryBig,
  LogOut,
  KeyRound,
  Pencil,
  Plus,
  RotateCcw,
  Save,
  Search,
  Settings,
  Sparkles,
  Trash2,
  Upload,
  Users,
  X,
} from 'lucide-react';
import { ChangeEvent, CSSProperties, FormEvent, MutableRefObject, ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import {
  createDepartment,
  createDraft,
  createDocumentType,
  createParagraphCandidateJob,
  createTemplate,
  createUser,
  ApiRequestError,
  acceptParagraphCandidate,
  acceptParagraphCandidateBatch,
  cancelParagraphCandidateJob,
  deleteDepartment,
  deleteDraft,
  deleteDocumentType,
  deleteTemplate,
  discardParagraphCandidate,
  disableUser,
  downloadExportRecord,
  exportDraftWord,
  getDocumentStructureProfile,
  getExportRecordDetail,
  getAiProviderSettings,
  getCurrentUser,
  getDraftRenderPreview,
  getRenderPreview,
  getStructureMapping,
  getTemplateProfile,
  getTemplateDocumentKind,
  getTemplateStructureFormatting,
  generateDraftOutline,
  generateLocalOperation,
  getDraft,
  initializeDraftNodes,
  insertDraftNode,
  listDepartments,
  listDocumentTypes,
  listDraftNodes,
  listInsertableDraftNodeRoles,
  listParagraphCandidates,
  listDrafts,
  listExportRecords,
  listDraftMaterials,
  listTemplates,
  listTemplateVersions,
  listUsers,
  login,
  logout,
  openParagraphCandidateJobEvents,
  resetUserPassword,
  reinitializeDraftNodes,
  publishStructureMapping,
  requestDraftRenderPreview,
  requestRenderPreview,
  restoreDraftNodeFormatOverride,
  retryExportRecord,
  retryParagraphCandidate,
  runQualityCheck,
  saveDraftNode,
  saveDraftNodeFormatOverride,
  saveDraftBlocks,
  saveStructureMappingDraft,
  testAiProviderConnection,
  updateParagraphCandidateText,
  updateAiProviderSettings,
  updateDepartment,
  updateDraftTitle,
  updateDraftTemplateVersion,
  updateTemplateStructureFormatting,
  updateUser,
  uploadDraftMaterial,
  uploadTemplateVersion,
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
import {
  renderPreviewResultMessage,
  WorkbenchExportPanel,
  type WorkbenchExportStatus,
  type WorkbenchPreviewRequestStatus,
} from './components/workbench/WorkbenchExportPanel';
import { WorkbenchContextPanel } from './components/workbench/WorkbenchContextPanel';
import {
  WorkbenchInspectorPanel,
  type WorkbenchInspectorSection,
} from './components/workbench/WorkbenchInspectorPanel';
import { NodeFormatPanel, type NodeFormatPanelStatus } from './components/workbench/NodeFormatPanel';
import { WorkbenchPreview } from './components/workbench/WorkbenchPreview';
import { WorkbenchStructureTree, type ReinitializeNodeStatus } from './components/workbench/WorkbenchStructureTree';
import { TemplateParseWorkspace } from './components/template/TemplateParseWorkspace';
import {
  qualitySummary,
  WorkbenchQualityPanel,
  type WorkbenchQualityCheckStatus,
} from './components/workbench/WorkbenchQualityPanel';
import type {
  AiLocalOperation,
  AiLocalOperationType,
  AiNodeRequestContext,
  AiOutline,
  AiParagraphCandidate,
  AiProviderSettings,
  AiProviderStatus,
  AuthUser,
  Department,
  DocumentRenderPreview,
  DocumentStructureProfile,
  DocumentType,
  DraftNode,
  DraftNodeFormatOverride,
  DraftNodeRoleOption,
  InsertDraftNodePosition,
  DraftBlock,
  DraftBlockUpdate,
  DraftDetail,
  DraftSummary,
  ExportRecordDetail,
  ExportRecordSummary,
  Material,
  ParagraphCandidateJobEvent,
  ParagraphCandidateSectionRequest,
  QualityCheckItem,
  QualityCheckResult,
  StructureMappingItem,
  StructureMappingProfile,
  TemplateDocumentKind,
  TemplateProfile,
  TemplateStructureFormatting,
  TemplateStructureFormattingOverrides,
  TemplateSummary,
  TemplateUploadResult,
  TemplateVersionSummary,
  UserAdmin,
  WorkbenchNode,
} from './draftTypes';
import { estimateAiProgress } from './progress';
import {
  bodyNodeLabel,
  composeBodySectionParts,
  composeBodySectionContent,
  deriveWorkbenchNodes,
} from './workbenchNodes';

const DEFAULT_TITLE = '未命名草稿';
const CURRENT_DRAFT_ID_KEY = 'gongwen.currentDraftId';
const DELETED_NODE_STORAGE_PREFIX = 'gongwen.deletedNodes';
const ACTIVE_CANDIDATE_STATUSES = new Set(['PENDING', 'RETRYING', 'STREAMING']);

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
type CandidateStatus = 'idle' | 'loading' | 'generating' | 'error';
type LocalOperationStatus = 'idle' | 'generating' | 'suggested' | 'saving' | 'saved' | 'error';
type AppView =
  | 'overview'
  | 'workbench'
  | 'drafts'
  | 'templates'
  | 'materials'
  | 'exports'
  | 'ai-tasks'
  | 'settings';
type AiSettingsStatus = 'loading' | 'idle' | 'saving' | 'testing' | 'error';
type DraftListStatus = 'idle' | 'loading' | 'creating' | 'error';
type AdminPageStatus = 'idle' | 'loading' | 'saving' | 'error';
type ExportRecordListStatus = 'idle' | 'loading' | 'error';
type DraftListPageMode = 'folders' | 'list';
type SettingsTab = 'ai' | 'accounts' | 'departments';
type AiDialog = 'outline' | 'quality' | 'local' | null;
type TemplateStructureOverride = Partial<TemplateStructureFormatting>;
type TemplateStructureOverrideMap = TemplateStructureFormattingOverrides;
type TemplateStructureOverridesByVersion = Record<number, TemplateStructureOverrideMap>;
type DraftWorkspaceData = {
  loadedDraft: DraftDetail;
  loadedDraftNodes: DraftNode[];
  loadedMaterials: Material[];
  loadedParagraphCandidates: AiParagraphCandidate[];
  loadedTemplateVersions: TemplateVersionSummary[];
  loadedTemplateProfile: TemplateProfile | null;
  loadedStructureOverrides: TemplateStructureOverrideMap;
  loadedRenderPreview: DocumentRenderPreview | null;
  loadedInsertableRoles: DraftNodeRoleOption[];
};

function pickLatestTemplateVersions(versions: TemplateVersionSummary[]) {
  const latestByTemplateId = new Map<number, TemplateVersionSummary>();
  versions.forEach((version) => {
    const current = latestByTemplateId.get(version.templateId);
    if (!current || version.versionNo > current.versionNo || (
      version.versionNo === current.versionNo && version.templateVersionId > current.templateVersionId
    )) {
      latestByTemplateId.set(version.templateId, version);
    }
  });
  return Array.from(latestByTemplateId.values()).sort((left, right) => left.templateName.localeCompare(right.templateName, 'zh-CN'));
}

function candidatePlaceholderFromSection(
  draftId: number,
  candidateId: number,
  section: ParagraphCandidateSectionRequest,
  outlineTraceId: string | null,
): AiParagraphCandidate {
  const now = new Date().toISOString();
  return {
    id: candidateId,
    draftId,
    targetNodeId: section.targetNodeId ?? null,
    targetNodeRole: section.targetNodeRole ?? '',
    targetNodeTitle: section.targetNodeTitle ?? '',
    outlineTraceId,
    paragraphTraceId: null,
    sectionIndex: section.sectionIndex ?? 0,
    heading: section.heading,
    points: section.points,
    instructionSummary: '',
    candidateText: section.candidateText ?? '',
    candidateTextDigest: '',
    status: 'PENDING',
    errorCode: '',
    errorMessage: '',
    acceptedAt: null,
    acceptedBy: null,
    createdAt: now,
    updatedAt: now,
  };
}

function outlineCandidateStatusLabel(status: string) {
  switch (status) {
    case 'READY':
      return '已生成';
    case 'EDITED':
      return '已编辑';
    case 'STREAMING':
    case 'RETRYING':
      return '生成中';
    case 'ERROR':
      return '失败';
    case 'ACCEPTED':
      return '已确认';
    case 'DISCARDED':
      return '已放弃';
    case 'CANCELLED':
      return '已停止';
    case 'PENDING':
    default:
      return '等待中';
  }
}

function normalizeOutlineHeading(value: string | null | undefined) {
  return (value ?? '')
    .replace(/\s+/g, '')
    .replace(/[：:。；;，,]/g, '')
    .trim();
}

function outlineHeadingMatches(left: string | null | undefined, right: string | null | undefined) {
  const normalizedLeft = normalizeOutlineHeading(left);
  const normalizedRight = normalizeOutlineHeading(right);
  return Boolean(normalizedLeft && normalizedRight && normalizedLeft === normalizedRight);
}

function stripCandidateHeadingPrefix(content: string | null | undefined, heading: string | null | undefined) {
  const normalizedHeading = (heading ?? '').trim();
  let stripped = (content ?? '').trim();
  if (!normalizedHeading) {
    return stripped;
  }
  while (stripped.startsWith(normalizedHeading)) {
    stripped = stripped.slice(normalizedHeading.length).trimStart();
    while (/^[：:。；;，,\s]/.test(stripped)) {
      stripped = stripped.slice(1).trimStart();
    }
  }
  return stripped.trim();
}

function candidateBodyText(candidate: AiParagraphCandidate) {
  return stripCandidateHeadingPrefix(candidate.candidateText, candidate.heading || candidate.targetNodeTitle);
}

function hasCandidateBodyText(candidate: AiParagraphCandidate) {
  const bodyText = candidateBodyText(candidate);
  return Boolean(bodyText && !outlineHeadingMatches(bodyText, candidate.heading || candidate.targetNodeTitle));
}

function targetDraftNodeForOutlineHeading(heading: string, nodes: DraftNode[]) {
  const sortedNodes = nodes
    .filter((node) => node.status !== 'DELETED')
    .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id);
  for (let index = 0; index < sortedNodes.length; index += 1) {
    const node = sortedNodes[index];
    if (!node.role.startsWith('BODY_HEADING_LEVEL_')) {
      continue;
    }
    if (!outlineHeadingMatches(node.content || node.title, heading)) {
      continue;
    }
    const bodyNode = firstRealBodyNodeAfterHeading(sortedNodes, index);
    if (bodyNode) {
      return bodyNode;
    }
  }
  return null;
}

function targetDraftNodeForOutlineSection(sectionIndex: number, heading: string, nodes: DraftNode[]) {
  const headingTarget = targetDraftNodeForOutlineHeading(heading, nodes);
  if (headingTarget) {
    return headingTarget;
  }
  const sortedNodes = nodes
    .filter((node) => node.status !== 'DELETED')
    .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id);
  const sectionBodyNodes: DraftNode[] = [];
  for (let index = 0; index < sortedNodes.length; index += 1) {
    if (!sortedNodes[index].role.startsWith('BODY_HEADING_LEVEL_')) {
      continue;
    }
    const bodyNode = firstRealBodyNodeAfterHeading(sortedNodes, index);
    if (bodyNode) {
      sectionBodyNodes.push(bodyNode);
    }
  }
  return sectionBodyNodes[sectionIndex] ?? null;
}

function firstRealBodyNodeAfterHeading(sortedNodes: DraftNode[], headingIndex: number) {
  for (let nextIndex = headingIndex + 1; nextIndex < sortedNodes.length; nextIndex += 1) {
    const nextNode = sortedNodes[nextIndex];
    if (nextNode.role.startsWith('BODY_HEADING_LEVEL_')) {
      return null;
    }
    if (nextNode.role === 'BODY' && looksLikeRealBodyNode(nextNode)) {
      return nextNode;
    }
  }
  return null;
}

function looksLikeRealBodyNode(node: DraftNode) {
  if (node.role.startsWith('BODY_HEADING_LEVEL_')) {
    return true;
  }
  if (node.role !== 'BODY') {
    return false;
  }
  const compact = (node.content || node.title || '').replace(/\s+/g, '');
  if (!compact) {
    return true;
  }
  if (compact.startsWith('附件') || compact.startsWith('联系人') || compact.startsWith('（联系人') || compact.startsWith('(联系人')) {
    return false;
  }
  if (compact.includes('印发') || compact.includes('抄送')) {
    return false;
  }
  if (compact.length <= 32 && /^[0-9Xx]{2,4}年[0-9Xx]{1,2}月[0-9Xx]{1,2}日$/.test(compact)) {
    return false;
  }
  return true;
}

function defaultOutlineInsertAnchor(nodes: DraftNode[]) {
  const sortedNodes = nodes
    .filter((node) => node.status !== 'DELETED')
    .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id);
  const firstRealBody = sortedNodes.find((node) => node.role === 'BODY' && looksLikeRealBodyNode(node));
  if (firstRealBody) {
    return firstRealBody;
  }
  const firstHeading = sortedNodes.find((node) => node.role.startsWith('BODY_HEADING_LEVEL_'));
  return firstHeading ?? null;
}

function previousOutlineTargetNode(sectionIndex: number, sections: AiOutline['sections'], nodes: DraftNode[]) {
  for (let index = sectionIndex - 1; index >= 0; index -= 1) {
    const targetNode = targetDraftNodeForOutlineSection(index, sections[index].heading, nodes);
    if (targetNode) {
      return targetNode;
    }
  }
  return null;
}

const LOCAL_OPERATION_OPTIONS: Array<{ value: AiLocalOperationType; label: string }> = [
  { value: 'FORMALIZE', label: '正式化' },
  { value: 'COMPRESS', label: '压缩' },
  { value: 'EXPAND', label: '扩写' },
  { value: 'REWRITE', label: '改写' },
  { value: 'SUPPLEMENT', label: '补充' },
];

type NodeAiActionKind = 'local-operation' | 'quality-check' | 'none';

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
  { view: 'settings', label: '系统设置', description: 'AI、账号与组织', icon: Settings },
];

export function App() {
  return (
    <ToastProvider>
      <AuthenticatedApp />
    </ToastProvider>
  );
}

function AuthenticatedApp() {
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null);
  const [authStatus, setAuthStatus] = useState<'loading' | 'login' | 'ready'>('loading');
  const [authMessage, setAuthMessage] = useState('');

  useEffect(() => {
    let cancelled = false;
    getCurrentUser()
      .then((user) => {
        if (cancelled) {
          return;
        }
        setCurrentUser(user);
        setAuthStatus('ready');
        setAuthMessage('');
      })
      .catch(() => {
        if (cancelled) {
          return;
        }
        setCurrentUser(null);
        setAuthStatus('login');
        setAuthMessage('请先登录后继续使用公文工作台。');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleLogin(username: string, password: string) {
    const user = await login(username, password);
    setCurrentUser(user);
    setAuthStatus('ready');
    setAuthMessage('');
  }

  function handleLogoutComplete(message = '已退出登录。') {
    setCurrentUser(null);
    setAuthStatus('login');
    setAuthMessage(message);
  }

  if (authStatus === 'loading') {
    return <AuthLoadingPage />;
  }

  if (authStatus === 'login' || !currentUser) {
    return <LoginPage message={authMessage} onLogin={handleLogin} />;
  }

  return <Workbench currentUser={currentUser} onLogout={handleLogoutComplete} />;
}

function Workbench({ currentUser, onLogout }: { currentUser: AuthUser; onLogout: (message?: string) => void }) {
  const { showToast } = useToast();
  const [activeView, setActiveView] = useState<AppView>('overview');
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [draftSummaries, setDraftSummaries] = useState<DraftSummary[]>([]);
  const [selectedDraftDocumentTypeCode, setSelectedDraftDocumentTypeCode] = useState('NOTICE');
  const [draftListPageMode, setDraftListPageMode] = useState<DraftListPageMode>('folders');
  const [draftListStatus, setDraftListStatus] = useState<DraftListStatus>('idle');
  const [draftListMessage, setDraftListMessage] = useState('');
  const [exportRecords, setExportRecords] = useState<ExportRecordSummary[]>([]);
  const [exportRecordStatus, setExportRecordStatus] = useState<ExportRecordListStatus>('idle');
  const [exportRecordMessage, setExportRecordMessage] = useState('');
  const [downloadingExportRecordId, setDownloadingExportRecordId] = useState<number | null>(null);
  const [retryingExportRecordId, setRetryingExportRecordId] = useState<number | null>(null);
  const [exportRecordDetail, setExportRecordDetail] = useState<ExportRecordDetail | null>(null);
  const [exportRecordDetailStatus, setExportRecordDetailStatus] = useState<ExportRecordListStatus>('idle');
  const [exportRecordDetailMessage, setExportRecordDetailMessage] = useState('');
  const [draft, setDraft] = useState<DraftDetail | null>(null);
  const [blocks, setBlocks] = useState<DraftBlock[]>([]);
  const [draftNodes, setDraftNodes] = useState<DraftNode[]>([]);
  const [dirtyDraftNodeIds, setDirtyDraftNodeIds] = useState<Set<number>>(() => new Set());
  const [materials, setMaterials] = useState<Material[]>([]);
  const [templateVersions, setTemplateVersions] = useState<TemplateVersionSummary[]>([]);
  const [selectedTemplateProfile, setSelectedTemplateProfile] = useState<TemplateProfile | null>(null);
  const [templateStructureOverrides, setTemplateStructureOverrides] = useState<TemplateStructureOverridesByVersion>({});
  const [deletedNodeIds, setDeletedNodeIds] = useState<Set<string>>(() => new Set());
  const [outline, setOutline] = useState<AiOutline | null>(null);
  const [outlineStatus, setOutlineStatus] = useState<OutlineStatus>('idle');
  const [outlineError, setOutlineError] = useState('');
  const [outlineInstruction, setOutlineInstruction] = useState('');
  const [activeAiDialog, setActiveAiDialog] = useState<AiDialog>(null);
  const [paragraphStatuses, setParagraphStatuses] = useState<Record<string, ParagraphStatus>>({});
  const [paragraphErrors, setParagraphErrors] = useState<Record<string, string>>({});
  const [allParagraphStatus, setAllParagraphStatus] = useState<ParagraphStatus>('idle');
  const [allParagraphError, setAllParagraphError] = useState('');
  const [paragraphCandidates, setParagraphCandidates] = useState<AiParagraphCandidate[]>([]);
  const [candidateStatus, setCandidateStatus] = useState<CandidateStatus>('idle');
  const [candidateJobId, setCandidateJobId] = useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [rightInspectorSection, setRightInspectorSection] = useState<WorkbenchInspectorSection>('ai');
  const [localOperationType, setLocalOperationType] = useState<AiLocalOperationType>('FORMALIZE');
  const [localOperationInstruction, setLocalOperationInstruction] = useState('');
  const [localOperationStatus, setLocalOperationStatus] = useState<LocalOperationStatus>('idle');
  const [localOperationError, setLocalOperationError] = useState('');
  const [localOperationSuggestion, setLocalOperationSuggestion] = useState<AiLocalOperation | null>(null);
  const [nodeFormatStatus, setNodeFormatStatus] = useState<NodeFormatPanelStatus>('idle');
  const [nodeFormatError, setNodeFormatError] = useState('');
  const [reinitializeNodeStatus, setReinitializeNodeStatus] = useState<ReinitializeNodeStatus>('idle');
  const [reinitializeNodeMessage, setReinitializeNodeMessage] = useState('');
  const [insertableBodyRoles, setInsertableBodyRoles] = useState<DraftNodeRoleOption[]>([]);
  const [renderPreviewOutdated, setRenderPreviewOutdated] = useState(false);
  const [renderPreviewRevision, setRenderPreviewRevision] = useState(0);
  const [workbenchRenderPreview, setWorkbenchRenderPreview] = useState<DocumentRenderPreview | null>(null);
  const [workbenchRenderPreviewStatus, setWorkbenchRenderPreviewStatus] = useState<WorkbenchPreviewRequestStatus>('idle');
  const [workbenchRenderPreviewMessage, setWorkbenchRenderPreviewMessage] = useState('');
  const [qualityCheck, setQualityCheck] = useState<QualityCheckResult | null>(null);
  const [qualityCheckStatus, setQualityCheckStatus] = useState<WorkbenchQualityCheckStatus>('idle');
  const [qualityCheckError, setQualityCheckError] = useState('');
  const [exportStatus, setExportStatus] = useState<WorkbenchExportStatus>('idle');
  const [exportError, setExportError] = useState('');
  const [discardSuggestionConfirmOpen, setDiscardSuggestionConfirmOpen] = useState(false);
  const paragraphRefs = useRef<Record<string, HTMLElement | null>>({});
  const outlineRequestRef = useRef<AbortController | null>(null);
  const qualityRequestRef = useRef<AbortController | null>(null);
  const localOperationRequestRef = useRef<AbortController | null>(null);
  const candidateEventSourceRef = useRef<EventSource | null>(null);
  const candidateJobIdsRef = useRef<number[]>([]);
  const [aiSettings, setAiSettings] = useState<AiProviderSettings>(DEFAULT_AI_SETTINGS);
  const [aiSettingsApiKey, setAiSettingsApiKey] = useState('');
  const [aiSettingsStatus, setAiSettingsStatus] = useState<AiSettingsStatus>('loading');
  const [aiSettingsMessage, setAiSettingsMessage] = useState('正在加载 AI 配置');
  const [aiProviderStatus, setAiProviderStatus] = useState<AiProviderStatus | null>(null);
  const [status, setStatus] = useState<WorkbenchStatus>('loading');
  const [materialStatus, setMaterialStatus] = useState<MaterialStatus>('loading');
  const [statusMessage, setStatusMessage] = useState('正在加载草稿');
  const outlineProgress = useEstimatedProgress(outlineStatus === 'generating');
  const qualityProgress = useEstimatedProgress(qualityCheckStatus === 'checking');
  const localOperationProgress = useEstimatedProgress(localOperationStatus === 'generating');
  const isCandidateGenerating = candidateStatus === 'generating';
  const allParagraphProgress = useMemo(() => {
    if (!outline || outline.sections.length === 0) {
      return 0;
    }
    const completedCount = outline.sections
      .filter((section) => paragraphStatuses[section.heading] === 'success')
      .length;
    return Math.round((completedCount / outline.sections.length) * 100);
  }, [outline, paragraphStatuses]);
  const outlineDialogCandidates = useMemo(() => {
    if (!outline) {
      return [];
    }
    const activeCandidateIds = new Set(candidateJobIdsRef.current);
    return paragraphCandidates
      .filter((candidate) => {
        if (candidate.status === 'DISCARDED') {
          return false;
        }
        if (candidate.outlineTraceId && candidate.outlineTraceId === outline.traceId) {
          return true;
        }
        return activeCandidateIds.has(candidate.id);
      })
      .sort((left, right) => left.sectionIndex - right.sectionIndex || left.id - right.id);
  }, [outline, paragraphCandidates, candidateStatus]);
  const outlineDialogCandidateTextCount = outlineDialogCandidates.filter(hasCandidateBodyText).length;
  const outlineDialogGeneratingCandidateCount = outlineDialogCandidates
    .filter((candidate) => ACTIVE_CANDIDATE_STATUSES.has(candidate.status))
    .length;
  const outlineDialogAcceptableCandidates = outlineDialogCandidates
    .filter((candidate) => (candidate.status === 'READY' || candidate.status === 'EDITED') && hasCandidateBodyText(candidate));
  const isOutlineBatchGenerating = isCandidateGenerating && outlineDialogGeneratingCandidateCount > 1;
  const outlineSectionIsGenerating = (sectionIndex: number) => isCandidateGenerating
    && outlineDialogCandidates.some((candidate) => (
      candidate.sectionIndex === sectionIndex && ACTIVE_CANDIDATE_STATUSES.has(candidate.status)
    ));

  useEffect(() => () => {
    outlineRequestRef.current?.abort();
    qualityRequestRef.current?.abort();
    localOperationRequestRef.current?.abort();
    closeCandidateEventSource();
  }, []);

  useEffect(() => {
    if (!draft?.id || !draft.templateVersionId || !renderPreviewOutdated) {
      return undefined;
    }
    const timeoutId = window.setTimeout(() => {
      void handleRefreshWorkbenchPreview(true);
    }, 1000);
    return () => window.clearTimeout(timeoutId);
  }, [draft?.id, draft?.templateVersionId, renderPreviewOutdated, renderPreviewRevision]);

  useEffect(() => {
    let mounted = true;

    async function loadWorkbench() {
      try {
        setStatus('loading');
        setMaterialStatus('loading');
        setStatusMessage('正在加载草稿');
        const types = await listDocumentTypes();
        const workspaceData = await loadWorkspaceData(await loadCurrentDraft());
        if (!mounted) {
          return;
        }
        setDocumentTypes(types);
        setSelectedDraftDocumentTypeCode(workspaceData.loadedDraft.documentTypeCode);
        applyWorkspaceData(workspaceData);
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

  useEffect(() => {
    if (activeView !== 'drafts' || documentTypes.length === 0) {
      return undefined;
    }

    let mounted = true;

    async function loadDraftSummaries() {
      try {
        setDraftListStatus('loading');
        setDraftListMessage('正在加载草稿列表');
        const code = selectedDraftDocumentTypeCode || documentTypes[0].code;
        const summaries = await listDrafts(code);
        if (!mounted) {
          return;
        }
        setDraftSummaries(summaries);
        setDraftListStatus('idle');
        setDraftListMessage(summaries.length > 0 ? '草稿列表已加载' : '当前文种暂无草稿');
      } catch (error) {
        if (!mounted) {
          return;
        }
        setDraftSummaries([]);
        setDraftListStatus('error');
        setDraftListMessage(error instanceof Error ? error.message : '草稿列表加载失败');
      }
    }

    void loadDraftSummaries();

    return () => {
      mounted = false;
    };
  }, [activeView, documentTypes, selectedDraftDocumentTypeCode]);

  useEffect(() => {
    if (activeView !== 'exports') {
      return undefined;
    }

    let mounted = true;

    async function loadRecords() {
      try {
        setExportRecordStatus('loading');
        setExportRecordMessage('正在加载导出记录');
        const records = await listExportRecords();
        if (!mounted) {
          return;
        }
        setExportRecords(records);
        setExportRecordStatus('idle');
        setExportRecordMessage(records.length > 0 ? '导出记录已加载' : '暂无导出记录');
      } catch (error) {
        if (!mounted) {
          return;
        }
        setExportRecords([]);
        setExportRecordStatus('error');
        setExportRecordMessage(error instanceof Error ? error.message : '导出记录加载失败');
      }
    }

    void loadRecords();

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

  async function loadWorkspaceData(loadedDraft: DraftDetail): Promise<DraftWorkspaceData> {
    const loadedMaterials = await listDraftMaterials(loadedDraft.id);
    const loadedParagraphCandidates = await listParagraphCandidates(loadedDraft.id).catch(() => []);
    const loadedTemplateVersions = await listTemplateVersions(loadedDraft.documentTypeCode).catch(() => []);
    const loadedDraftNodes = await loadDraftNodesForWorkbench(loadedDraft);
    const [loadedTemplateProfile, loadedStructureOverrides, loadedRenderPreview, loadedInsertableRoles] = loadedDraft.templateVersionId
      ? await Promise.all([
        getTemplateProfile(loadedDraft.templateVersionId).catch(() => null),
        getTemplateStructureFormatting(loadedDraft.templateVersionId).catch(() => ({})),
        getDraftRenderPreview(loadedDraft.id).catch(() => null),
        listInsertableDraftNodeRoles(loadedDraft.id).catch(() => []),
      ])
      : [null, {}, null, []];
    return {
      loadedDraft,
      loadedDraftNodes,
      loadedMaterials,
      loadedParagraphCandidates,
      loadedTemplateVersions,
      loadedTemplateProfile,
      loadedStructureOverrides,
      loadedRenderPreview,
      loadedInsertableRoles,
    };
  }

  async function loadDraftNodesForWorkbench(loadedDraft: DraftDetail) {
    if (!loadedDraft.templateVersionId) {
      return loadedDraft.nodes ?? [];
    }

    const listedNodes = await listDraftNodes(loadedDraft.id).catch(() => loadedDraft.nodes ?? []);
    if (listedNodes.length > 0) {
      return listedNodes;
    }
    return initializeDraftNodes(loadedDraft.id).catch(() => []);
  }

  function applyWorkspaceData(workspaceData: DraftWorkspaceData) {
    const {
      loadedDraft,
      loadedDraftNodes,
      loadedMaterials,
      loadedParagraphCandidates,
      loadedTemplateVersions,
      loadedTemplateProfile,
      loadedStructureOverrides,
      loadedRenderPreview,
      loadedInsertableRoles,
    } = workspaceData;
    setDraft(loadedDraft);
    setBlocks(loadedDraft.blocks);
    setDraftNodes(loadedDraftNodes);
    setParagraphCandidates(loadedParagraphCandidates);
    setCandidateStatus('idle');
    setCandidateJobId(null);
    closeCandidateEventSource();
    setDirtyDraftNodeIds(new Set());
    setDeletedNodeIds(readDeletedNodeIds(deletedNodeStorageKey(loadedDraft.id, loadedDraft.templateVersionId)));
    setMaterials(loadedMaterials);
    setTemplateVersions(loadedTemplateVersions);
    setSelectedTemplateProfile(loadedTemplateProfile);
    setInsertableBodyRoles(loadedInsertableRoles);
    setWorkbenchRenderPreview(loadedRenderPreview);
    setWorkbenchRenderPreviewStatus('idle');
    setWorkbenchRenderPreviewMessage(loadedRenderPreview ? renderPreviewResultMessage(loadedRenderPreview) : '');
    setRenderPreviewOutdated(false);
    setSelectedNodeId(null);
    setOutline(null);
    setQualityCheck(null);
    setLocalOperationSuggestion(null);
    setLocalOperationStatus('idle');
    setReinitializeNodeStatus('idle');
    setReinitializeNodeMessage('');
    setExportError('');
    if (loadedDraft.templateVersionId) {
      setTemplateStructureOverrides((current) => ({
        ...current,
        [loadedDraft.templateVersionId as number]: loadedStructureOverrides,
      }));
    }
  }

  async function openDraftInWorkbench(draftId: number) {
    try {
      setStatus('loading');
      setMaterialStatus('loading');
      setStatusMessage('正在加载草稿');
      const openedDraft = await getDraft(draftId);
      const workspaceData = await loadWorkspaceData(openedDraft);
      window.localStorage.setItem(CURRENT_DRAFT_ID_KEY, String(openedDraft.id));
      setSelectedDraftDocumentTypeCode(openedDraft.documentTypeCode);
      applyWorkspaceData(workspaceData);
      setStatus('idle');
      setMaterialStatus('idle');
      setStatusMessage('草稿已载入');
      setActiveView('workbench');
    } catch (error) {
      const message = error instanceof Error ? error.message : '草稿加载失败';
      setStatus('error');
      setMaterialStatus('error');
      setStatusMessage(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  async function createBlankDraftInDraftList(documentTypeCode: string) {
    try {
      setDraftListStatus('creating');
      setDraftListMessage('正在创建草稿');
      setStatus('loading');
      setMaterialStatus('loading');
      const createdDraft = await createDraft(documentTypeCode, DEFAULT_TITLE);
      const workspaceData = await loadWorkspaceData(createdDraft);
      window.localStorage.setItem(CURRENT_DRAFT_ID_KEY, String(createdDraft.id));
      setSelectedDraftDocumentTypeCode(createdDraft.documentTypeCode);
      applyWorkspaceData(workspaceData);
      setDraftSummaries((current) => [
        draftDetailToSummary(createdDraft),
        ...current.filter((summary) => summary.id !== createdDraft.id),
      ]);
      setDraftListStatus('idle');
      setDraftListMessage('草稿已创建');
      setStatus('idle');
      setMaterialStatus('idle');
      setStatusMessage('空白草稿已载入');
      showToast({ title: '草稿已创建', description: '可从当前文种列表进入工作台继续编辑。', tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '草稿创建失败';
      setDraftListStatus('error');
      setDraftListMessage(message);
      setStatus('error');
      setMaterialStatus('error');
      setStatusMessage(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  async function deleteDraftFromList(draftId: number) {
    try {
      await deleteDraft(draftId);
      setDraftSummaries((current) => current.filter((summary) => summary.id !== draftId));
      if (draft?.id === draftId) {
        window.localStorage.removeItem(CURRENT_DRAFT_ID_KEY);
        setDraft(null);
        setBlocks([]);
        setDraftNodes([]);
        setDirtyDraftNodeIds(new Set());
        setParagraphCandidates([]);
        setCandidateStatus('idle');
        setCandidateJobId(null);
        closeCandidateEventSource();
        setMaterials([]);
        setTemplateVersions([]);
        setSelectedTemplateProfile(null);
        setStatus('error');
        setMaterialStatus('idle');
        setStatusMessage('当前草稿已删除，请从草稿列表进入其他草稿或新建草稿。');
      }
      setDraftListStatus('idle');
      setDraftListMessage('草稿已删除');
      showToast({ title: '草稿已删除', tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '草稿删除失败';
      setDraftListStatus('error');
      setDraftListMessage(message);
      showToast({ title: message, tone: 'error' });
      throw error;
    }
  }

  async function renameDraftFromList(draftId: number, title: string) {
    try {
      const renamedDraft = await updateDraftTitle(draftId, title);
      setDraftSummaries((current) => current.map((summary) => (
        summary.id === draftId ? { ...summary, title: renamedDraft.title, updatedAt: new Date().toISOString() } : summary
      )));
      if (draft?.id === draftId) {
        setDraft({ ...renamedDraft, nodes: draftNodes });
        setBlocks(renamedDraft.blocks);
      }
      setDraftListStatus('idle');
      setDraftListMessage('草稿名称已更新');
      showToast({ title: '草稿名称已更新', tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '草稿重命名失败';
      setDraftListStatus('error');
      setDraftListMessage(message);
      showToast({ title: message, tone: 'error' });
      throw error;
    }
  }

  const blockValues = useMemo(() => {
    return blocks.reduce<Record<string, string>>((acc, block) => {
      acc[block.blockType] = block.content;
      return acc;
    }, {});
  }, [blocks]);
  const draftNodeValues = useMemo(() => {
    return draftNodes.filter((node) => node.status !== 'DELETED').reduce<Record<string, string>>((acc, node) => {
      if (node.role && !acc[node.role]) {
        acc[node.role] = node.content;
      }
      if ((node.role === 'ATTACHMENT_NOTE' || node.role === 'ATTACHMENT_CONTENT') && !acc.ATTACHMENT) {
        acc.ATTACHMENT = node.content;
      }
      return acc;
    }, {});
  }, [draftNodes]);

  const currentWorkbenchDocumentTypeCode = selectedDraftDocumentTypeCode || draft?.documentTypeCode || 'NOTICE';
  const currentDocumentType = documentTypes.find((type) => type.code === currentWorkbenchDocumentTypeCode);
  const title = draftNodeValues.TITLE ?? blockValues.TITLE ?? draft?.title ?? DEFAULT_TITLE;
  const recipient = draftNodeValues.RECIPIENT ?? blockValues.RECIPIENT ?? '';
  const bodyBlocks = blocks
    .filter((block) => block.blockType === 'BODY_PARAGRAPH')
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const attachment = draftNodeValues.ATTACHMENT ?? blockValues.ATTACHMENT ?? '';
  const signature = draftNodeValues.SIGNATURE ?? blockValues.SIGNATURE ?? '';
  const date = draftNodeValues.DATE ?? blockValues.DATE ?? '';
  const selectedTemplateOverrides = draft?.templateVersionId
    ? templateStructureOverrides[draft.templateVersionId] ?? {}
    : {};
  const workbenchNodes = useMemo(
    () => deriveWorkbenchNodes(draft ? { ...draft, blocks, nodes: draftNodes } : null, selectedTemplateProfile, selectedTemplateOverrides)
      .filter((node) => !deletedNodeIds.has(node.nodeId)),
    [blocks, deletedNodeIds, draft, draftNodes, selectedTemplateProfile, selectedTemplateOverrides],
  );
  const bodySectionNodes = workbenchNodes.filter((node) => node.nodeType === 'BODY_SECTION');
  const selectedNode = workbenchNodes.find((node) => node.nodeId === selectedNodeId) ?? null;
  const selectedBodyNode = selectedNode?.nodeType === 'BODY_SECTION' ? selectedNode : null;
  const selectedBodyBlock = selectedBodyNode
    ? bodyBlocks.find((block) => selectedBodyNode.draftBlockId && block.id === selectedBodyNode.draftBlockId)
      ?? bodyBlocks.find((block) => block.sortOrder === selectedBodyNode.sortOrder)
      ?? null
    : null;
  const selectedDraftNode = selectedNode?.draftNodeId
    ? draftNodes.find((node) => node.id === selectedNode.draftNodeId) ?? null
    : null;
  const selectedNodeFormatDisabled = !draft || !selectedNode?.draftNodeId || Boolean(selectedNode.locked);
  const selectedNodeFormatLabel = selectedNode
    ? selectedNode.label || workbenchNodeRoleLabel(selectedNode.nodeType)
    : '未选择';
  const selectedNodeAiContext = aiNodeContextForWorkbenchNode(selectedNode);
  const selectedNodeActionKind = aiActionKindForWorkbenchNode(selectedNode);
  const selectedLocalOperationOptions = useMemo(
    () => localOperationOptionsForWorkbenchNode(selectedNode),
    [selectedNode],
  );
  const selectedNodeSupportsLocalOperation = selectedNodeActionKind === 'local-operation';
  const selectedNodeCanTargetLocalOperation = Boolean(selectedNodeAiContext || selectedBodyBlock);
  const selectedNodeActionButtonLabel = aiActionButtonLabelForWorkbenchNode(selectedNode);
  const selectedNodePanelTitle = aiPanelTitleForWorkbenchNode(selectedNode);
  const selectedNodePanelKicker = aiPanelKickerForWorkbenchNode(selectedNode, selectedBodyNode, bodySectionNodes);
  const selectedNodeActionLoading = (
    (selectedNodeActionKind === 'local-operation' && localOperationStatus === 'generating')
    || (selectedNodeActionKind === 'quality-check' && qualityCheckStatus === 'checking')
  );
  const selectedNodeActionDisabled = !draft
    || status === 'loading'
    || selectedNodeActionKind === 'none'
    || Boolean(selectedNode?.locked)
    || (selectedNodeActionKind === 'local-operation' && (!selectedNodeCanTargetLocalOperation || localOperationStatus === 'generating' || localOperationStatus === 'saving'))
    || (selectedNodeActionKind === 'quality-check' && qualityCheckStatus === 'checking');
  const titleNode = workbenchNodes.find((node) => node.nodeType === 'TITLE') ?? null;
  const recipientNode = workbenchNodes.find((node) => node.nodeType === 'RECIPIENT') ?? null;
  const attachmentNode = workbenchNodes.find((node) => node.nodeType === 'ATTACHMENT') ?? null;
  const signatureNode = workbenchNodes.find((node) => node.nodeType === 'SIGNATURE') ?? null;
  const dateNode = workbenchNodes.find((node) => node.nodeType === 'DATE') ?? null;
  const visibleNavItems = useMemo(() => NAV_ITEMS.filter((item) => canAccessView(currentUser, item.view)), [currentUser]);
  const titlePreviewStyle = structurePreviewStyle(selectedTemplateProfile, selectedTemplateOverrides, 'TITLE');
  const recipientPreviewStyle = structurePreviewStyle(selectedTemplateProfile, selectedTemplateOverrides, 'RECIPIENT');
  const bodyPreviewStyle = structurePreviewStyle(selectedTemplateProfile, selectedTemplateOverrides, 'BODY');
  const attachmentPreviewStyle = structurePreviewStyle(selectedTemplateProfile, selectedTemplateOverrides, 'ATTACHMENT');
  const signaturePreviewStyle = structurePreviewStyle(selectedTemplateProfile, selectedTemplateOverrides, 'SIGNATURE');
  const datePreviewStyle = structurePreviewStyle(selectedTemplateProfile, selectedTemplateOverrides, 'DATE');
  const titleNodePreviewStyle = mergePreviewStyle(titlePreviewStyle, titleNode?.formatting);
  const recipientNodePreviewStyle = mergePreviewStyle(recipientPreviewStyle, recipientNode?.formatting);
  const attachmentNodePreviewStyle = mergePreviewStyle(attachmentPreviewStyle, attachmentNode?.formatting);
  const signatureNodePreviewStyle = mergePreviewStyle(signaturePreviewStyle, signatureNode?.formatting);
  const dateNodePreviewStyle = mergePreviewStyle(datePreviewStyle, dateNode?.formatting);
  const latestTemplateVersions = useMemo(
    () => pickLatestTemplateVersions(templateVersions),
    [templateVersions],
  );

  useEffect(() => {
    if (selectedLocalOperationOptions.length === 0) {
      return;
    }
    if (!selectedLocalOperationOptions.some((option) => option.value === localOperationType)) {
      setLocalOperationType(selectedLocalOperationOptions[0].value);
    }
  }, [localOperationType, selectedLocalOperationOptions]);

  useEffect(() => {
    setLocalOperationError('');
    setLocalOperationSuggestion(null);
    setNodeFormatStatus('idle');
    setNodeFormatError('');
  }, [selectedNodeId]);

  useEffect(() => {
    if (!selectedNodeId) {
      setRightInspectorSection('ai');
      return;
    }
    setRightInspectorSection(selectedNodeActionKind === 'quality-check' ? 'review' : 'ai');
  }, [selectedNodeId, selectedNodeActionKind]);

  function handleSidebarNavigate(view: AppView) {
    if (view === 'drafts') {
      setDraftListPageMode('folders');
    }
    setActiveView(view);
  }

  function handleReturnToDraftDirectory() {
    setSelectedDraftDocumentTypeCode(draft?.documentTypeCode ?? selectedDraftDocumentTypeCode);
    setDraftListPageMode('list');
    setActiveView('drafts');
  }

  async function handleWorkbenchDocumentTypeChange(documentTypeCode: string) {
    if (documentTypeCode === currentWorkbenchDocumentTypeCode) {
      return;
    }

    try {
      setSelectedDraftDocumentTypeCode(documentTypeCode);
      setDraftListPageMode('list');
      setStatus('loading');
      setMaterialStatus('loading');
      setStatusMessage('正在切换文种');
      const summaries = await listDrafts(documentTypeCode);
      setDraftSummaries(summaries);
      if (summaries.length === 0) {
        setDraftListStatus('idle');
        setDraftListMessage('当前文种暂无草稿');
        setStatus('idle');
        setMaterialStatus('idle');
        setStatusMessage('当前文种暂无草稿，请先新建草稿。');
        setActiveView('drafts');
        showToast({ title: '当前文种暂无草稿，请先新建草稿。', tone: 'info' });
        return;
      }
      setDraftListStatus('idle');
      setDraftListMessage('草稿列表已加载');
      await openDraftInWorkbench(summaries[0].id);
    } catch (error) {
      const message = error instanceof Error ? error.message : '文种切换失败';
      setStatus('error');
      setMaterialStatus('error');
      setStatusMessage(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  function selectNode(nodeId: string, shouldScroll = true) {
    setSelectedNodeId(nodeId);
    setLocalOperationError('');
    setLocalOperationSuggestion(null);
    setLocalOperationStatus('idle');
    if (shouldScroll) {
      window.setTimeout(() => {
        const target = paragraphRefs.current[nodeId];
        if (typeof target?.scrollIntoView === 'function') {
          target.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
        target?.focus({ preventScroll: true });
      }, 0);
    }
  }

  function markDraftContentDirty() {
    setStatus('idle');
    setStatusMessage('草稿有未保存修改，质检和真预览需刷新');
    markRenderPreviewOutdated();
    setQualityCheck(null);
    setQualityCheckStatus('idle');
    setQualityCheckError('');
    setExportStatus('idle');
    setExportError('');
    setLocalOperationSuggestion(null);
  }

  function markRenderPreviewOutdated() {
    setRenderPreviewOutdated(true);
    setRenderPreviewRevision((revision) => revision + 1);
  }

  function updateDraftNodeLocal(nodeId: number | undefined, content: string, statusValue = 'USER_FILLED') {
    if (!nodeId) {
      return;
    }
    setDraftNodes((currentNodes) => currentNodes.map((node) => (
      node.id === nodeId ? { ...node, content, status: statusValue } : node
    )));
    setDirtyDraftNodeIds((currentIds) => new Set(currentIds).add(nodeId));
  }

  function syncGeneratedDraftNodes(nextNodes: DraftNode[] | undefined, nextNode: DraftNode | null | undefined) {
    if (nextNodes) {
      setDraftNodes(nextNodes);
      setDirtyDraftNodeIds(new Set());
      return;
    }
    if (!nextNode) {
      return;
    }
    setDraftNodes((currentNodes) => (
      currentNodes.some((node) => node.id === nextNode.id)
        ? currentNodes.map((node) => node.id === nextNode.id ? nextNode : node)
        : [...currentNodes, nextNode].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    ));
    setDirtyDraftNodeIds((currentIds) => {
      const nextIds = new Set(currentIds);
      nextIds.delete(nextNode.id);
      return nextIds;
    });
  }

  function updateWorkbenchNodeContent(node: WorkbenchNode | null, content: string) {
    if (node?.draftNodeId) {
      updateDraftNodeLocal(node.draftNodeId, content);
    }
    if (node?.nodeType === 'BODY_SECTION') {
      updateBodyNodeContent(node, content);
      return;
    }
    const blockType = blockTypeForWorkbenchNode(node);
    if (blockType) {
      updateBlock(blockType, content);
    }
  }

  function updateBlock(blockType: string, content: string) {
    markDraftContentDirty();
    const nodeRole = nodeRoleForBlockType(blockType);
    const matchingNode = draftNodes.find((node) => node.role === nodeRole || (
      blockType === 'ATTACHMENT' && (node.role === 'ATTACHMENT_NOTE' || node.role === 'ATTACHMENT_CONTENT')
    ));
    if (matchingNode) {
      updateDraftNodeLocal(matchingNode.id, content);
    }
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
    markDraftContentDirty();
    setBlocks((currentBlocks) => currentBlocks.map((block) => (
      block.id === blockId ? { ...block, content } : block
    )));
  }

  function updateBodyNodeContent(node: WorkbenchNode, content: string) {
    markDraftContentDirty();
    if (node.draftNodeId) {
      updateDraftNodeLocal(node.draftNodeId, content);
    }
    const nextBlockContent = composeBodySectionContent(node, content);
    const sortOrder = node.sortOrder || BLOCK_SORT_ORDER.BODY_PARAGRAPH;
    setBlocks((currentBlocks) => {
      const existingBlock = currentBlocks.find((block) => (
        (node.draftBlockId && block.id === node.draftBlockId)
        || (block.blockType === 'BODY_PARAGRAPH' && block.sortOrder === sortOrder)
      ));
      if (existingBlock) {
        return currentBlocks.map((block) => (
          block === existingBlock ? { ...block, content: nextBlockContent } : block
        ));
      }
      return [
        ...currentBlocks,
        {
          id: 0,
          blockType: 'BODY_PARAGRAPH',
          content: nextBlockContent,
          sortOrder,
        },
      ].sort((a, b) => a.sortOrder - b.sortOrder);
    });
  }

  function updateBodyNodeHeading(node: WorkbenchNode, heading: string) {
    markDraftContentDirty();
    if (node.headingDraftNodeId) {
      updateDraftNodeLocal(node.headingDraftNodeId, heading);
    }
    const nextBlockContent = composeBodySectionParts(heading, node.content);
    const sortOrder = node.sortOrder || BLOCK_SORT_ORDER.BODY_PARAGRAPH;
    setBlocks((currentBlocks) => {
      const existingBlock = currentBlocks.find((block) => (
        (node.draftBlockId && block.id === node.draftBlockId)
        || (block.blockType === 'BODY_PARAGRAPH' && block.sortOrder === sortOrder)
      ));
      if (existingBlock) {
        return currentBlocks.map((block) => (
          block === existingBlock ? { ...block, content: nextBlockContent } : block
        ));
      }
      return [
        ...currentBlocks,
        {
          id: 0,
          blockType: 'BODY_PARAGRAPH',
          content: nextBlockContent,
          sortOrder,
        },
      ].sort((a, b) => a.sortOrder - b.sortOrder);
    });
  }

  function removeWorkbenchNode(node: WorkbenchNode) {
    if (node.nodeType === 'BODY_SECTION') {
      removeBodyNode(node);
      return;
    }
    const nodeLabel = node.label || workbenchNodeRoleLabel(node.nodeType);
    const confirmed = window.confirm(`删除当前草稿中的“${nodeLabel}”结构？模板内容不会被删除。`);
    if (!confirmed) {
      return;
    }
    const storageKey = draft ? deletedNodeStorageKey(draft.id, draft.templateVersionId) : null;
    markDraftContentDirty();
    setLocalOperationError('');
    setLocalOperationStatus('idle');
    setSelectedNodeId(null);
    if (node.draftNodeId) {
      setDraftNodes((currentNodes) => currentNodes.map((draftNode) => (
        draftNode.id === node.draftNodeId ? { ...draftNode, status: 'DELETED' } : draftNode
      )));
      setDirtyDraftNodeIds((currentIds) => new Set(currentIds).add(node.draftNodeId as number));
    }
    setDeletedNodeIds((current) => {
      const next = new Set(current);
      next.add(node.nodeId);
      if (storageKey) {
        writeDeletedNodeIds(storageKey, next);
      }
      return next;
    });
    const blockType = blockTypeForWorkbenchNode(node);
    setBlocks((currentBlocks) => currentBlocks.filter((block) => {
      if (node.draftBlockId && block.id === node.draftBlockId) {
        return false;
      }
      return !blockType || block.blockType !== blockType;
    }));
  }

  function removeBodyNode(node: WorkbenchNode) {
    const confirmed = window.confirm('删除当前草稿中的这个正文结构？模板内容不会被删除。');
    if (!confirmed) {
      return;
    }
    const storageKey = draft ? deletedNodeStorageKey(draft.id, draft.templateVersionId) : null;
    markDraftContentDirty();
    setLocalOperationError('');
    setLocalOperationStatus('idle');
    setSelectedNodeId(null);
    const persistedNodeIds = [node.headingDraftNodeId, node.draftNodeId].filter((nodeId): nodeId is number => Boolean(nodeId));
    if (persistedNodeIds.length > 0) {
      setDraftNodes((currentNodes) => currentNodes.map((draftNode) => (
        persistedNodeIds.includes(draftNode.id) ? { ...draftNode, status: 'DELETED' } : draftNode
      )));
      setDirtyDraftNodeIds((currentIds) => {
        const nextIds = new Set(currentIds);
        persistedNodeIds.forEach((nodeId) => nextIds.add(nodeId));
        return nextIds;
      });
    }
    setDeletedNodeIds((current) => {
      const next = new Set(current);
      next.add(node.nodeId);
      if (storageKey) {
        writeDeletedNodeIds(storageKey, next);
      }
      return next;
    });
    const sortOrder = node.sortOrder || BLOCK_SORT_ORDER.BODY_PARAGRAPH;
    setBlocks((currentBlocks) => currentBlocks.filter((block) => {
      if (block.blockType !== 'BODY_PARAGRAPH') {
        return true;
      }
      if (node.draftBlockId && block.id === node.draftBlockId) {
        return false;
      }
      return block.sortOrder !== sortOrder;
    }));
  }

  async function handleReinitializeDraftNodes(preserveUserEditedNodes = false) {
    if (!draft) {
      return;
    }
    try {
      setReinitializeNodeStatus('running');
      setReinitializeNodeMessage(preserveUserEditedNodes ? '正在保留编辑并重建结构节点' : '正在按原稿覆盖重建结构节点');
      const nextNodes = await reinitializeDraftNodes(draft.id, preserveUserEditedNodes);
      setDraftNodes(nextNodes);
      setDraft((currentDraft) => currentDraft ? { ...currentDraft, nodes: nextNodes } : currentDraft);
      setDirtyDraftNodeIds(new Set());
      setDeletedNodeIds(new Set());
      writeDeletedNodeIds(deletedNodeStorageKey(draft.id, draft.templateVersionId), new Set());
      setSelectedNodeId(null);
      markRenderPreviewOutdated();
      setQualityCheck(null);
      setQualityCheckStatus('idle');
      setQualityCheckError('');
      setExportStatus('idle');
      setExportError('');
      setLocalOperationSuggestion(null);
      setReinitializeNodeStatus('success');
      setReinitializeNodeMessage(preserveUserEditedNodes ? '结构节点已重建，并保留已编辑内容' : '结构节点已按原稿重建');
      setStatus('idle');
      setStatusMessage(preserveUserEditedNodes ? '结构节点已重建并保留编辑，真实预览待刷新' : '结构节点已按原稿重建，真实预览待刷新');
      showToast({ title: preserveUserEditedNodes ? '结构节点已重建，并保留已编辑内容' : '结构节点已按原稿重建', tone: 'success' });
    } catch (error) {
      const message = reinitializeDraftNodesErrorMessage(error);
      setReinitializeNodeStatus('error');
      setReinitializeNodeMessage(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleInsertBodyStructure(request: { role: string; anchorNodeId: number | null; position: InsertDraftNodePosition }) {
    if (!draft) {
      return;
    }
    try {
      await saveCurrentDraft('正在保存当前草稿');
      const existingNodeIds = new Set(draftNodes.map((node) => node.id));
      const nextNodes = await insertDraftNode(draft.id, request);
      const createdNodes = nextNodes.filter((node) => !existingNodeIds.has(node.id));
      const selectedCreatedNode = request.role.startsWith('BODY_HEADING_LEVEL_')
        ? createdNodes.find((node) => node.role === 'BODY') ?? createdNodes.find((node) => node.role === request.role) ?? null
        : createdNodes.find((node) => node.role === request.role) ?? createdNodes[0] ?? null;
      setDraftNodes(nextNodes);
      setDraft((currentDraft) => currentDraft ? { ...currentDraft, nodes: nextNodes } : currentDraft);
      setDirtyDraftNodeIds(new Set());
      if (selectedCreatedNode) {
        setSelectedNodeId(`draft-node:${selectedCreatedNode.id}`);
      }
      setQualityCheck(null);
      setQualityCheckStatus('idle');
      setQualityCheckError('');
      setExportStatus('idle');
      setExportError('');
      setLocalOperationSuggestion(null);
      setStatus('saved');
      setStatusMessage('正文结构已新增，正在刷新真实预览');
      setRenderPreviewOutdated(true);
      setWorkbenchRenderPreviewStatus('requesting');
      setWorkbenchRenderPreviewMessage('正在刷新真实预览');
      const preview = await requestDraftRenderPreview(draft.id);
      setWorkbenchRenderPreview(preview);
      setRenderPreviewOutdated(false);
      setWorkbenchRenderPreviewStatus('idle');
      setWorkbenchRenderPreviewMessage(renderPreviewResultMessage(preview));
      showToast({ title: '正文结构已新增', description: renderPreviewResultMessage(preview), tone: preview.status === 'READY' ? 'success' : 'info' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '新增正文结构失败';
      setRenderPreviewOutdated(true);
      setWorkbenchRenderPreviewStatus('error');
      setWorkbenchRenderPreviewMessage(message);
      setStatus('error');
      setStatusMessage(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  function reinitializeDraftNodesErrorMessage(error: unknown) {
    const message = error instanceof Error ? error.message : '';
    if (message.includes('Published structure mapping is required before nodes can be reinitialized')) {
      return '需要先在模板解析工作台发布映射，工作台才能按新映射重建结构。仅保存映射草稿不会影响当前工作台。';
    }
    return message || '结构重建失败';
  }

  function draftBlockPayload(
    sourceBlocks = blocks,
    sourceBodyNodes = bodySectionNodes,
  ): DraftBlockUpdate[] {
    const hasStructuredDraftNodes = draftNodes.length > 0;
    const nonBodyBlocks = sourceBlocks
      .filter((block) => block.blockType !== 'BODY_PARAGRAPH')
      .map((block) => ({
        blockType: block.blockType,
        content: block.content,
        sortOrder: block.sortOrder,
      }));
    const bodyBlocksFromPaper = hasStructuredDraftNodes ? [] : sourceBodyNodes
      .map((node, index) => ({
        blockType: 'BODY_PARAGRAPH',
        content: composeBodySectionParts(node.heading ?? '', node.content),
        sortOrder: node.sortOrder || BLOCK_SORT_ORDER.BODY_PARAGRAPH + index,
      }))
      .filter((block) => block.content.trim());
    return [...nonBodyBlocks, ...bodyBlocksFromPaper].sort((a, b) => a.sortOrder - b.sortOrder);
  }

  async function saveCurrentDraft(message: string) {
    if (!draft) {
      return null;
    }
    setStatus('saving');
    setStatusMessage(message);
    const savedNodes = await saveDirtyDraftNodes();
    const updatedDraft = await saveDraftBlocks(draft.id, draftBlockPayload());
    window.localStorage.setItem(CURRENT_DRAFT_ID_KEY, String(updatedDraft.id));
    setDraft({ ...updatedDraft, nodes: savedNodes });
    setBlocks(updatedDraft.blocks);
    setDraftNodes(savedNodes);
    setStatus('saved');
    setStatusMessage('已保存');
    return { ...updatedDraft, nodes: savedNodes };
  }

  async function saveDirtyDraftNodes() {
    if (!draft || dirtyDraftNodeIds.size === 0) {
      return draftNodes;
    }
    const dirtyIds = Array.from(dirtyDraftNodeIds);
    const savedNodes = await Promise.all(dirtyIds.map((nodeId) => {
      const node = draftNodes.find((candidate) => candidate.id === nodeId);
      if (!node) {
        return null;
      }
      return saveDraftNode(draft.id, node.id, node.content, node.status || 'USER_FILLED');
    }));
    const savedById = new Map(savedNodes.filter((node): node is DraftNode => Boolean(node)).map((node) => [node.id, node]));
    const mergedNodes = draftNodes.map((node) => savedById.get(node.id) ?? node);
    setDirtyDraftNodeIds(new Set());
    return mergedNodes;
  }

  async function handleSave() {
    if (!draft) {
      return;
    }

    try {
      const updatedDraft = await saveCurrentDraft('正在保存');
      if (updatedDraft) {
        showToast({ title: '草稿已保存', tone: 'success' });
      }
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

  function closeCandidateEventSource() {
    candidateEventSourceRef.current?.close();
    candidateEventSourceRef.current = null;
  }

  function openCandidateEventSource(draftId: number, jobId: string) {
    const source = openParagraphCandidateJobEvents(draftId, jobId);
    candidateEventSourceRef.current = source;
    const handleMessage = (event: MessageEvent) => {
      handleParagraphCandidateJobEvent(draftId, event);
    };
    source.onmessage = handleMessage;
    source.onerror = () => {
      if (candidateEventSourceRef.current !== source) {
        return;
      }
      closeCandidateEventSource();
      void handleParagraphCandidateStreamInterrupted(draftId);
    };
    [
      'batch_started',
      'candidate_started',
      'candidate_delta',
      'candidate_ready',
      'candidate_error',
      'batch_done',
      'batch_cancelled',
    ].forEach((eventName) => {
      source.addEventListener(eventName, ((event: MessageEvent) => {
        handleParagraphCandidateJobEvent(draftId, event, eventName);
      }) as EventListener);
    });
  }

  function handleParagraphCandidateJobEvent(draftId: number, event: MessageEvent, fallbackEvent?: string) {
    const payload = parseParagraphCandidateEvent(event.data, fallbackEvent);
    if (!payload) {
      return;
    }
    if (payload.candidate) {
      upsertParagraphCandidate(payload.candidate);
    } else if (payload.candidateId) {
      patchParagraphCandidateFromEvent(payload);
    }

    if (payload.event === 'batch_started') {
      setCandidateStatus('generating');
      return;
    }
    if (payload.event === 'batch_done' || payload.event === 'batch_cancelled') {
      closeCandidateEventSource();
      setCandidateJobId(null);
      setCandidateStatus('idle');
      void refreshParagraphCandidates(draftId);
    }
  }

  function parseParagraphCandidateEvent(data: string, fallbackEvent?: string) {
    try {
      const payload = JSON.parse(data) as Partial<ParagraphCandidateJobEvent> & {
        candidate?: AiParagraphCandidate;
      };
      return {
        ...payload,
        event: payload.event ?? fallbackEvent ?? '',
      };
    } catch {
      return null;
    }
  }

  function patchParagraphCandidateFromEvent(payload: Partial<ParagraphCandidateJobEvent>) {
    setParagraphCandidates((current) => current.map((candidate) => {
      if (candidate.id !== payload.candidateId) {
        return candidate;
      }
      if (payload.event === 'candidate_started') {
        return { ...candidate, status: 'STREAMING', candidateText: '', errorCode: '', errorMessage: '' };
      }
      if (payload.event === 'candidate_delta') {
        return {
          ...candidate,
          status: 'STREAMING',
          candidateText: `${candidate.candidateText}${payload.delta ?? ''}`,
          errorCode: '',
          errorMessage: '',
        };
      }
      if (payload.event === 'candidate_ready') {
        return { ...candidate, status: 'READY', errorCode: '', errorMessage: '' };
      }
      if (payload.event === 'candidate_error') {
        return {
          ...candidate,
          status: 'ERROR',
          errorCode: payload.errorCode ?? '',
          errorMessage: payload.message ?? '候选段落生成失败',
        };
      }
      return candidate;
    }));
  }

  function upsertParagraphCandidate(candidate: AiParagraphCandidate) {
    setParagraphCandidates((current) => {
      const exists = current.some((item) => item.id === candidate.id);
      const nextCandidates = exists
        ? current.map((item) => item.id === candidate.id ? candidate : item)
        : [...current, candidate];
      return nextCandidates.sort((left, right) => left.sectionIndex - right.sectionIndex || left.id - right.id);
    });
  }

  async function refreshParagraphCandidates(draftId: number) {
    const candidates = await listParagraphCandidates(draftId).catch(() => null);
    if (candidates) {
      setParagraphCandidates(candidates);
    }
  }

  async function handleParagraphCandidateStreamInterrupted(draftId: number) {
    closeCandidateEventSource();
    const refreshedCandidates = await listParagraphCandidates(draftId).catch(() => null);
    if (!refreshedCandidates) {
      setCandidateStatus('error');
      setCandidateJobId(null);
      candidateJobIdsRef.current = [];
      showToast({ title: '正文候选连接中断，请稍后刷新或重试', tone: 'error' });
      return;
    }
    setParagraphCandidates(refreshedCandidates);
    setCandidateStatus('error');
    setCandidateJobId(null);
    candidateJobIdsRef.current = [];
    showToast({
      title: '正文候选连接中断',
      description: '已保留当前候选状态，不会自动重复生成。未完成段落可手动重试。',
      tone: 'info',
    });
  }

  function applyAcceptedCandidateDraft(updatedDraft: DraftDetail, updatedNode: DraftNode | null) {
    const nextNodes = updatedDraft.nodes ?? (updatedNode ? undefined : draftNodes);
    setDraft({ ...updatedDraft, nodes: nextNodes ?? draftNodes });
    setBlocks(updatedDraft.blocks);
    syncGeneratedDraftNodes(updatedDraft.nodes, updatedNode);
  }

  function isAbortError(error: unknown) {
    return error instanceof DOMException && error.name === 'AbortError';
  }

  function isAuthenticationRequiredError(error: unknown) {
    if (error instanceof ApiRequestError) {
      return error.status === 401
        || error.errorCode === 'AUTHENTICATION_REQUIRED'
        || error.errorCode === 'AUTHENTICATION_FAILED';
    }
    return error instanceof Error
      && (error.message.includes('Authentication required') || error.message.includes('Authentication failed'));
  }

  function handleAuthenticationRequiredError(error: unknown) {
    if (!isAuthenticationRequiredError(error)) {
      return false;
    }
    onLogout('登录状态已过期，请重新登录后继续使用。');
    return true;
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
    if (selectedNodeActionKind === 'quality-check') {
      openQualityDialog();
      return;
    }
    if (selectedNodeActionKind !== 'local-operation') {
      return;
    }
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
      const [updatedProfile, updatedStructureOverrides] = templateVersionId
        ? await Promise.all([
          getTemplateProfile(templateVersionId).catch(() => null),
          getTemplateStructureFormatting(templateVersionId).catch(() => ({})),
        ])
        : [null, {}];
      const updatedDraftNodes = templateVersionId
        ? await initializeDraftNodes(updatedDraft.id).catch(() => [])
        : [];
      const updatedInsertableRoles = templateVersionId
        ? await listInsertableDraftNodeRoles(updatedDraft.id).catch(() => [])
        : [];
      setDraft({ ...updatedDraft, nodes: updatedDraftNodes });
      setBlocks(updatedDraft.blocks);
      setDraftNodes(updatedDraftNodes);
      setInsertableBodyRoles(updatedInsertableRoles);
      setWorkbenchRenderPreview(null);
      setWorkbenchRenderPreviewStatus('idle');
      setWorkbenchRenderPreviewMessage('');
      if (templateVersionId) {
        markRenderPreviewOutdated();
      } else {
        setRenderPreviewOutdated(false);
      }
      setDirtyDraftNodeIds(new Set());
      setDeletedNodeIds(readDeletedNodeIds(deletedNodeStorageKey(updatedDraft.id, updatedDraft.templateVersionId)));
      setSelectedTemplateProfile(updatedProfile);
      if (templateVersionId) {
        setTemplateStructureOverrides((current) => ({
          ...current,
          [templateVersionId]: updatedStructureOverrides,
        }));
      }
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
      candidateJobIdsRef.current = [];
      setOutlineStatus('success');
      showToast({ title: '提纲已生成', description: generatedOutline.titleSuggestion, tone: 'success' });
    } catch (error) {
      if (isAbortError(error)) {
        setOutlineStatus('idle');
        setOutlineError('');
        showToast({ title: '提纲生成已取消', tone: 'info' });
        return;
      }
      if (handleAuthenticationRequiredError(error)) {
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
    await handleGenerateAllParagraphCandidates([sectionIndex]);
  }

  async function handleGenerateAllParagraphs() {
    await handleGenerateAllParagraphCandidates();
  }

  async function ensureParagraphCandidateTarget(
    sectionIndex: number,
    heading: string,
    workingNodes: DraftNode[],
    anchorNode: DraftNode | null,
  ) {
    const matchedTarget = targetDraftNodeForOutlineSection(sectionIndex, heading, workingNodes);
    if (matchedTarget) {
      return {
        nodes: workingNodes,
        targetNode: matchedTarget,
      };
    }
    if (!draft?.templateVersionId) {
      return {
        nodes: workingNodes,
        targetNode: null,
      };
    }

    const existingNodeIds = new Set(workingNodes.map((node) => node.id));
    const insertAnchor = anchorNode && looksLikeRealBodyNode(anchorNode)
      ? anchorNode
      : defaultOutlineInsertAnchor(workingNodes);
    const insertedNodes = await insertDraftNode(draft.id, {
      role: 'BODY_HEADING_LEVEL_1',
      anchorNodeId: insertAnchor?.id ?? null,
      position: insertAnchor ? 'AFTER' : 'END_OF_BODY',
    });
    const createdNodes = insertedNodes.filter((node) => !existingNodeIds.has(node.id));
    const headingNode = createdNodes.find((node) => node.role.startsWith('BODY_HEADING_LEVEL_')) ?? null;
    const bodyNode = createdNodes.find((node) => node.role === 'BODY') ?? null;
    let nextNodes = insertedNodes;
    if (headingNode) {
      const savedHeadingNode = await saveDraftNode(draft.id, headingNode.id, heading, 'USER_FILLED');
      nextNodes = insertedNodes.map((node) => node.id === savedHeadingNode.id ? savedHeadingNode : node);
    }
    setDraftNodes(nextNodes);
    setDraft((currentDraft) => currentDraft ? { ...currentDraft, nodes: nextNodes } : currentDraft);
    setDirtyDraftNodeIds(new Set());
    markRenderPreviewOutdated();
    return {
      nodes: nextNodes,
      targetNode: bodyNode,
    };
  }

  async function handleGenerateAllParagraphCandidates(sectionIndexes?: number[]) {
    if (!draft || !outline || outline.sections.length === 0) {
      showToast({ title: '请先生成提纲', tone: 'info' });
      return;
    }

    try {
      const indexes = sectionIndexes && sectionIndexes.length > 0
        ? sectionIndexes
        : outline.sections.map((_, index) => index);
      const sections: ParagraphCandidateSectionRequest[] = [];
      let workingNodes = draftNodes;
      let lastTargetNode: DraftNode | null = null;
      for (const index of indexes) {
        const section = outline.sections[index];
        const anchorNode = lastTargetNode ?? previousOutlineTargetNode(index, outline.sections, workingNodes);
        const resolvedTarget = await ensureParagraphCandidateTarget(index, section.heading, workingNodes, anchorNode);
        workingNodes = resolvedTarget.nodes;
        const targetNode = resolvedTarget.targetNode;
        if (targetNode) {
          lastTargetNode = targetNode;
        }
        sections.push({
          sectionIndex: index,
          heading: section.heading,
          points: section.points,
          targetNodeId: targetNode?.id ?? null,
          targetNodeRole: targetNode?.role ?? '',
          targetNodeTitle: section.heading,
        });
      }

      closeCandidateEventSource();
      candidateJobIdsRef.current = [];
      setCandidateStatus('generating');
      setAllParagraphStatus('idle');
      setAllParagraphError('');
      setParagraphErrors({});
      const job = await createParagraphCandidateJob(draft.id, {
        outlineTraceId: outline.traceId,
        instructionSummary: outlineInstruction,
        sections,
      });
      setCandidateJobId(job.jobId);
      candidateJobIdsRef.current = job.candidateIds;
      const replacementSectionIndexes = new Set(sections.map((section) => section.sectionIndex ?? 0));
      const placeholders = sections.map((section, index) => candidatePlaceholderFromSection(
        draft.id,
        job.candidateIds[index] ?? -(index + 1),
        section,
        outline.traceId,
      ));
      setParagraphCandidates((current) => {
        const keptCandidates = current.filter((candidate) => !(
          candidate.outlineTraceId === outline.traceId
          && replacementSectionIndexes.has(candidate.sectionIndex)
        ));
        return [...keptCandidates, ...placeholders]
          .sort((left, right) => left.sectionIndex - right.sectionIndex || left.id - right.id);
      });
      openCandidateEventSource(draft.id, job.jobId);
      showToast({ title: '正文候选正在生成', description: `${sections.length} 个段落将进入右栏确认区`, tone: 'info' });
    } catch (error) {
      if (handleAuthenticationRequiredError(error)) {
        return;
      }
      const message = error instanceof Error ? error.message : '段落候选生成失败';
      setCandidateStatus('error');
      setCandidateJobId(null);
      candidateJobIdsRef.current = [];
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleStopParagraphCandidateJob() {
    if (!draft || !candidateJobId) {
      closeCandidateEventSource();
      setCandidateStatus('idle');
      return;
    }

    const stoppingJobId = candidateJobId;
    closeCandidateEventSource();
    setCandidateJobId(null);
    try {
      await cancelParagraphCandidateJob(draft.id, stoppingJobId);
      await refreshParagraphCandidates(draft.id);
      setCandidateStatus('idle');
      candidateJobIdsRef.current = [];
      showToast({ title: '段落候选生成已停止', tone: 'info' });
    } catch (error) {
      if (handleAuthenticationRequiredError(error)) {
        return;
      }
      const message = error instanceof Error ? error.message : '停止候选生成失败';
      setCandidateStatus('error');
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleEditParagraphCandidate(candidate: AiParagraphCandidate, candidateText: string) {
    if (!draft) {
      return;
    }
    setParagraphCandidates((current) => current.map((item) => (
      item.id === candidate.id ? { ...item, candidateText, status: 'EDITED' } : item
    )));
    try {
      const updatedCandidate = await updateParagraphCandidateText(draft.id, candidate.id, candidateText);
      upsertParagraphCandidate(updatedCandidate);
    } catch (error) {
      if (handleAuthenticationRequiredError(error)) {
        return;
      }
      const message = error instanceof Error ? error.message : '候选段落编辑保存失败';
      setParagraphCandidates((current) => current.map((item) => (
        item.id === candidate.id ? { ...item, errorMessage: message } : item
      )));
    }
  }

  async function handleRetryParagraphCandidate(candidate: AiParagraphCandidate) {
    if (!draft) {
      return;
    }
    setParagraphCandidates((current) => current.map((item) => (
      item.id === candidate.id ? { ...item, status: 'RETRYING', errorMessage: '' } : item
    )));
    try {
      const updatedCandidate = await retryParagraphCandidate(draft.id, candidate.id);
      upsertParagraphCandidate(updatedCandidate);
    } catch (error) {
      if (handleAuthenticationRequiredError(error)) {
        return;
      }
      const message = error instanceof Error ? error.message : '候选段落重试失败';
      setParagraphCandidates((current) => current.map((item) => (
        item.id === candidate.id ? { ...item, status: 'ERROR', errorMessage: message } : item
      )));
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleDiscardParagraphCandidate(candidate: AiParagraphCandidate) {
    if (!draft) {
      return;
    }
    try {
      const updatedCandidate = await discardParagraphCandidate(draft.id, candidate.id);
      if (updatedCandidate.status === 'DISCARDED') {
        setParagraphCandidates((current) => current.filter((item) => item.id !== updatedCandidate.id));
      } else {
        upsertParagraphCandidate(updatedCandidate);
      }
      showToast({ title: '候选段落已放弃', description: candidate.heading, tone: 'info' });
    } catch (error) {
      if (handleAuthenticationRequiredError(error)) {
        return;
      }
      const message = error instanceof Error ? error.message : '候选段落放弃失败';
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleAcceptParagraphCandidate(candidate: AiParagraphCandidate) {
    if (!draft) {
      return;
    }
    try {
      const response = await acceptParagraphCandidate(draft.id, candidate.id);
      applyAcceptedCandidateDraft(response.draft, response.node);
      const refreshedNodes = draft.templateVersionId ? await listDraftNodes(draft.id).catch(() => null) : null;
      if (refreshedNodes) {
        setDraftNodes(refreshedNodes);
        setDirtyDraftNodeIds(new Set());
      }
      upsertParagraphCandidate(response.candidate);
      markRenderPreviewOutdated();
      setStatus('saved');
      setStatusMessage('候选正文已采纳，真实预览待刷新');
      showToast({ title: '候选段落已采纳', description: candidate.heading, tone: 'success' });
    } catch (error) {
      if (handleAuthenticationRequiredError(error)) {
        return;
      }
      const message = error instanceof Error ? error.message : '候选段落采纳失败';
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleAcceptParagraphCandidateBatch(candidates: AiParagraphCandidate[]) {
    if (!draft || candidates.length === 0) {
      return;
    }
    const confirmed = window.confirm(`批量采纳 ${candidates.length} 条候选段落并替换正文？`);
    if (!confirmed) {
      return;
    }

    try {
      const response = await acceptParagraphCandidateBatch(draft.id, candidates.map((candidate) => candidate.id));
      applyAcceptedCandidateDraft(response.updatedDraft, null);
      const refreshedNodes = draft.templateVersionId ? await listDraftNodes(draft.id).catch(() => null) : null;
      if (refreshedNodes) {
        setDraftNodes(refreshedNodes);
        setDirtyDraftNodeIds(new Set());
      }
      await refreshParagraphCandidates(draft.id);
      markRenderPreviewOutdated();
      setStatus('saved');
      setStatusMessage('候选正文已批量采纳，真实预览待刷新');
      showToast({ title: '候选段落已批量采纳', description: `${response.acceptedIds.length} 条已替换`, tone: 'success' });
    } catch (error) {
      if (handleAuthenticationRequiredError(error)) {
        return;
      }
      const message = error instanceof Error ? error.message : '候选段落批量采纳失败';
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleGenerateLocalOperation() {
    if (!draft) {
      return;
    }
    if (selectedNodeActionKind === 'quality-check') {
      openQualityDialog();
      return;
    }
    if (selectedNodeActionKind !== 'local-operation' || !selectedNodeCanTargetLocalOperation) {
      setActiveAiDialog('local');
      setLocalOperationStatus('error');
      setLocalOperationError(selectedNode ? '请先保存当前结构节点后再生成建议' : '请先在预览中选择可编辑结构');
      return;
    }

    const controller = startAiRequest(localOperationRequestRef);
    try {
      setLocalOperationStatus('generating');
      setLocalOperationError('');
      setLocalOperationSuggestion(null);
      const target = selectedNodeAiContext ?? selectedBodyBlock?.id;
      if (!target) {
        throw new Error('请先保存当前结构节点后再生成建议');
      }
      const suggestion = await generateLocalOperation(
        draft.id,
        target,
        localOperationType,
        localOperationInstruction,
        controller.signal,
      );
      setLocalOperationSuggestion(suggestion);
      setLocalOperationStatus('suggested');
      showToast({ title: selectedNodeAiContext ? '节点建议已生成' : '段落建议已生成', tone: 'success' });
    } catch (error) {
      if (isAbortError(error)) {
        setLocalOperationStatus('idle');
        setLocalOperationError('');
        showToast({ title: '段落建议生成已取消', tone: 'info' });
        return;
      }
      if (handleAuthenticationRequiredError(error)) {
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
      if (handleAuthenticationRequiredError(error)) {
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

  async function handleExportWord() {
    if (!draft) {
      return;
    }
    try {
      setExportStatus('exporting');
      setExportError('');
      const updatedDraft = await saveCurrentDraft('导出前保存当前草稿');
      const exportDraftId = updatedDraft?.id ?? draft.id;
      const result = await exportDraftWord(exportDraftId);
      downloadBlob(result.blob, result.fileName);
      setExportStatus('success');
      showToast({ title: 'Word 已导出', description: result.fileName, tone: 'success' });
    } catch (error) {
      const message = exportWordErrorMessage(error);
      setExportStatus('error');
      setExportError(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  function exportWordErrorMessage(error: unknown) {
    const message = error instanceof Error ? error.message : '';
    if (
      message.includes('发布映射前必须确认模板结构映射')
      || message.includes('导出前需要当前模板版本已有已发布的结构映射')
      || message.includes('Structure mapping')
    ) {
      return '导出前需要先发布当前模板的结构映射。请到模板解析工作台点击“保存草稿”并“发布映射”，再回工作台重建结构后导出。';
    }
    return message || 'Word 导出失败';
  }

  async function handleDownloadExportRecord(record: ExportRecordSummary) {
    if (!record.canDownload) {
      return;
    }
    try {
      setDownloadingExportRecordId(record.id);
      const result = await downloadExportRecord(record.id);
      downloadBlob(result.blob, result.fileName);
      showToast({ title: '导出文件已下载', description: result.fileName, tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '导出文件下载失败';
      showToast({ title: message, tone: 'error' });
    } finally {
      setDownloadingExportRecordId(null);
    }
  }

  async function handleOpenExportRecordDetail(record: ExportRecordSummary) {
    setExportRecordDetail(null);
    setExportRecordDetailStatus('loading');
    setExportRecordDetailMessage('正在加载导出详情');
    try {
      const detail = await getExportRecordDetail(record.id);
      setExportRecordDetail(detail);
      setExportRecordDetailStatus('idle');
      setExportRecordDetailMessage('导出详情已加载');
    } catch (error) {
      setExportRecordDetailStatus('error');
      setExportRecordDetailMessage(error instanceof Error ? error.message : '导出详情加载失败');
    }
  }

  function handleCloseExportRecordDetail() {
    if (retryingExportRecordId !== null) {
      return;
    }
    setExportRecordDetail(null);
    setExportRecordDetailStatus('idle');
    setExportRecordDetailMessage('');
  }

  async function handleRetryExportRecord(record: ExportRecordSummary | ExportRecordDetail) {
    if (!record.canRetry) {
      return;
    }
    try {
      setRetryingExportRecordId(record.id);
      const result = await retryExportRecord(record.id);
      downloadBlob(result.blob, result.fileName);
      setExportRecordDetailMessage('重试导出已完成');
      showToast({ title: '重试导出已完成', description: result.fileName, tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '重试导出失败';
      setExportRecordDetailMessage(message);
      showToast({ title: message, tone: 'error' });
    } finally {
      setRetryingExportRecordId(null);
    }
  }

  async function handleAcceptLocalOperation() {
    if (!draft || !localOperationSuggestion) {
      return;
    }

    try {
      setLocalOperationStatus('saving');
      const targetNodeId = localOperationSuggestion.targetNodeId ?? selectedNodeAiContext?.nodeId ?? null;
      if (targetNodeId) {
        const updatedNode = await saveDraftNode(
          draft.id,
          targetNodeId,
          localOperationSuggestion.suggestionText,
          'USER_MODIFIED_AFTER_AI',
        );
        syncGeneratedDraftNodes(undefined, updatedNode);
        if (selectedNode) {
          setSelectedNodeId(selectedNode.nodeId);
        }
        setLocalOperationSuggestion(null);
        setLocalOperationStatus('saved');
        markRenderPreviewOutdated();
        setStatus('saved');
        setStatusMessage('节点建议已采纳并保存');
        showToast({ title: '建议已采纳', tone: 'success' });
        return;
      }
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
      if (selectedBodyNode) {
        setSelectedNodeId(selectedBodyNode.nodeId);
      }
      setLocalOperationSuggestion(null);
      setLocalOperationStatus('saved');
      markRenderPreviewOutdated();
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

  async function handleSaveNodeFormatOverride(formatOverride: DraftNodeFormatOverride) {
    if (!draft || !selectedNode?.draftNodeId) {
      setNodeFormatStatus('error');
      setNodeFormatError('请先选择可编辑结构节点');
      return;
    }
    try {
      setNodeFormatStatus('saving');
      setNodeFormatError('');
      const updatedNode = await saveDraftNodeFormatOverride(draft.id, selectedNode.draftNodeId, formatOverride);
      syncGeneratedDraftNodes(undefined, updatedNode);
      markRenderPreviewOutdated();
      setNodeFormatStatus('saved');
      setStatus('saved');
      setStatusMessage('节点格式已保存');
      showToast({ title: '节点格式已保存', tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '节点格式保存失败';
      setNodeFormatStatus('error');
      setNodeFormatError(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleRestoreNodeFormatOverride() {
    if (!draft || !selectedNode?.draftNodeId) {
      setNodeFormatStatus('error');
      setNodeFormatError('请先选择可编辑结构节点');
      return;
    }
    try {
      setNodeFormatStatus('restoring');
      setNodeFormatError('');
      const updatedNode = await restoreDraftNodeFormatOverride(draft.id, selectedNode.draftNodeId);
      syncGeneratedDraftNodes(undefined, updatedNode);
      markRenderPreviewOutdated();
      setNodeFormatStatus('restored');
      setStatus('saved');
      setStatusMessage('已恢复模板默认格式');
      showToast({ title: '已恢复模板默认格式', tone: 'success' });
    } catch (error) {
      const message = error instanceof Error ? error.message : '恢复模板默认格式失败';
      setNodeFormatStatus('error');
      setNodeFormatError(message);
      showToast({ title: message, tone: 'error' });
    }
  }

  async function handleRefreshWorkbenchPreview(silent = false) {
    if (!draft?.templateVersionId) {
      setWorkbenchRenderPreviewStatus('error');
      setWorkbenchRenderPreviewMessage('请先选择套版模板');
      return;
    }
    try {
      setWorkbenchRenderPreviewStatus('requesting');
      setWorkbenchRenderPreviewMessage('正在刷新真实预览');
      const updatedDraft = await saveCurrentDraft('正在同步真实预览');
      if (!updatedDraft) {
        return;
      }
      const requestedPreview = await requestDraftRenderPreview(updatedDraft.id);
      setWorkbenchRenderPreview(requestedPreview);
      const preview = await waitForDraftRenderPreview(updatedDraft.id, requestedPreview);
      const resultMessage = renderPreviewResultMessage(preview);
      setWorkbenchRenderPreview(preview);
      setRenderPreviewOutdated(!isRenderPreviewTerminal(preview));
      setWorkbenchRenderPreviewStatus('idle');
      setWorkbenchRenderPreviewMessage(resultMessage);
      if (!silent) {
        showToast({
          title: preview.status === 'READY'
            ? '真实预览已刷新'
            : preview.status === 'FAILED' || preview.status === 'UNSUPPORTED'
              ? '真实预览不可用'
              : '预览任务已提交',
          description: resultMessage,
          tone: preview.status === 'READY' ? 'success' : preview.status === 'FAILED' || preview.status === 'UNSUPPORTED' ? 'error' : 'info',
        });
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '真实预览刷新失败';
      setWorkbenchRenderPreviewStatus('error');
      setWorkbenchRenderPreviewMessage(message);
      if (!silent) {
        showToast({ title: message, tone: 'error' });
      }
    }
  }

  async function waitForDraftRenderPreview(draftId: number, initialPreview: DocumentRenderPreview) {
    let latestPreview = initialPreview;
    for (let attempt = 0; attempt < 8; attempt += 1) {
      if (isRenderPreviewTerminal(latestPreview)) {
        return latestPreview;
      }
      await delay(1200);
      const polledPreview = await getDraftRenderPreview(draftId).catch(() => null);
      if (polledPreview) {
        latestPreview = polledPreview;
      }
    }
    return latestPreview;
  }

  function isRenderPreviewTerminal(preview: DocumentRenderPreview) {
    return preview.status === 'READY' || preview.status === 'FAILED' || preview.status === 'UNSUPPORTED';
  }

  function delay(ms: number) {
    return new Promise<void>((resolve) => window.setTimeout(resolve, ms));
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
    <div className={`app-shell ${activeView === 'workbench' ? 'app-shell--workbench-focus' : ''}`}>
      {activeView !== 'workbench' && (
        <aside className="app-sidebar">
          <div className="sidebar-brand">
            <div className="sidebar-mark">文</div>
            <div>
              <div className="brand-title">公文助手</div>
              <div className="brand-subtitle">AI 公文工作台</div>
            </div>
          </div>
          <nav className="sidebar-nav" aria-label="主导航">
            {visibleNavItems.map((item) => {
              const Icon = item.icon;
              return (
                <button
                  aria-current={activeView === item.view ? 'page' : undefined}
                  aria-label={item.label}
                  className="nav-item"
                  key={item.view}
                  onClick={() => handleSidebarNavigate(item.view)}
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
          <section aria-label="当前账号" className="sidebar-account">
            <div className="sidebar-account-row">
              <span className="sidebar-account-name" title={currentUser.displayName}>
                {currentUser.displayName}
              </span>
              <Button
                aria-label="退出登录"
                className="sidebar-account-action"
                icon={<LogOut aria-hidden="true" />}
                iconOnly
                onClick={() => {
                  void logout()
                    .catch(() => undefined)
                    .finally(onLogout);
                }}
                variant="ghost"
              >
                退出登录
              </Button>
            </div>
          </section>
        </aside>
      )}

      <div className="app-main">
        <header className="app-header">
          <div className={`header-title-group ${activeView === 'workbench' ? 'header-title-group-workbench' : ''}`}>
            <div className="header-title-copy">
              <div className="header-title-row">
                <h1 className="page-title">{activeView === 'workbench' ? '公文工作台' : viewTitle(activeView)}</h1>
                {activeView === 'workbench' && (
                  <Button icon={<ArrowLeft aria-hidden="true" />} onClick={handleReturnToDraftDirectory} variant="secondary">
                    回到目录
                  </Button>
                )}
              </div>
              <p className="brand-subtitle">{activeView === 'workbench' ? '通知 / 请示 / 报告起草工作台' : viewSubtitle(activeView)}</p>
            </div>
          </div>
          <div className="header-actions">
            {activeView === 'workbench' && (
              <Button
                icon={<LogOut aria-hidden="true" />}
                onClick={() => {
                  void logout()
                    .catch(() => undefined)
                    .finally(onLogout);
                }}
                variant="ghost"
              >
                退出
              </Button>
            )}
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
                <Button
                  disabled={!draft || !draft.templateVersionId || exportStatus === 'exporting' || status === 'loading'}
                  icon={<FileDown aria-hidden="true" />}
                  isLoading={exportStatus === 'exporting'}
                  loadingLabel="正在导出"
                  onClick={() => void handleExportWord()}
                >
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
            <WorkbenchContextPanel
              attachment={attachment}
              blockCount={blocks.length}
              bodySectionCount={bodySectionNodes.length}
              currentDocumentTypeCode={currentWorkbenchDocumentTypeCode}
              currentDocumentTypeName={currentDocumentType?.name ?? '通知'}
              date={date}
              documentTypes={documentTypes}
              draft={draft}
              latestTemplateVersions={latestTemplateVersions}
              materialStatus={materialStatus}
              materials={materials}
              nodeCount={workbenchNodes.length}
              onDocumentTypeChange={(documentTypeCode) => void handleWorkbenchDocumentTypeChange(documentTypeCode)}
              onFieldChange={updateBlock}
              onMaterialUpload={handleMaterialUpload}
              onTemplateVersionChange={(templateVersionId) => void handleTemplateVersionChange(templateVersionId)}
              recipient={recipient}
              signature={signature}
              status={status}
              structureTree={(
                <WorkbenchStructureTree
                  bodySectionNodes={bodySectionNodes}
                  canInsertBodyStructure={Boolean(draft?.templateVersionId) && workbenchRenderPreviewStatus !== 'requesting'}
                  canReinitialize={Boolean(draft?.templateVersionId) && status !== 'loading'}
                  insertableRoles={insertableBodyRoles}
                  nodes={workbenchNodes}
                  onInsertBodyStructure={(request) => void handleInsertBodyStructure(request)}
                  onReinitialize={(preserveUserEditedNodes) => void handleReinitializeDraftNodes(preserveUserEditedNodes)}
                  onRemoveBodyNode={removeBodyNode}
                  onSelectNode={selectNode}
                  reinitializeMessage={reinitializeNodeMessage}
                  reinitializeStatus={reinitializeNodeStatus}
                  selectedNodeId={selectedNodeId}
                />
              )}
              title={title}
            />

            <WorkbenchPreview
              attachment={attachment}
              attachmentNode={attachmentNode}
              attachmentStyle={attachmentNodePreviewStyle}
              bodySectionNodes={bodySectionNodes}
              bodyStyleForNode={(node) => mergePreviewStyle(bodyPreviewStyle, node.formatting)}
              date={date}
              dateNode={dateNode}
              dateStyle={dateNodePreviewStyle}
              isRefreshingRenderPreview={workbenchRenderPreviewStatus === 'requesting'}
              nodes={workbenchNodes}
              onRefreshRenderPreview={handleRefreshWorkbenchPreview}
              renderPreview={workbenchRenderPreview}
              renderPreviewOutdated={renderPreviewOutdated}
              onRemoveBodyNode={removeBodyNode}
              onSelectNode={(nodeId) => selectNode(nodeId, false)}
              onUpdateAttachment={(content) => updateWorkbenchNodeContent(attachmentNode, content)}
              onUpdateBodyContent={updateBodyNodeContent}
              onUpdateBodyHeading={updateBodyNodeHeading}
              onUpdateDate={(content) => updateWorkbenchNodeContent(dateNode, content)}
              onUpdateNodeContent={updateWorkbenchNodeContent}
              onUpdateRecipient={(content) => updateWorkbenchNodeContent(recipientNode, content)}
              onUpdateSignature={(content) => updateWorkbenchNodeContent(signatureNode, content)}
              onUpdateTitle={(content) => updateWorkbenchNodeContent(titleNode, content)}
              recipient={recipient}
              recipientNode={recipientNode}
              recipientStyle={recipientNodePreviewStyle}
              registerNodeRef={(nodeId, element) => {
                paragraphRefs.current[nodeId] = element;
              }}
              selectedNodeId={selectedNodeId}
              signature={signature}
              signatureNode={signatureNode}
              signatureStyle={signatureNodePreviewStyle}
              syncParagraphEditorHeight={syncParagraphEditorHeight}
              title={title}
              titleNode={titleNode}
              titleStyle={titleNodePreviewStyle}
            />

            <WorkbenchInspectorPanel
              activeSection={rightInspectorSection}
              blockCount={blocks.length}
              candidateStatus={candidateStatus}
              draftId={draft?.id ?? null}
              exportSlot={(
                <WorkbenchExportPanel
                  exportError={exportError}
                  exportStatus={exportStatus}
                  hasDraft={Boolean(draft)}
                  isWorkbenchLoading={status === 'loading'}
                  onExportWord={() => void handleExportWord()}
                  onRefreshPreview={() => void handleRefreshWorkbenchPreview()}
                  preview={workbenchRenderPreview}
                  previewMessage={workbenchRenderPreviewMessage}
                  previewOutdated={renderPreviewOutdated}
                  previewStatus={workbenchRenderPreviewStatus}
                  templateVersionId={draft?.templateVersionId ?? null}
                />
              )}
              exportStatus={exportStatus}
              formatSlot={(
                <NodeFormatPanel
                  disabled={selectedNodeFormatDisabled}
                  effectiveFormatting={selectedDraftNode?.effectiveFormatting ?? selectedNode?.formatting ?? null}
                  error={nodeFormatError}
                  formatOverride={selectedDraftNode?.formatOverride ?? null}
                  nodeLabel={selectedNodeFormatLabel}
                  onRestore={() => void handleRestoreNodeFormatOverride()}
                  onSave={(formatOverride) => void handleSaveNodeFormatOverride(formatOverride)}
                  previewOutdated={renderPreviewOutdated}
                  status={nodeFormatStatus}
                />
              )}
              localOperationSlot={(
                <div className="local-operation" aria-label="局部段落操作">
                  {selectedNodeSupportsLocalOperation ? (
                    <>
                      <div className="operation-grid" role="group" aria-label="局部操作类型">
                        {selectedLocalOperationOptions.map((option) => (
                          <button
                            aria-pressed={localOperationType === option.value}
                            className={`operation-choice ${localOperationType === option.value ? 'selected' : ''}`}
                            disabled={localOperationStatus === 'generating' || localOperationStatus === 'saving'}
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
                    </>
                  ) : (
                    <StatusMessage title={selectedNode ? '该结构节点建议先通过质检确认，不直接改写正文。' : '未选择结构时，可使用上方提纲生成、基础质检和 Word 导出。'} />
                  )}
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
              )}
              materialCount={materials.length}
              node={selectedNode}
              nodeCount={workbenchNodes.length}
              nodeKicker={selectedNodePanelKicker}
              nodeTitle={selectedNodePanelTitle}
              onSectionChange={setRightInspectorSection}
              outlineSlot={(
                <>
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
                </>
              )}
              outlineStatus={outlineStatus}
              primaryAction={(
                <Button
                  disabled={selectedNodeActionDisabled}
                  icon={selectedNodeActionKind === 'quality-check' ? <CheckCircle2 aria-hidden="true" /> : <Sparkles aria-hidden="true" />}
                  isLoading={selectedNodeActionLoading}
                  loadingLabel={selectedNodeActionKind === 'quality-check' ? '正在质检' : '正在生成建议'}
                  onClick={openLocalOperationDialog}
                  variant="secondary"
                >
                  {selectedNodeActionButtonLabel}
                </Button>
              )}
              qualitySlot={(
                <WorkbenchQualityPanel
                  disabled={!draft || qualityCheckStatus === 'checking' || status === 'loading'}
                  error={qualityCheckError}
                  onOpenResult={() => setActiveAiDialog('quality')}
                  onRun={openQualityDialog}
                  result={qualityCheck}
                  status={qualityCheckStatus}
                />
              )}
              qualityStatus={qualityCheckStatus}
              renderPreviewOutdated={renderPreviewOutdated}
              statusMessage={statusMessage}
              statusTone={status === 'error' ? 'warning' : status === 'loading' ? 'info' : 'success'}
            />
          </main>
        )}

        {activeView !== 'overview' && activeView !== 'workbench' && (
          activeView === 'settings' ? (
            <SystemSettingsPage
              apiKey={aiSettingsApiKey}
              currentUser={currentUser}
              message={aiSettingsMessage}
              onApiKeyChange={setAiSettingsApiKey}
              onSave={() => void handleSaveAiSettings()}
              onSettingsChange={updateAiSettingsDraft}
              onTest={() => void handleTestAiConnection()}
              providerStatus={aiProviderStatus}
              settings={aiSettings}
              status={aiSettingsStatus}
            />
          ) : activeView === 'drafts' ? (
            <DraftListPage
              currentDraftId={draft?.id ?? null}
              documentTypes={documentTypes}
              drafts={draftSummaries}
              message={draftListMessage}
              onCreateBlankDraft={(documentTypeCode) => void createBlankDraftInDraftList(documentTypeCode)}
              onDeleteDraft={(draftId) => deleteDraftFromList(draftId)}
              onDocumentTypesChange={setDocumentTypes}
              onOpenDraft={(draftId) => void openDraftInWorkbench(draftId)}
              onRenameDraft={(draftId, title) => renameDraftFromList(draftId, title)}
              onSelectDocumentType={setSelectedDraftDocumentTypeCode}
              preferredPageMode={draftListPageMode}
              selectedDocumentTypeCode={selectedDraftDocumentTypeCode}
              status={draftListStatus}
            />
          ) : activeView === 'templates' ? (
            <TemplateManagementPage
              defaultDocumentTypeCode={draft?.documentTypeCode ?? 'NOTICE'}
              documentTypes={documentTypes}
              onDocumentTypesChange={setDocumentTypes}
              structureOverrides={templateStructureOverrides}
              onStructureOverridesLoaded={(templateVersionId, overrides) => {
                setTemplateStructureOverrides((current) => ({
                  ...current,
                  [templateVersionId]: overrides,
                }));
              }}
              onStructureOverrideChange={async (templateVersionId, structureKey, nextOverride) => {
                await updateTemplateStructureFormatting(templateVersionId, structureKey, nextOverride);
                setTemplateStructureOverrides((current) => ({
                  ...current,
                  [templateVersionId]: {
                    ...(current[templateVersionId] ?? {}),
                    [structureKey]: nextOverride,
                  },
                }));
                if (draft?.templateVersionId === templateVersionId && selectedTemplateProfile) {
                  setSelectedTemplateProfile({ ...selectedTemplateProfile });
                }
                showToast({ title: '结构维度已保存', tone: 'success' });
              }}
              onTemplateVersionCreated={async () => {
                if (draft) {
                  setTemplateVersions(await listTemplateVersions(draft.documentTypeCode));
                }
              }}
            />
          ) : activeView === 'exports' ? (
            <ExportRecordsPage
              downloadingRecordId={downloadingExportRecordId}
              message={exportRecordMessage}
              onDownload={(record) => void handleDownloadExportRecord(record)}
              onOpenDetail={(record) => void handleOpenExportRecordDetail(record)}
              onRetry={(record) => void handleRetryExportRecord(record)}
              records={exportRecords}
              retryingRecordId={retryingExportRecordId}
              status={exportRecordStatus}
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
          className="ai-task-dialog outline-task-dialog"
          description="提纲结果、缺失信息和正文生成入口集中在这里，不再撑高右侧面板。"
          onClose={closeAiDialog}
          open={activeAiDialog === 'outline'}
          title="生成提纲"
        >
          <div className="ai-dialog-stack">
            {outlineStatus === 'generating' && (
              <AiProgress detail="正在分析文种字段、草稿块和参考材料" label="生成提纲进度" value={outlineProgress} />
            )}
            {outlineStatus === 'error' && (
              <StatusMessage title={outlineError} tone="warning">
                <Button icon={<Sparkles aria-hidden="true" />} onClick={() => void handleGenerateOutline()} variant="secondary">
                  重试生成提纲
                </Button>
              </StatusMessage>
            )}
            {outline && (
              <div className="outline-dialog-grid">
                <div className="outline-result" aria-label="AI 提纲结果">
                  <div className="outline-result-header">
                    <div className="outline-title">{outline.titleSuggestion}</div>
                    <Button
                      className="outline-generate-all"
                      disabled={!draft || isCandidateGenerating || outline.sections.length === 0}
                      icon={<Sparkles aria-hidden="true" />}
                      isLoading={isOutlineBatchGenerating}
                      loadingLabel="正在生成正文候选"
                      onClick={() => void handleGenerateAllParagraphs()}
                      variant="secondary"
                    >
                      生成全部正文候选
                    </Button>
                  </div>
                  {allParagraphStatus === 'generating' && (
                    <AiProgress detail="按提纲顺序逐段生成正文候选" label="正文生成进度" value={allParagraphProgress} />
                  )}
                  {allParagraphStatus === 'error' && <StatusMessage title={allParagraphError} tone="warning" />}
                  {outline.sections.map((section, index) => {
                    const isSectionGenerating = outlineSectionIsGenerating(index);
                    return (
                      <div className="outline-section" key={section.heading}>
                        <div className="outline-heading">{section.heading}</div>
                        <ul>
                          {section.points.map((point) => <li key={point}>{point}</li>)}
                        </ul>
                        <Button
                          className="outline-action"
                          disabled={!draft || isCandidateGenerating}
                          icon={<Sparkles aria-hidden="true" />}
                          isLoading={isSectionGenerating}
                          loadingLabel={`正在生成候选：${section.heading}`}
                          onClick={() => void handleGenerateParagraph(index)}
                          variant="secondary"
                        >
                          生成正文候选：{section.heading}
                        </Button>
                        {paragraphStatuses[section.heading] === 'error' && (
                          <StatusMessage title={paragraphErrors[section.heading]} tone="warning" />
                        )}
                      </div>
                    );
                  })}
                  {outline.missingInformation.length > 0 && (
                    <div className="outline-missing">
                      缺失信息：{outline.missingInformation.join('、')}
                    </div>
                  )}
                </div>

                <section className="outline-body-stream-panel" aria-label="正文生成预览">
                  <div className="outline-body-stream-header">
                    <div>
                      <div className="outline-title">正文生成</div>
                      <div className="panel-kicker">
                        {outlineDialogCandidates.length > 0
                          ? `${outlineDialogCandidates.length} 段，${outlineDialogCandidateTextCount} 段已有内容`
                          : '等待生成'}
                      </div>
                    </div>
                    <div className="outline-body-stream-toolbar">
                      <Button
                        disabled={!draft || isCandidateGenerating || outlineDialogAcceptableCandidates.length === 0}
                        icon={<Check aria-hidden="true" />}
                        onClick={() => void handleAcceptParagraphCandidateBatch(outlineDialogAcceptableCandidates)}
                        variant="secondary"
                      >
                        批量确认
                      </Button>
                      <Button
                        disabled={!draft || !isCandidateGenerating}
                        icon={<X aria-hidden="true" />}
                        onClick={() => void handleStopParagraphCandidateJob()}
                        variant="ghost"
                      >
                        停止
                      </Button>
                    </div>
                  </div>

                  {outlineDialogCandidates.length === 0 ? (
                    <StatusMessage title="暂无正文内容" tone="info">
                      点击左侧生成全部正文候选，或选择单段生成。
                    </StatusMessage>
                  ) : (
                    <div className="outline-body-stream-list">
                      {outlineDialogCandidates.map((candidate) => {
                        const bodyText = candidateBodyText(candidate);
                        const canAcceptCandidate = (candidate.status === 'READY' || candidate.status === 'EDITED')
                          && hasCandidateBodyText(candidate);
                        const hasTitleOnlyResult = (candidate.status === 'READY' || candidate.status === 'EDITED')
                          && !hasCandidateBodyText(candidate);
                        return (
                          <article className="outline-body-stream-item" key={candidate.id}>
                            <div className="outline-body-stream-item-header">
                              <div className="outline-heading">{candidate.heading || `第 ${candidate.sectionIndex + 1} 段`}</div>
                              <span className="paragraph-candidate-status">
                                {outlineCandidateStatusLabel(candidate.status)}
                              </span>
                            </div>
                            {candidate.status === 'ERROR' && candidate.errorMessage ? (
                              <StatusMessage title={candidate.errorMessage} tone="warning" />
                            ) : null}
                            {!candidate.targetNodeId ? (
                              <StatusMessage title="将按提纲顺序匹配正文结构" tone="info">
                                这是旧候选，确认时会自动匹配对应正文小标题后的正文节点。
                              </StatusMessage>
                            ) : null}
                            {hasTitleOnlyResult ? (
                              <StatusMessage title="未生成正文内容" tone="warning">
                                当前候选只包含标题，请重新生成这一段。
                              </StatusMessage>
                            ) : null}
                            {bodyText ? (
                              <div className="outline-body-stream-text">{bodyText}</div>
                            ) : (
                              <div className="paragraph-candidate-skeleton" aria-hidden="true">
                                <span />
                                <span />
                                <span />
                              </div>
                            )}
                            {candidate.status === 'READY' || candidate.status === 'EDITED' ? (
                              <div className="outline-body-stream-actions">
                                <Button
                                  disabled={!draft || isCandidateGenerating || !canAcceptCandidate}
                                  icon={<Check aria-hidden="true" />}
                                  onClick={() => void handleAcceptParagraphCandidate(candidate)}
                                  variant="secondary"
                                >
                                  确认替换
                                </Button>
                              </div>
                            ) : null}
                          </article>
                        );
                      })}
                    </div>
                  )}
                </section>
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
              <AiProgress detail="正在检查必填字段、正文结构、材料依据和表达风险" label="运行质检进度" value={qualityProgress} />
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
          description={localOperationDialogDescription(selectedNode, selectedBodyNode, bodySectionNodes, selectedNodeAiContext)}
          onClose={closeAiDialog}
          open={activeAiDialog === 'local'}
          title={localOperationDialogTitle(selectedNodeAiContext)}
        >
          <div className="ai-dialog-stack">
            {localOperationStatus === 'generating' && (
              <AiProgress
                detail={`正在生成${localOperationLabel(localOperationType)}建议，不会直接覆盖原文`}
                label={`${localOperationDialogTitle(selectedNodeAiContext)}进度`}
                value={localOperationProgress}
              />
            )}
            {localOperationError && (
              <StatusMessage title={localOperationError} tone="warning">
                {selectedNodeActionKind === 'local-operation' && selectedNodeCanTargetLocalOperation ? (
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
              <StatusMessage title={`点击右栏${selectedNodeActionButtonLabel}后，建议文本会显示在这里。`} />
            )}
          </div>
        </Dialog>

        <ExportRecordDetailDialog
          detail={exportRecordDetail}
          isRetrying={retryingExportRecordId === exportRecordDetail?.id}
          message={exportRecordDetailMessage}
          onClose={handleCloseExportRecordDetail}
          onRetry={(record) => void handleRetryExportRecord(record)}
          open={exportRecordDetailStatus === 'loading' || exportRecordDetail !== null || exportRecordDetailStatus === 'error'}
          status={exportRecordDetailStatus}
        />

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

function TemplateManagementPage({
  defaultDocumentTypeCode,
  documentTypes,
  onDocumentTypesChange,
  structureOverrides,
  onStructureOverridesLoaded,
  onStructureOverrideChange,
  onTemplateVersionCreated,
}: {
  defaultDocumentTypeCode: string;
  documentTypes: DocumentType[];
  onDocumentTypesChange: (documentTypes: DocumentType[]) => void;
  structureOverrides: TemplateStructureOverridesByVersion;
  onStructureOverridesLoaded: (templateVersionId: number, overrides: TemplateStructureOverrideMap) => void;
  onStructureOverrideChange: (
    templateVersionId: number,
    structureKey: string,
    nextOverride: TemplateStructureOverride,
  ) => Promise<void>;
  onTemplateVersionCreated: () => Promise<void>;
}) {
  const { showToast } = useToast();
  const fallbackDocumentTypes = documentTypes.length > 0
    ? documentTypes
    : [{ code: defaultDocumentTypeCode, name: '通知', status: 'ACTIVE', sortOrder: 1 }];
  const [documentTypeCode, setDocumentTypeCode] = useState<string | null>(null);
  const [pageMode, setPageMode] = useState<'folders' | 'list' | 'create'>('folders');
  const [templateName, setTemplateName] = useState('');
  const [templates, setTemplates] = useState<TemplateSummary[]>([]);
  const [versions, setVersions] = useState<TemplateVersionSummary[]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [profile, setProfile] = useState<TemplateProfile | null>(null);
  const [structureProfile, setStructureProfile] = useState<DocumentStructureProfile | null>(null);
  const [documentKind, setDocumentKind] = useState<TemplateDocumentKind | null>(null);
  const [renderPreview, setRenderPreview] = useState<DocumentRenderPreview | null>(null);
  const [renderPreviewStatus, setRenderPreviewStatus] = useState<'idle' | 'loading' | 'requesting' | 'error'>('idle');
  const [renderPreviewMessage, setRenderPreviewMessage] = useState('');
  const [mappingProfile, setMappingProfile] = useState<StructureMappingProfile | null>(null);
  const [mappingItems, setMappingItems] = useState<StructureMappingItem[]>([]);
  const [mappingStatus, setMappingStatus] = useState<'idle' | 'loading' | 'saving' | 'publishing' | 'blocked' | 'error'>('idle');
  const [mappingMessage, setMappingMessage] = useState('');
  const [profileContext, setProfileContext] = useState<{
    templateVersionId: number;
    templateName: string;
    versionNo: number;
    originalFileName: string;
  } | null>(null);
  const [uploadResult, setUploadResult] = useState<TemplateUploadResult | null>(null);
  const [selectedTemplateFile, setSelectedTemplateFile] = useState<File | null>(null);
  const [templateToDelete, setTemplateToDelete] = useState<TemplateSummary | null>(null);
  const [isDeletingTemplate, setIsDeletingTemplate] = useState(false);
  const [status, setStatus] = useState<'idle' | 'loading' | 'uploading' | 'error'>('loading');
  const [message, setMessage] = useState('正在加载模板');
  const activeDocumentType = fallbackDocumentTypes.find((type) => type.code === documentTypeCode);
  const activeDocumentTypeCode = documentTypeCode ?? defaultDocumentTypeCode;
  const versionsByTemplateId = useMemo(() => {
    const grouped = new Map<number, TemplateVersionSummary[]>();
    versions.forEach((version) => {
      const items = grouped.get(version.templateId) ?? [];
      items.push(version);
      grouped.set(version.templateId, items);
    });
    grouped.forEach((items) => {
      items.sort((left, right) => right.versionNo - left.versionNo);
    });
    return grouped;
  }, [versions]);

  useEffect(() => {
    if (!documentTypeCode) {
      setStatus('idle');
      setMessage('请选择文种');
      return undefined;
    }
    const code = documentTypeCode;
    let mounted = true;
    async function loadTemplates() {
      try {
        setStatus('loading');
        const [loadedTemplates, loadedVersions] = await Promise.all([
          listTemplates(code),
          listTemplateVersions(code),
        ]);
        if (!mounted) {
          return;
        }
        setTemplates(loadedTemplates);
        setVersions(loadedVersions);
        setSelectedTemplateId((current) => {
          if (current && loadedTemplates.some((template) => template.id === current)) {
            return current;
          }
          return null;
        });
        setStatus('idle');
        setMessage('模板已加载');
      } catch (error) {
        if (!mounted) {
          return;
        }
        const errorMessage = error instanceof Error ? error.message : '模板加载失败';
        setStatus('error');
        setMessage(errorMessage);
      }
    }
    void loadTemplates();
    return () => {
      mounted = false;
    };
  }, [documentTypeCode]);

  function handleSelectDocumentType(code: string) {
    setDocumentTypeCode(code);
    setPageMode('list');
    setTemplateName('');
    setSelectedTemplateId(null);
    setProfile(null);
    setStructureProfile(null);
    setDocumentKind(null);
    setRenderPreview(null);
    setRenderPreviewStatus('idle');
    setRenderPreviewMessage('');
    clearMappingState();
    setProfileContext(null);
    setUploadResult(null);
    setSelectedTemplateFile(null);
  }

  function handleBackToFolders() {
    setPageMode('folders');
    setDocumentTypeCode(null);
    setTemplateName('');
    setSelectedTemplateId(null);
    setProfile(null);
    setStructureProfile(null);
    setDocumentKind(null);
    setRenderPreview(null);
    setRenderPreviewStatus('idle');
    setRenderPreviewMessage('');
    clearMappingState();
    setProfileContext(null);
    setUploadResult(null);
    setSelectedTemplateFile(null);
    setTemplates([]);
    setVersions([]);
  }

  function handleStartCreate(template?: TemplateSummary) {
    setSelectedTemplateId(template?.id ?? null);
    setTemplateName(template?.templateName ?? '');
    setProfile(null);
    setStructureProfile(null);
    setDocumentKind(null);
    setRenderPreview(null);
    setRenderPreviewStatus('idle');
    setRenderPreviewMessage('');
    clearMappingState();
    setProfileContext(null);
    setUploadResult(null);
    setSelectedTemplateFile(null);
    setPageMode('create');
  }

  async function handleViewProfile(version: TemplateVersionSummary) {
    try {
      setStatus('loading');
      const [parsedProfile, formattingOverrides, parsedStructureProfile, parsedDocumentKind, loadedMapping] = await Promise.all([
        getTemplateProfile(version.templateVersionId),
        getTemplateStructureFormatting(version.templateVersionId),
        getDocumentStructureProfile(version.templateVersionId).catch(() => null),
        getTemplateDocumentKind(version.templateVersionId).catch(() => null),
        getStructureMapping(version.templateVersionId).catch(() => null),
      ]);
      setProfile(parsedProfile);
      setStructureProfile(parsedStructureProfile);
      setDocumentKind(parsedDocumentKind ?? documentKindFromProfile(parsedProfile));
      applyMappingState(loadedMapping);
      onStructureOverridesLoaded(version.templateVersionId, formattingOverrides);
      setProfileContext({
        templateVersionId: version.templateVersionId,
        templateName: version.templateName,
        versionNo: version.versionNo,
        originalFileName: version.originalFileName,
      });
      setUploadResult(null);
      setStatus('idle');
      setMessage(`${version.templateName} v${version.versionNo} 解析结果已加载`);
      void loadRenderPreviewStatus(version.templateVersionId);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '解析结果加载失败';
      setStatus('error');
      setMessage(errorMessage);
      showToast({ title: errorMessage, tone: 'error' });
    }
  }

  function handleSelectTemplateFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setSelectedTemplateFile(file);
  }

  async function handleConfirmUpload() {
    const file = selectedTemplateFile;
    if (!file) {
      showToast({ title: '请先选择 Word 模板文件', tone: 'error' });
      return;
    }
    try {
      setStatus('uploading');
      const name = templateName.trim() || file.name.replace(/\.docx$/i, '');
      const template = selectedTemplateId
        ? templates.find((candidate) => candidate.id === selectedTemplateId) ?? await createTemplate(name, activeDocumentTypeCode)
        : await createTemplate(name, activeDocumentTypeCode);
      setSelectedTemplateId(template.id);
      const result = await uploadTemplateVersion(template.id, file);
      const [parsedProfile, formattingOverrides, parsedStructureProfile, parsedDocumentKind, loadedMapping] = await Promise.all([
        getTemplateProfile(result.templateVersionId),
        getTemplateStructureFormatting(result.templateVersionId),
        getDocumentStructureProfile(result.templateVersionId).catch(() => null),
        getTemplateDocumentKind(result.templateVersionId).catch(() => null),
        getStructureMapping(result.templateVersionId).catch(() => null),
      ]);
      const [loadedTemplates, loadedVersions] = await Promise.all([
        listTemplates(activeDocumentTypeCode),
        listTemplateVersions(activeDocumentTypeCode),
      ]);
      setTemplates(loadedTemplates);
      setVersions(loadedVersions);
      setUploadResult(result);
      setProfile(parsedProfile);
      setStructureProfile(parsedStructureProfile);
      setDocumentKind(parsedDocumentKind ?? documentKindFromProfile(parsedProfile));
      applyMappingState(loadedMapping);
      onStructureOverridesLoaded(result.templateVersionId, formattingOverrides);
      setProfileContext({
        templateVersionId: result.templateVersionId,
        templateName: template.templateName,
        versionNo: result.versionNo,
        originalFileName: file.name,
      });
      setPageMode('list');
      setStatus('idle');
      setMessage('模板已解析');
      void loadRenderPreviewStatus(result.templateVersionId);
      await onTemplateVersionCreated();
      showToast({ title: '模板版本已上传', description: `${name} v${result.versionNo}`, tone: 'success' });
      setSelectedTemplateFile(null);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '模板上传失败';
      setStatus('error');
      setMessage(errorMessage);
      showToast({ title: errorMessage, tone: 'error' });
    }
  }

  async function handleConfirmDeleteTemplate() {
    if (!templateToDelete) {
      return;
    }
    try {
      setIsDeletingTemplate(true);
      await deleteTemplate(templateToDelete.id);
      setTemplates((current) => current.filter((template) => template.id !== templateToDelete.id));
      setVersions((current) => current.filter((version) => version.templateId !== templateToDelete.id));
      if (selectedTemplateId === templateToDelete.id) {
        setSelectedTemplateId(null);
      }
      setProfile(null);
      setStructureProfile(null);
      setDocumentKind(null);
      setRenderPreview(null);
      setRenderPreviewStatus('idle');
      setRenderPreviewMessage('');
      clearMappingState();
      setProfileContext(null);
      setUploadResult(null);
      setMessage('模板已删除');
      setStatus('idle');
      setTemplateToDelete(null);
      await onTemplateVersionCreated();
      showToast({ title: '模板已删除', tone: 'success' });
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '模板删除失败';
      setStatus('error');
      setMessage(errorMessage);
      showToast({ title: errorMessage, tone: 'error' });
    } finally {
      setIsDeletingTemplate(false);
    }
  }

  function handleCloseProfileDialog() {
    setProfile(null);
    setStructureProfile(null);
    setDocumentKind(null);
    setRenderPreview(null);
    setRenderPreviewStatus('idle');
    setRenderPreviewMessage('');
    clearMappingState();
    setProfileContext(null);
    setUploadResult(null);
  }

  async function loadRenderPreviewStatus(templateVersionId: number) {
    try {
      setRenderPreviewStatus('loading');
      setRenderPreviewMessage('正在读取渲染预览状态');
      const preview = await getRenderPreview(templateVersionId);
      setRenderPreview(preview);
      setRenderPreviewStatus('idle');
      setRenderPreviewMessage('预览状态已加载');
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '渲染预览状态加载失败';
      setRenderPreview(null);
      setRenderPreviewStatus('error');
      setRenderPreviewMessage(errorMessage);
    }
  }

  async function handleRequestRenderPreview() {
    if (!profileContext) {
      return;
    }
    try {
      setRenderPreviewStatus('requesting');
      setRenderPreviewMessage('正在提交渲染预览任务');
      const preview = await requestRenderPreview(profileContext.templateVersionId);
      setRenderPreview(preview);
      setRenderPreviewStatus('idle');
      setRenderPreviewMessage(preview.status === 'READY' ? '渲染预览已更新' : '预览任务已提交，稍后可再次刷新状态。');
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '渲染预览生成失败';
      setRenderPreviewStatus('error');
      setRenderPreviewMessage(errorMessage);
      showToast({ title: errorMessage, tone: 'error' });
    }
  }

  function clearMappingState() {
    setMappingProfile(null);
    setMappingItems([]);
    setMappingStatus('idle');
    setMappingMessage('');
  }

  function applyMappingState(nextMapping: StructureMappingProfile | null) {
    setMappingProfile(nextMapping);
    setMappingItems(nextMapping?.items ?? []);
    setMappingStatus(nextMapping ? 'idle' : 'error');
    setMappingMessage(nextMapping ? '结构映射已加载' : '结构映射暂不可用');
  }

  function handleMappingRoleChange(nodeKey: string, role: string, sortOrder: number) {
    setMappingItems((current) => {
      const nextItem: StructureMappingItem = {
        nodeKey,
        role,
        slotKey: slotKeyForMappingRole(role),
        status: role === 'IGNORE' ? 'IGNORED' : 'CONFIRMED',
        source: 'USER',
        confidence: 1,
        notes: '',
        sortOrder,
      };
      const exists = current.some((item) => item.nodeKey === nodeKey);
      return exists
        ? current.map((item) => item.nodeKey === nodeKey ? { ...item, ...nextItem } : item)
        : [...current, nextItem];
    });
    setMappingStatus('idle');
    setMappingMessage('映射草稿有未保存修改');
  }

  async function handleSaveMappingDraft() {
    if (!profileContext) {
      return;
    }
    try {
      setMappingStatus('saving');
      const confirmedItems = confirmVisibleMappingItems(mappingItems);
      const saved = await saveStructureMappingDraft(
        profileContext.templateVersionId,
        mappingProfile?.mappingProfileId ?? null,
        confirmedItems,
      );
      setMappingProfile(saved);
      setMappingItems(saved.items);
      setMappingStatus('idle');
      setMappingMessage('映射草稿已保存');
      showToast({ title: '映射草稿已保存', tone: 'success' });
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '映射草稿保存失败';
      setMappingStatus('error');
      setMappingMessage(errorMessage);
      showToast({ title: errorMessage, tone: 'error' });
    }
  }

  async function handlePublishMapping() {
    if (!profileContext) {
      return;
    }
    try {
      setMappingStatus('publishing');
      const published = await publishStructureMapping(profileContext.templateVersionId);
      setMappingProfile(published);
      setMappingItems(published.items);
      if (published.validationItems.length > 0) {
        setMappingStatus('blocked');
        setMappingMessage('映射发布被阻断');
      } else {
        setMappingStatus('idle');
        setMappingMessage('映射已发布');
        showToast({ title: '映射已发布', tone: 'success' });
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '映射发布失败';
      setMappingStatus('error');
      setMappingMessage(errorMessage);
      showToast({ title: errorMessage, tone: 'error' });
    }
  }

  return (
    <>
      <main className="settings-page" aria-label="模板管理">
        <section className="settings-panel template-admin-panel">
          <div className="settings-header">
            <div>
              <div className="eyebrow">模板库</div>
              <h2>
                {pageMode === 'create' ? (selectedTemplateId ? '上传新版本' : '新增模板') : '模板管理'}
                {activeDocumentType && <span className="template-title-suffix"> - {activeDocumentType.name}</span>}
              </h2>
              <p>{activeDocumentType ? '新增模板后会解析占位符和风险。' : '先选择文种，再管理该文种下的模板。'}</p>
            </div>
            {pageMode === 'folders' ? (
              <DocumentTypeCreateButton onDocumentTypesChange={onDocumentTypesChange} />
            ) : (
              <span className={`status-chip ${status === 'error' ? 'danger' : ''}`}>
                {status === 'uploading' ? '解析中' : `${templates.length} 个模板 · ${versions.length} 个版本`}
              </span>
            )}
          </div>

        {pageMode === 'folders' && (
          <DocumentTypeFolderGrid
            documentTypes={fallbackDocumentTypes}
            onDocumentTypesChange={onDocumentTypesChange}
            onSelectDocumentType={handleSelectDocumentType}
          />
        )}

        {pageMode === 'list' && (
          <>
            <div className="template-list-toolbar">
              <Button icon={<ArrowLeft aria-hidden="true" />} onClick={handleBackToFolders} variant="secondary">
                返回文种
              </Button>
              <Button icon={<Plus aria-hidden="true" />} onClick={() => handleStartCreate()}>
                新增模板
              </Button>
            </div>

            {status === 'error' && <StatusMessage title={message} tone="warning" />}

            {status === 'loading' && templates.length === 0 ? (
              <div className="template-card-grid" aria-live="polite">
                <div className="template-card skeleton-row" />
                <div className="template-card skeleton-row" />
              </div>
            ) : templates.length === 0 ? (
              <div className="template-empty-panel">
                <FileText aria-hidden="true" />
                <div>
                  <strong>暂无模板</strong>
                  <span>为{activeDocumentType?.name ?? activeDocumentTypeCode}上传第一个 Word 模板。</span>
                </div>
              </div>
            ) : (
              <div className="template-card-grid">
                {templates.map((template) => {
                  const templateVersions = versionsByTemplateId.get(template.id) ?? [];
                  const latestVersion = templateVersions[0];
                  return (
                    <article className="template-card" key={template.id}>
                      <div className="template-card-controls">
                        <button
                          aria-label={`删除模板：${template.templateName}`}
                          className="template-card-icon-button danger"
                          disabled={status === 'loading' || status === 'uploading' || isDeletingTemplate}
                          onClick={() => setTemplateToDelete(template)}
                          type="button"
                        >
                          <Trash2 aria-hidden="true" />
                        </button>
                      </div>
                      <div className="template-card-title">
                        <FileText aria-hidden="true" />
                        <div>
                          <h3>{template.templateName}</h3>
                          <span className="template-card-meta">{template.status} · {templateVersions.length} 个版本</span>
                        </div>
                      </div>
                      <p className="template-card-file">{latestVersion ? `最新版本 v${latestVersion.versionNo} · ${latestVersion.originalFileName}` : '尚未上传 Word 版本'}</p>
                      <div className="template-card-actions">
                        <Button disabled={!latestVersion || status === 'loading'} icon={<Eye aria-hidden="true" />} onClick={() => latestVersion && handleViewProfile(latestVersion)} variant="secondary">
                          解析结果
                        </Button>
                        <Button disabled={status === 'loading'} icon={<Upload aria-hidden="true" />} onClick={() => handleStartCreate(template)} variant="secondary">
                          上传新版本
                        </Button>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </>
        )}

        {pageMode === 'create' && (
          <>
            <div className="template-list-toolbar">
              <div className="template-toolbar-context">
                <Button icon={<ArrowLeft aria-hidden="true" />} onClick={() => setPageMode('list')} variant="secondary">
                  返回模板
                </Button>
              </div>
            </div>

            {status === 'error' && <StatusMessage title={message} tone="warning" />}

            <div className="settings-grid">
              <TextField
                disabled
                label="文种"
                value={activeDocumentType?.name ?? activeDocumentTypeCode}
              />
              <TextField
                disabled={status === 'uploading' || selectedTemplateId !== null}
                label="模板名称"
                onChange={(event) => setTemplateName(event.target.value)}
                placeholder="如：通知标准模板"
                value={templateName}
              />
            </div>

            <div className="template-upload-row">
              <input
                accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                aria-label="上传 Word 模板"
                className="visually-hidden"
                disabled={status === 'uploading'}
                id="template-version-upload"
                onChange={handleSelectTemplateFile}
                type="file"
              />
              <label
                aria-disabled={status === 'uploading'}
                className="ui-button ui-button-secondary upload-label"
                htmlFor="template-version-upload"
              >
                <Upload aria-hidden="true" />
                {selectedTemplateFile ? selectedTemplateFile.name : '选择 Word 模板'}
              </label>
              <Button
                disabled={!selectedTemplateFile || status === 'uploading'}
                icon={<CheckCircle2 aria-hidden="true" />}
                isLoading={status === 'uploading'}
                loadingLabel="正在解析"
                onClick={handleConfirmUpload}
              >
                确认创建并解析
              </Button>
            </div>
          </>
        )}

        </section>
      </main>

      <Dialog
        actions={(
          <Button onClick={handleCloseProfileDialog} variant="secondary">
            关闭
          </Button>
        )}
        className="template-profile-dialog"
        description={profileContext ? `${profileContext.templateName} v${profileContext.versionNo} · ${profileContext.originalFileName}` : undefined}
        onClose={handleCloseProfileDialog}
        open={Boolean(profile)}
        title="模板解析工作台"
      >
        {uploadResult && (
          <div className="template-profile-summary">
            <div className="metric-card">
              <span>占位符</span>
              <strong>{uploadResult.placeholderCount}</strong>
            </div>
            <div className="metric-card">
              <span>样式</span>
              <strong>{uploadResult.styleCount}</strong>
            </div>
            <div className="metric-card">
              <span>风险</span>
              <strong>{uploadResult.validationCount}</strong>
            </div>
          </div>
        )}

        {profile && (
          <TemplateParseWorkspace
            documentKind={documentKind ?? documentKindFromProfile(profile)}
            profile={profile}
            mappingItems={mappingItems}
            mappingMessage={mappingMessage}
            mappingProfile={mappingProfile}
            mappingStatus={mappingStatus}
            renderPreview={renderPreview}
            renderPreviewMessage={renderPreviewMessage}
            renderPreviewStatus={renderPreviewStatus}
            structureProfile={structureProfile}
            onMappingRoleChange={handleMappingRoleChange}
            onPublishMapping={() => void handlePublishMapping()}
            onSaveMappingDraft={() => void handleSaveMappingDraft()}
            onRequestRenderPreview={() => void handleRequestRenderPreview()}
          />
        )}
      </Dialog>
      <ConfirmDialog
        cancelLabel="继续保留"
        confirmLabel="删除模板"
        description={templateToDelete ? `将删除“${templateToDelete.templateName}”及全部模板版本，并解除草稿中的模板绑定。此操作不可撤销。` : undefined}
        isConfirming={isDeletingTemplate}
        onCancel={() => setTemplateToDelete(null)}
        onConfirm={() => void handleConfirmDeleteTemplate()}
        open={Boolean(templateToDelete)}
        title="删除这个模板？"
      />
    </>
  );
}

function TemplateStructureEditor({
  structure,
  override,
  onChange,
}: {
  structure: TemplateProfile['structures'][number];
  override?: TemplateStructureOverride;
  onChange: (nextOverride: TemplateStructureOverride) => Promise<void>;
}) {
  const { showToast } = useToast();
  const effectiveFormatting = { ...structure.formatting, ...(override ?? {}) };
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [draftOverride, setDraftOverride] = useState<TemplateStructureOverride>(override ?? {});
  const draftFormatting = { ...structure.formatting, ...draftOverride };
  const hasOverride = override ? Object.keys(override).length > 0 : false;

  useEffect(() => {
    if (!isEditing) {
      setDraftOverride(override ?? {});
    }
  }, [isEditing, override]);

  function updateDimension<Key extends keyof TemplateStructureFormatting>(
    key: Key,
    value: TemplateStructureFormatting[Key],
  ) {
    setDraftOverride((current) => ({ ...current, [key]: value }));
  }

  async function handleApply() {
    try {
      setIsSaving(true);
      await onChange(draftOverride);
      setIsEditing(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : '结构维度保存失败';
      showToast({ title: message, tone: 'error' });
    } finally {
      setIsSaving(false);
    }
  }

  function handleCancel() {
    setDraftOverride(override ?? {});
    setIsEditing(false);
  }

  return (
    <article className="template-structure-item">
      <div className="template-structure-header">
        <div className="template-structure-title">
          <div>
            <strong>{structure.label}</strong>
            <span>{locationLabel(structure.locationType)} · {structureSourceLabel(structure.source)}</span>
          </div>
          {hasOverride && <small>已调整</small>}
        </div>
        <div className="template-structure-actions">
          {isEditing ? (
            <>
              <Button icon={<Check aria-hidden="true" />} isLoading={isSaving} loadingLabel="保存中" onClick={handleApply} variant="secondary">
                应用
              </Button>
              <Button disabled={isSaving} icon={<X aria-hidden="true" />} onClick={handleCancel} variant="ghost">
                取消
              </Button>
            </>
          ) : (
            <Button icon={<Pencil aria-hidden="true" />} onClick={() => setIsEditing(true)} variant="secondary">
              编辑
            </Button>
          )}
        </div>
      </div>
      <div className="template-structure-main">
        <p>{structure.textPreview || '该结构暂无可展示文字'}</p>
        <div className="template-dimension-summary" aria-label={`${structure.label} 当前维度`}>
          {dimensionSummary(effectiveFormatting).map((item) => (
            <span key={item}>{item}</span>
          ))}
        </div>
      </div>
      {isEditing && (
        <div className="template-dimension-grid" aria-label={`${structure.label} 可编辑维度`}>
          <label>
            <span>字体</span>
            <input
              onChange={(event) => updateDimension('fontFamily', event.target.value || null)}
              placeholder="默认"
              value={draftFormatting.fontFamily ?? ''}
            />
          </label>
          <label>
            <span>字号 pt</span>
            <input
              min="8"
              onChange={(event) => updateDimension('fontSizeHalfPoints', pointToHalfPoint(event.target.value))}
              placeholder="默认"
              type="number"
              value={draftFormatting.fontSizeHalfPoints ? draftFormatting.fontSizeHalfPoints / 2 : ''}
            />
          </label>
          <label>
            <span>对齐</span>
            <select
              onChange={(event) => updateDimension('alignment', event.target.value || null)}
              value={draftFormatting.alignment ?? ''}
            >
              <option value="">默认</option>
              <option value="LEFT">左对齐</option>
              <option value="CENTER">居中</option>
              <option value="RIGHT">右对齐</option>
              <option value="BOTH">两端对齐</option>
            </select>
          </label>
          <label>
            <span>首行缩进 mm</span>
            <input
              min="0"
              onChange={(event) => updateDimension('indentationFirstLine', millimeterToTwips(event.target.value))}
              placeholder="默认"
              type="number"
              value={draftFormatting.indentationFirstLine ? twipsToMillimeters(draftFormatting.indentationFirstLine) : ''}
            />
          </label>
          <label>
            <span>行距</span>
            <input
              min="1"
              onChange={(event) => updateDimension('spacingBetween', lineSpacingToProfileValue(event.target.value))}
              placeholder="默认"
              step="0.1"
              type="number"
              value={draftFormatting.spacingBetween ? draftFormatting.spacingBetween / 100 : ''}
            />
          </label>
          <label>
            <span>段后 mm</span>
            <input
              min="0"
              onChange={(event) => updateDimension('spacingAfter', millimeterToTwips(event.target.value))}
              placeholder="默认"
              type="number"
              value={draftFormatting.spacingAfter ? twipsToMillimeters(draftFormatting.spacingAfter) : ''}
            />
          </label>
          <label className="template-dimension-check">
            <input
              checked={Boolean(draftFormatting.bold)}
              onChange={(event) => updateDimension('bold', event.target.checked)}
              type="checkbox"
            />
            <span>加粗</span>
          </label>
        </div>
      )}
    </article>
  );
}

function AuthLoadingPage() {
  return (
    <main className="auth-page" aria-busy="true" aria-label="登录状态检查">
      <section className="auth-panel">
        <div className="auth-brand">
          <div className="sidebar-mark">文</div>
          <div>
            <h1>公文助手</h1>
            <p>正在恢复登录状态</p>
          </div>
        </div>
        <StatusMessage title="正在连接账号服务" tone="info" />
      </section>
    </main>
  );
}

function LoginPage({ message, onLogin }: { message: string; onLogin: (username: string, password: string) => Promise<void> }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('idle');
  const [error, setError] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!username.trim() || !password) {
      setStatus('error');
      setError('请输入账号和密码。');
      return;
    }
    try {
      setStatus('loading');
      setError('');
      await onLogin(username.trim(), password);
      setPassword('');
      setStatus('idle');
    } catch (loginError) {
      setStatus('error');
      setError(loginError instanceof Error ? loginError.message : '登录失败，请检查账号密码。');
    }
  }

  return (
    <main className="auth-page" aria-label="登录">
      <section className="auth-panel">
        <div className="auth-brand">
          <div className="sidebar-mark">文</div>
          <div>
            <h1>公文助手</h1>
            <p>账号、部门、草稿和模板统一归属到当前登录身份。</p>
          </div>
        </div>
        {message && <StatusMessage title={message} tone="info" />}
        {error && <StatusMessage title={error} tone="warning" />}
        <form className="auth-form" onSubmit={(event) => void handleSubmit(event)}>
          <TextField
            autoComplete="username"
            disabled={status === 'loading'}
            label="账号"
            onChange={(event) => setUsername(event.target.value)}
            required
            value={username}
          />
          <TextField
            autoComplete="current-password"
            disabled={status === 'loading'}
            label="密码"
            onChange={(event) => setPassword(event.target.value)}
            required
            type="password"
            value={password}
          />
          <Button isLoading={status === 'loading'} loadingLabel="正在登录" type="submit">
            登录
          </Button>
        </form>
      </section>
    </main>
  );
}

function SystemSettingsPage({
  apiKey,
  currentUser,
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
  currentUser: AuthUser;
  message: string;
  onApiKeyChange: (value: string) => void;
  onSave: () => void;
  onSettingsChange: (patch: Partial<AiProviderSettings>) => void;
  onTest: () => void;
  providerStatus: AiProviderStatus | null;
  settings: AiProviderSettings;
  status: AiSettingsStatus;
}) {
  const [activeSettingsTab, setActiveSettingsTab] = useState<SettingsTab>(
    currentUser.roles.includes('SYSTEM_ADMIN') ? 'accounts' : 'ai',
  );
  const [departments, setDepartments] = useState<Department[]>([]);
  const [departmentStatus, setDepartmentStatus] = useState<AdminPageStatus>('loading');
  const [departmentMessage, setDepartmentMessage] = useState('');
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | null>(null);
  const canManageOrganization = currentUser.roles.includes('SYSTEM_ADMIN');
  const selectedDepartment = useMemo(
    () => (selectedDepartmentId ? findDepartmentById(departments, selectedDepartmentId) : null),
    [departments, selectedDepartmentId],
  );
  const visibleTabs = useMemo(() => {
    const tabs: Array<{ id: SettingsTab; label: string }> = [{ id: 'ai', label: 'AI 配置' }];
    if (canManageOrganization) {
      tabs.push({ id: 'accounts', label: '人员管理' }, { id: 'departments', label: '部门管理' });
    }
    return tabs;
  }, [canManageOrganization]);

  useEffect(() => {
    if (!canManageOrganization) {
      return;
    }
    void refreshSystemDepartments();
  }, [canManageOrganization]);

  useEffect(() => {
    if (!canManageOrganization && activeSettingsTab !== 'ai') {
      setActiveSettingsTab('ai');
    }
  }, [activeSettingsTab, canManageOrganization]);

  useEffect(() => {
    if (selectedDepartmentId && !findDepartmentById(departments, selectedDepartmentId)) {
      setSelectedDepartmentId(null);
    }
  }, [departments, selectedDepartmentId]);

  async function refreshSystemDepartments() {
    try {
      setDepartmentStatus('loading');
      setDepartmentMessage('正在加载部门树');
      setDepartments(await listDepartments());
      setDepartmentStatus('idle');
      setDepartmentMessage('');
    } catch (error) {
      setDepartmentStatus('error');
      setDepartmentMessage(error instanceof Error ? error.message : '部门加载失败');
    }
  }

  return (
    <main className="settings-page system-settings-page" aria-label="系统设置">
      <section className="settings-panel system-settings-panel">
        {canManageOrganization && departmentMessage && (
          <StatusMessage title={departmentMessage} tone={departmentStatus === 'error' ? 'warning' : 'success'} />
        )}

        <div className={`system-settings-workspace ${canManageOrganization ? '' : 'single-pane'}`}>
          {canManageOrganization && (
            <DepartmentTreePane
              departments={departments}
              onSelectDepartment={setSelectedDepartmentId}
              selectedDepartmentId={selectedDepartmentId}
              status={departmentStatus}
            />
          )}

          <section className="system-settings-main" aria-label="系统设置详情">
            <div className="system-settings-tabs" role="tablist" aria-label="系统设置分类">
              {visibleTabs.map((tab) => (
                <button
                  aria-selected={activeSettingsTab === tab.id}
                  className={`system-settings-tab ${activeSettingsTab === tab.id ? 'active' : ''}`}
                  key={tab.id}
                  onClick={() => setActiveSettingsTab(tab.id)}
                  role="tab"
                  type="button"
                >
                  {tab.label}
                </button>
              ))}
            </div>

            <div className="system-settings-content">
              {activeSettingsTab === 'ai' ? (
                <AiSettingsPage
                  apiKey={apiKey}
                  embedded
                  message={message}
                  onApiKeyChange={onApiKeyChange}
                  onSave={onSave}
                  onSettingsChange={onSettingsChange}
                  onTest={onTest}
                  providerStatus={providerStatus}
                  settings={settings}
                  status={status}
                />
              ) : activeSettingsTab === 'accounts' ? (
                <AccountManagementPage
                  embedded
                  selectedDepartment={selectedDepartment}
                  selectedDepartmentId={selectedDepartmentId}
                  sharedDepartments={departments}
                />
              ) : (
                <DepartmentManagementPage
                  embedded
                  onDepartmentsChange={setDepartments}
                  onSelectedDepartmentChange={setSelectedDepartmentId}
                  selectedDepartmentId={selectedDepartmentId}
                  sharedDepartmentStatus={departmentStatus}
                  sharedDepartments={departments}
                />
              )}
            </div>
          </section>
        </div>
      </section>
    </main>
  );
}

type ManagementTableColumn<T> = {
  key: string;
  header: string;
  width: string;
  align?: 'start' | 'center' | 'end';
  render: (item: T) => ReactNode;
};

function ManagementTable<T>({
  ariaLabel,
  columns,
  emptyDescription,
  emptyIcon,
  emptyTitle,
  getKey,
  items,
  minWidth = '720px',
  skeletonRows = 2,
  status,
}: {
  ariaLabel: string;
  columns: Array<ManagementTableColumn<T>>;
  emptyDescription: string;
  emptyIcon: ReactNode;
  emptyTitle: string;
  getKey: (item: T) => string | number;
  items: T[];
  minWidth?: string;
  skeletonRows?: number;
  status: AdminPageStatus;
}) {
  const tableStyle = {
    '--management-table-columns': columns.map((column) => column.width).join(' '),
    '--management-table-min-width': minWidth,
  } as CSSProperties;

  return (
    <div className="management-table" role="table" aria-label={ariaLabel} style={tableStyle}>
      <div className="management-table-row management-table-head" role="row">
        {columns.map((column) => (
          <span className="management-table-cell" data-align={column.align ?? 'start'} key={column.key} role="columnheader">
            {column.header}
          </span>
        ))}
      </div>
      {status === 'loading' && Array.from({ length: skeletonRows }).map((_, index) => (
        <div className="management-table-row management-table-skeleton" key={`skeleton-${index}`} role="row">
          {columns.map((column) => (
            <span className="management-table-cell" data-align={column.align ?? 'start'} key={column.key} role="cell">
              <span className="management-table-skeleton-bar" />
            </span>
          ))}
        </div>
      ))}
      {status !== 'loading' && items.length === 0 && (
        <div className="template-empty-panel management-table-empty">
          {emptyIcon}
          <div>
            <strong>{emptyTitle}</strong>
            <span>{emptyDescription}</span>
          </div>
        </div>
      )}
      {status !== 'loading' && items.map((item) => (
        <article className="management-table-row" key={getKey(item)} role="row">
          {columns.map((column) => (
            <div className="management-table-cell" data-align={column.align ?? 'start'} key={column.key} role="cell">
              {column.render(item)}
            </div>
          ))}
        </article>
      ))}
    </div>
  );
}

function DepartmentTreePane({
  departments,
  onSelectDepartment,
  selectedDepartmentId,
  status,
}: {
  departments: Department[];
  onSelectDepartment: (departmentId: number | null) => void;
  selectedDepartmentId: number | null;
  status: AdminPageStatus;
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedDepartmentIds, setExpandedDepartmentIds] = useState<Set<number>>(() => new Set());
  const flatDepartments = useMemo(() => flattenDepartments(departments), [departments]);
  const filteredDepartments = useMemo(
    () => filterDepartmentTree(departments, searchTerm),
    [departments, searchTerm],
  );
  const isSearching = searchTerm.trim().length > 0;

  useEffect(() => {
    if (!selectedDepartmentId) {
      return;
    }
    const selectedPath = getDepartmentPath(departments, selectedDepartmentId);
    if (selectedPath.length < 2) {
      return;
    }
    setExpandedDepartmentIds((current) => {
      const next = new Set(current);
      selectedPath.slice(0, -1).forEach((department) => next.add(department.id));
      return next;
    });
  }, [departments, selectedDepartmentId]);

  function toggleDepartment(departmentId: number) {
    setExpandedDepartmentIds((current) => {
      const next = new Set(current);
      if (next.has(departmentId)) {
        next.delete(departmentId);
      } else {
        next.add(departmentId);
      }
      return next;
    });
  }

  function renderDepartmentTree(nodes: Department[], depth = 0) {
    return sortDepartments(nodes).map((department) => {
      const hasChildren = Boolean(department.children?.length);
      const isExpanded = isSearching || expandedDepartmentIds.has(department.id);
      return (
        <div className="department-tree-branch" key={department.id}>
          <div
            className={`department-tree-node ${selectedDepartmentId === department.id ? 'active' : ''}`}
            style={{ '--department-tree-depth': depth } as CSSProperties}
          >
            {hasChildren ? (
              <button
                aria-expanded={isExpanded}
                aria-label={`${isExpanded ? '收起' : '展开'}部门：${department.name}`}
                className="department-tree-toggle"
                onClick={() => toggleDepartment(department.id)}
                type="button"
              >
                <ChevronRight aria-hidden="true" className={isExpanded ? 'expanded' : ''} />
              </button>
            ) : (
              <span className="department-tree-spacer" aria-hidden="true" />
            )}
            <button
              aria-current={selectedDepartmentId === department.id ? 'true' : undefined}
              className="department-tree-select"
              onClick={() => onSelectDepartment(department.id)}
              type="button"
            >
              <Building2 aria-hidden="true" />
              <span className="department-tree-copy">
                <span className="department-tree-name">{department.name}</span>
              </span>
            </button>
            <span className="department-tree-count">{countDepartmentDescendants(department)}</span>
          </div>
          {hasChildren && isExpanded ? (
            <div className="department-tree-children" role="group">
              {renderDepartmentTree(department.children ?? [], depth + 1)}
            </div>
          ) : null}
        </div>
      );
    });
  }

  return (
    <aside className="department-tree-pane" aria-label="部门树">
      <div className="department-tree-header">
        <div>
          <strong>部门列表</strong>
          <span>{status === 'loading' ? '加载中' : `${flatDepartments.length} 个节点`}</span>
        </div>
      </div>
      <label className="department-tree-search">
        <Search aria-hidden="true" />
        <span className="ui-visually-hidden">搜索部门</span>
        <input
          aria-label="搜索部门"
          onChange={(event) => setSearchTerm(event.target.value)}
          placeholder="搜索"
          type="search"
          value={searchTerm}
        />
      </label>
      {status === 'loading' ? (
        <div className="department-tree-loading" aria-live="polite">
          <div className="department-tree-node skeleton-row" />
          <div className="department-tree-node skeleton-row" />
          <div className="department-tree-node skeleton-row" />
        </div>
      ) : (
        <>
          <div
            className={`department-tree-node department-tree-root ${selectedDepartmentId === null ? 'active' : ''}`}
            style={{ '--department-tree-depth': 0 } as CSSProperties}
          >
            <span className="department-tree-spacer" aria-hidden="true" />
            <button
              aria-current={selectedDepartmentId === null ? 'true' : undefined}
              className="department-tree-select"
              onClick={() => onSelectDepartment(null)}
              type="button"
            >
              <Building2 aria-hidden="true" />
              <span className="department-tree-copy">
                <span className="department-tree-name">全部部门</span>
              </span>
            </button>
            <span className="department-tree-count">{flatDepartments.length}</span>
          </div>
          {filteredDepartments.length === 0 ? (
            <div className="template-empty-panel department-tree-empty">
              <Building2 aria-hidden="true" />
              <div>
                <strong>{departments.length === 0 ? '暂无部门' : '无匹配部门'}</strong>
                <span>{departments.length === 0 ? '先创建根级部门，再为账号分配部门。' : '换一个关键词再试。'}</span>
              </div>
            </div>
          ) : (
            <div className="department-tree-list">{renderDepartmentTree(filteredDepartments)}</div>
          )}
        </>
      )}
    </aside>
  );
}

function DepartmentManagementPage({
  embedded = false,
  onDepartmentsChange,
  onSelectedDepartmentChange,
  selectedDepartmentId: controlledSelectedDepartmentId,
  sharedDepartmentStatus,
  sharedDepartments,
}: {
  embedded?: boolean;
  onDepartmentsChange?: (departments: Department[]) => void;
  onSelectedDepartmentChange?: (departmentId: number | null) => void;
  selectedDepartmentId?: number | null;
  sharedDepartmentStatus?: AdminPageStatus;
  sharedDepartments?: Department[];
} = {}) {
  const { showToast } = useToast();
  const [localDepartments, setLocalDepartments] = useState<Department[]>([]);
  const [status, setStatus] = useState<AdminPageStatus>(sharedDepartments ? 'idle' : 'loading');
  const [message, setMessage] = useState('');
  const [form, setForm] = useState({ parentId: '', name: '', sortOrder: '10' });
  const [localSelectedDepartmentId, setLocalSelectedDepartmentId] = useState<number | null>(null);
  const [editingDepartment, setEditingDepartment] = useState<Department | null>(null);
  const [departmentDialogOpen, setDepartmentDialogOpen] = useState(false);
  const [departmentToDelete, setDepartmentToDelete] = useState<Department | null>(null);
  const departments = sharedDepartments ?? localDepartments;
  const selectedDepartmentId = controlledSelectedDepartmentId !== undefined ? controlledSelectedDepartmentId : localSelectedDepartmentId;
  const displayStatus = sharedDepartmentStatus === 'loading' ? 'loading' : status;
  const flatDepartments = useMemo(() => flattenDepartments(departments), [departments]);
  const selectedDepartment = useMemo(
    () => (selectedDepartmentId ? findDepartmentById(departments, selectedDepartmentId) : null),
    [departments, selectedDepartmentId],
  );
  const selectedDepartmentPath = useMemo(
    () => (selectedDepartmentId ? getDepartmentPath(departments, selectedDepartmentId) : []),
    [departments, selectedDepartmentId],
  );
  const visibleDepartments = useMemo(
    () => (selectedDepartment ? flattenDepartmentDescendants(selectedDepartment) : sortDepartments(departments)),
    [departments, selectedDepartment],
  );
  const parentOptions = useMemo(
    () => flatDepartments.filter(({ department }) => !editingDepartment || !isDepartmentInSubtree(editingDepartment, department.id)),
    [editingDepartment, flatDepartments],
  );

  useEffect(() => {
    if (!sharedDepartments) {
      void refreshDepartments();
    }
  }, [sharedDepartments]);

  useEffect(() => {
    if (selectedDepartmentId && !findDepartmentById(departments, selectedDepartmentId)) {
      selectDepartment(null);
    }
  }, [departments, selectedDepartmentId]);

  useEffect(() => {
    if (!editingDepartment) {
      setForm((current) => ({
        ...current,
        parentId: selectedDepartmentId ? String(selectedDepartmentId) : '',
      }));
    }
  }, [editingDepartment, selectedDepartmentId]);

  function selectDepartment(departmentId: number | null) {
    if (controlledSelectedDepartmentId !== undefined) {
      onSelectedDepartmentChange?.(departmentId);
    } else {
      setLocalSelectedDepartmentId(departmentId);
    }
  }

  function updateDepartmentTree(nextDepartments: Department[]) {
    if (sharedDepartments) {
      onDepartmentsChange?.(nextDepartments);
    } else {
      setLocalDepartments(nextDepartments);
    }
  }

  async function refreshDepartments() {
    try {
      setStatus('loading');
      setMessage('正在加载部门树');
      updateDepartmentTree(await listDepartments());
      setStatus('idle');
      setMessage('');
    } catch (error) {
      setStatus('error');
      setMessage(error instanceof Error ? error.message : '部门加载失败');
    }
  }

  function startCreateDepartment() {
    setEditingDepartment(null);
    setForm({
      parentId: selectedDepartmentId ? String(selectedDepartmentId) : '',
      name: '',
      sortOrder: '10',
    });
    setDepartmentDialogOpen(true);
  }

  function startEditDepartment(department: Department) {
    setEditingDepartment(department);
    setForm({
      parentId: department.parentId ? String(department.parentId) : '',
      name: department.name,
      sortOrder: String(department.sortOrder),
    });
    setDepartmentDialogOpen(true);
  }

  function resetDepartmentForm() {
    setEditingDepartment(null);
    setForm({ parentId: selectedDepartmentId ? String(selectedDepartmentId) : '', name: '', sortOrder: '10' });
    setDepartmentDialogOpen(false);
  }

  async function handleSaveDepartment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.name.trim()) {
      setStatus('error');
      setMessage('请填写部门名称。');
      return;
    }
    try {
      setStatus('saving');
      const payload = {
        parentId: form.parentId ? Number(form.parentId) : null,
        name: form.name.trim(),
        sortOrder: Number(form.sortOrder || 0),
      };
      if (editingDepartment) {
        await updateDepartment(editingDepartment.id, payload);
      } else {
        await createDepartment(payload);
      }
      updateDepartmentTree(await listDepartments());
      resetDepartmentForm();
      setStatus('idle');
      setMessage(editingDepartment ? '部门已更新' : '部门已创建');
      showToast({ title: editingDepartment ? '部门已更新' : '部门已创建', tone: 'success' });
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '部门保存失败';
      setStatus('error');
      setMessage(nextMessage);
      showToast({ title: nextMessage, tone: 'error' });
    }
  }

  async function handleDeleteDepartment() {
    if (!departmentToDelete) {
      return;
    }
    try {
      setStatus('saving');
      await deleteDepartment(departmentToDelete.id);
      updateDepartmentTree(await listDepartments());
      if (selectedDepartmentId === departmentToDelete.id) {
        selectDepartment(departmentToDelete.parentId ?? null);
      }
      setDepartmentToDelete(null);
      setStatus('idle');
      setMessage('部门已删除');
      showToast({ title: '部门已删除', tone: 'success' });
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '部门删除失败';
      setStatus('error');
      setMessage(nextMessage);
      showToast({ title: nextMessage, tone: 'error' });
    }
  }

  const departmentColumns: Array<ManagementTableColumn<Department>> = [
    {
      key: 'department',
      header: '部门',
      width: 'minmax(220px, 1.4fr)',
      render: (department) => (
        <button className="management-table-primary" onClick={() => selectDepartment(department.id)} type="button">
          <Building2 aria-hidden="true" />
          <span>{department.name}</span>
        </button>
      ),
    },
    {
      key: 'status',
      header: '状态',
      width: 'minmax(92px, 0.5fr)',
      render: (department) => <span className="management-table-text">{department.status}</span>,
    },
    {
      key: 'children',
      header: '下级',
      width: 'minmax(72px, 0.4fr)',
      align: 'center',
      render: (department) => <span className="management-table-text">{countDepartmentDescendants(department)}</span>,
    },
    {
      key: 'actions',
      header: '操作',
      width: 'minmax(112px, max-content)',
      align: 'end',
      render: (department) => (
        <div className="management-table-actions">
          <Button
            aria-label={`编辑部门：${department.name}`}
            icon={<Pencil aria-hidden="true" />}
            iconOnly
            onClick={() => startEditDepartment(department)}
            title="编辑部门"
            variant="secondary"
          >
            编辑
          </Button>
          <Button
            aria-label={`删除部门：${department.name}`}
            icon={<Trash2 aria-hidden="true" />}
            iconOnly
            onClick={() => setDepartmentToDelete(department)}
            title="删除部门"
            variant="danger"
          >
            删除
          </Button>
        </div>
      ),
    },
  ];

  const departmentHeader = (
    <div className="settings-header">
      <div>
        <div className="eyebrow">Organization</div>
        <h2>部门管理</h2>
        <p>维护树级部门结构，账号、草稿、模板和文种会挂到对应部门边界下。</p>
      </div>
      <span className={`status-chip ${status === 'error' ? 'danger' : ''}`}>
        {displayStatus === 'loading' ? '加载中' : `${flatDepartments.length} 个部门`}
      </span>
    </div>
  );

  const departmentDetail = (
    <div className="department-detail-pane">
      <div className="department-detail-header">
        <div>
          <div className="eyebrow">Selected Branch</div>
          <h3>{selectedDepartment?.name ?? '全部部门'}</h3>
          <p>
            {selectedDepartmentPath.length > 0
              ? selectedDepartmentPath.map((department) => department.name).join(' / ')
              : '根级部门视图'}
          </p>
        </div>
      </div>

      <div className="management-list-toolbar">
        <div>
          <strong>{selectedDepartment ? '下级部门' : '根级部门'}</strong>
          <span>{selectedDepartment ? `维护“${selectedDepartment.name}”的直属下级部门。` : '维护组织树的根级部门。'}</span>
        </div>
        <Button disabled={status === 'saving'} icon={<Plus aria-hidden="true" />} onClick={startCreateDepartment} variant="secondary">
          新增部门
        </Button>
      </div>

      <ManagementTable
        ariaLabel={selectedDepartment ? `${selectedDepartment.name}下级部门` : '根级部门'}
        columns={departmentColumns}
        emptyDescription={selectedDepartment ? '可以直接新增该部门的下级节点。' : '先创建根级部门，再继续补充组织树。'}
        emptyIcon={<Building2 aria-hidden="true" />}
        emptyTitle={selectedDepartment ? '暂无下级部门' : '暂无根级部门'}
        getKey={(department) => department.id}
        items={visibleDepartments}
        minWidth="640px"
        status={displayStatus}
      />
    </div>
  );

  return (
    <>
      {embedded ? (
        <section className="settings-tab-content department-management-panel" aria-busy={displayStatus === 'loading' || status === 'saving'} aria-label="部门管理">
          {departmentHeader}
          {message && <StatusMessage title={message} tone={status === 'error' ? 'warning' : 'success'} />}
          {departmentDetail}
        </section>
      ) : (
        <main className="settings-page admin-page" aria-busy={displayStatus === 'loading' || status === 'saving'} aria-label="部门管理">
          <section className="settings-panel admin-panel department-management-panel">
            {departmentHeader}
            {message && <StatusMessage title={message} tone={status === 'error' ? 'warning' : 'success'} />}
            <div className="department-layout">
              <DepartmentTreePane
                departments={departments}
                onSelectDepartment={selectDepartment}
                selectedDepartmentId={selectedDepartmentId}
                status={displayStatus}
              />
              {departmentDetail}
            </div>
          </section>
        </main>
      )}
      <ConfirmDialog
        cancelLabel="取消"
        confirmLabel="删除部门"
        description={departmentToDelete ? `将删除“${departmentToDelete.name}”。仅允许删除没有子部门、账号或业务数据引用的空部门，此操作不可撤销。` : undefined}
        isConfirming={status === 'saving'}
        onCancel={() => setDepartmentToDelete(null)}
        onConfirm={() => void handleDeleteDepartment()}
        open={Boolean(departmentToDelete)}
        title="删除部门？"
      />
      <Dialog
        actions={(
          <>
            <Button disabled={status === 'saving'} onClick={resetDepartmentForm} variant="secondary">
              取消
            </Button>
            <Button
              form="department-management-form"
              icon={<Save aria-hidden="true" />}
              isLoading={status === 'saving'}
              loadingLabel="正在保存"
              type="submit"
            >
              {editingDepartment ? '保存部门' : '创建部门'}
            </Button>
          </>
        )}
        description={editingDepartment ? `正在编辑“${editingDepartment.name}”的部门信息。` : selectedDepartment ? `新部门默认创建在“${selectedDepartment.name}”下。` : '新部门默认创建为根级部门。'}
        onClose={resetDepartmentForm}
        open={departmentDialogOpen}
        title={editingDepartment ? '编辑部门' : '新增部门'}
      >
        <form className="settings-grid management-dialog-form" id="department-management-form" onSubmit={(event) => void handleSaveDepartment(event)}>
          <SelectField
            disabled={status === 'saving'}
            label="上级部门"
            onChange={(event) => setForm((current) => ({ ...current, parentId: event.target.value }))}
            value={form.parentId}
          >
            <option value="">根级部门</option>
            {parentOptions.map(({ department, depth }) => (
              <option key={department.id} value={department.id}>
                {`${'　'.repeat(depth)}${department.name}`}
              </option>
            ))}
          </SelectField>
          <TextField
            disabled={status === 'saving'}
            label="部门名称"
            onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            placeholder="综合管理部"
            value={form.name}
          />
          <TextField
            disabled={status === 'saving'}
            label="排序"
            onChange={(event) => setForm((current) => ({ ...current, sortOrder: event.target.value }))}
            type="number"
            value={form.sortOrder}
          />
        </form>
      </Dialog>
    </>
  );
}

function AccountManagementPage({
  embedded = false,
  selectedDepartment,
  selectedDepartmentId = null,
  sharedDepartments,
}: {
  embedded?: boolean;
  selectedDepartment?: Department | null;
  selectedDepartmentId?: number | null;
  sharedDepartments?: Department[];
} = {}) {
  const { showToast } = useToast();
  const [users, setUsers] = useState<UserAdmin[]>([]);
  const [localDepartments, setLocalDepartments] = useState<Department[]>([]);
  const [status, setStatus] = useState<AdminPageStatus>('loading');
  const [message, setMessage] = useState('');
  const [form, setForm] = useState({
    username: '',
    displayName: '',
    password: '',
    departmentId: '',
    role: 'DRAFTER',
  });
  const [userDialogOpen, setUserDialogOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserAdmin | null>(null);
  const [resetTarget, setResetTarget] = useState<UserAdmin | null>(null);
  const [resetPasswordValue, setResetPasswordValue] = useState('');
  const [userToDisable, setUserToDisable] = useState<UserAdmin | null>(null);
  const departments = sharedDepartments ?? localDepartments;
  const flatDepartments = useMemo(() => flattenDepartments(departments), [departments]);
  const selectedDepartmentIds = useMemo(() => {
    const root = selectedDepartmentId ? findDepartmentById(departments, selectedDepartmentId) : null;
    return root ? collectDepartmentIds(root) : null;
  }, [departments, selectedDepartmentId]);
  const visibleUsers = useMemo(
    () => (selectedDepartmentIds ? users.filter((user) => user.departmentId !== null && selectedDepartmentIds.has(user.departmentId)) : users),
    [selectedDepartmentIds, users],
  );

  useEffect(() => {
    void refreshUsersAndDepartments();
  }, [sharedDepartments]);

  useEffect(() => {
    if (!editingUser && selectedDepartmentId) {
      setForm((current) => ({ ...current, departmentId: String(selectedDepartmentId) }));
    }
  }, [editingUser, selectedDepartmentId]);

  async function refreshUsersAndDepartments() {
    try {
      setStatus('loading');
      setMessage('正在加载账号与部门');
      const [loadedUsers, loadedDepartments] = await Promise.all([
        listUsers(),
        sharedDepartments ? Promise.resolve(sharedDepartments) : listDepartments(),
      ]);
      setUsers(loadedUsers);
      if (!sharedDepartments) {
        setLocalDepartments(loadedDepartments);
      }
      setStatus('idle');
      setMessage('');
    } catch (error) {
      setStatus('error');
      setMessage(error instanceof Error ? error.message : '账号加载失败');
    }
  }

  function startEditUser(user: UserAdmin) {
    setEditingUser(user);
    setForm({
      username: user.username,
      displayName: user.displayName,
      password: '',
      departmentId: user.departmentId ? String(user.departmentId) : '',
      role: user.roles[0] ?? 'DRAFTER',
    });
    setUserDialogOpen(true);
  }

  function startCreateUser() {
    setEditingUser(null);
    setForm({
      username: '',
      displayName: '',
      password: '',
      departmentId: selectedDepartmentId ? String(selectedDepartmentId) : '',
      role: 'DRAFTER',
    });
    setUserDialogOpen(true);
  }

  function resetUserForm() {
    setEditingUser(null);
    setForm({ username: '', displayName: '', password: '', departmentId: '', role: 'DRAFTER' });
    setUserDialogOpen(false);
  }

  async function handleSaveUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.displayName.trim() || (!editingUser && (!form.username.trim() || !form.password))) {
      setStatus('error');
      setMessage('请补齐账号、姓名和初始密码。');
      return;
    }
    try {
      setStatus('saving');
      if (editingUser) {
        await updateUser(editingUser.id, {
          displayName: form.displayName.trim(),
          departmentId: form.departmentId ? Number(form.departmentId) : null,
          roles: [form.role],
          status: editingUser.status,
        });
      } else {
        await createUser({
          username: form.username.trim(),
          displayName: form.displayName.trim(),
          password: form.password,
          departmentId: form.departmentId ? Number(form.departmentId) : null,
          roles: [form.role],
        });
      }
      setUsers(await listUsers());
      resetUserForm();
      setStatus('idle');
      setMessage(editingUser ? '账号已更新' : '账号已创建');
      showToast({ title: editingUser ? '账号已更新' : '账号已创建', tone: 'success' });
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '账号保存失败';
      setStatus('error');
      setMessage(nextMessage);
      showToast({ title: nextMessage, tone: 'error' });
    }
  }

  async function handleResetPassword() {
    if (!resetTarget || !resetPasswordValue) {
      return;
    }
    try {
      setStatus('saving');
      await resetUserPassword(resetTarget.id, resetPasswordValue);
      setResetTarget(null);
      setResetPasswordValue('');
      setStatus('idle');
      setMessage('密码已重置');
      showToast({ title: '密码已重置', tone: 'success' });
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '密码重置失败';
      setStatus('error');
      setMessage(nextMessage);
      showToast({ title: nextMessage, tone: 'error' });
    }
  }

  async function handleDisableUser() {
    if (!userToDisable) {
      return;
    }
    try {
      setStatus('saving');
      await disableUser(userToDisable.id);
      setUsers(await listUsers());
      setUserToDisable(null);
      setStatus('idle');
      setMessage('账号已停用');
      showToast({ title: '账号已停用', tone: 'success' });
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '账号停用失败';
      setStatus('error');
      setMessage(nextMessage);
      showToast({ title: nextMessage, tone: 'error' });
    }
  }

  const userColumns: Array<ManagementTableColumn<UserAdmin>> = [
    {
      key: 'username',
      header: '账号',
      width: 'minmax(130px, 0.9fr)',
      render: (user) => <span className="management-table-title">{user.username}</span>,
    },
    {
      key: 'displayName',
      header: '姓名',
      width: 'minmax(120px, 0.9fr)',
      render: (user) => <span className="management-table-text">{user.displayName}</span>,
    },
    {
      key: 'department',
      header: '所属部门',
      width: 'minmax(150px, 1fr)',
      render: (user) => <span className="management-table-text">{user.departmentName ?? '未分配部门'}</span>,
    },
    {
      key: 'roles',
      header: '角色',
      width: 'minmax(180px, 1.1fr)',
      render: (user) => <span className="management-table-text">{user.roles.map(roleLabel).join('、')}</span>,
    },
    {
      key: 'status',
      header: '状态',
      width: 'minmax(90px, 0.5fr)',
      render: (user) => <span className="management-table-text">{user.status}</span>,
    },
    {
      key: 'actions',
      header: '操作',
      width: 'minmax(156px, max-content)',
      align: 'end',
      render: (user) => (
        <div className="management-table-actions">
          <Button
            aria-label={`编辑账号：${user.displayName}`}
            icon={<Pencil aria-hidden="true" />}
            iconOnly
            onClick={() => startEditUser(user)}
            title="编辑账号"
            variant="secondary"
          >
            编辑
          </Button>
          <Button
            aria-label={`重置密码：${user.displayName}`}
            icon={<KeyRound aria-hidden="true" />}
            iconOnly
            onClick={() => setResetTarget(user)}
            title="重置密码"
            variant="secondary"
          >
            重置密码
          </Button>
          <Button
            aria-label={`停用账号：${user.displayName}`}
            icon={<Trash2 aria-hidden="true" />}
            iconOnly
            onClick={() => setUserToDisable(user)}
            title="停用账号"
            variant="danger"
          >
            停用
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <main className={`settings-page admin-page ${embedded ? 'embedded-settings-page' : ''}`} aria-busy={status === 'loading' || status === 'saving'} aria-label="账号管理">
        <section className="settings-panel admin-panel">
          <div className="settings-header">
            <div>
              <div className="eyebrow">Accounts</div>
              <h2>账号管理</h2>
              <p>{selectedDepartment ? `当前显示“${selectedDepartment.name}”及其下级部门账号。` : '为每个部门配置账号与角色。起草、模板和文种数据会按账号归属过滤。'}</p>
            </div>
            <span className={`status-chip ${status === 'error' ? 'danger' : ''}`}>
              {status === 'loading' ? '加载中' : `${visibleUsers.length} 个账号`}
            </span>
          </div>

          {message && <StatusMessage title={message} tone={status === 'error' ? 'warning' : 'success'} />}

          <div className="management-list-toolbar">
            <div>
              <strong>{selectedDepartment ? '当前部门账号' : '全部账号'}</strong>
              <span>{selectedDepartment ? `维护“${selectedDepartment.name}”及其下级部门账号。` : '维护系统内起草、模板和系统管理账号。'}</span>
            </div>
            <Button disabled={status === 'saving'} icon={<Plus aria-hidden="true" />} onClick={startCreateUser} variant="secondary">
              新增账号
            </Button>
          </div>

          <ManagementTable
            ariaLabel="账号列表"
            columns={userColumns}
            emptyDescription={selectedDepartment ? '可以新增账号并分配到当前部门。' : '先创建起草人或模板管理员账号。'}
            emptyIcon={<Users aria-hidden="true" />}
            emptyTitle={selectedDepartment ? '当前部门暂无账号' : '暂无账号'}
            getKey={(user) => user.id}
            items={visibleUsers}
            minWidth="880px"
            status={status}
          />
        </section>
      </main>
      <Dialog
        actions={(
          <>
            <Button disabled={status === 'saving'} onClick={resetUserForm} variant="secondary">
              取消
            </Button>
            <Button
              form="account-management-form"
              icon={<Save aria-hidden="true" />}
              isLoading={status === 'saving'}
              loadingLabel="正在保存"
              type="submit"
            >
              {editingUser ? '保存账号' : '创建账号'}
            </Button>
          </>
        )}
        description={editingUser ? `正在编辑“${editingUser.displayName}”的账号信息。` : selectedDepartment ? `新账号默认分配到“${selectedDepartment.name}”。` : '新账号可选择所属部门与角色。'}
        onClose={resetUserForm}
        open={userDialogOpen}
        title={editingUser ? '编辑账号' : '新增账号'}
      >
        <form className="settings-grid management-dialog-form" id="account-management-form" onSubmit={(event) => void handleSaveUser(event)}>
          <TextField
            disabled={status === 'saving' || Boolean(editingUser)}
            label="账号"
            onChange={(event) => setForm((current) => ({ ...current, username: event.target.value }))}
            placeholder="zhangsan"
            value={form.username}
          />
          <TextField
            disabled={status === 'saving'}
            label="姓名"
            onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
            placeholder="张三"
            value={form.displayName}
          />
          {!editingUser && (
            <TextField
              autoComplete="new-password"
              disabled={status === 'saving'}
              label="初始密码"
              onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
              type="password"
              value={form.password}
            />
          )}
          <SelectField
            disabled={status === 'saving'}
            label="所属部门"
            onChange={(event) => setForm((current) => ({ ...current, departmentId: event.target.value }))}
            value={form.departmentId}
          >
            <option value="">暂不分配</option>
            {flatDepartments.map(({ department, depth }) => (
              <option key={department.id} value={department.id}>
                {`${'　'.repeat(depth)}${department.name}`}
              </option>
            ))}
          </SelectField>
          <SelectField
            disabled={status === 'saving'}
            label="角色"
            onChange={(event) => setForm((current) => ({ ...current, role: event.target.value }))}
            value={form.role}
          >
            <option value="DRAFTER">起草人</option>
            <option value="TEMPLATE_ADMIN">模板管理员</option>
            <option value="SYSTEM_ADMIN">系统管理员</option>
          </SelectField>
        </form>
      </Dialog>
      <Dialog
        actions={(
          <>
            <Button disabled={status === 'saving'} onClick={() => setResetTarget(null)} variant="secondary">
              取消
            </Button>
            <Button
              disabled={!resetPasswordValue}
              isLoading={status === 'saving'}
              loadingLabel="正在重置"
              onClick={() => void handleResetPassword()}
            >
              确认重置
            </Button>
          </>
        )}
        description={resetTarget ? `为“${resetTarget.displayName}”设置新的登录密码。` : undefined}
        onClose={() => setResetTarget(null)}
        open={Boolean(resetTarget)}
        title="重置账号密码"
      >
        <TextField
          autoComplete="new-password"
          disabled={status === 'saving'}
          label="新密码"
          onChange={(event) => setResetPasswordValue(event.target.value)}
          type="password"
          value={resetPasswordValue}
        />
      </Dialog>
      <ConfirmDialog
        cancelLabel="取消"
        confirmLabel="停用账号"
        description={userToDisable ? `停用“${userToDisable.displayName}”后，该账号将无法登录。` : undefined}
        isConfirming={status === 'saving'}
        onCancel={() => setUserToDisable(null)}
        onConfirm={() => void handleDisableUser()}
        open={Boolean(userToDisable)}
        title="停用账号？"
      />
    </>
  );
}

function DocumentTypeFolderGrid({
  ariaLabel,
  disabled = false,
  documentTypes,
  onDocumentTypesChange,
  onSelectDocumentType,
}: {
  ariaLabel?: string;
  disabled?: boolean;
  documentTypes: DocumentType[];
  onDocumentTypesChange: (documentTypes: DocumentType[]) => void;
  onSelectDocumentType: (documentTypeCode: string) => void;
}) {
  const { showToast } = useToast();
  const [typeToDelete, setTypeToDelete] = useState<DocumentType | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  async function reloadDocumentTypes() {
    onDocumentTypesChange(await listDocumentTypes());
  }

  async function handleConfirmDeleteDocumentType() {
    if (!typeToDelete) {
      return;
    }
    try {
      setIsDeleting(true);
      await deleteDocumentType(typeToDelete.code);
      await reloadDocumentTypes();
      setTypeToDelete(null);
      showToast({ title: '文种已删除', tone: 'success' });
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '文种删除失败';
      showToast({ title: nextMessage, tone: 'error' });
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <>
      <div className="template-folder-grid" aria-label={ariaLabel}>
        {documentTypes.map((type) => (
          <article className="document-type-folder-card" key={type.code}>
            <button
              aria-label={type.name}
              className="template-folder-card"
              disabled={disabled || isDeleting}
              onClick={() => onSelectDocumentType(type.code)}
              type="button"
            >
              <FolderOpen aria-hidden="true" />
              <span>{type.name}</span>
              <small>{type.code}</small>
            </button>
            <div className="template-card-controls document-type-folder-controls">
              <button
                aria-label="删除文种"
                className="template-card-icon-button danger"
                disabled={disabled || isDeleting}
                onClick={() => setTypeToDelete(type)}
                title={`删除文种：${type.name}`}
                type="button"
              >
                <Trash2 aria-hidden="true" />
              </button>
            </div>
          </article>
        ))}
      </div>
      <ConfirmDialog
        cancelLabel="取消"
        confirmLabel="删除文种"
        description={typeToDelete ? `将删除“${typeToDelete.name}”。若已有草稿或模板依赖该文种，后端会阻断本次操作。` : undefined}
        isConfirming={isDeleting}
        onCancel={() => setTypeToDelete(null)}
        onConfirm={() => void handleConfirmDeleteDocumentType()}
        open={Boolean(typeToDelete)}
        title="删除文种？"
      />
    </>
  );
}

function DocumentTypeCreateButton({
  disabled = false,
  onDocumentTypesChange,
}: {
  disabled?: boolean;
  onDocumentTypesChange: (documentTypes: DocumentType[]) => void;
}) {
  const { showToast } = useToast();
  const [status, setStatus] = useState<'idle' | 'saving' | 'error'>('idle');
  const [message, setMessage] = useState('');
  const [form, setForm] = useState({ code: '', name: '', sortOrder: '10' });
  const [typeDialogOpen, setTypeDialogOpen] = useState(false);

  function startCreateType() {
    setForm({ code: '', name: '', sortOrder: '10' });
    setMessage('');
    setStatus('idle');
    setTypeDialogOpen(true);
  }

  function resetTypeForm() {
    setForm({ code: '', name: '', sortOrder: '10' });
    setMessage('');
    setStatus('idle');
    setTypeDialogOpen(false);
  }

  async function reloadDocumentTypes() {
    onDocumentTypesChange(await listDocumentTypes());
  }

  async function handleSaveDocumentType(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.code.trim() || !form.name.trim()) {
      setStatus('error');
      setMessage('请填写文种编码和名称。');
      return;
    }
    try {
      setStatus('saving');
      const payload = {
        code: form.code.trim().toUpperCase(),
        name: form.name.trim(),
        sortOrder: Number(form.sortOrder || 0),
      };
      await createDocumentType(payload);
      await reloadDocumentTypes();
      resetTypeForm();
      setStatus('idle');
      showToast({ title: '文种已创建', tone: 'success' });
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '文种保存失败';
      setStatus('error');
      setMessage(nextMessage);
      showToast({ title: nextMessage, tone: 'error' });
    }
  }

  return (
    <>
      <Button disabled={disabled || status === 'saving'} icon={<Plus aria-hidden="true" />} onClick={startCreateType}>
        新增文种
      </Button>
      <Dialog
        actions={(
          <>
            <Button disabled={status === 'saving'} onClick={resetTypeForm} variant="secondary">
              取消
            </Button>
            <Button
              form="document-type-management-form"
              icon={<Save aria-hidden="true" />}
              isLoading={status === 'saving'}
              loadingLabel="正在保存"
              type="submit"
            >
              创建文种
            </Button>
          </>
        )}
        description="新增文种后会出现在草稿列表和模板管理的文种入口中。"
        onClose={resetTypeForm}
        open={typeDialogOpen}
        title="新增文种"
      >
        {message && <StatusMessage title={message} tone="warning" />}
        <form className="settings-grid management-dialog-form" id="document-type-management-form" onSubmit={(event) => void handleSaveDocumentType(event)}>
          <TextField
            disabled={status === 'saving'}
            label="文种编码"
            onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))}
            placeholder="NOTICE"
            value={form.code}
          />
          <TextField
            disabled={status === 'saving'}
            label="文种名称"
            onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            placeholder="通知"
            value={form.name}
          />
          <TextField
            disabled={status === 'saving'}
            label="排序"
            onChange={(event) => setForm((current) => ({ ...current, sortOrder: event.target.value }))}
            type="number"
            value={form.sortOrder}
          />
        </form>
      </Dialog>
    </>
  );
}

function ExportRecordsPage({
  downloadingRecordId,
  message,
  onDownload,
  onOpenDetail,
  onRetry,
  records,
  retryingRecordId,
  status,
}: {
  downloadingRecordId: number | null;
  message: string;
  onDownload: (record: ExportRecordSummary) => void;
  onOpenDetail: (record: ExportRecordSummary) => void;
  onRetry: (record: ExportRecordSummary) => void;
  records: ExportRecordSummary[];
  retryingRecordId: number | null;
  status: ExportRecordListStatus;
}) {
  const columns: Array<ManagementTableColumn<ExportRecordSummary>> = [
    {
      key: 'draft',
      header: '草稿',
      width: '1.5fr',
      render: (record) => (
        <span className="management-table-title">{record.draftTitle ?? '未绑定草稿'}</span>
      ),
    },
    {
      key: 'template',
      header: '模板版本',
      width: '1.1fr',
      render: (record) => (
        <span className="management-table-text">{record.templateName} v{record.templateVersion}</span>
      ),
    },
    {
      key: 'status',
      header: '状态',
      width: '1.2fr',
      render: (record) => (
        <div>
          <span className={`status-chip ${record.status === 'SUCCESS' ? 'success' : 'danger'}`}>
            {exportRecordStatusLabel(record.status)}
          </span>
          {record.errorMessage && (
            <span className="management-table-text">{record.errorMessage}</span>
          )}
        </div>
      ),
    },
    {
      key: 'createdAt',
      header: '导出时间',
      width: '0.9fr',
      render: (record) => <span className="management-table-text">{formatTimestamp(record.createdAt)}</span>,
    },
    {
      key: 'actions',
      header: '操作',
      width: '1.4fr',
      align: 'end',
      render: (record) => (
        <div className="management-table-actions">
          <Button
            aria-label={`查看导出记录：${record.draftTitle ?? record.fileName}`}
            icon={<Eye aria-hidden="true" />}
            onClick={() => onOpenDetail(record)}
            variant="secondary"
          >
            详情
          </Button>
          {record.canRetry && (
            <Button
              aria-label={`重试导出记录：${record.draftTitle ?? record.fileName}`}
              icon={<RotateCcw aria-hidden="true" />}
              isLoading={retryingRecordId === record.id}
              loadingLabel="重试中"
              onClick={() => onRetry(record)}
              variant="secondary"
            >
              重试
            </Button>
          )}
          {record.canDownload && (
            <Button
              aria-label={`下载导出文件：${record.fileName}`}
              icon={<FileDown aria-hidden="true" />}
              isLoading={downloadingRecordId === record.id}
              loadingLabel="正在下载"
              onClick={() => onDownload(record)}
              variant="secondary"
            >
              下载
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <main className="settings-page" aria-busy={status === 'loading'} aria-label="导出记录">
      <section className="settings-panel template-admin-panel">
        <div className="settings-header">
          <div>
            <div className="eyebrow">Export Records</div>
            <h2>导出记录</h2>
            <p>追踪 Word 导出结果、模板版本和失败原因。成功导出的历史文件可在这里重新下载。</p>
          </div>
          <span className={`status-chip ${status === 'error' ? 'danger' : ''}`}>
            {records.length} 条记录
          </span>
        </div>

        {message && <StatusMessage title={message} tone={status === 'error' ? 'warning' : 'success'} />}

        <ManagementTable<ExportRecordSummary>
          ariaLabel="导出记录列表"
          columns={columns}
          emptyDescription="工作台成功导出 Word 后，会在这里形成可追溯记录。"
          emptyIcon={<FileDown aria-hidden="true" />}
          emptyTitle="暂无导出记录"
          getKey={(record) => record.id}
          items={records}
          minWidth="1040px"
          status={status}
        />
      </section>
    </main>
  );
}

function ExportRecordDetailDialog({
  detail,
  isRetrying,
  message,
  onClose,
  onRetry,
  open,
  status,
}: {
  detail: ExportRecordDetail | null;
  isRetrying: boolean;
  message: string;
  onClose: () => void;
  onRetry: (record: ExportRecordDetail) => void;
  open: boolean;
  status: ExportRecordListStatus;
}) {
  return (
    <Dialog
      actions={(
        <>
          <Button disabled={isRetrying} onClick={onClose} variant="secondary">
            关闭
          </Button>
          {detail?.canRetry && (
            <Button
              icon={<RotateCcw aria-hidden="true" />}
              isLoading={isRetrying}
              loadingLabel="重试中"
              onClick={() => onRetry(detail)}
            >
              重试导出
            </Button>
          )}
        </>
      )}
      description={detail ? `${detail.templateName} v${detail.templateVersion} · ${detail.fileName}` : '正在读取导出记录'}
      onClose={onClose}
      open={open}
      title="导出详情"
    >
      {message && <StatusMessage title={message} tone={status === 'error' ? 'warning' : 'info'} />}
      {detail && (
        <div className="template-profile-grid">
          <section className="template-profile-box">
            <h3>基础信息</h3>
            <div className="template-profile-item">
              <strong>草稿</strong>
              <span>{detail.draftTitle ?? '未绑定草稿'}</span>
            </div>
            <div className="template-profile-item">
              <strong>模板版本</strong>
              <span>{detail.templateName} v{detail.templateVersion}</span>
            </div>
            <div className="template-profile-item">
              <strong>导出时间</strong>
              <span>{formatTimestamp(detail.createdAt)}</span>
            </div>
          </section>
          <section className="template-profile-box">
            <h3>执行状态</h3>
            <div className="template-profile-item">
              <strong>状态</strong>
              <span className={`status-chip ${detail.status === 'SUCCESS' ? 'success' : 'danger'}`}>
                {exportRecordStatusLabel(detail.status)}
              </span>
            </div>
            <div className="template-profile-item">
              <strong>错误码</strong>
              <span>{detail.errorCode ?? '无'}</span>
            </div>
            <div className="template-profile-item">
              <strong>文件状态</strong>
              <span>{detail.fileAvailable ? '历史文件可下载' : '历史文件不可用'}</span>
            </div>
          </section>
          {detail.errorMessage && (
            <section className="template-profile-box template-profile-wide">
              <h3>失败原因</h3>
              <StatusMessage title={detail.errorMessage} tone="warning" />
            </section>
          )}
          {detail.status === 'SUCCESS' && !detail.fileAvailable && (
            <section className="template-profile-box template-profile-wide">
              <StatusMessage title="历史文件不可用" tone="warning">
                <span>记录仍可追溯模板版本，但本地文件可能已被移动或清理，需要重新导出。</span>
              </StatusMessage>
            </section>
          )}
        </div>
      )}
    </Dialog>
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

function DraftListPage({
  currentDraftId,
  documentTypes,
  drafts,
  message,
  onCreateBlankDraft,
  onDeleteDraft,
  onDocumentTypesChange,
  onOpenDraft,
  onRenameDraft,
  onSelectDocumentType,
  preferredPageMode,
  selectedDocumentTypeCode,
  status,
}: {
  currentDraftId: number | null;
  documentTypes: DocumentType[];
  drafts: DraftSummary[];
  message: string;
  onCreateBlankDraft: (documentTypeCode: string) => void;
  onDeleteDraft: (draftId: number) => Promise<void>;
  onDocumentTypesChange: (documentTypes: DocumentType[]) => void;
  onOpenDraft: (draftId: number) => void;
  onRenameDraft: (draftId: number, title: string) => Promise<void>;
  onSelectDocumentType: (documentTypeCode: string) => void;
  preferredPageMode: DraftListPageMode;
  selectedDocumentTypeCode: string;
  status: DraftListStatus;
}) {
  const selectedDocumentType = documentTypes.find((type) => type.code === selectedDocumentTypeCode)
    ?? documentTypes[0]
    ?? { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 };
  const [pageMode, setPageMode] = useState<DraftListPageMode>(preferredPageMode);
  const [draftToDelete, setDraftToDelete] = useState<DraftSummary | null>(null);
  const [draftToRename, setDraftToRename] = useState<DraftSummary | null>(null);
  const [renameTitle, setRenameTitle] = useState('');
  const [isDeletingDraft, setIsDeletingDraft] = useState(false);
  const [isRenamingDraft, setIsRenamingDraft] = useState(false);
  const isBusy = status === 'loading' || status === 'creating';
  const empty = !isBusy && drafts.length === 0 && status !== 'error';

  useEffect(() => {
    setPageMode(preferredPageMode);
  }, [preferredPageMode]);

  function handleSelectDocumentType(code: string) {
    onSelectDocumentType(code);
    setPageMode('list');
  }

  function handleBackToFolders() {
    setPageMode('folders');
  }

  async function handleConfirmDeleteDraft() {
    if (!draftToDelete) {
      return;
    }
    try {
      setIsDeletingDraft(true);
      await onDeleteDraft(draftToDelete.id);
      setDraftToDelete(null);
    } finally {
      setIsDeletingDraft(false);
    }
  }

  function handleStartRenameDraft(item: DraftSummary) {
    setDraftToRename(item);
    setRenameTitle(item.title);
  }

  async function handleConfirmRenameDraft() {
    if (!draftToRename || !renameTitle.trim()) {
      return;
    }
    try {
      setIsRenamingDraft(true);
      await onRenameDraft(draftToRename.id, renameTitle.trim());
      setDraftToRename(null);
      setRenameTitle('');
    } finally {
      setIsRenamingDraft(false);
    }
  }

  return (
    <>
    <main className="settings-page drafts-page" aria-busy={isBusy} aria-label="草稿列表">
      <section className="settings-panel template-admin-panel">
        <div className="settings-header">
          <div>
            <div className="eyebrow">Drafts</div>
            <h2>
              草稿列表
              {pageMode === 'list' && <span className="template-title-suffix"> - {selectedDocumentType.name}</span>}
            </h2>
            <p>{pageMode === 'list' ? '新建草稿会留在当前文种列表中，再进入工作台继续编辑。' : '先选择文种，再管理该文种下的草稿。'}</p>
          </div>
          {pageMode === 'folders' ? (
            <DocumentTypeCreateButton disabled={isBusy} onDocumentTypesChange={onDocumentTypesChange} />
          ) : (
            <span className={`status-chip ${status === 'error' ? 'danger' : ''}`}>
              {status === 'loading' ? '加载中' : `${drafts.length} 个草稿`}
            </span>
          )}
        </div>

        {pageMode === 'folders' && (
          <DocumentTypeFolderGrid
            ariaLabel="文种"
            disabled={isBusy}
            documentTypes={documentTypes}
            onDocumentTypesChange={onDocumentTypesChange}
            onSelectDocumentType={handleSelectDocumentType}
          />
        )}

        {pageMode === 'list' && (
          <>
            <div className="template-list-toolbar">
              <Button icon={<ArrowLeft aria-hidden="true" />} onClick={handleBackToFolders} variant="secondary">
                返回文种
              </Button>
              <Button
                disabled={isBusy}
                icon={<Plus aria-hidden="true" />}
                isLoading={status === 'creating'}
                loadingLabel="正在创建"
                onClick={() => onCreateBlankDraft(selectedDocumentType.code)}
              >
                新建草稿
              </Button>
            </div>

            {message && !(empty && message === '当前文种暂无草稿') && (
              <StatusMessage title={message} tone={status === 'error' ? 'warning' : 'success'} />
            )}

            {isBusy && (
              <div className="template-card-grid" aria-label="草稿加载中" aria-live="polite">
                <div className="template-card skeleton-row" />
                <div className="template-card skeleton-row" />
              </div>
            )}

            {empty && (
              <div className="template-empty-panel">
                <FileText aria-hidden="true" />
                <div>
                  <strong>当前文种暂无草稿</strong>
                  <span>为{selectedDocumentType.name}新建第一个草稿，随后进入工作台编辑保存。</span>
                </div>
              </div>
            )}

            {!isBusy && drafts.length > 0 && (
              <div className="template-card-grid" aria-label={`${selectedDocumentType.name}草稿`}>
                {drafts.map((item) => (
                    <article className="template-card" key={item.id}>
                      <div className="template-card-controls">
                        <button
                          aria-label={`重命名草稿：${item.title}`}
                          className="template-card-icon-button"
                          disabled={isBusy || isRenamingDraft || isDeletingDraft}
                          onClick={() => handleStartRenameDraft(item)}
                          type="button"
                        >
                          <Pencil aria-hidden="true" />
                        </button>
                        <button
                          aria-label={`删除草稿：${item.title}`}
                          className="template-card-icon-button danger"
                          disabled={isBusy || isRenamingDraft || isDeletingDraft}
                          onClick={() => setDraftToDelete(item)}
                          type="button"
                        >
                          <Trash2 aria-hidden="true" />
                        </button>
                      </div>
                      <div className="template-card-title">
                      <FileText aria-hidden="true" />
                      <div>
                        <h3>{item.title}</h3>
                        <span className="template-card-meta">
                          {item.status}
                          {currentDraftId === item.id ? ' · 当前草稿' : ''}
                        </span>
                      </div>
                    </div>
                    <p className="template-card-file">
                      {item.templateVersionId ? `已绑定模板版本 #${item.templateVersionId}` : '未选择模板'}
                      {' · '}
                      {formatTimestamp(item.updatedAt)}
                    </p>
                    <div className="template-card-actions">
                      <Button icon={<FileText aria-hidden="true" />} onClick={() => onOpenDraft(item.id)} variant="secondary">
                        进入工作台
                      </Button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </>
        )}
      </section>
    </main>

    <ConfirmDialog
      cancelLabel="继续保留"
      confirmLabel="删除草稿"
      description={draftToDelete ? `将删除“${draftToDelete.title}”及其草稿块、材料和质检结果。此操作不可撤销。` : undefined}
      isConfirming={isDeletingDraft}
      onCancel={() => setDraftToDelete(null)}
      onConfirm={() => void handleConfirmDeleteDraft()}
      open={Boolean(draftToDelete)}
      title="删除这个草稿？"
    />
    <Dialog
      actions={(
        <>
          <Button disabled={isRenamingDraft} onClick={() => setDraftToRename(null)} variant="secondary">
            取消
          </Button>
          <Button
            disabled={!renameTitle.trim()}
            isLoading={isRenamingDraft}
            loadingLabel="保存中"
            onClick={() => void handleConfirmRenameDraft()}
          >
            保存名称
          </Button>
        </>
      )}
      description={draftToRename ? `调整“${draftToRename.title}”在草稿列表中的显示名称。` : undefined}
      onClose={() => setDraftToRename(null)}
      open={Boolean(draftToRename)}
      title="重命名草稿"
    >
      <TextField
        disabled={isRenamingDraft}
        label="草稿名称"
        onChange={(event) => setRenameTitle(event.target.value)}
        value={renameTitle}
      />
    </Dialog>
    </>
  );
}

function AiSettingsPage({
  apiKey,
  embedded = false,
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
  embedded?: boolean;
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
    <main className={`settings-page ${embedded ? 'embedded-settings-page' : ''}`} aria-label="AI 配置">
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

function canAccessView(user: AuthUser, view: AppView) {
  if (view === 'settings') {
    return user.roles.includes('SYSTEM_ADMIN') || user.roles.includes('TEMPLATE_ADMIN');
  }
  return true;
}

function roleLabel(role?: string) {
  switch (role) {
    case 'SYSTEM_ADMIN':
      return '系统管理员';
    case 'TEMPLATE_ADMIN':
      return '模板管理员';
    case 'DRAFTER':
      return '起草人';
    default:
      return role ?? '未分配角色';
  }
}

function sortDepartments(departments: Department[]) {
  return [...departments].sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id);
}

function flattenDepartments(departments: Department[], depth = 0): Array<{ department: Department; depth: number }> {
  return sortDepartments(departments).flatMap((department) => [
    { department, depth },
    ...flattenDepartments(department.children ?? [], depth + 1),
  ]);
}

function flattenDepartmentDescendants(department: Department): Department[] {
  return sortDepartments(department.children ?? []).flatMap((child) => [
    child,
    ...flattenDepartmentDescendants(child),
  ]);
}

function filterDepartmentTree(departments: Department[], query: string): Department[] {
  const normalizedQuery = query.trim().toLowerCase();
  if (!normalizedQuery) {
    return sortDepartments(departments);
  }
  return sortDepartments(departments).flatMap((department) => {
    const filteredChildren = filterDepartmentTree(department.children ?? [], query);
    const matches = department.name.toLowerCase().includes(normalizedQuery);
    return matches || filteredChildren.length > 0 ? [{ ...department, children: filteredChildren }] : [];
  });
}

function findDepartmentById(departments: Department[], id: number): Department | null {
  for (const department of departments) {
    if (department.id === id) {
      return department;
    }
    const childMatch = findDepartmentById(department.children ?? [], id);
    if (childMatch) {
      return childMatch;
    }
  }
  return null;
}

function getDepartmentPath(departments: Department[], id: number): Department[] {
  for (const department of departments) {
    if (department.id === id) {
      return [department];
    }
    const childPath = getDepartmentPath(department.children ?? [], id);
    if (childPath.length > 0) {
      return [department, ...childPath];
    }
  }
  return [];
}

function isDepartmentInSubtree(root: Department, id: number): boolean {
  return root.id === id || (root.children ?? []).some((child) => isDepartmentInSubtree(child, id));
}

function countDepartmentDescendants(department: Department): number {
  return (department.children ?? []).reduce((count, child) => count + 1 + countDepartmentDescendants(child), 0);
}

function collectDepartmentIds(department: Department): Set<number> {
  const ids = new Set<number>([department.id]);
  (department.children ?? []).forEach((child) => {
    collectDepartmentIds(child).forEach((id) => ids.add(id));
  });
  return ids;
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

function AiProgress({ detail, label, value }: { detail: string; label: string; value: number }) {
  const normalizedValue = Math.min(100, Math.max(0, value));
  const roundedValue = Math.round(normalizedValue);

  return (
    <div
      aria-label={label}
      aria-valuemax={100}
      aria-valuemin={0}
      aria-valuenow={roundedValue}
      aria-valuetext={`${detail}, ${roundedValue}%`}
      className="ai-progress"
      role="progressbar"
    >
      <div className="ai-progress-header">
        <span>{detail}</span>
        <span>{roundedValue}%</span>
      </div>
      <div className="ai-progress-track" aria-hidden="true">
        <span className="ai-progress-bar" style={{ transform: `scaleX(${normalizedValue / 100})` }} />
      </div>
    </div>
  );
}

function useEstimatedProgress(isActive: boolean) {
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    if (!isActive) {
      setProgress(0);
      return undefined;
    }

    const startedAt = Date.now();
    setProgress(0);
    let frameId = 0;
    const updateProgress = () => {
      setProgress(estimateAiProgress(Date.now() - startedAt));
      frameId = window.requestAnimationFrame(updateProgress);
    };

    frameId = window.requestAnimationFrame(updateProgress);

    return () => window.cancelAnimationFrame(frameId);
  }, [isActive]);

  return progress;
}

function qualitySeverityLabel(severity: QualityCheckItem['severity']) {
  return severity === 'ERROR' ? '错误' : severity === 'WARNING' ? '警告' : '建议';
}

function exportRecordStatusLabel(status: string) {
  return status === 'SUCCESS' ? '成功' : status === 'FAILED' ? '失败' : status;
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

function templateKindLabel(templateKind: string) {
  const labels: Record<string, string> = {
    STANDARD_PLACEHOLDER_TEMPLATE: '标准占位符模板',
    STYLE_TEMPLATE: '样式模板',
    REFERENCE_DOCUMENT: '范文/示例公文',
    ORDINARY_DOCUMENT: '普通 Word 文件',
    UNKNOWN_DOCUMENT: '待人工确认',
  };
  return labels[templateKind] ?? templateKind;
}

function slotKeyForMappingRole(role: string) {
  switch (role) {
    case 'TITLE':
      return 'title';
    case 'RECIPIENT':
      return 'recipient';
    case 'BODY':
    case 'BODY_HEADING_LEVEL_1':
    case 'BODY_HEADING_LEVEL_2':
    case 'BODY_HEADING_LEVEL_3':
      return 'body';
    case 'ATTACHMENT_NOTE':
    case 'ATTACHMENT_CONTENT':
    case 'TABLE_ATTACHMENT':
      return 'attachment';
    case 'SIGNATURE':
      return 'signature';
    case 'DATE':
      return 'date';
    default:
      return '';
  }
}

function confirmVisibleMappingItems(items: StructureMappingItem[]) {
  return items.map((item) => {
    if (item.role === 'IGNORE') {
      return {
        ...item,
        status: 'IGNORED',
        source: item.source || 'USER',
      };
    }
    if (item.role && item.role !== 'UNKNOWN') {
      return {
        ...item,
        slotKey: item.slotKey || slotKeyForMappingRole(item.role),
        status: 'CONFIRMED',
        source: 'USER',
        confidence: item.confidence > 0 ? item.confidence : 1,
      };
    }
    return {
      ...item,
      status: 'NEEDS_REVIEW',
      source: item.source || 'SYSTEM',
    };
  });
}

function nodeRoleForBlockType(blockType: string) {
  switch (blockType) {
    case 'TITLE':
      return 'TITLE';
    case 'RECIPIENT':
      return 'RECIPIENT';
    case 'ATTACHMENT':
      return 'ATTACHMENT_NOTE';
    case 'SIGNATURE':
      return 'SIGNATURE';
    case 'DATE':
      return 'DATE';
    default:
      return blockType;
  }
}

function blockTypeForWorkbenchNode(node: WorkbenchNode | null) {
  switch (node?.nodeType) {
    case 'TITLE':
      return 'TITLE';
    case 'RECIPIENT':
      return 'RECIPIENT';
    case 'ATTACHMENT':
      return 'ATTACHMENT';
    case 'SIGNATURE':
      return 'SIGNATURE';
    case 'DATE':
      return 'DATE';
    default:
      return null;
  }
}

function workbenchNodeRoleLabel(nodeType: WorkbenchNode['nodeType']) {
  const labels: Record<WorkbenchNode['nodeType'], string> = {
    TITLE: '标题',
    RECIPIENT: '主送',
    BODY_SECTION: '正文',
    ATTACHMENT: '附件',
    SIGNATURE: '落款',
    DATE: '日期',
    STATIC_TEMPLATE_TEXT: '固定文本',
    HEADER: '页眉',
    FOOTER: '页脚',
  };
  return labels[nodeType];
}

function workbenchNodeLabel(node: WorkbenchNode, index: number) {
  if (node.nodeType === 'BODY_SECTION') {
    return bodyNodeLabel(node, index);
  }
  return node.label || workbenchNodeRoleLabel(node.nodeType);
}

function aiNodeContextForWorkbenchNode(node: WorkbenchNode | null): AiNodeRequestContext | null {
  if (!node?.draftNodeId) {
    return null;
  }
  return {
    nodeId: node.draftNodeId,
    nodeRole: aiRoleForWorkbenchNode(node),
    nodeTitle: aiNodeTitleForWorkbenchNode(node),
    nodeContext: node.content,
  };
}

function aiNodeTitleForWorkbenchNode(node: WorkbenchNode) {
  if (node.nodeType === 'BODY_SECTION') {
    return bodyNodeLabel(node, 0);
  }
  return node.label || workbenchNodeRoleLabel(node.nodeType);
}

function aiRoleForWorkbenchNode(node: WorkbenchNode) {
  if (node.role) {
    return normalizeAiNodeRole(node.role);
  }
  switch (node.nodeType) {
    case 'TITLE':
      return 'TITLE';
    case 'RECIPIENT':
      return 'RECIPIENT';
    case 'BODY_SECTION':
      return 'BODY';
    case 'ATTACHMENT':
      return 'ATTACHMENT_NOTE';
    case 'SIGNATURE':
      return 'SIGNATURE';
    case 'DATE':
      return 'DATE';
    default:
      return 'STATIC_TEXT';
  }
}

function normalizeAiNodeRole(role: string) {
  if (role.startsWith('BODY_HEADING_LEVEL_')) {
    return 'BODY';
  }
  if (role === 'ATTACHMENT_CONTENT') {
    return 'ATTACHMENT_NOTE';
  }
  return role;
}

function aiActionKindForWorkbenchNode(node: WorkbenchNode | null): NodeAiActionKind {
  switch (node?.nodeType) {
    case 'TITLE':
    case 'RECIPIENT':
    case 'BODY_SECTION':
    case 'ATTACHMENT':
      return 'local-operation';
    case 'SIGNATURE':
    case 'DATE':
      return 'quality-check';
    default:
      return 'none';
  }
}

function localOperationOptionsForWorkbenchNode(node: WorkbenchNode | null) {
  const allowedByType: Record<string, AiLocalOperationType[]> = {
    TITLE: ['FORMALIZE', 'COMPRESS', 'REWRITE'],
    RECIPIENT: ['FORMALIZE', 'REWRITE', 'SUPPLEMENT'],
    BODY_SECTION: ['FORMALIZE', 'COMPRESS', 'EXPAND', 'REWRITE', 'SUPPLEMENT'],
    ATTACHMENT: ['REWRITE', 'SUPPLEMENT'],
  };
  const allowed = node ? allowedByType[node.nodeType] ?? [] : [];
  return LOCAL_OPERATION_OPTIONS.filter((option) => allowed.includes(option.value));
}

function aiActionButtonLabelForWorkbenchNode(node: WorkbenchNode | null) {
  const actionKind = aiActionKindForWorkbenchNode(node);
  if (!node) {
    return '选择结构';
  }
  if (node.locked) {
    return '结构已锁定';
  }
  if (actionKind === 'quality-check') {
    return '运行质检确认';
  }
  if (actionKind === 'local-operation') {
    if (node.nodeType === 'BODY_SECTION' && !node.draftNodeId) {
      return '生成段落建议';
    }
    return `生成${workbenchNodeRoleLabel(node.nodeType)}建议`;
  }
  return '暂不支持节点 AI';
}

function aiPanelTitleForWorkbenchNode(node: WorkbenchNode | null) {
  if (!node) {
    return '全局 AI 操作';
  }
  return `${workbenchNodeRoleLabel(node.nodeType)}节点`;
}

function aiPanelKickerForWorkbenchNode(
  node: WorkbenchNode | null,
  selectedBodyNode: WorkbenchNode | null,
  bodySectionNodes: WorkbenchNode[],
) {
  if (!node) {
    return '未选择结构时，可使用提纲生成、基础质检和导出。';
  }
  if (selectedBodyNode) {
    return `已选择：${bodyNodeLabel(selectedBodyNode, bodySectionNodes.indexOf(selectedBodyNode))}`;
  }
  if (node.locked) {
    return '该节点来自模板或已锁定，暂不直接改写。';
  }
  if (aiActionKindForWorkbenchNode(node) === 'quality-check') {
    return '建议通过质检确认必填、格式和位置风险。';
  }
  return `已选择：${node.label || workbenchNodeRoleLabel(node.nodeType)}`;
}

function localOperationDialogTitle(nodeContext: AiNodeRequestContext | null) {
  return nodeContext ? '生成节点建议' : '生成段落建议';
}

function localOperationDialogDescription(
  selectedNode: WorkbenchNode | null,
  selectedBodyNode: WorkbenchNode | null,
  bodySectionNodes: WorkbenchNode[],
  nodeContext: AiNodeRequestContext | null,
) {
  if (nodeContext && selectedNode) {
    return `目标节点：${workbenchNodeRoleLabel(selectedNode.nodeType)} · ${selectedNode.label || selectedNode.content || '结构节点'}`;
  }
  if (selectedBodyNode) {
    return `目标结构：${bodyNodeLabel(selectedBodyNode, bodySectionNodes.indexOf(selectedBodyNode))}`;
  }
  return '请先在预览中选择正文结构。';
}

function draftNodeStatusLabel(status: string | undefined) {
  const labels: Record<string, string> = {
    EMPTY: '空',
    USER_FILLED: '已填写',
    AI_GENERATED: 'AI',
    USER_MODIFIED_AFTER_AI: '已改',
    NEEDS_REVIEW: '待审',
    QUALITY_WARNING: '警告',
    QUALITY_ERROR: '错误',
    EXPORT_BLOCKED: '阻断',
    FORMAT_OVERRIDDEN: '改格式',
    LOCKED: '锁定',
  };
  return status ? labels[status] ?? status : '兼容';
}

function statusBadgeTone(status: string | undefined) {
  if (status === 'QUALITY_ERROR' || status === 'EXPORT_BLOCKED') {
    return 'danger';
  }
  if (status === 'QUALITY_WARNING' || status === 'NEEDS_REVIEW' || status === 'EMPTY') {
    return 'warning';
  }
  if (status === 'USER_FILLED' || status === 'AI_GENERATED' || status === 'USER_MODIFIED_AFTER_AI') {
    return 'success';
  }
  return 'neutral';
}

function documentKindFromProfile(profile: TemplateProfile): TemplateDocumentKind | null {
  const analysis = profile.templateAnalysis;
  if (!analysis) {
    return null;
  }
  const documentKind = analysis.documentKind ?? (
    analysis.templateKind === 'STANDARD_PLACEHOLDER_TEMPLATE'
      ? 'PLACEHOLDER_TEMPLATE'
      : analysis.templateKind
  );
  return {
    documentKind,
    templateKind: analysis.templateKind,
    confidence: analysis.confidence,
    documentTypeCode: analysis.documentTypeCode,
    reasonCodes: analysis.reasonCodes ?? [],
    recommendedWorkflow: analysis.recommendedWorkflow ?? 'REVIEW_REQUIRED',
    blockingWarnings: analysis.blockingWarnings ?? [],
    message: analysis.message,
    source: analysis.source,
  };
}

function structurePreviewStyle(
  profile: TemplateProfile | null,
  overrides: TemplateStructureOverrideMap,
  structureType: string,
): CSSProperties {
  const structure = profile?.structures?.find((candidate) => candidate.structureType === structureType);
  if (!structure) {
    return {};
  }
  return formattingToCss({ ...structure.formatting, ...(overrides[structure.structureKey] ?? {}) });
}

function mergePreviewStyle(
  baseStyle: CSSProperties,
  formatting: Partial<TemplateStructureFormatting> | undefined,
): CSSProperties {
  if (!formatting) {
    return baseStyle;
  }
  return { ...baseStyle, ...formattingToCss(formatting) };
}

function templateStructuresByType(
  profile: TemplateProfile | null,
  overrides: TemplateStructureOverrideMap,
  structureTypes: string[],
) {
  const allowed = new Set(structureTypes);
  return (profile?.structures ?? [])
    .filter((structure) => allowed.has(structure.structureType))
    .map((structure) => ({
      structure,
      style: formattingToCss({ ...structure.formatting, ...(overrides[structure.structureKey] ?? {}) }),
    }));
}

function formattingToCss(formatting: Partial<TemplateStructureFormatting>): CSSProperties {
  const style: CSSProperties = {};
  if (formatting.fontFamily) {
    style.fontFamily = formatting.fontFamily;
  }
  if (formatting.fontSizeHalfPoints) {
    style.fontSize = `${formatting.fontSizeHalfPoints / 2}pt`;
  }
  if (typeof formatting.bold === 'boolean') {
    style.fontWeight = formatting.bold ? 700 : 400;
  }
  if (formatting.colorHex) {
    style.color = formatting.colorHex.startsWith('#') ? formatting.colorHex : `#${formatting.colorHex}`;
  }
  if (formatting.alignment) {
    style.textAlign = alignmentToCss(formatting.alignment);
  }
  if (formatting.indentationFirstLine) {
    style.textIndent = `${twipsToMillimeters(formatting.indentationFirstLine)}mm`;
  }
  if (formatting.spacingBetween) {
    style.lineHeight = String(formatting.spacingBetween / 100);
  }
  if (formatting.spacingBefore) {
    style.marginBlockStart = `${twipsToMillimeters(formatting.spacingBefore)}mm`;
  }
  if (formatting.spacingAfter) {
    style.marginBlockEnd = `${twipsToMillimeters(formatting.spacingAfter)}mm`;
  }
  return style;
}

function alignmentToCss(alignment: string): CSSProperties['textAlign'] {
  const normalized = alignment.toUpperCase();
  if (normalized === 'CENTER') {
    return 'center';
  }
  if (normalized === 'RIGHT') {
    return 'right';
  }
  if (normalized === 'BOTH') {
    return 'justify';
  }
  return 'left';
}

function stripTemplateBraces(text: string) {
  return text.replace(/\{\{\s*([^{}]+?)\s*}}/g, '$1');
}

function looksLikePlaceholderOnly(text: string) {
  const normalized = text.trim();
  return /^\{\{\s*[^{}]+?\s*}}$/.test(normalized);
}

function pointToHalfPoint(value: string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.round(parsed * 2) : null;
}

function millimeterToTwips(value: string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? Math.round(parsed / 0.0176389) : null;
}

function lineSpacingToProfileValue(value: string) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.round(parsed * 100) : null;
}

function locationLabel(locationType: string) {
  const labels: Record<string, string> = {
    PARAGRAPH: '正文段落',
    TABLE: '表格内',
    HEADER: '页眉',
    FOOTER: '页脚',
  };
  return labels[locationType] ?? locationType;
}

function structureSourceLabel(source: string) {
  const labels: Record<string, string> = {
    PLACEHOLDER: '来自占位符',
    STYLE: '来自 Word 样式',
    TEXT: '来自文本识别',
  };
  return labels[source] ?? source;
}

function dimensionSummary(formatting: TemplateStructureFormatting) {
  const items = [
    formatting.fontFamily,
    formatting.fontSizeHalfPoints ? `${formatting.fontSizeHalfPoints / 2}pt` : null,
    formatting.alignment ? alignmentLabel(formatting.alignment) : null,
    formatting.indentationFirstLine ? `首行 ${twipsToMillimeters(formatting.indentationFirstLine)}mm` : null,
    formatting.spacingBetween ? `行距 ${formatting.spacingBetween / 100}` : null,
    formatting.spacingAfter ? `段后 ${twipsToMillimeters(formatting.spacingAfter)}mm` : null,
    formatting.bold ? '加粗' : null,
  ].filter((item): item is string => Boolean(item));
  return items.length > 0 ? items : ['沿用模板默认'];
}

function alignmentLabel(alignment: string) {
  const labels: Record<string, string> = {
    LEFT: '左对齐',
    CENTER: '居中',
    RIGHT: '右对齐',
    BOTH: '两端对齐',
  };
  return labels[alignment] ?? alignment;
}

function twipsToMillimeters(twips: number) {
  return Math.round(twips * 0.0176389);
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

function downloadBlob(blob: Blob, fileName: string) {
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}

function deletedNodeStorageKey(draftId: number, templateVersionId: number | null) {
  return `${DELETED_NODE_STORAGE_PREFIX}.${draftId}.${templateVersionId ?? 'none'}`;
}

function readDeletedNodeIds(storageKey: string) {
  try {
    const raw = window.localStorage.getItem(storageKey);
    const parsed = raw ? JSON.parse(raw) : [];
    return new Set(Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []);
  } catch {
    return new Set<string>();
  }
}

function writeDeletedNodeIds(storageKey: string, nodeIds: Set<string>) {
  window.localStorage.setItem(storageKey, JSON.stringify([...nodeIds]));
}

function formatTimestamp(value: string) {
  const timestamp = new Date(value);
  if (Number.isNaN(timestamp.getTime())) {
    return '时间未知';
  }
  return timestamp.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function draftDetailToSummary(draft: DraftDetail): DraftSummary {
  return {
    id: draft.id,
    documentTypeCode: draft.documentTypeCode,
    title: draft.title,
    status: draft.status,
    templateVersionId: draft.templateVersionId,
    updatedAt: new Date().toISOString(),
  };
}
