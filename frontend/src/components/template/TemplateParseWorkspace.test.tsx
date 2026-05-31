import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TemplateParseWorkspace } from './TemplateParseWorkspace';
import type {
  DocumentRenderPreview,
  DocumentStructureProfile,
  StructureMappingItem,
  StructureMappingProfile,
  TemplateDocumentKind,
  TemplateProfile,
} from '../../draftTypes';

describe('TemplateParseWorkspace', () => {
  afterEach(() => {
    cleanup();
  });

  it('shows all document fact nodes with role suggestions separated from node type', () => {
    renderWorkspace();

    expect(screen.getAllByText('PARAGRAPH').length).toBeGreaterThan(0);
    expect(screen.getByText('TABLE_PARAGRAPH')).toBeInTheDocument();
    expect(screen.getByText('HEADER_PARAGRAPH')).toBeInTheDocument();
    expect(screen.getByText('FOOTER_PARAGRAPH')).toBeInTheDocument();
    expect(screen.getByLabelText('映射角色：在全区重点工作推进会上的讲话')).toHaveValue('TITLE');
    expect(screen.getByText('建议：标题')).toBeInTheDocument();
    expect(screen.getAllByText('事实：PARAGRAPH').length).toBeGreaterThan(0);
    expect(screen.getByText('该文件是参考范文，必须确认映射后再用于套版。')).toBeInTheDocument();
    expect(screen.getByText('政务会议讲话稿测试样例')).toBeInTheDocument();
  });

  it('keeps no-placeholder reference documents mappable with unknown nodes visible', () => {
    renderWorkspace();

    expect(sampleTemplateProfile().placeholders).toHaveLength(0);
    expect(screen.getByRole('button', { name: '保存草稿' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '发布映射' })).toBeEnabled();
    expect(screen.getByLabelText('映射角色：政务会议讲话稿测试样例')).toHaveValue('UNKNOWN');
  });

  it('applies batch roles to selected nodes and saves mapping draft', async () => {
    const onSaveMappingDraft = vi.fn();

    render(<BatchHarness onSaveMappingDraft={onSaveMappingDraft} />);

    await userEvent.click(screen.getByLabelText('选择结构节点：政务会议讲话稿测试样例'));
    await userEvent.click(screen.getByLabelText('选择结构节点：2026年5月30日'));
    await userEvent.click(screen.getByRole('button', { name: '标为正文' }));

    expect(screen.getByLabelText('映射角色：政务会议讲话稿测试样例')).toHaveValue('BODY');
    expect(screen.getByLabelText('映射角色：2026年5月30日')).toHaveValue('BODY');

    await userEvent.click(screen.getByRole('button', { name: '保存草稿' }));

    expect(onSaveMappingDraft).toHaveBeenCalledTimes(1);
  });
});

function BatchHarness({ onSaveMappingDraft }: { onSaveMappingDraft: () => void }) {
  const [mappingItems, setMappingItems] = useState(sampleMappingItems());

  return (
    <TemplateParseWorkspace
      documentKind={referenceDocumentKind()}
      mappingItems={mappingItems}
      mappingMessage="结构映射已加载"
      mappingProfile={sampleMappingProfile(mappingItems)}
      mappingStatus="idle"
      profile={sampleTemplateProfile()}
      renderPreview={sampleRenderPreview()}
      renderPreviewMessage="渲染预览已更新"
      renderPreviewStatus="idle"
      structureProfile={sampleStructureProfile()}
      onMappingRoleChange={(nodeKey, role, sortOrder) => {
        setMappingItems((current) => {
          const nextItem: StructureMappingItem = {
            nodeKey,
            role,
            slotKey: role === 'BODY' ? 'body' : '',
            status: role === 'IGNORE' ? 'IGNORED' : 'CONFIRMED',
            source: 'USER',
            confidence: 1,
            notes: '',
            sortOrder,
          };
          return current.some((item) => item.nodeKey === nodeKey)
            ? current.map((item) => item.nodeKey === nodeKey ? { ...item, ...nextItem } : item)
            : [...current, nextItem];
        });
      }}
      onPublishMapping={vi.fn()}
      onRequestRenderPreview={vi.fn()}
      onSaveMappingDraft={onSaveMappingDraft}
    />
  );
}

function renderWorkspace() {
  return render(
    <TemplateParseWorkspace
      documentKind={referenceDocumentKind()}
      mappingItems={sampleMappingItems()}
      mappingMessage="结构映射已加载"
      mappingProfile={sampleMappingProfile(sampleMappingItems())}
      mappingStatus="idle"
      profile={sampleTemplateProfile()}
      renderPreview={sampleRenderPreview()}
      renderPreviewMessage="渲染预览已更新"
      renderPreviewStatus="idle"
      structureProfile={sampleStructureProfile()}
      onMappingRoleChange={vi.fn()}
      onPublishMapping={vi.fn()}
      onRequestRenderPreview={vi.fn()}
      onSaveMappingDraft={vi.fn()}
    />,
  );
}

