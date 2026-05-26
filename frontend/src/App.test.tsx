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

    await openWorkbench();
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

    await openWorkbench();
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

    await openWorkbench();
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

    await openWorkbench();
    await screen.findByDisplayValue('失败测试草稿');
    const input = screen.getByLabelText('上传材料文件');
    await userEvent.upload(input, new File(['plain text'], 'broken.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    }));

    expect(await screen.findByText('仅支持上传 Word 或 PDF 材料')).toBeInTheDocument();
  });

  it('generates an outline from the AI panel', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('提纲测试草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(sampleOutline()));
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    await openWorkbench();
    await screen.findByDisplayValue('提纲测试草稿');
    await userEvent.type(screen.getByLabelText('提纲补充要求'), '突出执行要求');
    await userEvent.click(within(screen.getByLabelText('AI 建议和质检')).getByRole('button', { name: '生成提纲' }));

    expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/api/drafts/1/ai/outline', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ instruction: '突出执行要求' }),
    }));
    const outlineResult = await screen.findByLabelText('AI 提纲结果');
    expect(within(outlineResult).getByText('AI 提纲标题')).toBeInTheDocument();
    expect(within(outlineResult).getByText('一、主要事项')).toBeInTheDocument();
    expect(within(outlineResult).getByText('缺失信息：会议时间')).toBeInTheDocument();
  });

  it('generates body paragraph from an outline section and refreshes the preview', async () => {
    const generatedDraft = {
      ...sampleDraft('正文生成草稿'),
      blocks: [
        { id: 1, blockType: 'TITLE', content: '正文生成草稿', sortOrder: 10 },
        { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
        { id: 3, blockType: 'BODY_PARAGRAPH', content: '一、主要事项：说明安排；明确分工。', sortOrder: 30 },
        { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
        { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
        { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
      ],
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('正文生成草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(sampleOutline()))
      .mockResolvedValueOnce(jsonResponse({
        traceId: '22222222-2222-2222-2222-222222222222',
        draft: generatedDraft,
        block: generatedDraft.blocks[2],
      }));
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    await openWorkbench();
    await screen.findByDisplayValue('正文生成草稿');
    await userEvent.type(screen.getByLabelText('提纲补充要求'), '突出执行要求');
    await userEvent.click(within(screen.getByLabelText('AI 建议和质检')).getByRole('button', { name: '生成提纲' }));
    await userEvent.click(await within(screen.getByLabelText('AI 提纲结果')).findByRole('button', { name: '生成正文：一、主要事项' }));

    expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/api/drafts/1/ai/paragraph', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        heading: '一、主要事项',
        points: ['说明安排', '明确分工'],
        instruction: '突出执行要求',
        sortOrder: 30,
      }),
    }));
    expect(await screen.findByText('正文已生成')).toBeInTheDocument();
    expect(within(screen.getByLabelText('公文预览')).getByText('一、主要事项：说明安排；明确分工。')).toBeInTheDocument();
  });

  it('shows retry state when outline generation fails', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('提纲失败草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(errorResponse('AI_MODEL_UNAVAILABLE', 'AI 服务暂不可用，请稍后重试'));
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    await openWorkbench();
    await screen.findByDisplayValue('提纲失败草稿');
    await userEvent.click(within(screen.getByLabelText('AI 建议和质检')).getByRole('button', { name: '生成提纲' }));

    expect(await within(screen.getByLabelText('AI 建议和质检')).findByRole('button', { name: '重试生成提纲' })).toBeInTheDocument();
    expect(screen.getAllByText('AI 服务暂不可用，请稍后重试').length).toBeGreaterThan(0);
  });

  it('opens on an overview page and navigates through the sidebar', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('总览草稿')))
      .mockResolvedValueOnce(jsonResponse([sampleMaterial('brief.pdf', 'READY')]));
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    expect(await screen.findByRole('heading', { name: '总览' })).toBeInTheDocument();
    expect(screen.getByLabelText('主导航')).toBeInTheDocument();
    expect(screen.getByText('最近草稿')).toBeInTheDocument();
    expect(screen.getByText('总览草稿')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '工作台' }));

    expect(await screen.findByDisplayValue('总览草稿')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '工作台' })).toHaveAttribute('aria-current', 'page');
  });

  it('configures DeepSeek from system settings', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('AI 配置草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(sampleAiSettings()))
      .mockResolvedValueOnce(jsonResponse({
        ...sampleAiSettings(),
        provider: 'deepseek',
        deepSeekEnabled: true,
        deepSeekApiKeyConfigured: true,
        maskedDeepSeekApiKey: 'sk-1...7890',
      }))
      .mockResolvedValueOnce(jsonResponse({
        ...sampleAiSettings(),
        provider: 'deepseek',
        deepSeekEnabled: true,
        deepSeekApiKeyConfigured: true,
        maskedDeepSeekApiKey: 'sk-1...7890',
      }))
      .mockResolvedValueOnce(jsonResponse({
        provider: 'deepseek',
        model: 'deepseek-v4-flash',
        available: true,
        message: 'DeepSeek 连接正常',
        latencyMs: 88,
      }));
    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '系统设置' }));
    expect(await screen.findByRole('heading', { name: 'AI 配置' })).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('AI 供应商'), 'deepseek');
    await userEvent.click(screen.getByLabelText('启用 DeepSeek'));
    await userEvent.clear(screen.getByLabelText('DeepSeek API Key'));
    await userEvent.type(screen.getByLabelText('DeepSeek API Key'), 'sk-1234567890');
    await userEvent.click(screen.getByRole('button', { name: '保存配置' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/ai/settings', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        provider: 'deepseek',
        deepSeekEnabled: true,
        deepSeekBaseUrl: 'https://api.deepseek.com',
        deepSeekModel: 'deepseek-v4-flash',
        deepSeekApiKey: 'sk-1234567890',
        clearDeepSeekApiKey: false,
        deepSeekTimeoutSeconds: 60,
      }),
    }));
    expect((await screen.findAllByText('AI 配置已保存')).length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: '测试连接' }));
    expect((await screen.findAllByText(/DeepSeek 连接正常/)).length).toBeGreaterThan(0);
  });
});

async function openWorkbench() {
  await userEvent.click(await screen.findByRole('button', { name: '工作台' }));
}

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

function sampleOutline() {
  return {
    traceId: '11111111-1111-1111-1111-111111111111',
    titleSuggestion: 'AI 提纲标题',
    sections: [
      { heading: '一、主要事项', points: ['说明安排', '明确分工'] },
    ],
    missingInformation: ['会议时间'],
  };
}

function sampleAiSettings() {
  return {
    provider: 'mock',
    deepSeekEnabled: false,
    deepSeekBaseUrl: 'https://api.deepseek.com',
    deepSeekModel: 'deepseek-v4-flash',
    deepSeekApiKeyConfigured: false,
    maskedDeepSeekApiKey: '',
    deepSeekTimeoutSeconds: 60,
  };
}
