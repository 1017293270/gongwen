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

export type AuthUser = {
  id: number;
  username: string;
  displayName: string;
  departmentId: number | null;
  departmentName: string | null;
  roles: string[];
};

export type Department = {
  id: number;
  parentId: number | null;
  code: string;
  name: string;
  status: string;
  sortOrder: number;
  children: Department[];
};

export type UserAdmin = {
  id: number;
  username: string;
  displayName: string;
  departmentId: number | null;
  departmentName: string | null;
  status: string;
  roles: string[];
};

export type CreateDocumentTypeRequest = {
  code: string;
  name: string;
  sortOrder: number;
};

export type UpdateDocumentTypeRequest = {
  name: string;
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
  templateVersionId: number | null;
  blocks: DraftBlock[];
};

export type DraftSummary = {
  id: number;
  documentTypeCode: string;
  title: string;
  status: string;
  templateVersionId: number | null;
  updatedAt: string;
};

export type TemplateVersionSummary = {
  templateVersionId: number;
  templateId: number;
  templateName: string;
  versionNo: number;
  documentTypeCode: string | null;
  originalFileName: string;
};

export type TemplateSummary = {
  id: number;
  templateName: string;
  documentTypeCode: string | null;
  status: string;
};

export type TemplateUploadResult = {
  templateVersionId: number;
  versionNo: number;
  parseStatus: string;
  placeholderCount: number;
  styleCount: number;
  validationCount: number;
  validationCodes: string[];
};

export type TemplateProfile = {
  schemaVersion: number;
  structures: Array<{
    structureKey: string;
    structureType: string;
    label: string;
    textPreview: string;
    locationType: string;
    styleId: string | null;
    styleName: string | null;
    source: string;
    formatting: TemplateStructureFormatting;
  }>;
  styles: Array<{
    styleId: string;
    styleName: string;
    type: string;
    basedOn: string | null;
    fontFamily: string | null;
    fontSizeHalfPoints: number | null;
    bold: boolean | null;
    alignment: string | null;
    indentationFirstLine: number | null;
    spacingBetween: number | null;
    spacingBefore: number | null;
    spacingAfter: number | null;
  }>;
  sections: Array<{
    sectionIndex: number;
    hasHeader: boolean;
    hasFooter: boolean;
    pageWidthTwips: number | null;
    pageHeightTwips: number | null;
    marginTopTwips: number | null;
    marginRightTwips: number | null;
    marginBottomTwips: number | null;
    marginLeftTwips: number | null;
  }>;
  tables: Array<{
    tableIndex: number;
    rowCount: number;
    columnCount: number;
    placeholderCount: number;
  }>;
  media: Array<{
    mediaType: string | null;
    relationshipId: string | null;
    fileName: string | null;
  }>;
  templateAnalysis: {
    templateKind: string;
    confidence: number;
    documentTypeCode: string;
    inferredFields: string[];
    suggestedPlaceholders: Array<{
      field: string;
      reason: string;
    }>;
    message: string;
    source: string;
  } | null;
  placeholders: Array<{
    key: string;
    locationType: string;
    paragraphKey: string;
    splitAcrossRuns: boolean;
  }>;
  validationItems: Array<{
    severity: string;
    code: string;
    message: string;
  }>;
};

export type TemplateStructureFormatting = {
  fontFamily: string | null;
  fontSizeHalfPoints: number | null;
  bold: boolean | null;
  alignment: string | null;
  indentationFirstLine: number | null;
  spacingBetween: number | null;
  spacingBefore: number | null;
  spacingAfter: number | null;
  colorHex?: string | null;
};

export type TemplateStructureFormattingOverrides = Record<string, Partial<TemplateStructureFormatting>>;

export type WorkbenchNodeType =
  | 'TITLE'
  | 'RECIPIENT'
  | 'BODY_SECTION'
  | 'ATTACHMENT'
  | 'SIGNATURE'
  | 'DATE'
  | 'STATIC_TEMPLATE_TEXT'
  | 'HEADER'
  | 'FOOTER';

export type WorkbenchNodePart = 'whole' | 'heading' | 'content';

export type WorkbenchNodeSource = 'TEMPLATE' | 'DRAFT' | 'AI' | 'USER';

export type WorkbenchNode = {
  nodeId: string;
  nodeType: WorkbenchNodeType;
  templateStructureKey?: string;
  draftBlockId?: number;
  sortOrder: number;
  label: string;
  heading?: string;
  content: string;
  source: WorkbenchNodeSource;
  locked: boolean;
  formatting?: Partial<TemplateStructureFormatting>;
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

export type QualityCheckItem = {
  severity: 'ERROR' | 'WARNING' | 'INFO';
  category: string;
  code: string;
  message: string;
  targetBlockType: string | null;
  targetBlockId: number | null;
  suggestion: string;
};

export type QualityCheckResult = {
  id: string;
  draftId: number;
  status: 'PASS' | 'WARNING' | 'ERROR';
  exportBlocked: boolean;
  aiTraceId: string | null;
  checkedAt: string;
  items: QualityCheckItem[];
};

export type ExportRecordSummary = {
  id: number;
  draftId: number | null;
  draftTitle: string | null;
  documentTypeCode: string | null;
  templateId: number | null;
  templateVersionId: number | null;
  templateName: string;
  templateVersion: number;
  fileName: string;
  status: 'SUCCESS' | 'FAILED' | string;
  errorCode: string | null;
  errorMessage: string | null;
  canDownload: boolean;
  canRetry: boolean;
  createdAt: string;
};

export type ExportRecordDetail = ExportRecordSummary & {
  fileAvailable: boolean;
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
