import { Check, RotateCcw, Sparkles, Square, Trash2 } from 'lucide-react';
import { useMemo } from 'react';
import type { AiParagraphCandidate, AiParagraphCandidateStatus } from '../../draftTypes';
import { Button, StatusMessage, TextareaField } from '../ui';

export type ParagraphCandidateCanvasProps = {
  candidates: AiParagraphCandidate[];
  disabled: boolean;
  isGenerating: boolean;
  onGenerateAll: () => void;
  onStop: () => void;
  onAccept: (candidate: AiParagraphCandidate) => void;
  onAcceptBatch: (candidates: AiParagraphCandidate[]) => void;
  onRetry: (candidate: AiParagraphCandidate) => void;
  onDiscard: (candidate: AiParagraphCandidate) => void;
  onEdit: (candidate: AiParagraphCandidate, candidateText: string) => void;
};

const ACCEPTABLE_STATUSES = new Set<AiParagraphCandidateStatus>(['READY', 'EDITED']);
const EDITABLE_STATUSES = new Set<AiParagraphCandidateStatus>(['READY', 'EDITED']);
const RETRYABLE_STATUSES = new Set<AiParagraphCandidateStatus>(['ERROR', 'CANCELLED']);
const ACTIVE_GENERATION_STATUSES = new Set<AiParagraphCandidateStatus>(['PENDING', 'RETRYING', 'STREAMING']);
const HIDDEN_STATUSES = new Set<AiParagraphCandidateStatus>(['ACCEPTED', 'DISCARDED']);
const DISCARDABLE_STATUSES = new Set<AiParagraphCandidateStatus>([
  'READY',
  'EDITED',
  'ERROR',
  'CANCELLED',
]);

export function ParagraphCandidateCanvas({
  candidates,
  disabled,
  isGenerating,
  onAccept,
  onAcceptBatch,
  onDiscard,
  onEdit,
  onGenerateAll,
  onRetry,
  onStop,
}: ParagraphCandidateCanvasProps) {
  const visibleCandidates = useMemo(
    () => candidates.filter((candidate) => !HIDDEN_STATUSES.has(candidate.status)),
    [candidates],
  );
  const acceptableCandidates = useMemo(
    () => visibleCandidates.filter((candidate) => ACCEPTABLE_STATUSES.has(candidate.status)),
    [visibleCandidates],
  );
  const hasCandidates = visibleCandidates.length > 0;

  return (
    <section className="paragraph-candidate-canvas" aria-label="正文候选画布">
      <div className="paragraph-candidate-canvas-header">
        <div>
          <div className="outline-title">正文候选</div>
          <div className="panel-kicker">
            {hasCandidates ? `${visibleCandidates.length} 条候选，${acceptableCandidates.length} 条可采纳` : '暂无候选'}
          </div>
        </div>
        <div className="paragraph-candidate-toolbar">
          <Button
            disabled={disabled || isGenerating}
            icon={<Sparkles aria-hidden="true" />}
            onClick={onGenerateAll}
            variant="secondary"
          >
            生成候选
          </Button>
          <Button
            disabled={disabled || !isGenerating}
            icon={<Square aria-hidden="true" />}
            onClick={onStop}
            variant="ghost"
          >
            停止
          </Button>
          <Button
            disabled={disabled || isGenerating || acceptableCandidates.length === 0}
            icon={<Check aria-hidden="true" />}
            onClick={() => onAcceptBatch(acceptableCandidates)}
            variant="secondary"
          >
            批量确认
          </Button>
        </div>
      </div>

      {isGenerating ? (
        <StatusMessage title="正在生成正文候选" tone="info">
          系统正在按提纲逐段生成，确认前不会替换正文。
        </StatusMessage>
      ) : null}

      {!hasCandidates ? (
        <StatusMessage title="暂无正文候选" tone="info">
          先根据当前提纲生成候选，再逐段或批量采纳。
        </StatusMessage>
      ) : (
        <div className="paragraph-candidate-list">
          {visibleCandidates.map((candidate) => (
            <ParagraphCandidateCard
              candidate={candidate}
              disabled={disabled}
              isGenerating={isGenerating}
              key={candidate.id}
              onAccept={onAccept}
              onDiscard={onDiscard}
              onEdit={onEdit}
              onRetry={onRetry}
              onStop={onStop}
            />
          ))}
        </div>
      )}
    </section>
  );
}

type ParagraphCandidateCardProps = {
  candidate: AiParagraphCandidate;
  disabled: boolean;
  isGenerating: boolean;
  onAccept: (candidate: AiParagraphCandidate) => void;
  onRetry: (candidate: AiParagraphCandidate) => void;
  onDiscard: (candidate: AiParagraphCandidate) => void;
  onEdit: (candidate: AiParagraphCandidate, candidateText: string) => void;
  onStop: () => void;
};

