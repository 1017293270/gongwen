import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import type { Department, DraftBlock } from './draftTypes';

describe('App', () => {
  const originalTextareaScrollHeight = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'scrollHeight');

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
    vi.useRealTimers();
    window.localStorage.clear();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
    if (originalTextareaScrollHeight) {
      Object.defineProperty(HTMLTextAreaElement.prototype, 'scrollHeight', originalTextareaScrollHeight);
    } else {
      Reflect.deleteProperty(HTMLTextAreaElement.prototype, 'scrollHeight');
    }
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
    stubFetch(fetchMock);

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
    stubFetch(fetchMock);

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

  it('renders the current account in the sidebar footer instead of the top header', async () => {
    vi.stubGlobal('fetch', (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/me')) {
        return Promise.resolve(jsonResponse({
          id: 1,
          username: 'admin',
          displayName: 'System Admin',
          departmentId: 1,
          departmentName: '总公司',
          roles: ['DRAFTER'],
        }));
      }
      if (url.endsWith('/api/auth/csrf')) {
        return Promise.resolve(jsonResponse({ token: 'test-csrf-token' }));
      }
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        ]));
      }
      if (url.includes('/api/templates/versions')) {
        return Promise.resolve(jsonResponse([]));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });

    const { container } = render(<App />);

    const accountSection = await screen.findByLabelText('当前账号');
    expect(within(accountSection).getByText('System Admin')).toBeInTheDocument();
    expect(within(accountSection).queryByText('总公司')).not.toBeInTheDocument();
    expect(within(accountSection).queryByText('起草人')).not.toBeInTheDocument();
    expect(within(accountSection).queryByText('当前账号')).not.toBeInTheDocument();
    expect(within(accountSection).getByRole('button', { name: '退出登录' })).toBeInTheDocument();

    const header = container.querySelector('.app-header');
    expect(header).not.toBeNull();
    expect(within(header as HTMLElement).queryByText('System Admin')).not.toBeInTheDocument();
  });

  it('selects a body section node from the paper and updates the right panel context', async () => {
    const sectionDraft = {
      ...sampleDraft('节点选择草稿'),
      blocks: [
        { id: 1, blockType: 'TITLE', content: '节点选择草稿', sortOrder: 10 },
        { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
        { id: 3, blockType: 'BODY_PARAGRAPH', content: '一、会议时间\n2026年6月3日（星期三）上午9:30。', sortOrder: 30 },
        { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
        { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
        { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
      ],
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sectionDraft))
      .mockResolvedValueOnce(jsonResponse([]));
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    const preview = screen.getByLabelText('公文预览');
    await userEvent.click(await within(preview).findByText('一、会议时间'));

    expect(screen.getByText('已选择：会议时间')).toBeInTheDocument();
    expect(within(screen.getByLabelText('正文段落目录')).getByText('会议时间')).toBeInTheDocument();
  });

  it('edits a selected body section heading and saves it with the body content', async () => {
    const sectionDraft = {
      ...sampleDraft('标题编辑草稿'),
      blocks: [
        { id: 1, blockType: 'TITLE', content: '标题编辑草稿', sortOrder: 10 },
        { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
        { id: 3, blockType: 'BODY_PARAGRAPH', content: '一、会议时间\n2026年6月3日（星期三）上午9:30。', sortOrder: 30 },
        { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
        { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
        { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
      ],
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sectionDraft))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({
        ...sectionDraft,
        blocks: sectionDraft.blocks.map((block) => block.id === 3
          ? { ...block, content: '一、会议安排\n2026年6月3日（星期三）上午9:30。' }
          : block),
      }));
    stubFetch(fetchMock);

    const { container } = render(<App />);

    await openWorkbench();
    const preview = screen.getByLabelText('公文预览');
    await userEvent.click(await within(preview).findByText('一、会议时间'));
    const headingEditor = await within(preview).findByLabelText('编辑标题：会议时间');
    await userEvent.clear(headingEditor);
    await userEvent.type(headingEditor, '一、会议安排');
    await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/api/drafts/1/blocks', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        blocks: [
          { blockType: 'TITLE', content: '标题编辑草稿', sortOrder: 10 },
          { blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
          { blockType: 'BODY_PARAGRAPH', content: '一、会议安排\n2026年6月3日（星期三）上午9:30。', sortOrder: 30 },
          { blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
          { blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
          { blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
        ],
      }),
    }));
    expect(await screen.findByText('草稿已保存')).toBeInTheDocument();
  });

  it('selects a persisted draft node, edits content, and saves through the node API', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '1');
    const nodeDraft = { ...sampleDraft('节点草稿'), templateVersionId: 9 };
    const nodeRows = sampleDraftNodes();
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts/1')) {
        return Promise.resolve(jsonResponse(nodeDraft));
      }
      if (url.endsWith('/api/drafts/1/materials') || url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions/9/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/9/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/drafts/1/nodes')) {
        return Promise.resolve(jsonResponse(nodeRows));
      }
      if (url.endsWith('/api/drafts/1/nodes/103') && init?.method === 'PUT') {
        const payload = JSON.parse(String(init.body));
        return Promise.resolve(jsonResponse({ ...nodeRows[2], content: payload.content, status: payload.status }));
      }
      if (url.endsWith('/api/drafts/1/blocks')) {
        const payload = JSON.parse(String(init?.body));
        return Promise.resolve(jsonResponse({ ...nodeDraft, blocks: payload.blocks }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await userEvent.click(await within(screen.getByLabelText('结构节点树')).findByText('节点事项'));
    const editor = await within(screen.getByLabelText('公文预览')).findByLabelText(/编辑段落：节点事项/);
    await userEvent.type(editor, '已修改');
    await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/1/nodes/103', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        content: '节点正文已修改',
        status: 'USER_FILLED',
      }),
    }));
    expect(await screen.findByText('草稿已保存')).toBeInTheDocument();
  });

  it('edits a template-derived body section without appending a block per keypress', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '1');
    const draft = {
      ...sampleDraft('结构编辑草稿'),
      templateVersionId: 9,
      blocks: [
        { id: 1, blockType: 'TITLE', content: '结构编辑草稿', sortOrder: 10 },
        { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
        { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
        { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
        { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
      ],
    };
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts/1')) {
        return Promise.resolve(jsonResponse(draft));
      }
      if (url.endsWith('/api/drafts/1/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions/9/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/9/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/drafts/1/blocks')) {
        const payload = JSON.parse(String(init?.body));
        return Promise.resolve(jsonResponse({ ...draft, blocks: payload.blocks }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    const preview = screen.getByLabelText('公文预览');
    await userEvent.click(await within(preview).findByText('二、会议地点'));
    const editor = await within(preview).findByLabelText(/编辑段落：会议地点/);
    await userEvent.type(editor, '{backspace}2');
    await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    const saveCall = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/api/drafts/1/blocks'));
    expect(saveCall).toBeTruthy();
    const payload = JSON.parse(String(saveCall?.[1]?.body));
    const bodyBlocks = payload.blocks.filter((block: { blockType: string }) => block.blockType === 'BODY_PARAGRAPH');
    expect(bodyBlocks).toHaveLength(2);
    expect(bodyBlocks.filter((block: { sortOrder: number }) => block.sortOrder === 3)).toHaveLength(1);
    expect(bodyBlocks[1]).toMatchObject({
      sortOrder: 3,
      content: expect.stringContaining('二、会议地点'),
    });
    expect(bodyBlocks[1].content).toContain('公司总部三楼第一会议室2');
  });

  it('renders template top structure color from parsed profile formatting', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '1');
    const draft = { ...sampleDraft('红头预览草稿'), templateVersionId: 9 };
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts/1')) {
        return Promise.resolve(jsonResponse(draft));
      }
      if (url.endsWith('/api/drafts/1/materials') || url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions/9/profile')) {
        return Promise.resolve(jsonResponse(templateTopColorProfile()));
      }
      if (url.endsWith('/api/templates/versions/9/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    const unit = await within(screen.getByLabelText('公文预览')).findByText('示例单位文件');
    expect(unit).toHaveStyle({ color: '#C00000' });
  });

  it('removes a template-derived body section from the current draft without deleting the template', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    window.localStorage.setItem('gongwen.currentDraftId', '1');
    const draft = { ...sampleDraft('删除结构草稿'), templateVersionId: 9 };
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts/1')) {
        return Promise.resolve(jsonResponse(draft));
      }
      if (url.endsWith('/api/drafts/1/materials') || url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions/9/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/9/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/drafts/1/blocks')) {
        const payload = JSON.parse(String(init?.body));
        return Promise.resolve(jsonResponse({ ...draft, blocks: payload.blocks }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    const { container } = render(<App />);

    await openWorkbench();
    await userEvent.click(await screen.findByRole('button', { name: '删除正文结构：会议地点' }));
    expect(screen.queryByText('二、会议地点')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    const saveCall = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/api/drafts/1/blocks'));
    expect(saveCall).toBeTruthy();
    const payload = JSON.parse(String(saveCall?.[1]?.body));
    expect(payload.blocks.some((block: { content: string }) => block.content.includes('二、会议地点'))).toBe(false);
    expect(JSON.parse(window.localStorage.getItem('gongwen.deletedNodes.1.9') ?? '[]')).toContain('template:body-3');
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
    stubFetch(fetchMock);

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
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await screen.findByDisplayValue('失败测试草稿');
    const input = screen.getByLabelText('上传材料文件');
    await userEvent.upload(input, new File(['plain text'], 'broken.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    }));

    expect(await screen.findByText('仅支持上传 Word 或 PDF 材料')).toBeInTheDocument();
  });

  it('opens outline generation in a modal with progress feedback', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let resolveOutline: (response: Response) => void = () => undefined;
    const outlinePromise = new Promise<Response>((resolve) => {
      resolveOutline = resolve;
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('提纲测试草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockReturnValueOnce(outlinePromise);
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await screen.findByDisplayValue('提纲测试草稿');
    await userEvent.type(screen.getByLabelText('提纲补充要求'), '突出执行要求');
    await userEvent.click(within(screen.getByLabelText('AI 建议和质检')).getByRole('button', { name: '生成提纲' }));

    const dialog = await screen.findByRole('dialog', { name: '生成提纲' });
    const progress = within(dialog).getByRole('progressbar', { name: '生成提纲进度' });
    expect(Number(progress.getAttribute('aria-valuenow'))).toBeLessThanOrEqual(5);
    await vi.advanceTimersByTimeAsync(240);
    expect(Number(progress.getAttribute('aria-valuenow'))).toBeGreaterThan(0);
    expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/api/drafts/1/ai/outline', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ instruction: '突出执行要求' }),
    }));
    resolveOutline(jsonResponse(sampleOutline()));
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
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await screen.findByDisplayValue('正文生成草稿');
    await userEvent.type(screen.getByLabelText('提纲补充要求'), '突出执行要求');
    await userEvent.click(within(screen.getByLabelText('AI 建议和质检')).getByRole('button', { name: '生成提纲' }));
    await screen.findByRole('dialog', { name: '生成提纲' });
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
    const preview = screen.getByLabelText('公文预览');
    expect(within(preview).getByText('一、主要事项')).toBeInTheDocument();
    expect(within(preview).getByText('说明安排；明确分工。')).toBeInTheDocument();
  });

  it('generates all body paragraphs from the outline in order', async () => {
    const outline = {
      ...sampleOutline(),
      sections: [
        { heading: '一、主要事项', points: ['说明安排'] },
        { heading: '二、工作要求', points: ['落实责任'] },
      ],
    };
    const firstDraft = {
      ...sampleDraft('全局生成草稿'),
      blocks: [
        { id: 1, blockType: 'TITLE', content: '全局生成草稿', sortOrder: 10 },
        { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
        { id: 3, blockType: 'BODY_PARAGRAPH', content: '一、主要事项：说明安排。', sortOrder: 30 },
        { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
        { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
        { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
      ],
    };
    const secondDraft = {
      ...firstDraft,
      blocks: [
        ...firstDraft.blocks,
        { id: 7, blockType: 'BODY_PARAGRAPH', content: '二、工作要求：落实责任。', sortOrder: 31 },
      ],
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('全局生成草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(outline))
      .mockResolvedValueOnce(jsonResponse({
        traceId: '22222222-2222-2222-2222-222222222222',
        draft: firstDraft,
        block: firstDraft.blocks[2],
      }))
      .mockResolvedValueOnce(jsonResponse({
        traceId: '33333333-3333-3333-3333-333333333333',
        draft: secondDraft,
        block: secondDraft.blocks[6],
      }));
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await screen.findByDisplayValue('全局生成草稿');
    await userEvent.click(within(screen.getByLabelText('AI 建议和质检')).getByRole('button', { name: '生成提纲' }));
    await screen.findByRole('dialog', { name: '生成提纲' });
    await userEvent.click(await within(screen.getByLabelText('AI 提纲结果')).findByRole('button', { name: '生成全部正文' }));

    const paragraphCalls = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/api/drafts/1/ai/paragraph'));
    expect(paragraphCalls).toHaveLength(2);
    expect(paragraphCalls[0][1]).toEqual(expect.objectContaining({
      body: JSON.stringify({
        heading: '一、主要事项',
        points: ['说明安排'],
        instruction: '',
        sortOrder: 30,
      }),
    }));
    expect(paragraphCalls[1][1]).toEqual(expect.objectContaining({
      body: JSON.stringify({
        heading: '二、工作要求',
        points: ['落实责任'],
        instruction: '',
        sortOrder: 31,
      }),
    }));
    expect(await screen.findByText('全部正文已生成')).toBeInTheDocument();
    const preview = screen.getByLabelText('公文预览');
    expect(within(preview).getByText('二、工作要求')).toBeInTheDocument();
    expect(within(preview).getByText('落实责任。')).toBeInTheDocument();
  });

  it('selects a paragraph, generates a local suggestion, and accepts it through block save', async () => {
    const originalDraft = {
      ...sampleDraft('局部操作草稿'),
      blocks: [
        { id: 1, blockType: 'TITLE', content: '局部操作草稿', sortOrder: 10 },
        { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
        { id: 3, blockType: 'BODY_PARAGRAPH', content: '第一段原文', sortOrder: 30 },
        { id: 7, blockType: 'BODY_PARAGRAPH', content: '第二段原文', sortOrder: 31 },
        { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
        { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
        { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
      ],
    };
    const savedDraft = {
      ...originalDraft,
      blocks: originalDraft.blocks.map((block) => block.id === 7
        ? { ...block, content: '第二段建议文本' }
        : block),
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(originalDraft))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({
        traceId: '33333333-3333-3333-3333-333333333333',
        targetBlockId: 7,
        operationType: 'FORMALIZE',
        suggestionText: '第二段建议文本',
      }))
      .mockResolvedValueOnce(jsonResponse(savedDraft));
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    const paragraphIndex = screen.getByLabelText('正文段落目录');
    const secondIndexItem = await within(paragraphIndex).findByText('第二段原文');
    await userEvent.click(secondIndexItem.closest('button') as HTMLButtonElement);
    const preview = screen.getByLabelText('公文预览');
    expect(await within(preview).findByLabelText('编辑段落：第二段原文')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '生成段落建议' }));
    expect(await screen.findByRole('dialog', { name: '生成段落建议' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/1/ai/local-operation', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        targetBlockId: 7,
        operationType: 'FORMALIZE',
        instruction: '',
      }),
    }));

    expect(await screen.findByText('第二段建议文本')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '采纳建议' }));

    expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/api/drafts/1/blocks', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({
        blocks: [
          { blockType: 'TITLE', content: '局部操作草稿', sortOrder: 10 },
          { blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
          { blockType: 'BODY_PARAGRAPH', content: '第一段原文', sortOrder: 30 },
          { blockType: 'BODY_PARAGRAPH', content: '第二段建议文本', sortOrder: 31 },
          { blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
          { blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
          { blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
        ],
      }),
    }));
    expect(within(screen.getByLabelText('公文预览')).getByText('第二段建议文本')).toBeInTheDocument();
    expect(within(screen.getByLabelText('公文预览')).getByText('第一段原文')).toBeInTheDocument();
  });

  it('routes local operation requests to the selected persisted draft node', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '1');
    const nodeDraft = {
      ...sampleDraft('节点 AI 草稿'),
      templateVersionId: 9,
      blocks: [
        { id: 1, blockType: 'TITLE', content: '节点 AI 草稿', sortOrder: 10 },
        { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
        { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
        { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
        { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
      ],
    };
    const nodeRows = sampleDraftNodes();
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts/1')) {
        return Promise.resolve(jsonResponse(nodeDraft));
      }
      if (url.endsWith('/api/drafts/1/materials') || url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions/9/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/9/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/drafts/1/nodes')) {
        return Promise.resolve(jsonResponse(nodeRows));
      }
      if (url.endsWith('/api/drafts/1/ai/local-operation')) {
        return Promise.resolve(jsonResponse({
          traceId: '55555555-5555-5555-5555-555555555555',
          targetBlockId: null,
          targetNodeId: 103,
          targetNodeRole: 'BODY',
          operationType: 'FORMALIZE',
          suggestionText: '节点建议文本',
        }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await userEvent.click(await within(screen.getByLabelText('结构节点树')).findByText('节点事项'));
    await userEvent.click(screen.getByRole('button', { name: '生成正文建议' }));
    await screen.findByRole('dialog', { name: '生成节点建议' });

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/1/ai/local-operation', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        nodeId: 103,
        nodeRole: 'BODY',
        nodeTitle: '正文',
        nodeContext: '节点正文',
        operationType: 'FORMALIZE',
        instruction: '',
      }),
    }));
    expect(await screen.findByText('节点建议文本')).toBeInTheDocument();
  });

  it('changes right-panel node actions when selected node role changes', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '1');
    const nodeDraft = { ...sampleDraft('节点动作草稿'), templateVersionId: 9 };
    const nodeRows = [
      ...sampleDraftNodes(),
      {
        ...sampleDraftNodes()[0],
        id: 104,
        templateNodeKey: 'date-node',
        role: 'DATE',
        slotKey: 'date',
        title: '日期',
        content: '2026年5月31日',
        sortOrder: 60,
      },
    ];
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts/1')) {
        return Promise.resolve(jsonResponse(nodeDraft));
      }
      if (url.endsWith('/api/drafts/1/materials') || url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions/9/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/9/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/drafts/1/nodes')) {
        return Promise.resolve(jsonResponse(nodeRows));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    const structureTree = screen.getByLabelText('结构节点树');

    await userEvent.click(await within(structureTree).findByRole('button', { name: /标题 标题/ }));
    expect(screen.getByText('标题节点')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '生成标题建议' })).toBeEnabled();

    await userEvent.click(await within(structureTree).findByText('节点事项'));
    expect(screen.getByText('正文节点')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '生成正文建议' })).toBeEnabled();

    await userEvent.click(await within(structureTree).findByRole('button', { name: /日期 日期/ }));
    expect(screen.getByText('日期节点')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '运行质检确认' })).toBeEnabled();
  });

  it('saves a selected node format override and marks preview as outdated', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '1');
    const nodeDraft = { ...sampleDraft('节点格式草稿'), templateVersionId: 9 };
    const nodeRows = sampleDraftNodes();
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts/1')) {
        return Promise.resolve(jsonResponse(nodeDraft));
      }
      if (url.endsWith('/api/drafts/1/materials') || url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions/9/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/9/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/drafts/1/nodes')) {
        return Promise.resolve(jsonResponse(nodeRows));
      }
      if (url.endsWith('/api/drafts/1/nodes/103/format-override') && init?.method === 'PUT') {
        const payload = JSON.parse(String(init.body));
        expect(payload).toEqual(expect.objectContaining({
          eastAsiaFont: 'KaiTi',
          fontSizePt: 18,
          alignment: 'CENTER',
          firstLineIndentTwip: 560,
        }));
        return Promise.resolve(jsonResponse({
          ...nodeRows[2],
          status: 'FORMAT_OVERRIDDEN',
          formatOverride: {
            ...nodeRows[2].formatOverride,
            eastAsiaFont: 'KaiTi',
            fontSizePt: 18,
            alignment: 'CENTER',
            firstLineIndentTwip: 560,
          },
        }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await userEvent.click(await within(screen.getByLabelText('结构节点树')).findByText('节点事项'));

    await userEvent.type(screen.getByLabelText('中文字体'), 'KaiTi');
    await userEvent.clear(screen.getByLabelText('字号'));
    await userEvent.type(screen.getByLabelText('字号'), '18');
    await userEvent.selectOptions(screen.getByLabelText('对齐方式'), 'CENTER');
    await userEvent.clear(screen.getByLabelText('首行缩进'));
    await userEvent.type(screen.getByLabelText('首行缩进'), '560');
    await userEvent.click(screen.getByRole('button', { name: '保存格式' }));

    expect(await screen.findByText('真实预览待刷新')).toBeInTheDocument();
    const editor = await within(screen.getByLabelText('公文预览')).findByLabelText(/编辑段落：节点事项/) as HTMLTextAreaElement;
    expect(editor.style.fontFamily).toContain('KaiTi');
    expect(editor.style.fontSize).toBe('18pt');
    expect(editor.style.textAlign).toBe('center');
  });

  it('expands the selected paragraph editor to fit its content', async () => {
    Object.defineProperty(HTMLTextAreaElement.prototype, 'scrollHeight', {
      configurable: true,
      get: () => 240,
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse({
        ...sampleDraft('自适应高度草稿'),
        blocks: [
          { id: 1, blockType: 'TITLE', content: '自适应高度草稿', sortOrder: 10 },
          { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
          { id: 7, blockType: 'BODY_PARAGRAPH', content: '这是一段较长的正文，需要编辑框按内容高度展开。', sortOrder: 30 },
          { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
          { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
          { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
        ],
      }))
      .mockResolvedValueOnce(jsonResponse([]));
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    const preview = screen.getByLabelText('公文预览');
    await userEvent.click((await within(preview).findByText('这是一段较长的正文，需要编辑框按内容高度展开。')).closest('button') as HTMLButtonElement);

    const editor = await within(preview).findByLabelText('编辑段落：这是一段较长的正文，需要编辑框按内容') as HTMLTextAreaElement;
    expect(editor.style.height).toBe('240px');
  });

  it('confirms before discarding a local paragraph suggestion', async () => {
    const originalDraft = {
      ...sampleDraft('放弃建议草稿'),
      blocks: [
        { id: 1, blockType: 'TITLE', content: '放弃建议草稿', sortOrder: 10 },
        { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
        { id: 7, blockType: 'BODY_PARAGRAPH', content: '第二段原文', sortOrder: 31 },
        { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
        { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
        { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
      ],
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(originalDraft))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({
        traceId: '44444444-4444-4444-4444-444444444444',
        targetBlockId: 7,
        operationType: 'FORMALIZE',
        suggestionText: '即将放弃的建议',
      }));
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    const preview = screen.getByLabelText('公文预览');
    await userEvent.click((await within(preview).findByText('第二段原文')).closest('button') as HTMLButtonElement);
    await userEvent.click(screen.getByRole('button', { name: '生成段落建议' }));
    await screen.findByRole('dialog', { name: '生成段落建议' });
    expect(await screen.findByText('即将放弃的建议')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '放弃' }));
    expect(screen.getByRole('dialog', { name: '放弃这条段落建议？' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '继续编辑' }));
    expect(screen.queryByRole('dialog', { name: '放弃这条段落建议？' })).not.toBeInTheDocument();
    expect(screen.getByText('即将放弃的建议')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '放弃' }));
    await userEvent.click(screen.getByRole('button', { name: '放弃建议' }));
    expect(screen.queryByText('即将放弃的建议')).not.toBeInTheDocument();
  });

  it('runs quality check and shows blocking rule errors with AI suggestions', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('质检草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({
        id: '22222222-2222-2222-2222-222222222222',
        draftId: 1,
        status: 'ERROR',
        exportBlocked: true,
        aiTraceId: '33333333-3333-3333-3333-333333333333',
        checkedAt: '2026-05-26T10:00:00Z',
        items: [
          {
            severity: 'ERROR',
            category: 'REQUIRED_FIELD',
            code: 'REQUIRED_RECIPIENT_MISSING',
            message: '主送对象不能为空。',
            targetBlockType: 'RECIPIENT',
            targetBlockId: 2,
            suggestion: '请先补齐该字段后再导出。',
          },
          {
            severity: 'WARNING',
            category: 'AI_EXPRESSION',
            code: 'AI_EXPRESSION_CLARITY',
            message: '责任要求还可以更明确。',
            targetBlockType: null,
            targetBlockId: null,
            suggestion: '建议补充责任部门和完成时限。',
          },
        ],
      }));
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await screen.findByDisplayValue('质检草稿');
    await userEvent.click(within(screen.getByLabelText('基础质检')).getByRole('button', { name: '运行质检' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/1/quality-check', expect.objectContaining({
      method: 'POST',
    }));
    const dialog = await screen.findByRole('dialog', { name: '运行质检' });
    expect(await within(dialog).findByText('主送对象不能为空。')).toBeInTheDocument();
    expect(within(dialog).getByText('责任要求还可以更明确。')).toBeInTheDocument();
    expect(within(dialog).getByText('存在 ERROR 项，后续导出前需要先处理。')).toBeInTheDocument();
  });

  it('exports the current draft as a Word file', async () => {
    Object.defineProperty(window.URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:word-export'),
    });
    Object.defineProperty(window.URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const exportDraft = { ...sampleDraft('导出草稿'), templateVersionId: 9 };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(exportDraft))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(exportDraft))
      .mockResolvedValueOnce(docxResponse('测试模板-v2.docx'));
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await userEvent.click(within(screen.getByRole('banner')).getByRole('button', { name: '导出 Word' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/1/blocks', expect.objectContaining({
      method: 'PUT',
    }));
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/api/drafts/1/quality-check'))).toBe(false);
    expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/api/exports/drafts/1/word', expect.objectContaining({
      method: 'POST',
    }));
    expect(clickSpy).toHaveBeenCalled();
    expect(await screen.findByText('Word 已导出')).toBeInTheDocument();
  });

  it('keeps Word export independent from manual quality check state', async () => {
    Object.defineProperty(window.URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:word-export'),
    });
    Object.defineProperty(window.URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const exportDraft = { ...sampleDraft('导出阻断草稿'), templateVersionId: 9 };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(exportDraft))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(exportDraft))
      .mockResolvedValueOnce(docxResponse('测试模板-v2.docx'));
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await userEvent.click(within(screen.getByRole('banner')).getByRole('button', { name: '导出 Word' }));

    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/api/drafts/1/quality-check'))).toBe(false);
    expect(fetchMock).toHaveBeenLastCalledWith('http://api.test/api/exports/drafts/1/word', expect.objectContaining({
      method: 'POST',
    }));
    expect(clickSpy).toHaveBeenCalled();
  });

  it('marks true preview outdated after editing and refreshes it from the workbench', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '1');
    const draft = { ...sampleDraft('预览刷新草稿'), templateVersionId: 9 };
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts/1') && !init?.method) {
        return Promise.resolve(jsonResponse(draft));
      }
      if (url.endsWith('/api/drafts/1/materials') || url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/drafts/1/nodes') || url.endsWith('/api/drafts/1/nodes/initialize')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions/9/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/9/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/templates/versions/9/render-preview') && init?.method === 'POST') {
        return Promise.resolve(jsonResponse({ ...renderPreviewFixture('READY'), templateVersionId: 9 }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    const titleInput = await screen.findByLabelText('标题');
    await userEvent.clear(titleInput);
    await userEvent.type(titleInput, '预览刷新后的标题');

    expect(await screen.findByText('真实预览待刷新')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '刷新预览' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/templates/versions/9/render-preview', expect.objectContaining({
      method: 'POST',
    }));
    expect(await screen.findByText('真实预览已是当前版本')).toBeInTheDocument();
  });

  it('shows backend export blocker messages in the workbench export panel', async () => {
    const exportDraft = { ...sampleDraft('导出后端阻断草稿'), templateVersionId: 9 };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(exportDraft))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(exportDraft))
      .mockResolvedValueOnce(errorResponse('EXPORT_REQUIRED_SLOT_EMPTY', '导出必填结构槽位为空：BODY（正文）'));
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await userEvent.click(within(screen.getByRole('banner')).getByRole('button', { name: '导出 Word' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/exports/drafts/1/word', expect.objectContaining({
      method: 'POST',
    }));
    expect((await screen.findAllByText('导出必填结构槽位为空：BODY（正文）')).length).toBeGreaterThan(0);
  });

  it('materializes template-derived body sections before exporting Word', async () => {
    Object.defineProperty(window.URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:word-export'),
    });
    Object.defineProperty(window.URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    window.localStorage.setItem('gongwen.currentDraftId', '1');
    const draft = {
      ...sampleDraft('导出结构草稿'),
      templateVersionId: 9,
      blocks: [
        { id: 1, blockType: 'TITLE', content: '导出结构草稿', sortOrder: 10 },
        { id: 2, blockType: 'RECIPIENT', content: '各部门、各直属单位', sortOrder: 20 },
        { id: 4, blockType: 'ATTACHMENT', content: '无', sortOrder: 40 },
        { id: 5, blockType: 'SIGNATURE', content: '办公室', sortOrder: 50 },
        { id: 6, blockType: 'DATE', content: '2026年5月25日', sortOrder: 60 },
      ],
    };
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts/1')) {
        return Promise.resolve(jsonResponse(draft));
      }
      if (url.endsWith('/api/drafts/1/materials') || url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions/9/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/9/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/drafts/1/blocks')) {
        const payload = JSON.parse(String(init?.body));
        return Promise.resolve(jsonResponse({ ...draft, blocks: payload.blocks }));
      }
      if (url.endsWith('/api/exports/drafts/1/word')) {
        return Promise.resolve(docxResponse('测试模板-v2.docx'));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await userEvent.click(within(screen.getByRole('banner')).getByRole('button', { name: '导出 Word' }));

    const saveCall = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/api/drafts/1/blocks'));
    expect(saveCall).toBeTruthy();
    const payload = JSON.parse(String(saveCall?.[1]?.body));
    const bodyBlocks = payload.blocks.filter((block: { blockType: string }) => block.blockType === 'BODY_PARAGRAPH');
    expect(bodyBlocks).toHaveLength(2);
    expect(bodyBlocks[0].content).toContain('一、会议时间');
    expect(bodyBlocks[0].content).toContain('2026年6月3日上午9:30。');
    expect(bodyBlocks[1].content).toContain('二、会议地点');
    expect(bodyBlocks[1].content).toContain('公司总部三楼第一会议室。');
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/api/drafts/1/quality-check'))).toBe(false);
  });

  it('shows retry state when outline generation fails', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('提纲失败草稿')))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(errorResponse('AI_MODEL_UNAVAILABLE', 'AI 服务暂不可用，请稍后重试'));
    stubFetch(fetchMock);

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
    stubFetch(fetchMock);

    const { container } = render(<App />);

    expect(await screen.findByRole('heading', { name: '总览' })).toBeInTheDocument();
    expect(screen.getByLabelText('主导航')).toBeInTheDocument();
    expect(screen.getByText('最近草稿')).toBeInTheDocument();
    expect(screen.getByText('总览草稿')).toBeInTheDocument();
    const navLabels = within(screen.getByLabelText('主导航')).getAllByRole('button').map((button) => button.getAttribute('aria-label'));
    expect(navLabels).toContain('系统设置');
    expect(navLabels).not.toContain('部门管理');
    expect(navLabels).not.toContain('账号管理');

    await userEvent.click(screen.getByRole('button', { name: '工作台' }));

    expect(await screen.findByDisplayValue('总览草稿')).toBeInTheDocument();
    expect(container.querySelector('.app-shell')).toHaveClass('app-shell--workbench-focus');
    expect(screen.getByRole('button', { name: '回到目录' })).toBeInTheDocument();
  });

  it('lists export records and downloads a successful Word export', async () => {
    Object.defineProperty(window.URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:export-history'),
    });
    Object.defineProperty(window.URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts') && init?.method === 'POST') {
        return Promise.resolve(jsonResponse(sampleDraft('导出记录草稿')));
      }
      if (url.endsWith('/api/drafts/1/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/exports')) {
        return Promise.resolve(jsonResponse([
          sampleExportRecord({
            id: 7,
            draftTitle: '会议通知草稿',
            templateVersionId: 9,
            templateName: '通知模板',
            templateVersion: 4,
            fileName: '通知模板-v4.docx',
            status: 'SUCCESS',
            canDownload: true,
          }),
          sampleExportRecord({
            id: 8,
            draftId: 3,
            draftTitle: '缺字段草稿',
            templateVersionId: 9,
            templateName: '通知模板',
            templateVersion: 4,
            fileName: '通知模板-v4.docx',
            status: 'FAILED',
            errorCode: 'MISSING_TEMPLATE_VALUE',
            errorMessage: '正文不能为空',
            canDownload: false,
            canRetry: true,
          }),
        ]));
      }
      if (url.endsWith('/api/exports/8') && init?.method !== 'POST') {
        return Promise.resolve(jsonResponse({
          ...sampleExportRecord({
            id: 8,
            draftId: 3,
            draftTitle: '缺字段草稿',
            templateVersionId: 9,
            templateName: '通知模板',
            templateVersion: 4,
            fileName: '通知模板-v4.docx',
            status: 'FAILED',
            errorCode: 'MISSING_TEMPLATE_VALUE',
            errorMessage: '正文不能为空',
            canDownload: false,
            canRetry: true,
          }),
          fileAvailable: false,
        }));
      }
      if (url.endsWith('/api/exports/7/download')) {
        return Promise.resolve(docxResponse('通知模板-v4.docx'));
      }
      if (url.endsWith('/api/exports/8/retry') && init?.method === 'POST') {
        return Promise.resolve(docxResponse('通知模板-v4.docx'));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '导出记录' }));

    expect((await screen.findAllByRole('heading', { name: '导出记录' })).length).toBeGreaterThan(0);
    expect(screen.getByRole('table', { name: '导出记录列表' })).toBeInTheDocument();
    expect(screen.getByText('会议通知草稿')).toBeInTheDocument();
    expect(screen.getAllByText('通知模板 v4').length).toBeGreaterThan(0);
    expect(screen.getByText('正文不能为空')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/exports', expect.objectContaining({
      credentials: 'include',
    }));

    await userEvent.click(screen.getByRole('button', { name: '下载导出文件：通知模板-v4.docx' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/exports/7/download', expect.any(Object));
    expect(clickSpy).toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: '查看导出记录：缺字段草稿' }));

    expect(await screen.findByRole('dialog', { name: '导出详情' })).toBeInTheDocument();
    expect(screen.getByText('MISSING_TEMPLATE_VALUE')).toBeInTheDocument();
    expect(screen.getByText('历史文件不可用')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '重试导出' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/exports/8/retry', expect.objectContaining({
      method: 'POST',
    }));
  });

  it('creates a document type from the new management page', async () => {
    let documentTypes = [
      { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
    ];
    const createCalls: unknown[] = [];
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types') && init?.method === 'POST') {
        createCalls.push(JSON.parse(String(init.body)));
        documentTypes = [
          ...documentTypes,
          { code: 'ANNOUNCEMENT', name: '公告', status: 'ACTIVE', sortOrder: 4 },
        ];
        return Promise.resolve(jsonResponse(documentTypes[1]));
      }
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse(documentTypes));
      }
      if (url.endsWith('/api/drafts') && init?.method === 'POST') {
        return Promise.resolve(jsonResponse(sampleDraft('文种管理草稿')));
      }
      if (url.endsWith('/api/drafts/1/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      return Promise.resolve(jsonResponse([]));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '文种管理' }));
    await userEvent.click(screen.getByRole('button', { name: '新增文种' }));
    await userEvent.type(screen.getByLabelText('文种编码'), 'ANNOUNCEMENT');
    await userEvent.type(screen.getByLabelText('文种名称'), '公告');
    await userEvent.clear(screen.getByLabelText('排序'));
    await userEvent.type(screen.getByLabelText('排序'), '4');
    await userEvent.click(screen.getByRole('button', { name: '创建文种' }));

    expect((await screen.findAllByText('文种已创建')).length).toBeGreaterThan(0);
    expect(createCalls).toEqual([{ code: 'ANNOUNCEMENT', name: '公告', sortOrder: 4 }]);
    expect(screen.getByText('公告')).toBeInTheDocument();
  });

  it('creates a department and an account from admin pages', async () => {
    let departments: Department[] = [
      { id: 1, parentId: null, code: 'ROOT', name: '总部', status: 'ACTIVE', sortOrder: 1, children: [] },
    ];
    let users: Array<{
      id: number;
      username: string;
      displayName: string;
      departmentId: number | null;
      departmentName: string | null;
      status: string;
      roles: string[];
    }> = [];
    const departmentCreateCalls: unknown[] = [];
    const userCreateCalls: unknown[] = [];
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts') && init?.method === 'POST') {
        return Promise.resolve(jsonResponse(sampleDraft('账号管理草稿')));
      }
      if (url.endsWith('/api/drafts/1/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/departments') && init?.method === 'POST') {
        departmentCreateCalls.push(JSON.parse(String(init.body)));
        const createdDepartment = { id: 2, parentId: 1, code: 'A01A01', name: '综合管理部', status: 'ACTIVE', sortOrder: 10, children: [] };
        departments = [
          {
            ...departments[0],
            children: [createdDepartment],
          },
        ];
        return Promise.resolve(jsonResponse(createdDepartment));
      }
      if (url.endsWith('/api/departments')) {
        return Promise.resolve(jsonResponse(departments));
      }
      if (url.endsWith('/api/users') && init?.method === 'POST') {
        userCreateCalls.push(JSON.parse(String(init.body)));
        users = [
          { id: 9, username: 'zhangsan', displayName: '张三', departmentId: 2, departmentName: '综合管理部', status: 'ACTIVE', roles: ['DRAFTER'] },
        ];
        return Promise.resolve(jsonResponse(users[0]));
      }
      if (url.endsWith('/api/users')) {
        return Promise.resolve(jsonResponse(users));
      }
      return Promise.resolve(jsonResponse([]));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '系统设置' }));
    await userEvent.click(await screen.findByRole('tab', { name: '部门管理' }));
    const departmentTree = await screen.findByLabelText('部门树');
    expect(within(departmentTree).getByRole('button', { name: /全部部门/ })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: '根级部门' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '新增部门' }));
    await userEvent.selectOptions(await screen.findByLabelText('上级部门'), '1');
    expect(screen.queryByLabelText('部门编码')).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('部门名称'), '综合管理部');
    await userEvent.click(screen.getByRole('button', { name: '创建部门' }));

    expect((await screen.findAllByText('部门已创建')).length).toBeGreaterThan(0);
    expect(departmentCreateCalls).toEqual([{ parentId: 1, name: '综合管理部', sortOrder: 10 }]);
    expect(within(departmentTree).queryByRole('button', { name: /^综合管理部/ })).not.toBeInTheDocument();
    await userEvent.click(within(departmentTree).getByRole('button', { name: '展开部门：总部' }));
    expect(within(departmentTree).getByRole('button', { name: /^综合管理部/ })).toBeInTheDocument();
    await userEvent.click(within(departmentTree).getByRole('button', { name: '收起部门：总部' }));
    expect(within(departmentTree).queryByRole('button', { name: /^综合管理部/ })).not.toBeInTheDocument();

    await userEvent.click(within(departmentTree).getByRole('button', { name: /^总部/ }));
    expect(screen.getByRole('table', { name: '总部下级部门' })).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: '总部下级部门' })).getByText('综合管理部')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: '人员管理' }));
    expect(screen.queryByLabelText('账号')).not.toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: '新增账号' }));
    const accountDialog = await screen.findByRole('dialog', { name: '新增账号' });
    await userEvent.type(within(accountDialog).getByLabelText('账号'), 'zhangsan');
    await userEvent.type(within(accountDialog).getByLabelText('姓名'), '张三');
    await userEvent.type(within(accountDialog).getByLabelText('初始密码'), 'StrongPass123');
    await userEvent.selectOptions(within(accountDialog).getByLabelText('所属部门'), '2');
    await userEvent.click(within(accountDialog).getByRole('button', { name: '创建账号' }));

    expect((await screen.findAllByText('账号已创建')).length).toBeGreaterThan(0);
    expect(userCreateCalls).toEqual([{
      username: 'zhangsan',
      displayName: '张三',
      password: 'StrongPass123',
      departmentId: 2,
      roles: ['DRAFTER'],
    }]);
    expect(screen.getByText('张三')).toBeInTheDocument();
  });

  it('enters a focused workbench mode that hides the global sidebar and shows a back-to-directory action', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse([
        { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
      ]))
      .mockResolvedValueOnce(jsonResponse(sampleDraft('专注工作台草稿')))
      .mockResolvedValueOnce(jsonResponse([]));
    stubFetch(fetchMock);

    const { container } = render(<App />);

    await screen.findByRole('heading', { name: '总览' });
    expect(container.querySelector('.app-sidebar')).toBeInTheDocument();

    await openWorkbench();

    expect(await screen.findByDisplayValue('专注工作台草稿')).toBeInTheDocument();
    expect(container.querySelector('.app-shell')).toHaveClass('app-shell--workbench-focus');
    expect(container.querySelector('.app-sidebar')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '回到目录' })).toBeInTheDocument();
  });

  it('returns from the focused workbench to the current document-type draft directory', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '5');
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
          { code: 'REQUEST', name: '请示', status: 'ACTIVE', sortOrder: 2 },
        ]));
      }
      if (url.endsWith('/api/drafts/5')) {
        return Promise.resolve(jsonResponse({
          ...sampleDraft('当前请示草稿'),
          id: 5,
          documentTypeCode: 'REQUEST',
        }));
      }
      if (url.endsWith('/api/drafts/5/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/drafts?documentTypeCode=REQUEST')) {
        return Promise.resolve(jsonResponse([
          {
            id: 5,
            documentTypeCode: 'REQUEST',
            title: '当前请示草稿',
            status: 'DRAFT',
            templateVersionId: null,
            updatedAt: '2026-05-29T08:00:00Z',
          },
        ]));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    expect(await screen.findByRole('button', { name: '回到目录' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '回到目录' }));

    expect(await screen.findByRole('heading', { name: '草稿列表' })).toBeInTheDocument();
    expect(screen.getByText('当前请示草稿')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '返回文种' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '回到目录' })).not.toBeInTheDocument();
  });

  it('switches the workbench document type to the first draft of the selected type', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '5');
    const noticeDraft = { ...sampleDraft('当前通知草稿'), id: 5 };
    const requestDraft = {
      ...sampleDraft('请示草稿'),
      id: 8,
      documentTypeCode: 'REQUEST',
      blocks: [
        { id: 21, blockType: 'TITLE', content: '请示草稿', sortOrder: 10 },
        { id: 22, blockType: 'RECIPIENT', content: '集团办公室', sortOrder: 20 },
        { id: 23, blockType: 'BODY_PARAGRAPH', content: '一、请示事项\n请审议本次预算调整方案。', sortOrder: 30 },
        { id: 24, blockType: 'ATTACHMENT', content: '预算调整说明', sortOrder: 40 },
        { id: 25, blockType: 'SIGNATURE', content: '战略发展部', sortOrder: 50 },
        { id: 26, blockType: 'DATE', content: '2026年5月29日', sortOrder: 60 },
      ],
    };
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
          { code: 'REQUEST', name: '请示', status: 'ACTIVE', sortOrder: 2 },
        ]));
      }
      if (url.endsWith('/api/drafts/5')) {
        return Promise.resolve(jsonResponse(noticeDraft));
      }
      if (url.endsWith('/api/drafts/5/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/drafts?documentTypeCode=REQUEST')) {
        return Promise.resolve(jsonResponse([
          {
            id: 8,
            documentTypeCode: 'REQUEST',
            title: '请示草稿',
            status: 'DRAFT',
            templateVersionId: null,
            updatedAt: '2026-05-29T08:00:00Z',
          },
        ]));
      }
      if (url.endsWith('/api/drafts/8')) {
        return Promise.resolve(jsonResponse(requestDraft));
      }
      if (url.endsWith('/api/drafts/8/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.includes('/api/templates/versions?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.includes('/api/templates/versions?documentTypeCode=REQUEST')) {
        return Promise.resolve(jsonResponse([]));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    expect(await screen.findByDisplayValue('当前通知草稿')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('文种'), 'REQUEST');

    expect(await screen.findByDisplayValue('请示草稿')).toBeInTheDocument();
    expect(screen.getByLabelText('文种')).toHaveValue('REQUEST');
    expect(window.localStorage.getItem('gongwen.currentDraftId')).toBe('8');
  });

  it('shows only the latest version of each template in the workbench selector', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '5');
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        ]));
      }
      if (url.endsWith('/api/drafts/5')) {
        return Promise.resolve(jsonResponse({ ...sampleDraft('模板筛选草稿'), id: 5 }));
      }
      if (url.endsWith('/api/drafts/5/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          { templateVersionId: 11, templateId: 2, templateName: '通知模板A', versionNo: 1, documentTypeCode: 'NOTICE', originalFileName: 'notice-a-v1.docx' },
          { templateVersionId: 13, templateId: 2, templateName: '通知模板A', versionNo: 3, documentTypeCode: 'NOTICE', originalFileName: 'notice-a-v3.docx' },
          { templateVersionId: 12, templateId: 3, templateName: '通知模板B', versionNo: 2, documentTypeCode: 'NOTICE', originalFileName: 'notice-b-v2.docx' },
          { templateVersionId: 10, templateId: 3, templateName: '通知模板B', versionNo: 1, documentTypeCode: 'NOTICE', originalFileName: 'notice-b-v1.docx' },
        ]));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();

    const options = within(screen.getByLabelText('套版模板')).getAllByRole('option');

    expect(options.map((option) => option.textContent)).toEqual([
      '未选择模板',
      '通知模板A v3',
      '通知模板B v2',
    ]);
    expect(screen.queryByRole('option', { name: '通知模板A v1' })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: '通知模板B v1' })).not.toBeInTheDocument();
  });

  it('keeps the workbench open when the selected document type has no template versions to load', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '5');
    const noticeDraft = { ...sampleDraft('当前通知草稿'), id: 5 };
    const requestDraft = {
      ...sampleDraft('无模板请示草稿'),
      id: 8,
      documentTypeCode: 'REQUEST',
      blocks: [
        { id: 31, blockType: 'TITLE', content: '无模板请示草稿', sortOrder: 10 },
        { id: 32, blockType: 'RECIPIENT', content: '集团办公室', sortOrder: 20 },
        { id: 33, blockType: 'BODY_PARAGRAPH', content: '一、请示事项\n本稿当前尚未绑定模板。', sortOrder: 30 },
        { id: 34, blockType: 'ATTACHMENT', content: '', sortOrder: 40 },
        { id: 35, blockType: 'SIGNATURE', content: '综合管理部', sortOrder: 50 },
        { id: 36, blockType: 'DATE', content: '2026年5月29日', sortOrder: 60 },
      ],
    };
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
          { code: 'REQUEST', name: '请示', status: 'ACTIVE', sortOrder: 2 },
        ]));
      }
      if (url.endsWith('/api/drafts/5')) {
        return Promise.resolve(jsonResponse(noticeDraft));
      }
      if (url.endsWith('/api/drafts/5/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/drafts?documentTypeCode=REQUEST')) {
        return Promise.resolve(jsonResponse([
          {
            id: 8,
            documentTypeCode: 'REQUEST',
            title: '无模板请示草稿',
            status: 'DRAFT',
            templateVersionId: null,
            updatedAt: '2026-05-29T09:00:00Z',
          },
        ]));
      }
      if (url.endsWith('/api/drafts/8')) {
        return Promise.resolve(jsonResponse(requestDraft));
      }
      if (url.endsWith('/api/drafts/8/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates/versions?documentTypeCode=REQUEST')) {
        return Promise.reject(new Error('当前文种暂无模板'));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await openWorkbench();
    await userEvent.selectOptions(screen.getByLabelText('文种'), 'REQUEST');

    expect(await screen.findByDisplayValue('无模板请示草稿')).toBeInTheDocument();
    expect(screen.getByLabelText('文种')).toHaveValue('REQUEST');
    expect(screen.queryByRole('heading', { name: '模板管理' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('套版模板')).toBeDisabled();
  });

  it('creates a draft inside the selected document-type list before opening it in the workbench', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '5');
    const createdDraft = {
      ...sampleDraft('未命名草稿'),
      id: 8,
      documentTypeCode: 'REQUEST',
      templateVersionId: null,
      blocks: [],
    };
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
          { code: 'REQUEST', name: '请示', status: 'ACTIVE', sortOrder: 2 },
          { code: 'REPORT', name: '报告', status: 'ACTIVE', sortOrder: 3 },
        ]));
      }
      if (url.endsWith('/api/drafts/5')) {
        return Promise.resolve(jsonResponse({ ...sampleDraft('当前通知草稿'), id: 5 }));
      }
      if (url.endsWith('/api/drafts/8')) {
        return Promise.resolve(jsonResponse(createdDraft));
      }
      if (url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/drafts?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          {
            id: 5,
            documentTypeCode: 'NOTICE',
            title: '当前通知草稿',
            status: 'DRAFT',
            templateVersionId: null,
            updatedAt: '2026-05-27T08:00:00Z',
          },
        ]));
      }
      if (url.endsWith('/api/drafts?documentTypeCode=REQUEST')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/drafts') && init?.method === 'POST') {
        return Promise.resolve(jsonResponse(createdDraft));
      }
      if (url.endsWith('/api/drafts/8/blocks') && init?.method === 'PUT') {
        return Promise.resolve(jsonResponse({
          ...createdDraft,
          blocks: JSON.parse(String(init.body)).blocks.map((block: DraftBlock, index: number) => ({
            ...block,
            id: 30 + index,
          })),
        }));
      }
      if (url.endsWith('/api/drafts/8/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/drafts/5/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    const { container } = render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '草稿列表' }));
    expect(container.querySelector('.drafts-page .settings-panel.template-admin-panel')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /请示/ })).toHaveClass('template-folder-card');
    expect(screen.queryByText('请示草稿')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /请示/ }));
    expect(await screen.findByRole('button', { name: '返回文种' })).toBeInTheDocument();
    expect(await screen.findByText('当前文种暂无草稿')).toBeInTheDocument();
    expect(screen.getByText('当前文种暂无草稿').closest('.template-empty-panel')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '新建草稿' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ documentTypeCode: 'REQUEST', title: '未命名草稿' }),
    }));
    expect(window.localStorage.getItem('gongwen.currentDraftId')).toBe('8');
    expect(screen.getByRole('button', { name: '草稿列表' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('heading', { name: '未命名草稿' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '进入工作台' }));
    expect(await screen.findByDisplayValue('未命名草稿')).toBeInTheDocument();
    expect(container.querySelector('.app-shell')).toHaveClass('app-shell--workbench-focus');
    expect(screen.getByRole('button', { name: '回到目录' })).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('标题'));
    await userEvent.type(screen.getByLabelText('标题'), '新建后编辑的请示');
    await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/8/blocks', expect.objectContaining({
      method: 'PUT',
      body: expect.stringContaining('新建后编辑的请示'),
    }));
  });

  it('deletes a draft from the selected document-type list after confirmation', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '5');
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        ]));
      }
      if (url.endsWith('/api/drafts/5')) {
        return Promise.resolve(jsonResponse({ ...sampleDraft('当前通知草稿'), id: 5 }));
      }
      if (url.endsWith('/api/drafts/5/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/drafts?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          {
            id: 5,
            documentTypeCode: 'NOTICE',
            title: '当前通知草稿',
            status: 'DRAFT',
            templateVersionId: null,
            updatedAt: '2026-05-27T08:00:00Z',
          },
          {
            id: 6,
            documentTypeCode: 'NOTICE',
            title: '待删除通知草稿',
            status: 'DRAFT',
            templateVersionId: null,
            updatedAt: '2026-05-27T09:00:00Z',
          },
        ]));
      }
      if (url.endsWith('/api/drafts/6') && init?.method === 'DELETE') {
        return Promise.resolve(jsonResponse(null));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '草稿列表' }));
    await userEvent.click(await screen.findByRole('button', { name: /通知/ }));
    expect(await screen.findByRole('heading', { name: '待删除通知草稿' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '删除草稿：待删除通知草稿' }));
    await userEvent.click(await screen.findByRole('button', { name: '删除草稿' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/6', expect.objectContaining({
      method: 'DELETE',
    }));
    expect(screen.queryByRole('heading', { name: '待删除通知草稿' })).not.toBeInTheDocument();
  });

  it('renames a draft from the selected document-type list', async () => {
    window.localStorage.setItem('gongwen.currentDraftId', '5');
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        ]));
      }
      if (url.endsWith('/api/drafts/5')) {
        return Promise.resolve(jsonResponse({ ...sampleDraft('当前通知草稿'), id: 5 }));
      }
      if (url.endsWith('/api/drafts/5/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.includes('/api/templates/versions?')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/drafts?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          {
            id: 6,
            documentTypeCode: 'NOTICE',
            title: '待重命名通知草稿',
            status: 'DRAFT',
            templateVersionId: null,
            updatedAt: '2026-05-27T09:00:00Z',
          },
        ]));
      }
      if (url.endsWith('/api/drafts/6/title') && init?.method === 'PUT') {
        return Promise.resolve(jsonResponse({
          ...sampleDraft('已重命名通知草稿'),
          id: 6,
          documentTypeCode: 'NOTICE',
        }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '草稿列表' }));
    await userEvent.click(await screen.findByRole('button', { name: /通知/ }));
    await userEvent.click(await screen.findByRole('button', { name: '重命名草稿：待重命名通知草稿' }));
    const nameInput = await screen.findByLabelText('草稿名称');
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, '已重命名通知草稿');
    await userEvent.click(screen.getByRole('button', { name: '保存名称' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/drafts/6/title', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify({ title: '已重命名通知草稿' }),
    }));
    expect(await screen.findByRole('heading', { name: '已重命名通知草稿' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '待重命名通知草稿' })).not.toBeInTheDocument();
  });

  it('deletes a template from template management after confirmation', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        ]));
      }
      if (url.endsWith('/api/drafts/1')) {
        return Promise.resolve(jsonResponse(sampleDraft('模板删除草稿')));
      }
      if (url.endsWith('/api/drafts/1/materials')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/templates?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          { id: 12, templateName: '待删除模板', documentTypeCode: 'NOTICE', status: 'ACTIVE' },
        ]));
      }
      if (url.endsWith('/api/templates/versions?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          {
            templateVersionId: 21,
            templateId: 12,
            templateName: '待删除模板',
            documentTypeCode: 'NOTICE',
            versionNo: 1,
            originalFileName: 'notice-template.docx',
            parseStatus: 'READY',
            createdAt: '2026-05-27T09:00:00Z',
          },
        ]));
      }
      if (url.endsWith('/api/drafts/1/template-version') && init?.method === 'PUT') {
        return Promise.resolve(jsonResponse({ ...sampleDraft('模板删除草稿'), templateVersionId: null }));
      }
      if (url.endsWith('/api/templates/12') && init?.method === 'DELETE') {
        return Promise.resolve(jsonResponse(null));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '模板管理' }));
    await userEvent.click(await screen.findByRole('button', { name: /通知/ }));
    expect(await screen.findByRole('heading', { name: '待删除模板' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '删除模板：待删除模板' }));
    await userEvent.click(await screen.findByRole('button', { name: '删除模板' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/templates/12', expect.objectContaining({
      method: 'DELETE',
    }));
    expect(screen.queryByRole('heading', { name: '待删除模板' })).not.toBeInTheDocument();
  });

  it('shows manual document warning and render preview loading state in template parse workspace', async () => {
    let resolvePreview: (value: Response) => void = () => undefined;
    const previewPromise = new Promise<Response>((resolve) => {
      resolvePreview = resolve;
    });
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        ]));
      }
      if (url.endsWith('/api/templates?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          { id: 12, templateName: '格式说明文件', documentTypeCode: 'NOTICE', status: 'ACTIVE' },
        ]));
      }
      if (url.endsWith('/api/templates/versions?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          {
            templateVersionId: 31,
            templateId: 12,
            templateName: '格式说明文件',
            documentTypeCode: 'NOTICE',
            versionNo: 1,
            originalFileName: '公文格式说明.docx',
          },
        ]));
      }
      if (url.endsWith('/api/templates/versions/31/profile')) {
        return Promise.resolve(jsonResponse(manualTemplateProfile()));
      }
      if (url.endsWith('/api/templates/versions/31/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/templates/versions/31/structure-profile')) {
        return Promise.resolve(jsonResponse(manualStructureProfile()));
      }
      if (url.endsWith('/api/templates/versions/31/document-kind')) {
        return Promise.resolve(jsonResponse(manualDocumentKind()));
      }
      if (url.endsWith('/api/templates/versions/31/render-preview')) {
        return previewPromise;
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '模板管理' }));
    await userEvent.click(await screen.findByRole('button', { name: /通知/ }));
    await userEvent.click(await screen.findByRole('button', { name: '解析结果' }));

    const dialog = await screen.findByRole('dialog', { name: '模板解析工作台' });
    expect(within(dialog).getByText('该文件不适合直接作为自动套版模板')).toBeInTheDocument();
    expect(within(dialog).getByText('该文件更像公文使用手册或格式说明，不应直接发布为自动套版模板。')).toBeInTheDocument();
    expect(within(dialog).getByText('正在读取渲染预览状态')).toBeInTheDocument();

    resolvePreview(jsonResponse(renderPreviewFixture('RENDERING')));
    expect(await within(dialog).findByText('渲染预览生成中')).toBeInTheDocument();
  });

  it('shows feedback after requesting a pending render preview', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        ]));
      }
      if (url.endsWith('/api/templates?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          { id: 12, templateName: '通知映射模板', documentTypeCode: 'NOTICE', status: 'ACTIVE' },
        ]));
      }
      if (url.endsWith('/api/templates/versions?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          {
            templateVersionId: 31,
            templateId: 12,
            templateName: '通知映射模板',
            documentTypeCode: 'NOTICE',
            versionNo: 1,
            originalFileName: 'notice-template.docx',
          },
        ]));
      }
      if (url.endsWith('/api/templates/versions/31/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/31/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/templates/versions/31/structure-profile')) {
        return Promise.resolve(jsonResponse(mappingStructureProfile()));
      }
      if (url.endsWith('/api/templates/versions/31/document-kind')) {
        return Promise.resolve(jsonResponse(styleDocumentKind()));
      }
      if (url.endsWith('/api/templates/versions/31/render-preview') && init?.method === 'POST') {
        return Promise.resolve(jsonResponse(renderPreviewFixture('PENDING')));
      }
      if (url.endsWith('/api/templates/versions/31/render-preview')) {
        return Promise.resolve(jsonResponse(renderPreviewFixture('PENDING')));
      }
      if (url.endsWith('/api/templates/versions/31/structure-mapping') && !init?.method) {
        return Promise.resolve(jsonResponse(structureMappingFixture('DRAFT', [])));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '模板管理' }));
    await userEvent.click(await screen.findByRole('button', { name: /通知/ }));
    await userEvent.click(await screen.findByRole('button', { name: '解析结果' }));

    const dialog = await screen.findByRole('dialog', { name: '模板解析工作台' });
    await userEvent.click(within(dialog).getByRole('button', { name: '生成预览' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/templates/versions/31/render-preview', expect.objectContaining({
      method: 'POST',
    }));
    expect(await within(dialog).findByText('预览任务已提交，稍后可再次刷新状态。')).toBeInTheDocument();
  });

  it('confirms suggested roles when saving a mapping draft', async () => {
    let savedItems: Array<{
      nodeKey: string;
      role: string;
      slotKey: string;
      status: string;
      source: string;
      confidence: number;
      notes: string;
      sortOrder: number;
    }> = [];
    const suggestedItems = [
      {
        nodeKey: 'title-node',
        role: 'TITLE',
        slotKey: 'title',
        status: 'SUGGESTED',
        source: 'RULE',
        confidence: 0.65,
        notes: '',
        sortOrder: 10,
      },
      {
        nodeKey: 'body-node',
        role: 'BODY',
        slotKey: 'body',
        status: 'SUGGESTED',
        source: 'RULE',
        confidence: 0.65,
        notes: '',
        sortOrder: 20,
      },
    ];
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        ]));
      }
      if (url.endsWith('/api/templates?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          { id: 12, templateName: '通知映射模板', documentTypeCode: 'NOTICE', status: 'ACTIVE' },
        ]));
      }
      if (url.endsWith('/api/templates/versions?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          {
            templateVersionId: 31,
            templateId: 12,
            templateName: '通知映射模板',
            documentTypeCode: 'NOTICE',
            versionNo: 1,
            originalFileName: 'notice-template.docx',
          },
        ]));
      }
      if (url.endsWith('/api/templates/versions/31/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/31/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/templates/versions/31/structure-profile')) {
        return Promise.resolve(jsonResponse(mappingStructureProfile()));
      }
      if (url.endsWith('/api/templates/versions/31/document-kind')) {
        return Promise.resolve(jsonResponse(styleDocumentKind()));
      }
      if (url.endsWith('/api/templates/versions/31/render-preview')) {
        return Promise.resolve(jsonResponse(renderPreviewFixture('PENDING')));
      }
      if (url.endsWith('/api/templates/versions/31/structure-mapping') && !init?.method) {
        return Promise.resolve(jsonResponse(structureMappingFixture('DRAFT', suggestedItems)));
      }
      if (url.endsWith('/api/templates/versions/31/structure-mapping/draft') && init?.method === 'PUT') {
        savedItems = JSON.parse(String(init.body)).items;
        return Promise.resolve(jsonResponse(structureMappingFixture('DRAFT', savedItems)));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '模板管理' }));
    await userEvent.click(await screen.findByRole('button', { name: /通知/ }));
    await userEvent.click(await screen.findByRole('button', { name: '解析结果' }));

    const dialog = await screen.findByRole('dialog', { name: '模板解析工作台' });
    await userEvent.click(within(dialog).getByRole('button', { name: '保存草稿' }));

    expect(savedItems).toEqual(expect.arrayContaining([
      expect.objectContaining({ nodeKey: 'title-node', role: 'TITLE', status: 'CONFIRMED', source: 'USER' }),
      expect.objectContaining({ nodeKey: 'body-node', role: 'BODY', status: 'CONFIRMED', source: 'USER' }),
    ]));
    expect(await within(dialog).findByText('v1 · DRAFT · 2 个已确认')).toBeInTheDocument();
  });

  it('maps a structure node and shows publish blockers from backend', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([
          { code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 },
        ]));
      }
      if (url.endsWith('/api/templates?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          { id: 12, templateName: '通知映射模板', documentTypeCode: 'NOTICE', status: 'ACTIVE' },
        ]));
      }
      if (url.endsWith('/api/templates/versions?documentTypeCode=NOTICE')) {
        return Promise.resolve(jsonResponse([
          {
            templateVersionId: 31,
            templateId: 12,
            templateName: '通知映射模板',
            documentTypeCode: 'NOTICE',
            versionNo: 1,
            originalFileName: 'notice-template.docx',
          },
        ]));
      }
      if (url.endsWith('/api/templates/versions/31/profile')) {
        return Promise.resolve(jsonResponse(templateBodyProfile()));
      }
      if (url.endsWith('/api/templates/versions/31/structure-formatting')) {
        return Promise.resolve(jsonResponse({}));
      }
      if (url.endsWith('/api/templates/versions/31/structure-profile')) {
        return Promise.resolve(jsonResponse(mappingStructureProfile()));
      }
      if (url.endsWith('/api/templates/versions/31/document-kind')) {
        return Promise.resolve(jsonResponse(styleDocumentKind()));
      }
      if (url.endsWith('/api/templates/versions/31/render-preview')) {
        return Promise.resolve(jsonResponse(renderPreviewFixture('PENDING')));
      }
      if (url.endsWith('/api/templates/versions/31/structure-mapping') && !init?.method) {
        return Promise.resolve(jsonResponse(structureMappingFixture('DRAFT', [])));
      }
      if (url.endsWith('/api/templates/versions/31/structure-mapping/draft') && init?.method === 'PUT') {
        return Promise.resolve(jsonResponse(structureMappingFixture('DRAFT', [
          {
            nodeKey: 'title-node',
            role: 'TITLE',
            slotKey: 'title',
            status: 'CONFIRMED',
            source: 'USER',
            confidence: 1,
            notes: '',
            sortOrder: 10,
          },
        ])));
      }
      if (url.endsWith('/api/templates/versions/31/structure-mapping/publish') && init?.method === 'POST') {
        return Promise.resolve(jsonResponse({
          ...structureMappingFixture('DRAFT', [
            {
              nodeKey: 'title-node',
              role: 'TITLE',
              slotKey: 'title',
              status: 'CONFIRMED',
              source: 'USER',
              confidence: 1,
              notes: '',
              sortOrder: 10,
            },
          ]),
          validationItems: [
            {
              severity: 'BLOCKING',
              code: 'REQUIRED_SLOT_MISSING',
              message: '发布映射前必须确认 BODY 槽位。',
              nodeKey: null,
              role: 'BODY',
            },
          ],
        }));
      }
      return Promise.reject(new Error(`Unexpected request: ${url}`));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '模板管理' }));
    await userEvent.click(await screen.findByRole('button', { name: /通知/ }));
    await userEvent.click(await screen.findByRole('button', { name: '解析结果' }));

    const dialog = await screen.findByRole('dialog', { name: '模板解析工作台' });
    await userEvent.selectOptions(within(dialog).getByLabelText('映射角色：关于召开会议的通知'), 'TITLE');
    await userEvent.click(within(dialog).getByRole('button', { name: '保存草稿' }));

    expect(fetchMock).toHaveBeenCalledWith('http://api.test/api/templates/versions/31/structure-mapping/draft', expect.objectContaining({
      method: 'PUT',
      body: expect.stringContaining('"role":"TITLE"'),
    }));

    await userEvent.click(await within(dialog).findByRole('button', { name: '发布映射' }));
    expect(await within(dialog).findByText('映射发布被阻断')).toBeInTheDocument();
    expect(within(dialog).getByText('发布映射前必须确认 BODY 槽位。')).toBeInTheDocument();
  });

  it('configures DeepSeek from system settings', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/document-types')) {
        return Promise.resolve(jsonResponse([{ code: 'NOTICE', name: '通知', status: 'ACTIVE', sortOrder: 1 }]));
      }
      if (url.endsWith('/api/drafts') && init?.method === 'POST') {
        return Promise.resolve(jsonResponse(sampleDraft('AI 配置草稿')));
      }
      if (url.endsWith('/api/drafts/1/materials') || url.endsWith('/api/departments') || url.endsWith('/api/users')) {
        return Promise.resolve(jsonResponse([]));
      }
      if (url.endsWith('/api/ai/settings') && init?.method === 'PUT') {
        return Promise.resolve(jsonResponse({
          ...sampleAiSettings(),
          provider: 'deepseek',
          deepSeekEnabled: true,
          deepSeekApiKeyConfigured: true,
          maskedDeepSeekApiKey: 'sk-1...7890',
        }));
      }
      if (url.endsWith('/api/ai/settings/test')) {
        return Promise.resolve(jsonResponse({
          provider: 'deepseek',
          model: 'deepseek-v4-flash',
          available: true,
          message: 'DeepSeek 连接正常',
          latencyMs: 88,
        }));
      }
      if (url.endsWith('/api/ai/settings')) {
        return Promise.resolve(jsonResponse(sampleAiSettings()));
      }
      return Promise.resolve(jsonResponse([]));
    });
    stubFetch(fetchMock);

    render(<App />);

    await userEvent.click(await screen.findByRole('button', { name: '系统设置' }));
    await userEvent.click(await screen.findByRole('tab', { name: 'AI 配置' }));
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
  } as Response;
}

