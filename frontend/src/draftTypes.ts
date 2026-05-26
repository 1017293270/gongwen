export type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode: string | null;
  message: string | null;
};

export type DocumentType = {
  code: string;
  name: string;
  status: string;
  sortOrder: number;
};

export type DraftBlock = {
  id: number;
  blockType: string;
  content: string;
  sortOrder: number;
};

export type DraftDetail = {
  id: number;
  documentTypeCode: string;
  title: string;
  status: string;
  blocks: DraftBlock[];
};

export type DraftBlockUpdate = {
  blockType: string;
  content: string;
  sortOrder: number;
};

export type Material = {
  id: number;
  draftId: number;
  originalFileName: string;
  contentType: string | null;
  fileSizeBytes: number;
  fileExtension: string;
  status: 'READY' | 'FAILED';
  extractedTextLength: number;
  errorMessage: string | null;
};

export type AiOutlineSection = {
  heading: string;
  points: string[];
};

export type AiOutline = {
  traceId: string;
  titleSuggestion: string;
  sections: AiOutlineSection[];
  missingInformation: string[];
};

export type AiParagraph = {
  traceId: string;
  draft: DraftDetail;
  block: DraftBlock;
};

export type AiLocalOperationType = 'FORMALIZE' | 'COMPRESS' | 'EXPAND' | 'REWRITE' | 'SUPPLEMENT';

export type AiLocalOperation = {
  traceId: string;
  targetBlockId: number;
  operationType: AiLocalOperationType;
  suggestionText: string;
};

export type AiProviderSettings = {
  provider: 'mock' | 'deepseek';
  deepSeekEnabled: boolean;
  deepSeekBaseUrl: string;
  deepSeekModel: string;
  deepSeekApiKeyConfigured: boolean;
  maskedDeepSeekApiKey: string;
  deepSeekTimeoutSeconds: number;
};

export type AiProviderSettingsUpdate = {
  provider: 'mock' | 'deepseek';
  deepSeekEnabled: boolean;
  deepSeekBaseUrl: string;
  deepSeekModel: string;
  deepSeekApiKey?: string;
  clearDeepSeekApiKey?: boolean;
  deepSeekTimeoutSeconds: number;
};

export type AiProviderStatus = {
  provider: string;
  model: string;
  available: boolean;
  message: string;
  latencyMs: number;
};
