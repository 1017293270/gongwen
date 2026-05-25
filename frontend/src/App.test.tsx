import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

describe('App', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: createStorageMock(),
    });
    window.localStorage.clear();
  });

  afterEach(() => {
    cleanup();
    window.localStorage.clear();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it('loads a real draft and saves edited blocks', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        { code: 'REQUEST', name: '请示', status: 'ACTIVE', sortOrder: 2 },
        { code: 'REPORT', name: '报告', status: 'ACTIVE', sortOrder: 3 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('后端草稿标题')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('更新后的标题')));
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    expect(await screen.findByDisplayValue('后端草稿标题')).toBeInTheDocument();
    expect(screen.getByLabelText('起草信息')).toBeInTheDocument();
    expect(screen.getByLabelText('公文预览')).toBeInTheDocument();
    expect(screen.getByLabelText('AI 建议和质检')).toBeInTheDocument();

    const title = screen.getByLabelText('标题');
    await userEvent.clear(title);
    await userEvent.type(title, '更新后的标题');
    await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/api/drafts/1/blocks', expect.objectContaining({
      method: 'PUT',
    }));
    expect(await screen.findByText('草稿已保存')).toBeInTheDocument();
    expect(window.localStorage.getItem('gongwen.currentDraftId')).toBe('1');
  });

  it('reloads the stored draft instead of creating a new one', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '42');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        { code: 'REQUEST', name: '请示', status: 'ACTIVE', sortOrder: 2 },
        { code: 'REPORT', name: '报告', status: 'ACTIVE', sortOrder: 3 },
      ]))
      .mockResolvedValueOnce(jsonResponse({ ...sampleDraft('刷新后的草稿标题'), id: 42 }))
      .mockResolvedValueOnce(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    expect(await screen.findByDisplayValue('刷新后的草稿标题')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/42', expect.objectContaining({
      headers: expect.any(Object),
    }));
    expect(fetchMock).not.toHaveBeenCalledWith('http://api.test/api/drafts', expect.objectContaining({
      method: 'POST',
    }));
  });

  it('uploads a material and shows a unified success toast', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('材料测试草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(sampleMaterial('meeting.docx', 'READY')))
      .mockResolvedValueOnce(jsonResponse([sampleMaterial('meeting.docx', 'READY')]));
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    await screen.findByDisplayValue('材料测试草稿');
    const input = screen.getByLabelText('上传材料文件');
    await userEvent.upload(input, new File(['会议纪要'], 'meeting.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/1/materials', expect.objectContaining({
      method: 'POST',
      body: expect.any(FormData),
    }));
    expect(await screen.findByText('材料上传成功')).toBeInTheDocument();
    expect(await within(screen.getByLabelText('材料列表')).findByText('meeting.docx')).toBeInTheDocument();
  });

  it('shows a unified error toast when material upload fails', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('失败测试草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(errorResponse('MATERIAL_TYPE_NOT_ALLOWED', '仅支持上传 Word 或 PDF 材料'));
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    await screen.findByDisplayValue('失败测试草稿');
    const input = screen.getByLabelText('上传材料文件');
    await userEvent.upload(input, new File(['plain text'], 'broken.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    }));

    expect(await screen.findByText('仅支持上传 Word 或 PDF 材料')).toBeInTheDocument();
  });
});

function jsonResponse<T>(data: T) {
  return {
    ok: true,
    json: async () => ({ success: true, data, errorCode: null, message: null }),
  };
}

function errorResponse(errorCode: string, message: string) {
  return {
    ok: false,
    json: async () => ({ success: false, data: null, errorCode, message }),
  };
}

function createStorageMock() {
  const store = new Map<string, string>();
  return {
    clear: () => store.clear(),
    getItem: (key: string) => store.get(key) ?? null,
    removeItem: (key: string) => store.delete(key),
    setItem: (key: string, value: string) => store.set(key, value),
  };
}

function sampleDraft(title: string) {
  return {
    id: 1,
    documentTypeCode: 'NOTICE',
    title,
    status: 'DRAFT',
    blocks: [
      { id: 1, blockType: 'TITLE', content: title, sortOrder: 10 },
      { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
      { id: 3, blockType: 'BODY_PARAGRAPH', content: '正文内容', sortOrder: 30 },
      { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
      { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
      { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
    ],
  };
}

function sampleMaterial(originalFileName: string, status: string) {
  return {
    id: 1,
    draftId: 1,
    originalFileName,
    contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    fileSizeBytes: 12,
    fileExtension: 'docx',
    status,
    extractedTextLength: status === 'READY' ? 4 : 0,
    errorMessage: null,
  };
}