function errorResponse(errorCode: string, message: string) {
  return {
    ok: false,
    json: async () => ({ success: false, data: null, errorCode, message }),
  } as Response;
}

function docxResponse(fileName: string) {
  return {
    ok: true,
    headers: new Headers({
      'content-disposition': `attachment; filename*=UTF-8''${encodeURIComponent(fileName)}`,
    }),
    blob: async () => new Blob(['docx-bytes'], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    }),
  } as Response;
}

function sampleExportRecord(overrides: Partial<{
  id: number;
  draftId: number | null;
  draftTitle: string | null;
  documentTypeCode: string | null;
  templateId: number | null;
  templateVersionId: number | null;
  templateName: string;
  templateVersion: number;
  fileName: string;
  status: string;
  errorCode: string | null;
  errorMessage: string | null;
  canDownload: boolean;
  canRetry: boolean;
  fileAvailable: boolean;
  createdAt: string;
}> = {}) {
  return {
    id: 1,
    draftId: 1,
    draftTitle: '导出测试草稿',
    documentTypeCode: 'NOTICE',
    templateId: 2,
    templateVersionId: 9,
    templateName: '通知模板',
    templateVersion: 1,
    fileName: '通知模板-v1.docx',
    status: 'SUCCESS',
    errorCode: null,
    errorMessage: null,
    canDownload: true,
    canRetry: false,
    fileAvailable: true,
    createdAt: '2026-05-30T09:30:00Z',
    ...overrides,
  };
}

