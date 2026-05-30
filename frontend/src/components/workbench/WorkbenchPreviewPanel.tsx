import { RefreshCw } from 'lucide-react';
import { Button, StatusMessage } from '../ui';

export type WorkbenchPreviewState = 'current' | 'outdated' | 'rendering' | 'failed' | 'unavailable';

type WorkbenchPreviewPanelProps = {
  canRefresh: boolean;
  isRefreshing: boolean;
  message: string;
  onRefresh: () => void;
  state: WorkbenchPreviewState;
};

const STATE_COPY: Record<WorkbenchPreviewState, {
  title: string;
  tone: 'info' | 'success' | 'warning' | 'error';
}> = {
  current: { title: '真实预览已是当前版本', tone: 'success' },
  outdated: { title: '真实预览待刷新', tone: 'warning' },
  rendering: { title: '真实预览生成中', tone: 'info' },
  failed: { title: '真实预览不可用', tone: 'error' },
  unavailable: { title: '未选择套版模板', tone: 'info' },
};

export function WorkbenchPreviewPanel({
  canRefresh,
  isRefreshing,
  message,
  onRefresh,
  state,
}: WorkbenchPreviewPanelProps) {
  const copy = STATE_COPY[state];

  return (
    <section aria-label="真实预览状态" className="workbench-preview-panel">
      <div className="workbench-preview-header">
        <div>
          <div className="outline-title">真实预览</div>
          <div className="panel-kicker">用于对照导出的 Word 版式</div>
        </div>
        <Button
          disabled={!canRefresh || isRefreshing}
          icon={<RefreshCw aria-hidden="true" />}
          isLoading={isRefreshing}
          loadingLabel="刷新中"
          onClick={onRefresh}
          variant="secondary"
        >
          刷新预览
        </Button>
      </div>
      <StatusMessage title={copy.title} tone={copy.tone}>
        {message && <span>{message}</span>}
      </StatusMessage>
    </section>
  );
}
