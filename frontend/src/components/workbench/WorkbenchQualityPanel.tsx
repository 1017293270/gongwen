import { CheckCircle2 } from 'lucide-react';
import type { QualityCheckResult } from '../../draftTypes';
import { Button, StatusMessage } from '../ui';

export type WorkbenchQualityCheckStatus = 'idle' | 'checking' | 'success' | 'error';

type WorkbenchQualityPanelProps = {
  disabled: boolean;
  error: string;
  onOpenResult: () => void;
  onRun: () => void;
  result: QualityCheckResult | null;
  status: WorkbenchQualityCheckStatus;
};

export function WorkbenchQualityPanel({
  disabled,
  error,
  onOpenResult,
  onRun,
  result,
  status,
}: WorkbenchQualityPanelProps) {
  return (
    <div className="quality-check" aria-label="基础质检">
      <div className="quality-check-header">
        <div>
          <div className="outline-title">基础质检</div>
          <div className="panel-kicker">
            {result ? qualitySummary(result) : '规则检查 + AI 表达建议'}
          </div>
        </div>
        {result && (
          <span className={`quality-badge ${result.status.toLowerCase()}`}>
            {qualityStatusLabel(result.status)}
          </span>
        )}
      </div>
      <Button
        disabled={disabled}
        icon={<CheckCircle2 aria-hidden="true" />}
        isLoading={status === 'checking'}
        loadingLabel="正在质检"
        onClick={onRun}
        variant="secondary"
      >
        {status === 'error' ? '重试质检' : '运行质检'}
      </Button>
      {status === 'error' && <StatusMessage title={error} tone="warning" />}
      {result && (
        <div className="ai-task-summary" aria-label="质检摘要">
          <span>{qualitySummary(result)}</span>
          <button className="summary-link" onClick={onOpenResult} type="button">
            查看结果
          </button>
        </div>
      )}
    </div>
  );
}

export function qualitySummary(result: QualityCheckResult) {
  const errorCount = result.items.filter((item) => item.severity === 'ERROR').length;
  const warningCount = result.items.filter((item) => item.severity === 'WARNING').length;
  const infoCount = result.items.filter((item) => item.severity === 'INFO').length;
  return `错误 ${errorCount} · 警告 ${warningCount} · 建议 ${infoCount}`;
}

function qualityStatusLabel(status: QualityCheckResult['status']) {
  return status === 'PASS' ? '通过' : status === 'WARNING' ? '有建议' : '需处理';
}