function stubFetch(fetchMock: ReturnType<typeof vi.fn>) {
  vi.stubGlobal('fetch', (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/api/auth/me')) {
      return Promise.resolve(jsonResponse(sampleAuthUser()));
    }
    if (url.endsWith('/api/auth/csrf')) {
      return Promise.resolve(jsonResponse({ token: 'test-csrf-token' }));
    }
    if (!fetchMock.getMockImplementation() && url.includes('/api/templates/versions')) {
      return Promise.resolve(jsonResponse([]));
    }
    if (!fetchMock.getMockImplementation() && /\/api\/drafts\/\d+\/nodes/.test(url)) {
      return Promise.resolve(jsonResponse([]));
    }
    return fetchMock(input, init);
  });
}

function sampleAuthUser() {
  return {
    id: 1,
    username: 'admin',
    displayName: '系统管理员',
    departmentId: 1,
    departmentName: '总部',
    roles: ['SYSTEM_ADMIN'],
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
    nodes: [],
  };
}

function sampleDraftNodes() {
  const base = {
    draftId: 1,
    structureMappingProfileId: 7,
    parentTemplateNodeKey: null,
    nodeType: 'PARAGRAPH',
    formatOverride: {
      eastAsiaFont: null,
      latinFont: null,
      fontSizePt: null,
      bold: null,
      alignment: null,
      firstLineIndentTwip: null,
      lineSpacingRule: null,
      lineSpacingTwip: null,
      spacingBeforeTwip: null,
      spacingAfterTwip: null,
    },
    createdAt: '2026-05-30T00:00:00Z',
    updatedAt: '2026-05-30T00:00:00Z',
  };
  return [
    {
      ...base,
      id: 101,
      templateNodeKey: 'title-node',
      role: 'TITLE',
      slotKey: 'title',
      title: '标题',
      content: '节点草稿',
      sortOrder: 10,
      status: 'USER_FILLED',
    },
    {
      ...base,
      id: 102,
      templateNodeKey: 'heading-node',
      role: 'BODY_HEADING_LEVEL_1',
      slotKey: 'body',
      title: '正文标题',
      content: '一、节点事项',
      sortOrder: 20,
      status: 'USER_FILLED',
    },
    {
      ...base,
      id: 103,
      templateNodeKey: 'body-node',
      role: 'BODY',
      slotKey: 'body',
      title: '正文',
      content: '节点正文',
      sortOrder: 30,
      status: 'USER_FILLED',
    },
  ];
}

