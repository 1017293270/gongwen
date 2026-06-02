import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiRequestError, downloadExportRecord, listTemplates } from './api';

describe('api client error messages', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it('uses backend user-facing error messages', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: false,
      data: null,
      errorCode: 'TEMPLATE_IN_USE_BY_DRAFTS',
      message: '模板已被草稿使用，不能直接删除；请先删除相关草稿或更换草稿模板后再删除模板',
    }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    })));

    await expect(listTemplates('NOTICE')).rejects.toMatchObject({
      errorCode: 'TEMPLATE_IN_USE_BY_DRAFTS',
      message: '模板已被草稿使用，不能直接删除；请先删除相关草稿或更换草稿模板后再删除模板',
      status: 400,
    });
  });

  it('turns non-json server failures into a user-facing message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>error</html>', {
      status: 500,
      headers: { 'Content-Type': 'text/html' },
    })));

    await expect(listTemplates('NOTICE')).rejects.toThrow(
      new ApiRequestError('服务返回异常，请稍后重试', 'INVALID_SERVER_RESPONSE', 500),
    );
  });

  it('turns network failures into a user-facing message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(listTemplates('NOTICE')).rejects.toThrow(
      new ApiRequestError('无法连接后端服务，请确认服务已启动后重试', 'NETWORK_ERROR', 0),
    );
  });

  it('reads UTF-8 export file names from content disposition', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(new Blob(['docx']), {
      status: 200,
      headers: {
        'content-disposition': `attachment; filename*=UTF-8''${encodeURIComponent('通知模板-v4.docx')}`,
      },
    })));

    await expect(downloadExportRecord(7)).resolves.toMatchObject({
      fileName: '通知模板-v4.docx',
    });
  });

  it('uses a readable fallback export file name when content disposition is hidden', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(new Blob(['docx']), {
      status: 200,
    })));

    await expect(downloadExportRecord(7)).resolves.toMatchObject({
      fileName: '公文导出.docx',
    });
  });
});
