import { describe, expect, it } from 'vitest';
import { bodyNodeLabel, composeBodySectionContent, deriveWorkbenchNodes } from './workbenchNodes';
import type { DraftDetail, TemplateProfile } from './draftTypes';

function draft(blocks: DraftDetail['blocks']): DraftDetail {
  return {
    id: 1,
    documentTypeCode: 'NOTICE',
    title: '通知',
    status: 'DRAFT',
    templateVersionId: 9,
    blocks,
  };
}

function profile(): TemplateProfile {
  return {
    schemaVersion: 1,
    structures: [
      {
        structureKey: 'body-1',
        structureType: 'BODY',
        label: '正文段落',
        textPreview: '一、会议时间',
        locationType: 'BODY',
        styleId: null,
        styleName: '正文',
        source: 'paragraph',
        formatting: {
          fontFamily: 'FangSong',
          fontSizeHalfPoints: 32,
          bold: null,
          alignment: 'LEFT',
          indentationFirstLine: 420,
          spacingBetween: null,
          spacingBefore: null,
          spacingAfter: null,
        },
      },
      {
        structureKey: 'body-2',
        structureType: 'BODY',
        label: '正文段落',
        textPreview: '2026年6月3日（星期三）上午9:30。',
        locationType: 'BODY',
        styleId: null,
        styleName: '正文',
        source: 'paragraph',
        formatting: {
          fontFamily: 'FangSong',
          fontSizeHalfPoints: 32,
          bold: null,
          alignment: 'LEFT',
          indentationFirstLine: 420,
          spacingBetween: null,
          spacingBefore: null,
          spacingAfter: null,
        },
      },
    ],
    styles: [],
    sections: [],
    tables: [],
    media: [],
    templateAnalysis: null,
    placeholders: [],
    validationItems: [],
  };
}

