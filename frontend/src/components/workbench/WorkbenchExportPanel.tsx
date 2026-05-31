import { FileDown } from 'lucide-react';
import type { DocumentRenderPreview } from '../../draftTypes';
import { Button, StatusMessage } from '../ui';
import { WorkbenchPreviewPanel, type WorkbenchPreviewState } from './WorkbenchPreviewPanel';

export type WorkbenchExportStatus = 'idle' | 'exporting' | 'success' | 'blocked' | 'error';
export type WorkbenchPreviewRequestStatus = 'idle' | 'requesting' | 'error';

type WorkbenchExportPanelProps = {
  exportError: string;
  exportStatus: WorkbenchExportStatus;
  hasDraft: boolean;
  isWorkbenchLoading: boolean;
  onExportWord: () => void;
  onRefreshPreview: () => void;
  preview: DocumentRenderPreview | null;
  previewMessage: string;
  previewOutdated: boolean;
  previewStatus: WorkbenchPreviewRequestStatus;
  templateVersionId: number | null;
};

export function WorkbenchExportPanel({
  exportError,
  exportStatus,
  hasDraft,
  isWorkbenchLoading,
  onExportWord,
  onRefreshPreview,
  preview,
  previewMessage,
  previewOutdated,
  previewStatus,
  templateVersionId,
}: WorkbenchExportPanelProps) {
  const state = workbenchPreviewStateFor(templateVersionId, previewOutdated, preview, previewStatus);
  const message = workbenchPreviewMessageFor(state, preview, previewMessage);

  return (
    <div className="export-action" aria-label="Word 导出">
      <WorkbenchPreviewPanel
        canRefresh={Boolean(templateVersionId)}
        isRefreshing={previewStatus === 'requesting'}
        message={message}
        onRefresh={onRefreshPreview}
        state={state}
      />
      <div>
        <div className="outline-title">Word 导出</div>
        <div className="panel-kicker">
          {templateVersionId ? '导出前会自动保存当前草稿' : '请先选择套版模板'}
        </div>
      </div>
      <Button
        disabled={!hasDraft || !templateVersionId || exportStatus === 'exporting' || isWorkbenchLoading}
        icon={<FileDown aria-hidden="true" />}
        isLoading={exportStatus === 'exporting'}
        loadingLabel="正在导出"
        onClick={onExportWord}
        variant="secondary"
      >
        导出当前草稿
      </Button>
      {exportError && <StatusMessage title={exportError} tone="warning" />}
      {exportStatus === 'success' && !exportError && (
        <StatusMessage title="已生成 Word 文件，可打开和模板对比。" tone="success" />
      )}
    </div>
  );
}

export function renderPreviewResultMessage(preview: DocumentRenderPreview) {
  if (preview.status === 'READY') {
    return '真实预览已生成';
  }
  if (preview.status === 'FAILED' || preview.status === 'UNSUPPORTED') {
    return preview.errorMessage ?? '真实预览生成失败';
  }
  return '真实预览任务已提交';
}

function workbenchPreviewStateFor(
  templateVersionId: number | null,
  isOutdated: boolean,
  preview: DocumentRenderPreview | null,
  requestStatus: WorkbenchPreviewRequestStatus,
): WorkbenchPreviewState {
  if (!templateVersionId) {
    return 'unavailable';
  }
  if (requestStatus === 'requesting') {
    return 'rendering';
  }
  if (isOutdated) {
    return 'outdated';
  }
  if (requestStatus === 'error') {
    return 'failed';
  }
  if (preview?.status === 'FAILED' || preview?.status === 'UNSUPPORTED') {
    return 'failed';
  }
  if (preview?.status === 'PENDING' || preview?.status === 'RENDERING') {
    return 'rendering';
  }
  return 'current';
}

function workbenchPreviewMessageFor(
  state: WorkbenchPreviewState,
  preview: DocumentRenderPreview | null,
  message: string,
) {
  if (message) {
    return message;
  }
  if (state === 'current') {
    return preview?.updatedAt
      ? `最后刷新：${new Date(preview.updatedAt).toLocaleString('zh-CN')}`
      : '当前草稿未发现待刷新的格式或正文修改';
  }
  if (state === 'outdated') {
    return '正文或格式已变化，建议刷新真实预览后再导出对比。';
  }
  if (state === 'rendering') {
    return '预览任务已提交，稍后可再次刷新状态。';
  }
  if (state === 'failed') {
    return preview?.errorMessage ?? '渲染预览暂不可用。';
  }
  return '选择套版模板后可刷新真实预览。';
}
