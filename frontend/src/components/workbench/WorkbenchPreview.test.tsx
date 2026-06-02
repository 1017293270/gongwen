import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { DocumentRenderPreview, WorkbenchNode } from '../../draftTypes';
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

function readyPreview(): DocumentRenderPreview {
  return {
    id: 9,
    templateVersionId: 31,
    sourceFileHash: 'hash',
    renderer: 'libreoffice',
    rendererVersion: 'LibreOffice',
    status: 'READY',
    pageCount: 1,
    storagePath: 'storage/previews/template-version-31/preview-9',
    manifest: {
      schemaVersion: 1,
      pdfFileName: 'preview.pdf',
      pages: [{
        pageNumber: 1,
        fileName: 'page-001.png',
        contentType: 'image/png',
        widthPixels: 1240,
        heightPixels: 1754,
        dpi: 150,
      }],
    },
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:01Z',
  };
}

function pendingPreview(): DocumentRenderPreview {
  return {
    ...readyPreview(),
    id: null,
    status: 'PENDING',
    pageCount: 0,
    storagePath: null,
    manifest: {
      schemaVersion: 1,
      pdfFileName: null,
      pages: [],
    },
    rendererVersion: null,
    updatedAt: null,
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

  it('shows rendered preview first and keeps structured editing available', () => {
    const title = node('title', 'TITLE', 'Rendered notice title', 10);
    const body = node('body', 'BODY_SECTION', 'Editable body text', 20);

    const { container } = render(
      <WorkbenchPreview
        attachment=""
        attachmentNode={null}
        bodySectionNodes={[body]}
        bodyStyleForNode={() => ({})}
        date=""
        dateNode={null}
        nodes={[title, body]}
        onRemoveBodyNode={vi.fn()}
        onSelectNode={vi.fn()}
        onUpdateAttachment={vi.fn()}
        onUpdateBodyContent={vi.fn()}
        onUpdateBodyHeading={vi.fn()}
        onUpdateDate={vi.fn()}
        onUpdateRecipient={vi.fn()}
        onUpdateSignature={vi.fn()}
        onUpdateTitle={vi.fn()}
        recipient=""
        recipientNode={null}
        registerNodeRef={vi.fn()}
        renderPreview={readyPreview()}
        selectedNodeId={null}
        signature=""
        signatureNode={null}
        syncParagraphEditorHeight={vi.fn()}
        title={title.content}
        titleNode={title}
      />,
    );

    const view = within(container);
    expect(view.getByRole('img', { name: '真实预览第 1 页' })).toHaveAttribute(
      'src',
      expect.stringContaining('/api/render-previews/9/pages/1'),
    );
    expect(view.queryByText('Editable body text')).not.toBeInTheDocument();

    fireEvent.click(view.getByRole('button', { name: '结构编辑' }));

    expect(view.getByText('Editable body text')).toBeInTheDocument();
  });

  it('edits manually mapped metadata nodes that render as static text', () => {
    const onUpdateNodeContent = vi.fn();
    const organ = node('organ', 'STATIC_TEMPLATE_TEXT', '示例办公室', 10, {
      draftNodeId: 12,
      label: '发文机关',
      role: 'ISSUING_ORGAN',
      locked: false,
      editable: true,
    });

    render(
      <WorkbenchPreview
        attachment=""
        attachmentNode={null}
        bodySectionNodes={[]}
        bodyStyleForNode={() => ({})}
        date=""
        dateNode={null}
        nodes={[organ]}
        onRemoveBodyNode={vi.fn()}
        onSelectNode={vi.fn()}
        onUpdateAttachment={vi.fn()}
        onUpdateBodyContent={vi.fn()}
        onUpdateBodyHeading={vi.fn()}
        onUpdateDate={vi.fn()}
        onUpdateNodeContent={onUpdateNodeContent}
        onUpdateRecipient={vi.fn()}
        onUpdateSignature={vi.fn()}
        onUpdateTitle={vi.fn()}
        recipient=""
        recipientNode={null}
        registerNodeRef={vi.fn()}
        selectedNodeId="organ"
        signature=""
        signatureNode={null}
        syncParagraphEditorHeight={vi.fn()}
        title=""
        titleNode={null}
      />,
    );

    const editor = screen.getByRole('textbox', { name: '编辑节点：发文机关' });
    fireEvent.change(editor, { target: { value: '修改后的办公室' } });

    expect(onUpdateNodeContent).toHaveBeenCalledWith(organ, '修改后的办公室');
  });

  it('emits heading changes for selected body sections', () => {
    const onUpdateBodyHeading = vi.fn();
    const body = node('body', 'BODY_SECTION', '', 20, {
      draftNodeId: 102,
      heading: '',
      headingDraftNodeId: 101,
      label: '正文',
    });

    render(
      <WorkbenchPreview
        attachment=""
        attachmentNode={null}
        bodySectionNodes={[body]}
        bodyStyleForNode={() => ({})}
        date=""
        dateNode={null}
        nodes={[body]}
        onRemoveBodyNode={vi.fn()}
        onSelectNode={vi.fn()}
        onUpdateAttachment={vi.fn()}
        onUpdateBodyContent={vi.fn()}
        onUpdateBodyHeading={onUpdateBodyHeading}
        onUpdateDate={vi.fn()}
        onUpdateRecipient={vi.fn()}
        onUpdateSignature={vi.fn()}
        onUpdateTitle={vi.fn()}
        recipient=""
        recipientNode={null}
        registerNodeRef={vi.fn()}
        selectedNodeId="body"
        signature=""
        signatureNode={null}
        syncParagraphEditorHeight={vi.fn()}
        title=""
        titleNode={null}
      />,
    );

    fireEvent.change(screen.getByRole('textbox', { name: /编辑标题/ }), {
      target: { value: '一、新增标题' },
    });

    expect(onUpdateBodyHeading).toHaveBeenCalledWith(body, '一、新增标题');
  });

  it('does not switch away from structured editing when a refreshed rendered preview becomes ready', () => {
    const title = node('title', 'TITLE', 'Rendered notice title', 10);
    const body = node('body', 'BODY_SECTION', 'Editable body text', 20);
    const { container, rerender } = render(
      <WorkbenchPreview
        attachment=""
        attachmentNode={null}
        bodySectionNodes={[body]}
        bodyStyleForNode={() => ({})}
        date=""
        dateNode={null}
        nodes={[title, body]}
        onRemoveBodyNode={vi.fn()}
        onSelectNode={vi.fn()}
        onUpdateAttachment={vi.fn()}
        onUpdateBodyContent={vi.fn()}
        onUpdateBodyHeading={vi.fn()}
        onUpdateDate={vi.fn()}
        onUpdateRecipient={vi.fn()}
        onUpdateSignature={vi.fn()}
        onUpdateTitle={vi.fn()}
        recipient=""
        recipientNode={null}
        registerNodeRef={vi.fn()}
        renderPreview={readyPreview()}
        selectedNodeId={null}
        signature=""
        signatureNode={null}
        syncParagraphEditorHeight={vi.fn()}
        title={title.content}
        titleNode={title}
      />,
    );
    const view = within(container);
    fireEvent.click(view.getByRole('button', { name: '结构编辑' }));

    rerender(
      <WorkbenchPreview
        attachment=""
        attachmentNode={null}
        bodySectionNodes={[body]}
        bodyStyleForNode={() => ({})}
        date=""
        dateNode={null}
        nodes={[title, body]}
        onRemoveBodyNode={vi.fn()}
        onSelectNode={vi.fn()}
        onUpdateAttachment={vi.fn()}
        onUpdateBodyContent={vi.fn()}
        onUpdateBodyHeading={vi.fn()}
        onUpdateDate={vi.fn()}
        onUpdateRecipient={vi.fn()}
        onUpdateSignature={vi.fn()}
        onUpdateTitle={vi.fn()}
        recipient=""
        recipientNode={null}
        registerNodeRef={vi.fn()}
        renderPreview={{
          ...readyPreview(),
          id: 10,
          updatedAt: '2026-06-01T00:01:00Z',
        }}
        selectedNodeId={null}
        signature=""
        signatureNode={null}
        syncParagraphEditorHeight={vi.fn()}
        title={title.content}
        titleNode={title}
      />,
    );

    expect(view.getByText('Editable body text')).toBeInTheDocument();
    expect(view.queryByRole('img', { name: '真实预览第 1 页' })).not.toBeInTheDocument();
  });

  it('lets users open the rendered preview state even before pages are ready', () => {
    const title = node('title', 'TITLE', 'Rendered notice title', 10);
    const body = node('body', 'BODY_SECTION', 'Editable body text', 20);
    const onRefreshRenderPreview = vi.fn();

    const { container } = render(
      <WorkbenchPreview
        attachment=""
        attachmentNode={null}
        bodySectionNodes={[body]}
        bodyStyleForNode={() => ({})}
        date=""
        dateNode={null}
        isRefreshingRenderPreview={false}
        nodes={[title, body]}
        onRefreshRenderPreview={onRefreshRenderPreview}
        onRemoveBodyNode={vi.fn()}
        onSelectNode={vi.fn()}
        onUpdateAttachment={vi.fn()}
        onUpdateBodyContent={vi.fn()}
        onUpdateBodyHeading={vi.fn()}
        onUpdateDate={vi.fn()}
        onUpdateRecipient={vi.fn()}
        onUpdateSignature={vi.fn()}
        onUpdateTitle={vi.fn()}
        recipient=""
        recipientNode={null}
        registerNodeRef={vi.fn()}
        renderPreview={pendingPreview()}
        selectedNodeId={null}
        signature=""
        signatureNode={null}
        syncParagraphEditorHeight={vi.fn()}
        title={title.content}
        titleNode={title}
      />,
    );

    const view = within(container);
    fireEvent.click(view.getByRole('button', { name: '真实预览' }));

    expect(view.getByText('真实预览还没有可用页面。')).toBeInTheDocument();
    fireEvent.click(view.getByRole('button', { name: '刷新真实预览' }));
    expect(onRefreshRenderPreview).toHaveBeenCalledTimes(1);
  });
});
