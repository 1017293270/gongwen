import type {
  DraftBlock,
  DraftDetail,
  TemplateProfile,
  TemplateStructureFormatting,
  TemplateStructureFormattingOverrides,
  WorkbenchNode,
  WorkbenchNodeType,
} from './draftTypes';

const BODY_PREFIX = '(?:[一二三四五六七八九十]+[、.．]|（[一二三四五六七八九十]+）|\\d+[.．、])';
const BODY_HEADING_PATTERN = new RegExp(`^${BODY_PREFIX}\\s*\\S+`);
const BODY_HEADING_WITH_CONTENT_PATTERN = new RegExp(`^(${BODY_PREFIX}\\s*[^：:。；;，,\\n]{1,32})([：:。；;，,])?(.*)$`);

export function deriveWorkbenchNodes(
  draft: DraftDetail | null,
  profile: TemplateProfile | null,
  overrides: TemplateStructureFormattingOverrides,
): WorkbenchNode[] {
  if (!draft) {
    return [];
  }

  const templateNodes = profile?.structures?.length
    ? nodesFromTemplateProfile(draft.blocks, profile, overrides)
    : [];

  if (templateNodes.length > 0) {
    return templateNodes;
  }

  return nodesFromDraftBlocks(draft.blocks);
}

export function bodyNodeLabel(node: WorkbenchNode, index: number) {
  if (node.heading) {
    return stripBodyPrefix(node.heading);
  }
  if (node.label && node.label !== '正文段落') {
    return node.label;
  }
  return `第 ${index + 1} 段`;
}

export function bodyNodeEditorLabel(node: WorkbenchNode, index: number) {
  if (node.heading) {
    return bodyNodeLabel(node, index);
  }
  const normalized = node.content.trim().replace(/\s+/g, ' ');
  return normalized ? normalized.slice(0, 18) : bodyNodeLabel(node, index);
}

export function bodyNodePreview(node: WorkbenchNode) {
  const normalized = node.content.trim().replace(/\s+/g, ' ');
  if (!normalized) {
    return '点击后在中间填写正文';
  }
  return normalized.length > 34 ? `${normalized.slice(0, 34)}...` : normalized;
}

export function composeBodySectionContent(node: WorkbenchNode, nextContent: string) {
  return composeBodySectionParts(node.heading ?? '', nextContent);
}

export function composeBodySectionParts(heading: string, content: string) {
  return [heading.trim(), content.trim()].filter(Boolean).join('\n');
}

function nodesFromTemplateProfile(
  draftBlocks: DraftBlock[],
  profile: TemplateProfile,
  overrides: TemplateStructureFormattingOverrides,
) {
  const nodes: WorkbenchNode[] = [];
  const structures = [...profile.structures].sort((a, b) => aSortOrder(a) - aSortOrder(b));
  let pendingBody: WorkbenchNode | null = null;

  for (const structure of structures) {
    const text = (structure.textPreview ?? '').trim();
    const formatting = mergeFormatting(structure.formatting, overrides[structure.structureKey]);

    if (structure.structureType === 'BODY') {
      if (isBodyHeadingText(text)) {
        if (pendingBody) {
          nodes.push(pendingBody);
        }
        pendingBody = {
          nodeId: `template:${structure.structureKey}`,
          nodeType: 'BODY_SECTION',
          templateStructureKey: structure.structureKey,
          sortOrder: aSortOrder(structure),
          label: stripBodyPrefix(text),
          heading: text,
          content: '',
          source: 'TEMPLATE',
          locked: false,
          formatting,
        };
      } else if (pendingBody) {
        pendingBody.content = [pendingBody.content, stripTemplateBraces(text)].filter(Boolean).join('\n');
      } else {
        nodes.push({
          nodeId: `template:${structure.structureKey}`,
          nodeType: 'BODY_SECTION',
          templateStructureKey: structure.structureKey,
          sortOrder: aSortOrder(structure),
          label: '正文',
          content: stripTemplateBraces(text),
          source: 'TEMPLATE',
          locked: false,
          formatting,
        });
      }
      continue;
    }

    if (pendingBody) {
      nodes.push(pendingBody);
      pendingBody = null;
    }

    nodes.push({
      nodeId: `template:${structure.structureKey}`,
      nodeType: mapStructureType(structure.structureType),
      templateStructureKey: structure.structureKey,
      sortOrder: aSortOrder(structure),
      label: structure.label || stripTemplateBraces(text) || '模板结构',
      content: stripTemplateBraces(text),
      source: 'TEMPLATE',
      locked: structure.structureType === 'HEADER' || structure.structureType === 'FOOTER',
      formatting,
    });
  }

  if (pendingBody) {
    nodes.push(pendingBody);
  }

  return applyDraftBlocksToNodes(nodes, draftBlocks);
}

