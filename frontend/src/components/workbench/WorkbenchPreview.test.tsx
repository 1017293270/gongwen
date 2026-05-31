import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { WorkbenchNode } from '../../draftTypes';
import { WorkbenchPreview } from './WorkbenchPreview';

function node(
  nodeId: string,
  nodeType: WorkbenchNode['nodeType'],
  content: string,
  sortOrder: number,
  options: Partial<WorkbenchNode> = {},
): WorkbenchNode {
  return {
    nodeId,
    nodeType,
    sortOrder,
    label: content,
    content,
    source: 'DRAFT',
    locked: false,
    editable: true,
    ...options,
  };
}

describe('WorkbenchPreview', () => {
  it('renders persisted nodes in source order including locked static nodes', () => {
    const title = node('title', 'TITLE', '在全区重点工作推进会上的讲话', 10);
    const subtitle = node('subtitle', 'STATIC_TEMPLATE_TEXT', '政务会议讲话稿测试样例', 20, {
      locked: true,
      editable: false,
    });
    const date = node('date', 'DATE', '2026年5月30日', 30);
    const recipient = node('recipient', 'RECIPIENT', '同志们：', 40);
    const body = node('body', 'BODY_SECTION', '今天我们召开这次重点工作推进会。', 50);

    render(
      <WorkbenchPreview
        attachment=""
        attachmentNode={null}
        bodySectionNodes={[body]}
        bodyStyleForNode={() => ({})}
        date={date.content}
        dateNode={date}
        nodes={[title, subtitle, date, recipient, body]}
        onRemoveBodyNode={vi.fn()}
        onSelectNode={vi.fn()}
        onUpdateAttachment={vi.fn()}
        onUpdateBodyContent={vi.fn()}
        onUpdateBodyHeading={vi.fn()}
        onUpdateDate={vi.fn()}
        onUpdateRecipient={vi.fn()}
        onUpdateSignature={vi.fn()}
        onUpdateTitle={vi.fn()}
        recipient={recipient.content}
        recipientNode={recipient}
        registerNodeRef={vi.fn()}
        selectedNodeId={null}
        signature=""
        signatureNode={null}
        syncParagraphEditorHeight={vi.fn()}
        title={title.content}
        titleNode={title}
      />,
    );

    const preview = screen.getByLabelText('公文预览');
    expect(within(preview).getByText('政务会议讲话稿测试样例')).toBeInTheDocument();

    const orderedText = Array.from(preview.querySelectorAll('[data-preview-node]')).map((element) =>
      element.textContent?.replace('选择正文结构：', '').replace(/\s+/g, ' ').trim(),
    );
    expect(orderedText).toEqual([
      '在全区重点工作推进会上的讲话',
      '政务会议讲话稿测试样例',
      '2026年5月30日',
      '同志们：',
      '今天我们召开这次重点工作推进会。',
    ]);
  });
});
