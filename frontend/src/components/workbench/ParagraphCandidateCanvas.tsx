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
const DISCARDABLE_STATUSES = new Set<AiParagraphCandidateStatus>([
  'PENDING',
  'RETRYING',
  'STREAMING',
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
  const acceptableCandidates = useMemo(
    () => candidates.filter((candidate) => ACCEPTABLE_STATUSES.has(candidate.status)),
    [candidates],
  );
  const hasCandidates = candidates.length > 0;

  return (
    <section className="paragraph-candidate-canvas" aria-label="Paragraph candidate canvas">
      <div className="paragraph-candidate-canvas-header">
        <div>
          <div className="outline-title">Paragraph candidates</div>
          <div className="panel-kicker">
            {hasCandidates ? `${candidates.length} candidates - ${acceptableCandidates.length} ready` : 'No candidates yet'}
          </div>
        </div>
        <div className="paragraph-candidate-toolbar">
          <Button
            disabled={disabled || isGenerating}
            icon={<Sparkles aria-hidden="true" />}
            onClick={onGenerateAll}
            variant="secondary"
          >
            Generate all
          </Button>
          <Button
            disabled={disabled || !isGenerating}
            icon={<Square aria-hidden="true" />}
            onClick={onStop}
            variant="ghost"
          >
            Stop
          </Button>
          <Button
            disabled={disabled || isGenerating || acceptableCandidates.length === 0}
            icon={<Check aria-hidden="true" />}
            onClick={() => onAcceptBatch(acceptableCandidates)}
            variant="secondary"
          >
            Batch confirm
          </Button>
        </div>
      </div>

      {isGenerating ? (
        <StatusMessage title="Generating candidates" tone="info">
          Output is being prepared section by section. You can stop the current job.
        </StatusMessage>
      ) : null}

      {!hasCandidates ? (
        <StatusMessage title="No paragraph candidates" tone="info">
          Generate candidates from the current outline before confirming replacements.
        </StatusMessage>
      ) : (
        <div className="paragraph-candidate-list">
          {candidates.map((candidate) => (
            <ParagraphCandidateCard
              candidate={candidate}
              disabled={disabled}
              key={candidate.id}
              onAccept={onAccept}
              onDiscard={onDiscard}
              onEdit={onEdit}
              onRetry={onRetry}
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
  onAccept: (candidate: AiParagraphCandidate) => void;
  onRetry: (candidate: AiParagraphCandidate) => void;
  onDiscard: (candidate: AiParagraphCandidate) => void;
  onEdit: (candidate: AiParagraphCandidate, candidateText: string) => void;
};

function ParagraphCandidateCard({
  candidate,
  disabled,
  onAccept,
  onDiscard,
  onEdit,
  onRetry,
}: ParagraphCandidateCardProps) {
  const status = displayStatus(candidate.status);
  const title = candidate.heading || candidate.targetNodeTitle || `Candidate ${candidate.sectionIndex + 1}`;
  const isEditable = EDITABLE_STATUSES.has(candidate.status);
  const canAccept = ACCEPTABLE_STATUSES.has(candidate.status);
  const canRetry = RETRYABLE_STATUSES.has(candidate.status);
  const canDiscard = DISCARDABLE_STATUSES.has(candidate.status);
  const isPending = status.key === 'pending' || status.key === 'streaming';

  return (
    <article className={`paragraph-candidate-card paragraph-candidate-card-${status.key}`} aria-label={`Candidate ${title}`}>
      <div className="paragraph-candidate-card-header">
        <div className="paragraph-candidate-title-group">
          <div className="paragraph-candidate-heading">{title}</div>
          <div className="paragraph-candidate-meta">
            Section {candidate.sectionIndex + 1}
            {candidate.targetNodeRole ? ` - ${candidate.targetNodeRole}` : ''}
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
          label={`Candidate text for ${title}`}
          onChange={(event) => onEdit(candidate, event.target.value)}
          rows={4}
          value={candidate.candidateText}
        />
      )}

      <div className="paragraph-candidate-actions">
        <Button
          disabled={disabled || !canAccept}
          icon={<Check aria-hidden="true" />}
          onClick={() => onAccept(candidate)}
          variant="secondary"
        >
          Confirm replace
        </Button>
        <Button
          disabled={disabled || !canRetry}
          icon={<RotateCcw aria-hidden="true" />}
          onClick={() => onRetry(candidate)}
          variant="ghost"
        >
          Retry
        </Button>
        <Button
          disabled={disabled || !canDiscard}
          icon={<Trash2 aria-hidden="true" />}
          onClick={() => onDiscard(candidate)}
          variant="ghost"
        >
          Discard
        </Button>
      </div>
    </article>
  );
}

function displayStatus(status: AiParagraphCandidateStatus) {
  switch (status) {
    case 'READY':
      return { key: 'ready', label: 'Ready' };
    case 'EDITED':
      return { key: 'edited', label: 'Edited' };
    case 'STREAMING':
    case 'RETRYING':
      return { key: 'streaming', label: 'Streaming' };
    case 'ERROR':
      return { key: 'error', label: 'Error' };
    case 'ACCEPTED':
      return { key: 'accepted', label: 'Accepted' };
    case 'DISCARDED':
      return { key: 'discarded', label: 'Discarded' };
    case 'CANCELLED':
      return { key: 'cancelled', label: 'Cancelled' };
    case 'PENDING':
    default:
      return { key: 'pending', label: 'Pending' };
  }
}
