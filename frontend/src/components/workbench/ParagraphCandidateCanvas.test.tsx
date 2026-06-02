import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AiParagraphCandidate } from '../../draftTypes';
import { ParagraphCandidateCanvas } from './ParagraphCandidateCanvas';

afterEach(() => cleanup());

function candidate(
  id: number,
  status: AiParagraphCandidate['status'],
  candidateText: string,
  options: Partial<AiParagraphCandidate> = {},
): AiParagraphCandidate {
  return {
    id,
    draftId: 1,
    targetNodeId: id + 100,
    targetNodeRole: 'BODY',
    targetNodeTitle: `Section ${id}`,
    outlineTraceId: '11111111-1111-1111-1111-111111111111',
    paragraphTraceId: null,
    sectionIndex: id,
    heading: `Heading ${id}`,
    points: ['Point one', 'Point two'],
    instructionSummary: 'Keep it formal',
    candidateText,
    candidateTextDigest: '',
    status,
    errorCode: '',
    errorMessage: '',
    acceptedAt: null,
    acceptedBy: null,
    createdAt: '2026-06-02T00:00:00Z',
    updatedAt: '2026-06-02T00:00:00Z',
    ...options,
  };
}

describe('ParagraphCandidateCanvas', () => {
  it('renders ready, streaming, and error candidate states', () => {
    render(
      <ParagraphCandidateCanvas
        candidates={[
          candidate(1, 'READY', 'Ready paragraph'),
          candidate(2, 'STREAMING', 'Streaming paragraph'),
          candidate(3, 'ERROR', '', { errorMessage: 'Model timeout' }),
        ]}
        disabled={false}
        isGenerating
        onAccept={vi.fn()}
        onAcceptBatch={vi.fn()}
        onDiscard={vi.fn()}
        onEdit={vi.fn()}
        onGenerateAll={vi.fn()}
        onRetry={vi.fn()}
        onStop={vi.fn()}
      />,
    );

    expect(screen.getByText('Ready')).toBeInTheDocument();
    expect(screen.getByText('Streaming')).toBeInTheDocument();
    expect(screen.getByText('Error')).toBeInTheDocument();
    expect(screen.getByText('Ready paragraph')).toBeInTheDocument();
    expect(screen.getByText('Streaming paragraph')).toBeInTheDocument();
    expect(screen.getByText('Model timeout')).toBeInTheDocument();
  });

  it('calls action callbacks for generation, acceptance, retry, discard, and edit', async () => {
    const onGenerateAll = vi.fn();
    const onStop = vi.fn();
    const onAccept = vi.fn();
    const onAcceptBatch = vi.fn();
    const onRetry = vi.fn();
    const onDiscard = vi.fn();
    const onEdit = vi.fn();
    const ready = candidate(1, 'READY', 'Ready paragraph');
    const edited = candidate(2, 'EDITED', 'Edited paragraph');
    const error = candidate(3, 'ERROR', '', { errorMessage: 'Model timeout' });

    const { rerender } = render(
      <ParagraphCandidateCanvas
        candidates={[ready, edited, error]}
        disabled={false}
        isGenerating={false}
        onAccept={onAccept}
        onAcceptBatch={onAcceptBatch}
        onDiscard={onDiscard}
        onEdit={onEdit}
        onGenerateAll={onGenerateAll}
        onRetry={onRetry}
        onStop={onStop}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Generate all' }));
    await userEvent.click(screen.getByRole('button', { name: 'Batch confirm' }));

    rerender(
      <ParagraphCandidateCanvas
        candidates={[ready, edited, error]}
        disabled={false}
        isGenerating
        onAccept={onAccept}
        onAcceptBatch={onAcceptBatch}
        onDiscard={onDiscard}
        onEdit={onEdit}
        onGenerateAll={onGenerateAll}
        onRetry={onRetry}
        onStop={onStop}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Stop' }));

    const readyCard = screen.getByLabelText('Candidate Heading 1');
    await userEvent.click(within(readyCard).getByRole('button', { name: 'Confirm replace' }));
    fireEvent.change(within(readyCard).getByLabelText('Candidate text for Heading 1'), {
      target: { value: 'Adjusted paragraph' },
    });

    const errorCard = screen.getByLabelText('Candidate Heading 3');
    await userEvent.click(within(errorCard).getByRole('button', { name: 'Retry' }));
    await userEvent.click(within(errorCard).getByRole('button', { name: 'Discard' }));

    expect(onGenerateAll).toHaveBeenCalledTimes(1);
    expect(onStop).toHaveBeenCalledTimes(1);
    expect(onAcceptBatch).toHaveBeenCalledWith([ready, edited]);
    expect(onAccept).toHaveBeenCalledWith(ready);
    expect(onEdit).toHaveBeenCalledWith(ready, 'Adjusted paragraph');
    expect(onRetry).toHaveBeenCalledWith(error);
    expect(onDiscard).toHaveBeenCalledWith(error);
  });
});
