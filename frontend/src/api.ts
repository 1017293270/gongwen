import type {
  AiLocalOperation,
  AiLocalOperationType,
  AiOutline,
  AiParagraph,
  AiOutlineSection,
  AiProviderSettings,
  AiProviderSettingsUpdate,
  AiProviderStatus,
  ApiResponse,
  AuthUser,
  CreateDocumentTypeRequest,
  Department,
  DocumentType,
  DraftBlockUpdate,
  DraftDetail,
  DraftSummary,
  Material,
  QualityCheckResult,
  TemplateProfile,
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

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase();
  const headers = {
    'Content-Type': 'application/json',
    ...await csrfHeader(path, method),
    ...init?.headers,
  };
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    credentials: 'include',
    headers,
    ...init,
  });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message ?? '请求失败');
  }
  return payload.data;
}

async function requestFormData<T>(path: string, formData: FormData): Promise<T> {
  const headers = await csrfHeader(path, 'POST');
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    credentials: 'include',
    headers,
    method: 'POST',
    body: formData,
  });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message ?? '请求失败');
  }
  return payload.data;
}

async function requestBlob(path: string, init?: RequestInit): Promise<{ blob: Blob; fileName: string }> {
  const method = (init?.method ?? 'GET').toUpperCase();
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    credentials: 'include',
    headers: {
      ...await csrfHeader(path, method),
      ...init?.headers,
    },
    ...init,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as ApiResponse<null> | null;
    throw new Error(payload?.message ?? '请求失败');
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
    const response = await fetch(`${apiBaseUrl()}/api/auth/csrf`, {
      credentials: 'include',
    });
    const payload = (await response.json()) as ApiResponse<{ token: string }>;
    if (!response.ok || !payload.success) {
      throw new Error(payload.message ?? 'CSRF 初始化失败');
    }
    csrfToken = payload.data.token;
  }
  return { 'X-XSRF-TOKEN': csrfToken };
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
  signal?: AbortSignal,
) {
  return requestJson<AiParagraph>(`/api/drafts/${draftId}/ai/paragraph`, {
    method: 'POST',
    signal,
    body: JSON.stringify({
      heading: section.heading,
      points: section.points,
      instruction,
      sortOrder,
    }),
  });
}

export function generateLocalOperation(
  draftId: number,
  targetBlockId: number,
  operationType: AiLocalOperationType,
  instruction: string,
  signal?: AbortSignal,
) {
  return requestJson<AiLocalOperation>(`/api/drafts/${draftId}/ai/local-operation`, {
    method: 'POST',
    signal,
    body: JSON.stringify({
      targetBlockId,
      operationType,
      instruction,
    }),
  });
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
