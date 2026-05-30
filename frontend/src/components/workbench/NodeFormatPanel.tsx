import { RotateCcw, Save } from 'lucide-react';
import { useEffect, useState } from 'react';
import type { DraftNodeFormatOverride } from '../../draftTypes';
import { Button, SelectField, StatusMessage, TextField } from '../ui';

export type NodeFormatPanelStatus = 'idle' | 'saving' | 'restoring' | 'saved' | 'restored' | 'error';

type NodeFormatPanelProps = {
  disabled: boolean;
  error: string;
  formatOverride: DraftNodeFormatOverride | null;
  nodeLabel: string;
  onRestore: () => void;
  onSave: (formatOverride: DraftNodeFormatOverride) => void;
  previewOutdated: boolean;
  status: NodeFormatPanelStatus;
};

const EMPTY_FORMAT_OVERRIDE: DraftNodeFormatOverride = {
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
};

export function NodeFormatPanel({
  disabled,
  error,
  formatOverride,
  nodeLabel,
  onRestore,
  onSave,
  previewOutdated,
  status,
}: NodeFormatPanelProps) {
  const [draft, setDraft] = useState<DraftNodeFormatOverride>(() => normalizeOverride(formatOverride));

  useEffect(() => {
    setDraft(normalizeOverride(formatOverride));
  }, [formatOverride]);

  const isBusy = status === 'saving' || status === 'restoring';

  return (
    <div className="node-format-panel" aria-label="节点格式">
      <div className="local-operation-header">
        <div>
          <div className="outline-title">节点格式</div>
          <div className="panel-kicker">{disabled ? '请选择可编辑结构节点' : `当前节点：${nodeLabel}`}</div>
        </div>
      </div>
      <div className="node-format-grid">
        <TextField
          disabled={disabled || isBusy}
          label="中文字体"
          onChange={(event) => setDraft((current) => ({ ...current, eastAsiaFont: emptyToNull(event.target.value) }))}
          value={draft.eastAsiaFont ?? ''}
        />
        <TextField
          disabled={disabled || isBusy}
          label="西文字体"
          onChange={(event) => setDraft((current) => ({ ...current, latinFont: emptyToNull(event.target.value) }))}
          value={draft.latinFont ?? ''}
        />
        <TextField
          disabled={disabled || isBusy}
          label="字号"
          min={1}
          onChange={(event) => setDraft((current) => ({ ...current, fontSizePt: numberOrNull(event.target.value) }))}
          type="number"
          value={numberValue(draft.fontSizePt)}
        />
        <SelectField
          disabled={disabled || isBusy}
          label="对齐方式"
          onChange={(event) => setDraft((current) => ({ ...current, alignment: emptyToNull(event.target.value) }))}
          value={draft.alignment ?? ''}
        >
          <option value="">模板默认</option>
          <option value="LEFT">左对齐</option>
          <option value="CENTER">居中</option>
          <option value="RIGHT">右对齐</option>
          <option value="BOTH">两端对齐</option>
        </SelectField>
        <SelectField
          disabled={disabled || isBusy}
          label="行距规则"
          onChange={(event) => setDraft((current) => ({ ...current, lineSpacingRule: emptyToNull(event.target.value) }))}
          value={draft.lineSpacingRule ?? ''}
        >
          <option value="">模板默认</option>
          <option value="AUTO">自动</option>
          <option value="EXACT">固定</option>
          <option value="AT_LEAST">最小值</option>
        </SelectField>
        <TextField
          disabled={disabled || isBusy}
          label="行距"
          onChange={(event) => setDraft((current) => ({ ...current, lineSpacingTwip: numberOrNull(event.target.value) }))}
          type="number"
          value={numberValue(draft.lineSpacingTwip)}
        />
        <TextField
          disabled={disabled || isBusy}
          label="首行缩进"
          onChange={(event) => setDraft((current) => ({ ...current, firstLineIndentTwip: numberOrNull(event.target.value) }))}
          type="number"
          value={numberValue(draft.firstLineIndentTwip)}
        />
        <TextField
          disabled={disabled || isBusy}
          label="段前"
          onChange={(event) => setDraft((current) => ({ ...current, spacingBeforeTwip: numberOrNull(event.target.value) }))}
          type="number"
          value={numberValue(draft.spacingBeforeTwip)}
        />
        <TextField
          disabled={disabled || isBusy}
          label="段后"
          onChange={(event) => setDraft((current) => ({ ...current, spacingAfterTwip: numberOrNull(event.target.value) }))}
          type="number"
          value={numberValue(draft.spacingAfterTwip)}
        />
        <label className="node-format-checkbox">
          <input
            checked={draft.bold === true}
            disabled={disabled || isBusy}
            onChange={(event) => setDraft((current) => ({ ...current, bold: event.target.checked ? true : null }))}
            type="checkbox"
          />
          <span>加粗</span>
        </label>
      </div>
      <div className="node-format-actions">
        <Button
          disabled={disabled || isBusy}
          icon={<Save aria-hidden="true" />}
          isLoading={status === 'saving'}
          loadingLabel="正在保存格式"
          onClick={() => onSave(draft)}
          variant="secondary"
        >
          保存格式
        </Button>
        <Button
          disabled={disabled || isBusy}
          icon={<RotateCcw aria-hidden="true" />}
          isLoading={status === 'restoring'}
          loadingLabel="正在恢复"
          onClick={onRestore}
          variant="ghost"
        >
          恢复模板默认
        </Button>
      </div>
      {previewOutdated && <StatusMessage title="真实预览待刷新" tone="warning" />}
      {status === 'saved' && <StatusMessage title="节点格式已保存" tone="success" />}
      {status === 'restored' && <StatusMessage title="已恢复模板默认格式" tone="success" />}
      {error && <StatusMessage title={error} tone="warning" />}
    </div>
  );
}

function normalizeOverride(override: DraftNodeFormatOverride | null): DraftNodeFormatOverride {
  return { ...EMPTY_FORMAT_OVERRIDE, ...(override ?? {}) };
}

function emptyToNull(value: string) {
  const normalized = value.trim();
  return normalized ? normalized : null;
}

function numberOrNull(value: string) {
  if (value.trim() === '') {
    return null;
  }
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function numberValue(value: number | null) {
  return value == null ? '' : String(value);
}