describe('deriveWorkbenchNodes', () => {
  it('groups a body heading and following paragraph into one BODY_SECTION node', () => {
    const nodes = deriveWorkbenchNodes(draft([]), profile(), {});

    expect(nodes).toHaveLength(1);
    expect(nodes[0]).toMatchObject({
      nodeType: 'BODY_SECTION',
      heading: '一、会议时间',
      content: '2026年6月3日（星期三）上午9:30。',
      templateStructureKey: 'body-1',
    });
    expect(bodyNodeLabel(nodes[0], 0)).toBe('会议时间');
  });

  it('keeps draft body content separate from its heading when overriding template text', () => {
    const nodes = deriveWorkbenchNodes(
      draft([{ id: 3, blockType: 'BODY_PARAGRAPH', content: '一、会议时间：2026年6月5日上午10:00。', sortOrder: 30 }]),
      profile(),
      {},
    );

    expect(nodes[0]).toMatchObject({
      draftBlockId: 3,
      heading: '一、会议时间',
      content: '2026年6月5日上午10:00。',
      source: 'DRAFT',
    });
    expect(composeBodySectionContent(nodes[0], nodes[0].content)).toBe('一、会议时间\n2026年6月5日上午10:00。');
  });

  it('prefers persisted draft nodes over template profile and legacy blocks', () => {
    const nodes = deriveWorkbenchNodes({
      ...draft([{ id: 3, blockType: 'BODY_PARAGRAPH', content: '旧正文', sortOrder: 30 }]),
      nodes: [
        draftNode(101, 'TITLE', '节点标题', 10),
        draftNode(102, 'BODY_HEADING_LEVEL_1', '一、节点标题', 20),
        draftNode(103, 'BODY', '节点正文', 30),
      ],
    }, profile(), {});

    expect(nodes).toHaveLength(2);
    expect(nodes[0]).toMatchObject({
      draftNodeId: 101,
      nodeType: 'TITLE',
      content: '节点标题',
      status: 'USER_FILLED',
    });
    expect(nodes[1]).toMatchObject({
      draftNodeId: 103,
      headingDraftNodeId: 102,
      nodeType: 'BODY_SECTION',
      heading: '一、节点标题',
      content: '节点正文',
      source: 'USER',
    });
  });

  it('derives persisted source nodes without injecting template or legacy title text', () => {
    const nodes = deriveWorkbenchNodes({
      ...draft([
        { id: 1, blockType: 'TITLE', content: '未命名通知', sortOrder: 10 },
        { id: 3, blockType: 'BODY_PARAGRAPH', content: '旧正文', sortOrder: 30 },
      ]),
      title: '未命名通知',
      nodes: [
        draftNode(201, 'TITLE', '在全区重点工作推进会上的讲话', 10),
        draftNode(202, 'DATE', '2026年5月30日', 20),
        draftNode(203, 'RECIPIENT', '同志们：', 30),
        draftNode(204, 'BODY_HEADING_LEVEL_1', '一、提高政治站位，把思想和行动统一到重点任务落实上来', 40),
        draftNode(205, 'BODY', '抓落实是检验干部作风和治理能力的重要标尺。', 50),
        draftNode(206, 'STATIC_TEXT', '文档类型\t讲话稿', 60, { nodeType: 'TABLE_PARAGRAPH' }),
      ],
    }, profile(), {});

    expect(nodes.map((node) => node.nodeType)).toContain('TITLE');
    expect(nodes.filter((node) => node.nodeType === 'BODY_SECTION')).toHaveLength(1);
    expect(nodes.find((node) => node.nodeType === 'TITLE')?.content).toBe('在全区重点工作推进会上的讲话');
    expect(nodes.some((node) => node.content.includes('未命名通知'))).toBe(false);
    expect(nodes.find((node) => node.templateNodeKey === 'node-206')).toMatchObject({
      nodeType: 'STATIC_TEMPLATE_TEXT',
      factNodeType: 'TABLE_PARAGRAPH',
      locked: true,
    });
  });

  it('does not turn reference recipient attachment signature and date paragraphs into body nodes', () => {
    const referenceProfile = {
      ...profile(),
      structures: [
        {
          ...profile().structures[0],
          structureKey: 'title',
          structureType: 'TITLE',
          textPreview: '关于召开2026年第二季度行政办公例会的通知',
          formatting: { ...profile().structures[0].formatting, alignment: 'CENTER', indentationFirstLine: null },
        },
        {
          ...profile().structures[0],
          structureKey: 'body-recipient',
          textPreview: '各部门、各直属单位：',
          formatting: { ...profile().structures[0].formatting, indentationFirstLine: null },
        },
        {
          ...profile().structures[0],
          structureKey: 'body-intro',
          textPreview: '为统筹推进近期重点工作，现将有关事项通知如下：',
        },
        {
          ...profile().structures[0],
          structureKey: 'body-heading',
          textPreview: '一、会议时间',
        },
        {
          ...profile().structures[0],
          structureKey: 'body-content',
          textPreview: '2026年6月3日（星期三）上午9:30。',
        },
        {
          ...profile().structures[0],
          structureKey: 'body-attachment',
          textPreview: '附件：会议议题征集表',
        },
        {
          ...profile().structures[0],
          structureKey: 'body-signature',
          textPreview: '示例单位办公室',
          formatting: { ...profile().structures[0].formatting, alignment: 'RIGHT', indentationFirstLine: null },
        },
        {
          ...profile().structures[0],
          structureKey: 'body-date',
          textPreview: '2026年5月27日',
          formatting: { ...profile().structures[0].formatting, alignment: 'RIGHT', indentationFirstLine: null },
        },
      ],
    } satisfies TemplateProfile;

    const nodes = deriveWorkbenchNodes(draft([]), referenceProfile, {});

    const bodyNodes = nodes.filter((node) => node.nodeType === 'BODY_SECTION');
    expect(bodyNodes).toHaveLength(2);
    expect(bodyNodes.map((node) => node.content || node.heading)).toEqual([
      '为统筹推进近期重点工作，现将有关事项通知如下：',
      '2026年6月3日（星期三）上午9:30。',
    ]);
    expect(nodes.find((node) => node.nodeType === 'RECIPIENT')).toMatchObject({ content: '各部门、各直属单位：' });
    expect(nodes.find((node) => node.nodeType === 'ATTACHMENT')).toMatchObject({ content: '附件：会议议题征集表' });
    expect(nodes.find((node) => node.nodeType === 'SIGNATURE')).toMatchObject({ content: '示例单位办公室' });
    expect(nodes.find((node) => node.nodeType === 'DATE')).toMatchObject({ content: '2026年5月27日' });
  });
});

function draftNode(
  id: number,
  role: string,
  content: string,
  sortOrder: number,
  options: { nodeType?: string } = {},
) {
  return {
    id,
    draftId: 1,
    structureMappingProfileId: 7,
    templateNodeKey: `node-${id}`,
    parentTemplateNodeKey: null,
    nodeType: options.nodeType ?? 'PARAGRAPH',
    role,
    slotKey: role === 'TITLE' ? 'title' : 'body',
    title: role,
    content,
    sortOrder,
    status: 'USER_FILLED',
    formatOverride: {
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
    },
    createdAt: '2026-05-30T00:00:00Z',
    updatedAt: '2026-05-30T00:00:00Z',
  };
}