function templateBodyProfile() {
  const formatting = {
    fontFamily: 'FangSong',
    fontSizeHalfPoints: 32,
    bold: null,
    alignment: 'LEFT',
    indentationFirstLine: 420,
    spacingBetween: null,
    spacingBefore: null,
    spacingAfter: null,
  };
  return {
    schemaVersion: 1,
    structures: [
      {
        structureKey: 'body-1',
        structureType: 'BODY',
        label: '正文段落',
        textPreview: '一、会议时间',
        locationType: 'BODY',
        styleId: null,
        styleName: '正文',
        source: 'paragraph',
        formatting,
      },
      {
        structureKey: 'body-2',
        structureType: 'BODY',
        label: '正文段落',
        textPreview: '2026年6月3日上午9:30。',
        locationType: 'BODY',
        styleId: null,
        styleName: '正文',
        source: 'paragraph',
        formatting,
      },
      {
        structureKey: 'body-3',
        structureType: 'BODY',
        label: '正文段落',
        textPreview: '二、会议地点',
        locationType: 'BODY',
        styleId: null,
        styleName: '正文',
        source: 'paragraph',
        formatting,
      },
      {
        structureKey: 'body-4',
        structureType: 'BODY',
        label: '正文段落',
        textPreview: '公司总部三楼第一会议室。',
        locationType: 'BODY',
        styleId: null,
        styleName: '正文',
        source: 'paragraph',
        formatting,
      },
    ],
    styles: [],
    sections: [],
    tables: [],
    media: [],
    templateAnalysis: null,
    placeholders: [],
    validationItems: [],
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

function templateTopColorProfile() {
  return {
    ...templateBodyProfile(),
    structures: [
      {
        structureKey: 'unit-1',
        structureType: 'UNIT',
        label: '发文机关',
        textPreview: '示例单位文件',
        locationType: 'PARAGRAPH',
        styleId: 'GongwenUnit',
        styleName: 'GongwenUnit',
        source: 'STYLE',
        formatting: {
          fontFamily: 'SimSun',
          fontSizeHalfPoints: 36,
          bold: true,
          alignment: 'CENTER',
          indentationFirstLine: 0,
          spacingBetween: null,
          spacingBefore: 0,
          spacingAfter: 160,
          colorHex: 'C00000',
        },
      },
      ...templateBodyProfile().structures,
    ],
  };
}

function manualTemplateProfile() {
  return {
    ...templateBodyProfile(),
    structures: [
      {
        structureKey: 'manual-1',
        structureType: 'UNKNOWN',
        label: '格式说明',
        textPreview: '1.标题：方正小标宋简体（二号）',
        locationType: 'BODY',
        styleId: null,
        styleName: null,
        source: 'paragraph',
        formatting: templateBodyProfile().structures[0].formatting,
      },
    ],
    templateAnalysis: {
      templateKind: 'ORDINARY_DOCUMENT',
      confidence: 0.92,
      documentTypeCode: 'NOTICE',
      inferredFields: [],
      suggestedPlaceholders: [],
      message: '疑似格式说明文件',
      source: 'rules',
      documentKind: 'MANUAL_OR_GUIDE',
      reasonCodes: ['MANUAL_OR_GUIDE_KEYWORD', 'FORMAT_INSTRUCTION_TEXT'],
      recommendedWorkflow: 'BLOCK_AUTO_TEMPLATE',
      blockingWarnings: ['该文件更像公文使用手册或格式说明，不应直接发布为自动套版模板。'],
    },
    placeholders: [],
    validationItems: [],
  };
}

function manualDocumentKind() {
  return {
    documentKind: 'MANUAL_OR_GUIDE',
    templateKind: 'ORDINARY_DOCUMENT',
    confidence: 0.92,
    documentTypeCode: 'NOTICE',
    reasonCodes: ['MANUAL_OR_GUIDE_KEYWORD', 'FORMAT_INSTRUCTION_TEXT'],
    recommendedWorkflow: 'BLOCK_AUTO_TEMPLATE',
    blockingWarnings: ['该文件更像公文使用手册或格式说明，不应直接发布为自动套版模板。'],
    message: '疑似格式说明文件',
    source: 'rules',
  };
}

function manualStructureProfile() {
  return {
    schemaVersion: 1,
    sourceFileHash: 'hash-31',
    extractorVersion: 'document-structure-v1',
    nodes: [
      {
        nodeKey: 'paragraph-0',
        parentKey: null,
        nodeType: 'PARAGRAPH',
        roleSuggestion: 'STATIC_TEXT',
        text: '1.标题：方正小标宋简体（二号）',
        textPreview: '1.标题：方正小标宋简体（二号）',
        orderIndex: 0,
        path: 'PARAGRAPH/0',
        formatting: templateBodyProfile().structures[0].formatting,
        riskCodes: [],
      },
    ],
    styles: [],
    sections: [],
    risks: [],
    createdAt: '2026-05-30T00:00:00Z',
  };
}

function mappingStructureProfile() {
  return {
    schemaVersion: 1,
    sourceFileHash: 'hash-31',
    extractorVersion: 'document-structure-v1',
    nodes: [
      {
        nodeKey: 'title-node',
        parentKey: null,
        nodeType: 'PARAGRAPH',
        roleSuggestion: 'UNKNOWN',
        text: '关于召开会议的通知',
        textPreview: '关于召开会议的通知',
        orderIndex: 10,
        path: 'PARAGRAPH/10',
        formatting: templateBodyProfile().structures[0].formatting,
        riskCodes: [],
      },
      {
        nodeKey: 'date-node',
        parentKey: null,
        nodeType: 'PARAGRAPH',
        roleSuggestion: 'DATE',
        text: '2026年5月30日',
        textPreview: '2026年5月30日',
        orderIndex: 20,
        path: 'PARAGRAPH/20',
        formatting: templateBodyProfile().structures[0].formatting,
        riskCodes: [],
      },
    ],
    styles: [],
    sections: [],
    risks: [],
    createdAt: '2026-05-30T00:00:00Z',
  };
}

function styleDocumentKind() {
  return {
    documentKind: 'STYLE_TEMPLATE',
    templateKind: 'STYLE_TEMPLATE',
    confidence: 0.88,
    documentTypeCode: 'NOTICE',
    reasonCodes: ['OFFICIAL_STRUCTURE'],
    recommendedWorkflow: 'REVIEW_AND_MAP',
    blockingWarnings: [],
    message: '可进入结构映射',
    source: 'rules',
  };
}

function renderPreviewFixture(status: string) {
  return {
    id: 5,
    templateVersionId: 31,
    sourceFileHash: 'hash-31',
    renderer: 'libreoffice',
    rendererVersion: null,
    status,
    pageCount: 0,
    storagePath: null,
    manifest: {
      schemaVersion: 1,
      pdfFileName: null,
      pages: [],
    },
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-05-30T00:00:00Z',
    updatedAt: '2026-05-30T00:00:00Z',
  };
}

function structureMappingFixture(status: string, items: Array<{
  nodeKey: string;
  role: string;
  slotKey: string;
  status: string;
  source: string;
  confidence: number;
  notes: string;
  sortOrder: number;
}>) {
  return {
    mappingProfileId: 7,
    templateVersionId: 31,
    versionNo: 1,
    status,
    items,
    validationItems: [],
    confirmedCount: items.filter((item) => item.status === 'CONFIRMED').length,
    needsReviewCount: 0,
    publishedAt: status === 'PUBLISHED' ? '2026-05-30T00:00:00Z' : null,
    createdAt: '2026-05-30T00:00:00Z',
    updatedAt: '2026-05-30T00:00:00Z',
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