function sampleStructureProfile(): DocumentStructureProfile {
  return {
    schemaVersion: 1,
    sourceFileHash: 'hash',
    extractorVersion: 'document-structure-v2',
    createdAt: '2026-05-31T00:00:00Z',
    styles: [],
    sections: [],
    risks: [],
    nodes: [
      node('title', 'PARAGRAPH', 'TITLE', '在全区重点工作推进会上的讲话', 10),
      node('subtitle', 'PARAGRAPH', 'UNKNOWN', '政务会议讲话稿测试样例', 20),
      node('date', 'PARAGRAPH', 'DATE', '2026年5月30日', 30),
      node('heading', 'PARAGRAPH', 'BODY_HEADING_LEVEL_1', '一、提高政治站位', 40),
      node('body', 'PARAGRAPH', 'BODY', '今天我们召开这次重点工作推进会。', 50),
      node('table', 'TABLE_PARAGRAPH', 'TABLE_ATTACHMENT', '文档类型', 60),
      node('header', 'HEADER_PARAGRAPH', 'STATIC_TEXT', '内部测试资料', 70),
      node('footer', 'FOOTER_PARAGRAPH', 'STATIC_TEXT', '测试文档 | 讲话稿范文示例', 80),
    ],
  };
}

function node(
  nodeKey: string,
  nodeType: string,
  roleSuggestion: string,
  text: string,
  orderIndex: number,
) {
  return {
    nodeKey,
    parentKey: null,
    nodeType,
    roleSuggestion,
    text,
    textPreview: text,
    orderIndex,
    path: `${nodeType}/${nodeKey}`,
    formatting: null,
    riskCodes: [],
  };
}

function sampleMappingItems(): StructureMappingItem[] {
  return sampleStructureProfile().nodes.map((structureNode) => ({
    nodeKey: structureNode.nodeKey,
    role: structureNode.roleSuggestion,
    slotKey: structureNode.roleSuggestion === 'BODY' ? 'body' : '',
    status: structureNode.roleSuggestion === 'UNKNOWN' ? 'NEEDS_REVIEW' : 'SUGGESTED',
    source: 'SYSTEM',
    confidence: 0.8,
    notes: '',
    sortOrder: structureNode.orderIndex,
  }));
}

function sampleMappingProfile(items: StructureMappingItem[]): StructureMappingProfile {
  return {
    mappingProfileId: 11,
    templateVersionId: 31,
    versionNo: 1,
    status: 'DRAFT',
    items,
    validationItems: [],
    confirmedCount: items.filter((item) => item.role !== 'UNKNOWN').length,
    needsReviewCount: items.filter((item) => item.role === 'UNKNOWN').length,
    publishedAt: null,
    createdAt: '2026-05-31T00:00:00Z',
    updatedAt: '2026-05-31T00:00:00Z',
  };
}

function sampleTemplateProfile(): TemplateProfile {
  return {
    schemaVersion: 1,
    placeholders: [],
    styles: [],
    sections: [],
    tables: [],
    media: [],
    structures: [],
    validationItems: [],
    templateAnalysis: {
      templateKind: 'REFERENCE_DOCUMENT',
      documentKind: 'REFERENCE_DOCUMENT',
      confidence: 0.94,
      documentTypeCode: 'UNKNOWN',
      reasonCodes: ['REFERENCE_DOCUMENT'],
      recommendedWorkflow: 'REVIEW_AND_MAP',
      blockingWarnings: [],
      inferredFields: [],
      suggestedPlaceholders: [],
      message: '可进入结构映射',
      source: 'RULE',
    },
  };
}

function referenceDocumentKind(): TemplateDocumentKind {
  return {
    documentKind: 'REFERENCE_DOCUMENT',
    templateKind: 'REFERENCE_DOCUMENT',
    confidence: 0.94,
    documentTypeCode: 'UNKNOWN',
    reasonCodes: ['REFERENCE_DOCUMENT'],
    recommendedWorkflow: 'REVIEW_AND_MAP',
    blockingWarnings: [],
    message: '可进入结构映射',
    source: 'RULE',
  };
}

function sampleRenderPreview(): DocumentRenderPreview {
  return {
    id: 7,
    templateVersionId: 31,
    sourceFileHash: 'hash',
    renderer: 'libreoffice',
    rendererVersion: '24.2',
    status: 'READY',
    pageCount: 1,
    storagePath: null,
    manifest: {
      schemaVersion: 1,
      pdfFileName: null,
      pages: [
        {
          pageNumber: 1,
          fileName: 'page-1.png',
          contentType: 'image/png',
          widthPixels: 1200,
          heightPixels: 1600,
          dpi: 144,
        },
      ],
    },
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-05-31T00:00:00Z',
    updatedAt: '2026-05-31T00:00:00Z',
  };
}
