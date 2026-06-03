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

    expect(screen.getByText('可采纳')).toBeInTheDocument();
    expect(screen.getByText('生成中')).toBeInTheDocument();
    expect(screen.getByText('失败')).toBeInTheDocument();
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

    await userEvent.click(screen.getByRole('button', { name: '生成候选' }));
    await userEvent.click(screen.getByRole('button', { name: '批量确认' }));

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
    await userEvent.click(screen.getByRole('button', { name: '停止' }));

    const readyCard = screen.getByLabelText('候选 Heading 1');
    await userEvent.click(within(readyCard).getByRole('button', { name: '确认替换' }));
    fireEvent.change(within(readyCard).getByLabelText('Heading 1 候选正文'), {
      target: { value: 'Adjusted paragraph' },
    });

    const errorCard = screen.getByLabelText('候选 Heading 3');
    await userEvent.click(within(errorCard).getByRole('button', { name: '重试' }));
    await userEvent.click(within(errorCard).getByRole('button', { name: '放弃' }));

    expect(onGenerateAll).toHaveBeenCalledTimes(1);
    expect(onStop).toHaveBeenCalledTimes(1);
    expect(onAcceptBatch).toHaveBeenCalledWith([ready, edited]);
    expect(onAccept).toHaveBeenCalledWith(ready);
    expect(onEdit).toHaveBeenCalledWith(ready, 'Adjusted paragraph');
    expect(onRetry).toHaveBeenCalledWith(error);
    expect(onDiscard).toHaveBeenCalledWith(error);
  });

  it('hides completed candidates and keeps interrupted pending candidates actionable', async () => {
    const onRetry = vi.fn();
    const onDiscard = vi.fn();
    const pending = candidate(4, 'PENDING', '');

    render(
      <ParagraphCandidateCanvas
        candidates={[
          pending,
          candidate(5, 'DISCARDED', 'Discarded paragraph'),
          candidate(6, 'ACCEPTED', 'Accepted paragraph'),
        ]}
        disabled={false}
        isGenerating={false}
        onAccept={vi.fn()}
        onAcceptBatch={vi.fn()}
        onDiscard={onDiscard}
        onEdit={vi.fn()}
        onGenerateAll={vi.fn()}
        onRetry={onRetry}
        onStop={vi.fn()}
      />,
    );

    expect(screen.queryByText('Discarded paragraph')).not.toBeInTheDocument();
    expect(screen.queryByText('Accepted paragraph')).not.toBeInTheDocument();

    const pendingCard = screen.getByLabelText('候选 Heading 4');
    const actionButtons = within(pendingCard).getAllByRole('button');
    await userEvent.click(actionButtons[0]);
    await userEvent.click(actionButtons[1]);

    expect(onRetry).toHaveBeenCalledWith(pending);
    expect(onDiscard).toHaveBeenCalledWith(pending);
  });
});
