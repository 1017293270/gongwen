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
