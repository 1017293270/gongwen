import type {
  AiLocalOperation,
  AiLocalOperationType,
  AiNodeRequestContext,
  AiOutline,
  AiParagraph,
  AiParagraphCandidate,
  AiParagraphCandidateAcceptResponse,
  AiParagraphCandidateBatchAcceptResponse,
  AiOutlineSection,
  AiProviderSettings,
  AiProviderSettingsUpdate,
  AiProviderStatus,
  ApiResponse,
  AuthUser,
  CreateParagraphCandidateJobRequest,
  CreateDocumentTypeRequest,
  Department,
  DocumentRenderPreview,
  DocumentStructureProfile,
  DocumentType,
  DraftNode,
  DraftNodeFormatOverride,
  DraftNodeRoleOption,
  DraftBlockUpdate,
  DraftDetail,
  DraftSummary,
  ExportRecordDetail,
  ExportRecordSummary,
  Material,
  ParagraphCandidateJobResponse,
  QualityCheckResult,
  StructureMappingItem,
  StructureMappingProfile,
  TemplateProfile,
  TemplateDocumentKind,
  TemplateStructureFormatting,
  TemplateStructureFormattingOverrides,
  TemplateSummary,
  TemplateUploadResult,
  TemplateVersionSummary,
  UpdateDocumentTypeRequest,
  UserAdmin,
} from './draftTypes';

function apiBaseUrl() {
  return import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
}

let csrfToken: string | null = null;

export class ApiRequestError extends Error {
  errorCode: string | null;
  status: number;

  constructor(message: string, errorCode: string | null, status: number) {
    super(message);
    this.name = 'ApiRequestError';
    this.errorCode = errorCode;
    this.status = status;
  }
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase();
  const headers = {
    'Content-Type': 'application/json',
    ...await csrfHeader(path, method),
    ...init?.headers,
  };
  const response = await fetchApi(`${apiBaseUrl()}${path}`, {
    credentials: 'include',
    headers,
    ...init,
  });
  const payload = await readApiPayload<T>(response);
  if (!response.ok || !payload.success) {
    throw new ApiRequestError(payload.message ?? fallbackMessageForStatus(response.status), payload.errorCode ?? null, response.status);
  }
  return payload.data;
}

async function requestFormData<T>(path: string, formData: FormData): Promise<T> {
  const headers = await csrfHeader(path, 'POST');
  const response = await fetchApi(`${apiBaseUrl()}${path}`, {
    credentials: 'include',
    headers,
    method: 'POST',
    body: formData,
  });
  const payload = await readApiPayload<T>(response);
  if (!response.ok || !payload.success) {
    throw new ApiRequestError(payload.message ?? fallbackMessageForStatus(response.status), payload.errorCode ?? null, response.status);
  }
  return payload.data;
}

async function requestBlob(path: string, init?: RequestInit): Promise<{ blob: Blob; fileName: string }> {
  const method = (init?.method ?? 'GET').toUpperCase();
  const response = await fetchApi(`${apiBaseUrl()}${path}`, {
    credentials: 'include',
    headers: {
      ...await csrfHeader(path, method),
      ...init?.headers,
    },
    ...init,
  });
  if (!response.ok) {
    const payload = await readOptionalApiPayload<null>(response);
    throw new ApiRequestError(payload?.message ?? fallbackMessageForStatus(response.status), payload?.errorCode ?? null, response.status);
  }
  return {
    blob: await response.blob(),
    fileName: parseFileName(response.headers.get('content-disposition')) ?? '公文导出.docx',
  };
}

async function csrfHeader(path: string, method: string): Promise<Record<string, string>> {
  if (!['POST', 'PUT', 'DELETE', 'PATCH'].includes(method) || path === '/api/auth/csrf') {
    return {};
  }
  if (!csrfToken) {
    const response = await fetchApi(`${apiBaseUrl()}/api/auth/csrf`, {
      credentials: 'include',
    });
    const payload = await readApiPayload<{ token: string }>(response);
    if (!response.ok || !payload.success) {
      throw new ApiRequestError(payload.message ?? '安全令牌初始化失败，请刷新页面后重试', payload.errorCode ?? null, response.status);
    }
    csrfToken = payload.data.token;
  }
  return { 'X-XSRF-TOKEN': csrfToken };
}

