import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

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
    expect(progress).toHaveAttribute('aria-valuenow', '0');
    expect(within(dialog).getByText('0%')).toBeInTheDocument();
    await vi.advanceTimersByTimeAsync(1600);
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
    expect(within(screen.getByLabelText('公文预览')).getByText('一、主要事项：说明安排；明确分工。')).toBeInTheDocument();
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
    expect(within(screen.getByLabelText('公文预览')).getByText('二、工作要求：落实责任。')).toBeInTheDocument();
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
    stubFetch(fetchMock);

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
  } as Response;
}

function errorResponse(errorCode: string, message: string) {
  return {
    ok: false,
    json: async () => ({ success: false, data: null, errorCode, message }),
  } as Response;
}

function stubFetch(fetchMock: ReturnType<typeof vi.fn>) {
  vi.stubGlobal('fetch', (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).includes('/api/templates/versions')) {
      return Promise.resolve(jsonResponse([]));
    }
    return fetchMock(input, init);
  });
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
