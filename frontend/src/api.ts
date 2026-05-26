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
  DocumentType,
  DraftBlockUpdate,
  DraftDetail,
  Material,
  QualityCheckResult,
} from './draftTypes';

function apiBaseUrl() {
  return import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    ...init,
  });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message ?? '请求失败');
  }
  return payload.data;
}

async function requestFormData<T>(path: string, formData: FormData): Promise<T> {
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method: 'POST',
    body: formData,
  });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message ?? '请求失败');
  }
  return payload.data;
}

export function listDocumentTypes() {
  return requestJson<DocumentType[]>('/api/document-types');
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

export function saveDraftBlocks(draftId: number, blocks: DraftBlockUpdate[]) {
  return requestJson<DraftDetail>(`/api/drafts/${draftId}/blocks`, {
    method: 'PUT',
    body: JSON.stringify({ blocks }),
  });
}

export function listDraftMaterials(draftId: number) {
  return requestJson<Material[]>(`/api/drafts/${draftId}/materials`);
}

export function uploadDraftMaterial(draftId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return requestFormData<Material>(`/api/drafts/${draftId}/materials`, formData);
}

export function generateDraftOutline(draftId: number, instruction: string) {
  return requestJson<AiOutline>(`/api/drafts/${draftId}/ai/outline`, {
    method: 'POST',
    body: JSON.stringify({ instruction }),
  });
}

export function generateDraftParagraph(
  draftId: number,
  section: AiOutlineSection,
  instruction: string,
  sortOrder: number,
) {
  return requestJson<AiParagraph>(`/api/drafts/${draftId}/ai/paragraph`, {
    method: 'POST',
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
) {
  return requestJson<AiLocalOperation>(`/api/drafts/${draftId}/ai/local-operation`, {
    method: 'POST',
    body: JSON.stringify({
      targetBlockId,
      operationType,
      instruction,
    }),
  });
}

export function runQualityCheck(draftId: number) {
  return requestJson<QualityCheckResult>(`/api/drafts/${draftId}/quality-check`, {
    method: 'POST',
    body: JSON.stringify({}),
  });
}

export function getLatestQualityCheck(draftId: number) {
  return requestJson<QualityCheckResult>(`/api/drafts/${draftId}/quality-check/latest`);
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