async function fetchApi(input: RequestInfo | URL, init?: RequestInit) {
  try {
    return await fetch(input, init);
  } catch (error) {
    if (isAbortError(error)) {
      throw error;
    }
    throw new ApiRequestError('无法连接后端服务，请确认服务已启动后重试', 'NETWORK_ERROR', 0);
  }
}

async function readApiPayload<T>(response: Response): Promise<ApiResponse<T>> {
  const payload = await readOptionalApiPayload<T>(response);
  if (!payload) {
    throw new ApiRequestError('服务返回异常，请稍后重试', 'INVALID_SERVER_RESPONSE', response.status);
  }
  return payload;
}

async function readOptionalApiPayload<T>(response: Response): Promise<ApiResponse<T> | null> {
  try {
    return await response.json() as ApiResponse<T>;
  } catch {
    return null;
  }
}

function fallbackMessageForStatus(status: number) {
  if (status === 401) {
    return '登录状态已失效，请重新登录';
  }
  if (status === 403) {
    return '当前账号没有权限执行此操作';
  }
  if (status === 404) {
    return '请求的内容不存在或已被删除';
  }
  if (status >= 500) {
    return '系统暂时无法完成操作，请稍后重试';
  }
  return '请求失败，请检查后重试';
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError';
}

function parseFileName(contentDisposition: string | null) {
  if (!contentDisposition) {
    return null;
  }
  const encoded = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) {
    return decodeURIComponent(encoded);
  }
  return contentDisposition.match(/filename="?([^";]+)"?/i)?.[1] ?? null;
}

export function listDocumentTypes() {
  return requestJson<DocumentType[]>('/api/document-types');
}

export function getCurrentUser() {
  return requestJson<AuthUser>('/api/auth/me');
}

