import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { WorkbenchNode } from '../../draftTypes';
import { WorkbenchStructureTree } from './WorkbenchStructureTree';

afterEach(() => cleanup());

function node(
  nodeId: string,
  nodeType: WorkbenchNode['nodeType'],
  label: string,
  sortOrder: number,
  options: Partial<WorkbenchNode> = {},
): WorkbenchNode {
  return {
    nodeId,
    nodeType,
    sortOrder,
    label,
    content: options.content ?? label,
    status: options.status ?? 'USER_FILLED',
    source: options.source ?? 'DRAFT',
    locked: options.locked ?? false,
    editable: options.editable ?? true,
    factNodeType: options.factNodeType ?? 'PARAGRAPH',
    ...options,
  };
}

describe('WorkbenchStructureTree', () => {
  it('groups body detail nodes inside the structure tree instead of repeating them at top level', async () => {
    const title = node('title', 'TITLE', '标题', 10);
    const bodyOne = node('body-1', 'BODY_SECTION', '正文段落', 20, {
      heading: '一、背景',
      content: '说明背景。',
    });
    const bodyTwo = node('body-2', 'BODY_SECTION', '正文段落', 30, {
      heading: '二、措施',
      content: '说明措施。',
    });
    const attachment = node('attachment', 'ATTACHMENT', '附件', 40);
    const onSelectNode = vi.fn();
    const onRemoveBodyNode = vi.fn();
    const { container } = render(
      <WorkbenchStructureTree
        bodySectionNodes={[bodyOne, bodyTwo]}
        canReinitialize={false}
        nodes={[title, bodyOne, bodyTwo, attachment]}
        onReinitialize={vi.fn()}
        onRemoveBodyNode={onRemoveBodyNode}
        onSelectNode={onSelectNode}
        reinitializeMessage=""
        reinitializeStatus="idle"
        selectedNodeId={null}
      />,
    );

    const structureTree = screen.getByLabelText('结构节点树');
    expect(within(structureTree).getByText('正文')).toBeInTheDocument();
    expect(within(screen.getByLabelText('正文段落目录')).getByText('背景')).toBeInTheDocument();
    expect(container.querySelectorAll('.structure-tree-list > .structure-tree-item')).toHaveLength(2);

    await userEvent.click(within(screen.getByLabelText('正文段落目录')).getByText('措施'));
    expect(onSelectNode).toHaveBeenCalledWith('body-2');

    await userEvent.click(screen.getByRole('button', { name: '删除正文结构：背景' }));
    expect(onRemoveBodyNode).toHaveBeenCalledWith(bodyOne);
  });

  it('lets users insert a heading body pair around the selected body node', async () => {
    const bodyOne = node('body-1', 'BODY_SECTION', 'Body section', 20, {
      heading: 'One',
      content: 'Existing body',
      draftNodeId: 101,
    });
    const onInsertBodyStructure = vi.fn();

    render(
      <WorkbenchStructureTree
        bodySectionNodes={[bodyOne]}
        canInsertBodyStructure
        canReinitialize={false}
        insertableRoles={[
          { role: 'BODY_HEADING_LEVEL_1', label: '一级标题 + 正文', createsBodyPair: true },
          { role: 'BODY_HEADING_LEVEL_2', label: '二级标题 + 正文', createsBodyPair: true },
          { role: 'BODY_HEADING_LEVEL_3', label: '三级标题 + 正文', createsBodyPair: true },
          { role: 'BODY', label: '正文段落', createsBodyPair: false },
        ]}
        nodes={[bodyOne]}
        onInsertBodyStructure={onInsertBodyStructure}
        onReinitialize={vi.fn()}
        onRemoveBodyNode={vi.fn()}
        onSelectNode={vi.fn()}
        reinitializeMessage=""
        reinitializeStatus="idle"
        selectedNodeId="body-1"
      />,
    );

    await userEvent.click(screen.getByText('结构维护'));
    const insertPanel = screen.getByLabelText('新增正文结构');
    await userEvent.selectOptions(within(insertPanel).getByLabelText('插入位置'), 'BEFORE');
    await userEvent.selectOptions(within(insertPanel).getByLabelText('新增结构'), 'BODY_HEADING_LEVEL_2');
    await userEvent.click(within(insertPanel).getByRole('button', { name: '新增结构' }));

    expect(onInsertBodyStructure).toHaveBeenCalledWith({
      role: 'BODY_HEADING_LEVEL_2',
      anchorNodeId: 101,
      position: 'BEFORE',
    });
  });
});
