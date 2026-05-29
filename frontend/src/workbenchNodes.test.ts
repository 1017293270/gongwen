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
});