export function login(username: string, password: string) {
  return requestJson<AuthUser>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function logout() {
  return requestJson<void>('/api/auth/logout', {
    method: 'POST',
  }).finally(() => {
    csrfToken = null;
  });
}

export function listDepartments() {
  return requestJson<Department[]>('/api/departments');
}

export function createDepartment(request: { parentId: number | null; name: string; sortOrder: number }) {
  return requestJson<Department>('/api/departments', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function updateDepartment(
  id: number,
  request: { parentId: number | null; name: string; sortOrder: number },
) {
  return requestJson<Department>(`/api/departments/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  });
}

export function deleteDepartment(id: number) {
  return requestJson<void>(`/api/departments/${id}`, {
    method: 'DELETE',
  });
}

export function listUsers() {
  return requestJson<UserAdmin[]>('/api/users');
}

export function createUser(request: {
  username: string;
  displayName: string;
  password: string;
  departmentId: number | null;
  roles: string[];
}) {
  return requestJson<UserAdmin>('/api/users', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function updateUser(id: number, request: {
  displayName: string;
  departmentId: number | null;
  roles: string[];
  status: string;
}) {
  return requestJson<UserAdmin>(`/api/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  });
}

export function resetUserPassword(id: number, password: string) {
  return requestJson<void>(`/api/users/${id}/password`, {
    method: 'PUT',
    body: JSON.stringify({ password }),
  });
}

export function disableUser(id: number) {
  return requestJson<void>(`/api/users/${id}`, {
    method: 'DELETE',
  });
}

export function createDocumentType(request: CreateDocumentTypeRequest) {
  return requestJson<DocumentType>('/api/document-types', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function updateDocumentType(code: string, request: UpdateDocumentTypeRequest) {
  return requestJson<DocumentType>(`/api/document-types/${encodeURIComponent(code)}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  });
}

export function deleteDocumentType(code: string) {
  return requestJson<void>(`/api/document-types/${encodeURIComponent(code)}`, {
    method: 'DELETE',
  });
}

export function createDraft(documentTypeCode: string, title: string) {
  return requestJson<DraftDetail>('/api/drafts', {
    method: 'POST',
    body: JSON.stringify({ documentTypeCode, title }),
  });
}

export function getDraft(draftId: number) {
  return requestJson<DraftDetail>(`/api/drafts/${draftId}`);
}

export function deleteDraft(draftId: number) {
  return requestJson<void>(`/api/drafts/${draftId}`, {
    method: 'DELETE',
  });
}

export function updateDraftTitle(draftId: number, title: string) {
  return requestJson<DraftDetail>(`/api/drafts/${draftId}/title`, {
    method: 'PUT',
    body: JSON.stringify({ title }),
  });
}

export function listDrafts(documentTypeCode: string) {
  const query = `?documentTypeCode=${encodeURIComponent(documentTypeCode)}`;
  return requestJson<DraftSummary[]>(`/api/drafts${query}`);
}

export function saveDraftBlocks(draftId: number, blocks: DraftBlockUpdate[]) {
  return requestJson<DraftDetail>(`/api/drafts/${draftId}/blocks`, {
    method: 'PUT',
    body: JSON.stringify({ blocks }),
  });
}

export function initializeDraftNodes(draftId: number) {
  return requestJson<DraftNode[]>(`/api/drafts/${draftId}/nodes/initialize`, {
    method: 'POST',
    body: JSON.stringify({}),
  });
}

export function reinitializeDraftNodes(draftId: number, preserveUserEditedNodes = true) {
  return requestJson<DraftNode[]>(`/api/drafts/${draftId}/nodes/reinitialize`, {
    method: 'POST',
    body: JSON.stringify({
      mode: 'FROM_SOURCE_DOCUMENT',
      preserveUserEditedNodes,
    }),
  });
}

export function listDraftNodes(draftId: number) {
  return requestJson<DraftNode[]>(`/api/drafts/${draftId}/nodes`);
}

export function listInsertableDraftNodeRoles(draftId: number) {
  return requestJson<DraftNodeRoleOption[]>(`/api/drafts/${draftId}/nodes/insertable-roles`);
}

export function insertDraftNode(
  draftId: number,
  request: { role: string; anchorNodeId: number | null; position: string },
) {
  return requestJson<DraftNode[]>(`/api/drafts/${draftId}/nodes`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function saveDraftNode(draftId: number, nodeId: number, content: string, status: string) {
  return requestJson<DraftNode>(`/api/drafts/${draftId}/nodes/${nodeId}`, {
    method: 'PUT',
    body: JSON.stringify({ content, status }),
  });
}

export function saveDraftNodeFormatOverride(
  draftId: number,
  nodeId: number,
  formatOverride: DraftNodeFormatOverride,
) {
  return requestJson<DraftNode>(`/api/drafts/${draftId}/nodes/${nodeId}/format-override`, {
    method: 'PUT',
    body: JSON.stringify(formatOverride),
  });
}

export function restoreDraftNodeFormatOverride(draftId: number, nodeId: number) {
  return requestJson<DraftNode>(`/api/drafts/${draftId}/nodes/${nodeId}/format-override`, {
    method: 'DELETE',
  });
}

export function updateDraftTemplateVersion(draftId: number, templateVersionId: number | null) {
  return requestJson<DraftDetail>(`/api/drafts/${draftId}/template-version`, {
    method: 'PUT',
    body: JSON.stringify({ templateVersionId }),
  });
}

export function listTemplateVersions(documentTypeCode?: string) {
  const query = documentTypeCode ? `?documentTypeCode=${encodeURIComponent(documentTypeCode)}` : '';
  return requestJson<TemplateVersionSummary[]>(`/api/templates/versions${query}`);
}

export function listTemplates(documentTypeCode?: string) {
  const query = documentTypeCode ? `?documentTypeCode=${encodeURIComponent(documentTypeCode)}` : '';
  return requestJson<TemplateSummary[]>(`/api/templates${query}`);
}

export function createTemplate(templateName: string, documentTypeCode: string) {
  return requestJson<TemplateSummary>('/api/templates', {
    method: 'POST',
    body: JSON.stringify({ templateName, documentTypeCode }),
  });
}

export function deleteTemplate(templateId: number) {
  return requestJson<void>(`/api/templates/${templateId}`, {
    method: 'DELETE',
  });
}

export function uploadTemplateVersion(templateId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return requestFormData<TemplateUploadResult>(`/api/templates/${templateId}/versions`, formData);
}

export function getTemplateProfile(templateVersionId: number) {
  return requestJson<TemplateProfile>(`/api/templates/versions/${templateVersionId}/profile`);
}

export function getDocumentStructureProfile(templateVersionId: number) {
  return requestJson<DocumentStructureProfile>(`/api/templates/versions/${templateVersionId}/structure-profile`);
}

export function getTemplateDocumentKind(templateVersionId: number) {
  return requestJson<TemplateDocumentKind>(`/api/templates/versions/${templateVersionId}/document-kind`);
}

export function getRenderPreview(templateVersionId: number) {
  return requestJson<DocumentRenderPreview>(`/api/templates/versions/${templateVersionId}/render-preview`);
}

export function getDraftRenderPreview(draftId: number) {
  return requestJson<DocumentRenderPreview>(`/api/drafts/${draftId}/render-preview`);
}

export function requestRenderPreview(templateVersionId: number) {
  return requestJson<DocumentRenderPreview>(`/api/templates/versions/${templateVersionId}/render-preview`, {
    method: 'POST',
    body: JSON.stringify({}),
  });
}

export function requestDraftRenderPreview(draftId: number) {
  return requestJson<DocumentRenderPreview>(`/api/drafts/${draftId}/render-preview`, {
    method: 'POST',
    body: JSON.stringify({}),
  });
}

export function getRenderPreviewPageUrl(previewId: number, pageNumber: number) {
  return `${apiBaseUrl()}/api/render-previews/${previewId}/pages/${pageNumber}`;
}

export function getStructureMapping(templateVersionId: number) {
  return requestJson<StructureMappingProfile>(`/api/templates/versions/${templateVersionId}/structure-mapping`);
}

export function saveStructureMappingDraft(
  templateVersionId: number,
  baseMappingProfileId: number | null,
  items: StructureMappingItem[],
) {
  return requestJson<StructureMappingProfile>(`/api/templates/versions/${templateVersionId}/structure-mapping/draft`, {
    method: 'PUT',
    body: JSON.stringify({ baseMappingProfileId, items }),
  });
}

export function publishStructureMapping(templateVersionId: number, adminOverride = false) {
  return requestJson<StructureMappingProfile>(`/api/templates/versions/${templateVersionId}/structure-mapping/publish`, {
    method: 'POST',
    body: JSON.stringify({ adminOverride }),
  });
}

export function getTemplateStructureFormatting(templateVersionId: number) {
  return requestJson<TemplateStructureFormattingOverrides>(`/api/templates/versions/${templateVersionId}/structure-formatting`);
}

export function updateTemplateStructureFormatting(
  templateVersionId: number,
  structureKey: string,
  formatting: Partial<TemplateStructureFormatting>,
) {
  return requestJson<TemplateStructureFormatting>(
    `/api/templates/versions/${templateVersionId}/structures/${encodeURIComponent(structureKey)}/formatting`,
    {
      method: 'PUT',
      body: JSON.stringify(formatting),
    },
  );
}

export function listDraftMaterials(draftId: number) {
  return requestJson<Material[]>(`/api/drafts/${draftId}/materials`);
}

export function uploadDraftMaterial(draftId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return requestFormData<Material>(`/api/drafts/${draftId}/materials`, formData);
}

export function generateDraftOutline(draftId: number, instruction: string, signal?: AbortSignal) {
  return requestJson<AiOutline>(`/api/drafts/${draftId}/ai/outline`, {
    method: 'POST',
    signal,
    body: JSON.stringify({ instruction }),
  });
}

export function generateDraftParagraph(
  draftId: number,
  section: AiOutlineSection,
  instruction: string,
  sortOrder: number,
  nodeContextOrSignal?: AiNodeRequestContext | AbortSignal,
  maybeSignal?: AbortSignal,
) {
  const signal = isAbortSignal(nodeContextOrSignal) ? nodeContextOrSignal : maybeSignal;
  const nodeContext = isAbortSignal(nodeContextOrSignal) ? undefined : nodeContextOrSignal;
  return requestJson<AiParagraph>(`/api/drafts/${draftId}/ai/paragraph`, {
    method: 'POST',
    signal,
    body: JSON.stringify({
      heading: section.heading,
      points: section.points,
      instruction,
      sortOrder,
      ...nodeContext,
    }),
  });
}

export function generateLocalOperation(
  draftId: number,
  target: number | (AiNodeRequestContext & { targetBlockId?: number }),
  operationType: AiLocalOperationType,
  instruction: string,
  signal?: AbortSignal,
) {
  const targetPayload = typeof target === 'number' ? { targetBlockId: target } : target;
  return requestJson<AiLocalOperation>(`/api/drafts/${draftId}/ai/local-operation`, {
    method: 'POST',
    signal,
    body: JSON.stringify({
      ...targetPayload,
      operationType,
      instruction,
    }),
  });
}

export function listParagraphCandidates(draftId: number) {
  return requestJson<AiParagraphCandidate[]>(`/api/drafts/${draftId}/ai/paragraph-candidates`);
}

export function createParagraphCandidateJob(
  draftId: number,
  request: CreateParagraphCandidateJobRequest = { sections: [] },
) {
  return requestJson<ParagraphCandidateJobResponse>(`/api/drafts/${draftId}/ai/paragraph-candidates/jobs`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function openParagraphCandidateJobEvents(draftId: number, jobId: string) {
  return new EventSource(
    `${apiBaseUrl()}/api/drafts/${draftId}/ai/paragraph-candidates/jobs/${encodeURIComponent(jobId)}/events`,
    { withCredentials: true },
  );
}

export function cancelParagraphCandidateJob(draftId: number, jobId: string) {
  return requestJson<ParagraphCandidateJobResponse>(
    `/api/drafts/${draftId}/ai/paragraph-candidates/jobs/${encodeURIComponent(jobId)}/cancel`,
    {
      method: 'POST',
      body: JSON.stringify({}),
    },
  );
}

export function retryParagraphCandidate(draftId: number, candidateId: number) {
  return requestJson<AiParagraphCandidate>(`/api/drafts/${draftId}/ai/paragraph-candidates/${candidateId}/retry`, {
    method: 'POST',
    body: JSON.stringify({}),
  });
}

export function updateParagraphCandidateText(
  draftId: number,
  candidateId: number,
  candidateText: string,
) {
  return requestJson<AiParagraphCandidate>(`/api/drafts/${draftId}/ai/paragraph-candidates/${candidateId}`, {
    method: 'PUT',
    body: JSON.stringify({ candidateText }),
  });
}

export function discardParagraphCandidate(draftId: number, candidateId: number) {
  return requestJson<AiParagraphCandidate>(`/api/drafts/${draftId}/ai/paragraph-candidates/${candidateId}`, {
    method: 'DELETE',
  });
}

export function acceptParagraphCandidate(draftId: number, candidateId: number) {
  return requestJson<AiParagraphCandidateAcceptResponse>(
    `/api/drafts/${draftId}/ai/paragraph-candidates/${candidateId}/accept`,
    {
      method: 'POST',
      body: JSON.stringify({}),
    },
  );
}

export function acceptParagraphCandidateBatch(draftId: number, candidateIds: number[] = []) {
  return requestJson<AiParagraphCandidateBatchAcceptResponse>(
    `/api/drafts/${draftId}/ai/paragraph-candidates/accept-batch`,
    {
      method: 'POST',
      body: JSON.stringify({ candidateIds }),
    },
  );
}

function isAbortSignal(value: unknown): value is AbortSignal {
  return typeof value === 'object' && value !== null && 'aborted' in value && 'addEventListener' in value;
}

export function runQualityCheck(draftId: number, signal?: AbortSignal) {
  return requestJson<QualityCheckResult>(`/api/drafts/${draftId}/quality-check`, {
    method: 'POST',
    signal,
    body: JSON.stringify({}),
  });
}

export function getLatestQualityCheck(draftId: number) {
  return requestJson<QualityCheckResult>(`/api/drafts/${draftId}/quality-check/latest`);
}

export function exportDraftWord(draftId: number) {
  return requestBlob(`/api/exports/drafts/${draftId}/word`, {
    method: 'POST',
  });
}

export function listExportRecords() {
  return requestJson<ExportRecordSummary[]>('/api/exports');
}

export function getExportRecordDetail(recordId: number) {
  return requestJson<ExportRecordDetail>(`/api/exports/${recordId}`);
}

export function downloadExportRecord(recordId: number) {
  return requestBlob(`/api/exports/${recordId}/download`);
}

export function retryExportRecord(recordId: number) {
  return requestBlob(`/api/exports/${recordId}/retry`, {
    method: 'POST',
  });
}

export function getAiProviderSettings() {
  return requestJson<AiProviderSettings>('/api/ai/settings');
}

export function updateAiProviderSettings(settings: AiProviderSettingsUpdate) {
  return requestJson<AiProviderSettings>('/api/ai/settings', {
    method: 'PUT',
    body: JSON.stringify(settings),
  });
}

export function testAiProviderConnection() {
  return requestJson<AiProviderStatus>('/api/ai/settings/test', {
    method: 'POST',
    body: JSON.stringify({}),
  });
}