function nodesFromDraftBlocks(blocks: DraftBlock[]) {
  return [...blocks]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map<WorkbenchNode>((block, index) => {
      const heading = block.blockType === 'BODY_PARAGRAPH' ? extractBodyHeading(block.content) : undefined;
      const content = block.blockType === 'BODY_PARAGRAPH' ? stripBodyHeading(block.content) : block.content;
      return {
        nodeId: `block:${block.id || block.sortOrder || index}`,
        nodeType: block.blockType === 'BODY_PARAGRAPH' ? 'BODY_SECTION' : mapStructureType(block.blockType),
        draftBlockId: block.id,
        sortOrder: block.sortOrder,
        label: block.blockType === 'BODY_PARAGRAPH' ? stripBodyPrefix(heading ?? `第 ${index + 1} 段`) : block.blockType,
        heading,
        content,
        source: 'DRAFT',
        locked: false,
      };
    });
}

function applyDraftBlocksToNodes(nodes: WorkbenchNode[], draftBlocks: DraftBlock[]) {
  const bodyBlocks = draftBlocks
    .filter((block) => block.blockType === 'BODY_PARAGRAPH' && block.content.trim())
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const bodyNodeSortOrders = new Set(nodes
    .filter((node) => node.nodeType === 'BODY_SECTION')
    .map((node) => node.sortOrder));
  const usedBlockIds = new Set<number>();

  return nodes.map((node) => {
    if (node.nodeType !== 'BODY_SECTION') {
      return node;
    }

    const block = bodyBlocks.find((candidate) => (
      !usedBlockIds.has(candidate.id) && candidate.sortOrder === node.sortOrder
    )) ?? bodyBlocks.find((candidate) => (
      !usedBlockIds.has(candidate.id) && !bodyNodeSortOrders.has(candidate.sortOrder)
    ));
    if (!block) {
      return node;
    }
    usedBlockIds.add(block.id);

    const heading = extractBodyHeading(block.content);
    const content = stripBodyHeading(block.content);
    return {
      ...node,
      draftBlockId: block.id,
      heading: heading || node.heading,
      content: content || node.content,
      source: 'DRAFT' as const,
    };
  });
}

function isBodyHeadingText(text: string) {
  const normalized = stripTemplateBraces(text).trim();
  return BODY_HEADING_PATTERN.test(normalized) && normalized.length <= 42;
}

function extractBodyHeading(content: string) {
  const normalized = content.trim();
  if (!normalized) {
    return undefined;
  }

  const lines = normalized.split(/\r?\n/);
  const [firstLine] = lines;
  const match = firstLine.match(BODY_HEADING_WITH_CONTENT_PATTERN);
  if (!match) {
    return lines.length > 1 && firstLine.trim().length <= 42 ? firstLine.trim() : undefined;
  }

  const heading = match[1]?.trim();
  if (!heading || heading.length > 42) {
    return undefined;
  }
  return heading;
}

function stripBodyHeading(content: string) {
  const normalized = content.trim();
  if (!normalized) {
    return '';
  }

  const lines = normalized.split(/\r?\n/);
  const firstLine = lines[0] ?? '';
  const match = firstLine.match(BODY_HEADING_WITH_CONTENT_PATTERN);
  if (!match) {
    return lines.length > 1 && firstLine.trim().length <= 42 ? lines.slice(1).join('\n').trim() : normalized;
  }

  const inlineContent = (match[3] ?? '').trim();
  return [inlineContent, ...lines.slice(1)].join('\n').trim();
}

function stripBodyPrefix(text: string) {
  return text.replace(new RegExp(`^${BODY_PREFIX}\\s*`), '').trim();
}

function stripTemplateBraces(text: string) {
  return text.replace(/\{\{([^}]+)\}\}/g, '$1').trim();
}

function mergeFormatting(
  base: TemplateStructureFormatting | undefined,
  override: Partial<TemplateStructureFormatting> | undefined,
) {
  return { ...(base ?? {}), ...(override ?? {}) };
}

function mapStructureType(type: string): WorkbenchNodeType {
  switch (type) {
    case 'TITLE':
      return 'TITLE';
    case 'RECIPIENT':
      return 'RECIPIENT';
    case 'ATTACHMENT':
      return 'ATTACHMENT';
    case 'SIGNATURE':
      return 'SIGNATURE';
    case 'DATE':
      return 'DATE';
    case 'HEADER':
      return 'HEADER';
    case 'FOOTER':
      return 'FOOTER';
    default:
      return 'STATIC_TEMPLATE_TEXT';
  }
}

function aSortOrder(structure: TemplateProfile['structures'][number]) {
  return Number.parseInt(structure.structureKey.replace(/\D+/g, ''), 10) || 0;
}
