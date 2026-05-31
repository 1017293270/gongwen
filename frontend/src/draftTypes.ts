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

export type DraftNodeFormatOverride = {
  eastAsiaFont: string | null;
  latinFont: string | null;
  fontSizePt: number | null;
  bold: boolean | null;
  alignment: string | null;
  firstLineIndentTwip: number | null;
  lineSpacingRule: string | null;
  lineSpacingTwip: number | null;
  spacingBeforeTwip: number | null;
  spacingAfterTwip: number | null;
};

export type DraftNode = {
  id: number;
  draftId: number;
  structureMappingProfileId: number | null;
  templateNodeKey: string;
  parentTemplateNodeKey: string | null;
  nodeType: string;
  role: string;
  slotKey: string;
  title: string;
  content: string;
  sortOrder: number;
  status: string;
  formatOverride: DraftNodeFormatOverride;
  createdAt: string;
  updatedAt: string;
};

export type DraftDetail = {
  id: number;
  documentTypeCode: string;
  title: string;
  status: string;
  templateVersionId: number | null;
  blocks: DraftBlock[];
  nodes?: DraftNode[];
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

export type TemplateLineSpacing = {
  mode: string;
  valueTwips: number | null;
  multipleHundred: number | null;
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
    eastAsiaFontFamily?: string | null;
    latinFontFamily?: string | null;
    fontSizeHalfPoints: number | null;
    bold: boolean | null;
    alignment: string | null;
    indentationFirstLine: number | null;
    spacingBetween: number | null;
    lineSpacing?: TemplateLineSpacing | null;
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
    documentKind?: string;
    reasonCodes?: string[];
    recommendedWorkflow?: string;
    blockingWarnings?: string[];
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
  eastAsiaFontFamily?: string | null;
  latinFontFamily?: string | null;
  fontSizeHalfPoints: number | null;
  bold: boolean | null;
  alignment: string | null;
  indentationFirstLine: number | null;
  spacingBetween: number | null;
  lineSpacing?: TemplateLineSpacing | null;
  spacingBefore: number | null;
  spacingAfter: number | null;
  colorHex?: string | null;
};

export type TemplateStructureFormattingOverrides = Record<string, Partial<TemplateStructureFormatting>>;

export type DocumentStructureNode = {
  nodeKey: string;
  parentKey: string | null;
  nodeType: string;
  roleSuggestion: string;
  text: string;
  textPreview: string;
  orderIndex: number;
  path: string;
  formatting: TemplateStructureFormatting | null;
  riskCodes: string[];
  location?: {
    part: string;
    paragraphIndex: number | null;
    tableIndex: number | null;
    rowIndex: number | null;
    cellIndex: number | null;
    cellParagraphIndex: number | null;
  } | null;
  runs?: Array<{
    runIndex: number;
    text: string;
    eastAsiaFontFamily: string | null;
    latinFontFamily: string | null;
    fontSizeHalfPoints: number | null;
    bold: boolean | null;
    italic: boolean | null;
    colorHex: string | null;
  }>;
  numbering?: {
    numId: string | null;
    ilvl: string | null;
    styleId: string | null;
  } | null;
};

export type DocumentStructureProfile = {
  schemaVersion: number;
  sourceFileHash: string;
  extractorVersion: string;
  nodes: DocumentStructureNode[];
  styles: TemplateProfile['styles'];
  sections: TemplateProfile['sections'];
  risks: TemplateProfile['validationItems'];
  createdAt: string;
};

export type TemplateDocumentKind = {
  documentKind: string;
  templateKind: string;
  confidence: number;
  documentTypeCode: string;
  reasonCodes: string[];
  recommendedWorkflow: string;
  blockingWarnings: string[];
  message: string;
  source: string;
};

export type DocumentRenderPreviewStatus = 'PENDING' | 'RENDERING' | 'READY' | 'FAILED' | 'UNSUPPORTED';

export type DocumentRenderPreviewPage = {
  pageNumber: number;
  fileName: string;
  contentType: string;
  widthPixels: number;
  heightPixels: number;
  dpi: number;
};

export type DocumentRenderPreview = {
  id: number | null;
  templateVersionId: number;
  sourceFileHash: string;
  renderer: string;
  rendererVersion: string | null;
  status: DocumentRenderPreviewStatus;
  pageCount: number;
  storagePath: string | null;
  manifest: {
    schemaVersion: number;
    pdfFileName: string | null;
    pages: DocumentRenderPreviewPage[];
  };
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type StructureMappingItem = {
  nodeKey: string;
  role: string;
  slotKey: string;
  status: 'SUGGESTED' | 'CONFIRMED' | 'IGNORED' | 'NEEDS_REVIEW' | string;
  source: 'RULE' | 'AI' | 'USER' | 'IMPORT' | 'SYSTEM' | string;
  confidence: number;
  notes: string;
  sortOrder: number;
};

export type StructureMappingValidationItem = {
  severity: 'INFO' | 'WARNING' | 'BLOCKING' | string;
  code: string;
  message: string;
  nodeKey: string | null;
  role: string | null;
};

export type StructureMappingProfile = {
  mappingProfileId: number | null;
  templateVersionId: number;
  versionNo: number;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | string;
  items: StructureMappingItem[];
  validationItems: StructureMappingValidationItem[];
  confirmedCount: number;
  needsReviewCount: number;
  publishedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

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
  factNodeType?: string;
  templateStructureKey?: string;
  draftBlockId?: number;
  draftNodeId?: number;
  headingDraftNodeId?: number;
  templateNodeKey?: string;
  role?: string;
  sortOrder: number;
  label: string;
  heading?: string;
  content: string;
  status?: string;
  source: WorkbenchNodeSource;
  locked: boolean;
  editable?: boolean;
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

export type AiNodeSuggestion = {
  nodeId: number | null;
  nodeRole: string;
  nodeTitle: string;
  action: string;
  suggestedText: string;
};

export type AiNodeRequestContext = {
  nodeId?: number;
  nodeRole?: string;
  nodeTitle?: string;
  nodeContext?: string;
};

export type AiOutline = {
  traceId: string;
  titleSuggestion: string;
  sections: AiOutlineSection[];
  missingInformation: string[];
  nodeSuggestions?: AiNodeSuggestion[];
};

export type AiParagraph = {
  traceId: string;
  draft: DraftDetail;
  block: DraftBlock;
  node?: DraftNode | null;
};

export type AiLocalOperationType = 'FORMALIZE' | 'COMPRESS' | 'EXPAND' | 'REWRITE' | 'SUPPLEMENT';

export type AiLocalOperation = {
  traceId: string;
  targetBlockId: number | null;
  targetNodeId?: number | null;
  targetNodeRole?: string;
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