function ParagraphCandidateCard({
  candidate,
  disabled,
  isGenerating,
  onAccept,
  onDiscard,
  onEdit,
  onRetry,
  onStop,
}: ParagraphCandidateCardProps) {
  const status = displayStatus(candidate.status);
  const title = candidate.heading || candidate.targetNodeTitle || `候选 ${candidate.sectionIndex + 1}`;
  const isEditable = EDITABLE_STATUSES.has(candidate.status);
  const canAccept = ACCEPTABLE_STATUSES.has(candidate.status);
  const isActiveGeneration = ACTIVE_GENERATION_STATUSES.has(candidate.status);
  const canRetry = RETRYABLE_STATUSES.has(candidate.status) || (!isGenerating && isActiveGeneration);
  const canDiscard = DISCARDABLE_STATUSES.has(candidate.status) || (!isGenerating && isActiveGeneration);
  const isPending = status.key === 'pending' || status.key === 'streaming';

  return (
    <article className={`paragraph-candidate-card paragraph-candidate-card-${status.key}`} aria-label={`候选 ${title}`}>
      <div className="paragraph-candidate-card-header">
        <div className="paragraph-candidate-title-group">
          <div className="paragraph-candidate-heading">{title}</div>
          <div className="paragraph-candidate-meta">
            第 {candidate.sectionIndex + 1} 段
            {candidate.targetNodeRole ? ` - ${displayRole(candidate.targetNodeRole)}` : ''}
          </div>
        </div>
        <span className={`paragraph-candidate-status paragraph-candidate-status-${status.key}`}>
          {status.label}
        </span>
      </div>

      {candidate.points.length > 0 ? (
        <ul className="paragraph-candidate-points">
          {candidate.points.map((point, index) => (
            <li key={`${candidate.id}-${index}`}>{point}</li>
          ))}
        </ul>
      ) : null}

      {candidate.status === 'ERROR' && candidate.errorMessage ? (
        <StatusMessage title={candidate.errorMessage} tone="warning" />
      ) : null}

      {isPending && !candidate.candidateText ? (
        <div className="paragraph-candidate-skeleton" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
      ) : (
        <TextareaField
          className="paragraph-candidate-textarea"
          disabled={disabled || !isEditable}
          label={`${title} 候选正文`}
          onChange={(event) => onEdit(candidate, event.target.value)}
          rows={4}
          value={candidate.candidateText}
        />
      )}

      <div className="paragraph-candidate-actions">
        {canAccept ? (
          <Button
            disabled={disabled}
            icon={<Check aria-hidden="true" />}
            onClick={() => onAccept(candidate)}
            variant="secondary"
          >
            确认替换
          </Button>
        ) : null}
        {isActiveGeneration && isGenerating ? (
          <Button
            disabled={disabled}
            icon={<Square aria-hidden="true" />}
            onClick={onStop}
            variant="ghost"
          >
            停止
          </Button>
        ) : (
          <>
            {canRetry ? (
              <Button
                disabled={disabled}
                icon={<RotateCcw aria-hidden="true" />}
                onClick={() => onRetry(candidate)}
                variant="ghost"
              >
                重试
              </Button>
            ) : null}
            {canDiscard ? (
              <Button
                disabled={disabled}
                icon={<Trash2 aria-hidden="true" />}
                onClick={() => onDiscard(candidate)}
                variant="ghost"
              >
                放弃
              </Button>
            ) : null}
          </>
        )}
      </div>
    </article>
  );
}

function displayStatus(status: AiParagraphCandidateStatus) {
  switch (status) {
    case 'READY':
      return { key: 'ready', label: '可采纳' };
    case 'EDITED':
      return { key: 'edited', label: '已编辑' };
    case 'STREAMING':
    case 'RETRYING':
      return { key: 'streaming', label: '生成中' };
    case 'ERROR':
      return { key: 'error', label: '失败' };
    case 'ACCEPTED':
      return { key: 'accepted', label: '已采纳' };
    case 'DISCARDED':
      return { key: 'discarded', label: '已放弃' };
    case 'CANCELLED':
      return { key: 'cancelled', label: '已停止' };
    case 'PENDING':
    default:
      return { key: 'pending', label: '等待中' };
  }
}

function displayRole(role: string) {
  const roleLabels: Record<string, string> = {
    TITLE: '标题',
    RECIPIENT: '主送',
    BODY: '正文',
    BODY_HEADING_LEVEL_1: '一级标题',
    BODY_HEADING_LEVEL_2: '二级标题',
    BODY_HEADING_LEVEL_3: '三级标题',
    ATTACHMENT_NOTE: '附件',
    SIGNATURE: '落款',
    DATE: '日期',
    STATIC_TEXT: '固定文本',
    IGNORE: '忽略',
  };
  return roleLabels[role] ?? role;
}
