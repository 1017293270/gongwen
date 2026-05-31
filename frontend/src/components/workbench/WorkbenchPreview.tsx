import { CSSProperties } from 'react';
import { Trash2 } from 'lucide-react';
import {
  bodyNodeEditorLabel,
  bodyNodeLabel,
} from '../../workbenchNodes';
import type { WorkbenchNode } from '../../draftTypes';
import { Button } from '../ui';

type WorkbenchPreviewProps = {
  nodes: WorkbenchNode[];
  title: string;
  recipient: string;
  date: string;
  signature: string;
  attachment: string;
  bodySectionNodes: WorkbenchNode[];
  selectedNodeId: string | null;
  titleNode: WorkbenchNode | null;
  recipientNode: WorkbenchNode | null;
  attachmentNode: WorkbenchNode | null;
  signatureNode: WorkbenchNode | null;
  dateNode: WorkbenchNode | null;
  titleStyle?: CSSProperties;
  recipientStyle?: CSSProperties;
  attachmentStyle?: CSSProperties;
  signatureStyle?: CSSProperties;
  dateStyle?: CSSProperties;
  bodyStyleForNode: (node: WorkbenchNode) => CSSProperties;
  registerNodeRef: (nodeId: string, element: HTMLElement | null) => void;
  syncParagraphEditorHeight: (element: HTMLTextAreaElement) => void;
  onSelectNode: (nodeId: string) => void;
  onUpdateTitle: (content: string) => void;
  onUpdateRecipient: (content: string) => void;
  onUpdateAttachment: (content: string) => void;
  onUpdateSignature: (content: string) => void;
  onUpdateDate: (content: string) => void;
  onUpdateBodyHeading: (node: WorkbenchNode, heading: string) => void;
  onUpdateBodyContent: (node: WorkbenchNode, content: string) => void;
  onRemoveBodyNode: (node: WorkbenchNode) => void;
};

export function WorkbenchPreview({
  nodes,
  title,
  recipient,
  date,
  signature,
  attachment,
  bodySectionNodes,
  selectedNodeId,
  titleNode,
  recipientNode,
  attachmentNode,
  signatureNode,
  dateNode,
  titleStyle,
  recipientStyle,
  attachmentStyle,
  signatureStyle,
  dateStyle,
  bodyStyleForNode,
  registerNodeRef,
  syncParagraphEditorHeight,
  onSelectNode,
  onUpdateTitle,
  onUpdateRecipient,
  onUpdateAttachment,
  onUpdateSignature,
  onUpdateDate,
  onUpdateBodyHeading,
  onUpdateBodyContent,
  onRemoveBodyNode,
}: WorkbenchPreviewProps) {
  const previewNodes = nodes.length > 0
    ? [...nodes].sort((a, b) => a.sortOrder - b.sortOrder || a.nodeId.localeCompare(b.nodeId))
    : legacyPreviewNodes({
      title,
      recipient,
      date,
      signature,
      attachment,
      bodySectionNodes,
      titleNode,
      recipientNode,
      attachmentNode,
      signatureNode,
      dateNode,
    });

  const styleForNode = (node: WorkbenchNode) => {
    switch (node.nodeType) {
      case 'TITLE':
        return titleStyle;
      case 'RECIPIENT':
        return recipientStyle;
      case 'ATTACHMENT':
        return attachmentStyle;
      case 'SIGNATURE':
        return signatureStyle;
      case 'DATE':
        return dateStyle;
      case 'BODY_SECTION':
      case 'STATIC_TEMPLATE_TEXT':
      case 'HEADER':
      case 'FOOTER':
        return bodyStyleForNode(node);
      default:
        return undefined;
    }
  };

  return (
    <section aria-label="公文预览">
      <div className="document-stage">
        <div className="document-preview-label">结构化编辑预览</div>
        <article className="document-paper">
          {previewNodes.length > 0 ? previewNodes.map((node) => renderPreviewNode({
            node,
            bodyIndex: bodySectionNodes.findIndex((bodyNode) => bodyNode.nodeId === node.nodeId),
            selectedNodeId,
            style: styleForNode(node),
            bodyStyle: node.nodeType === 'BODY_SECTION' ? bodyStyleForNode(node) : undefined,
            registerNodeRef,
            syncParagraphEditorHeight,
            onSelectNode,
            onUpdateTitle,
            onUpdateRecipient,
            onUpdateAttachment,
            onUpdateSignature,
            onUpdateDate,
            onUpdateBodyHeading,
            onUpdateBodyContent,
            onRemoveBodyNode,
          })) : <p>请在左侧填写正文内容。</p>}
        </article>
      </div>
    </section>
  );
}

type RenderPreviewNodeOptions = {
  node: WorkbenchNode;
  bodyIndex: number;
  selectedNodeId: string | null;
  style?: CSSProperties;
  bodyStyle?: CSSProperties;
  registerNodeRef: (nodeId: string, element: HTMLElement | null) => void;
  syncParagraphEditorHeight: (element: HTMLTextAreaElement) => void;
  onSelectNode: (nodeId: string) => void;
  onUpdateTitle: (content: string) => void;
  onUpdateRecipient: (content: string) => void;
  onUpdateAttachment: (content: string) => void;
  onUpdateSignature: (content: string) => void;
  onUpdateDate: (content: string) => void;
  onUpdateBodyHeading: (node: WorkbenchNode, heading: string) => void;
  onUpdateBodyContent: (node: WorkbenchNode, content: string) => void;
  onRemoveBodyNode: (node: WorkbenchNode) => void;
};

function renderPreviewNode({
  node,
  bodyIndex,
  selectedNodeId,
  style,
  bodyStyle,
  registerNodeRef,
  syncParagraphEditorHeight,
  onSelectNode,
  onUpdateTitle,
  onUpdateRecipient,
  onUpdateAttachment,
  onUpdateSignature,
  onUpdateDate,
  onUpdateBodyHeading,
  onUpdateBodyContent,
  onRemoveBodyNode,
}: RenderPreviewNodeOptions) {
  const selected = selectedNodeId === node.nodeId;
  const register = (element: HTMLElement | null) => registerNodeRef(node.nodeId, element);

  switch (node.nodeType) {
    case 'TITLE':
      return selected ? (
        <input
          aria-label="编辑节点：标题"
          className="document-title-editor"
          data-preview-node
          key={node.nodeId}
          onChange={(event) => onUpdateTitle(event.target.value)}
          ref={register as (element: HTMLInputElement | null) => void}
          style={style}
          value={node.content}
        />
      ) : (
        <h2 className="document-title" data-preview-node key={node.nodeId} ref={register} style={style}>{node.content}</h2>
      );
    case 'RECIPIENT':
      return selected ? (
        <input
          aria-label="编辑节点：主送"
          className="document-inline-editor"
          data-preview-node
          key={node.nodeId}
          onChange={(event) => onUpdateRecipient(event.target.value)}
          ref={register as (element: HTMLInputElement | null) => void}
          style={style}
          value={node.content}
        />
      ) : (
        <p data-preview-node key={node.nodeId} ref={register} style={style}>{formatRecipient(node.content)}</p>
      );
    case 'BODY_SECTION':
      return renderBodyNode({
        node,
        index: bodyIndex >= 0 ? bodyIndex : 0,
        selected,
        style: bodyStyle ?? style,
        register,
        syncParagraphEditorHeight,
        onSelectNode,
        onUpdateBodyHeading,
        onUpdateBodyContent,
        onRemoveBodyNode,
      });
    case 'ATTACHMENT':
      return selected ? (
        <input
          aria-label="编辑节点：附件"
          className="document-inline-editor"
          data-preview-node
          key={node.nodeId}
          onChange={(event) => onUpdateAttachment(event.target.value)}
          ref={register as (element: HTMLInputElement | null) => void}
          style={style}
          value={node.content}
        />
      ) : (
        <p data-preview-node key={node.nodeId} ref={register} style={style}>{formatAttachment(node.content)}</p>
      );
    case 'SIGNATURE':
      return selected ? (
        <input
          aria-label="编辑节点：落款"
          className="document-inline-editor signature"
          data-preview-node
          key={node.nodeId}
          onChange={(event) => onUpdateSignature(event.target.value)}
          ref={register as (element: HTMLInputElement | null) => void}
          style={style}
          value={node.content}
        />
      ) : (
        <p className="signature" data-preview-node key={node.nodeId} ref={register} style={style}>{node.content}</p>
      );
    case 'DATE':
      return selected ? (
        <input
          aria-label="编辑节点：日期"
          className="document-inline-editor"
          data-preview-node
          key={node.nodeId}
          onChange={(event) => onUpdateDate(event.target.value)}
          ref={register as (element: HTMLInputElement | null) => void}
          style={style}
          value={node.content}
        />
      ) : (
        <p data-preview-node key={node.nodeId} ref={register} style={style}>{node.content}</p>
      );
    case 'HEADER':
    case 'FOOTER':
    case 'STATIC_TEMPLATE_TEXT':
      return (
        <button
          className={`document-static-node document-static-node-${node.nodeType.toLowerCase()}`}
          data-preview-node
          key={node.nodeId}
          onClick={() => onSelectNode(node.nodeId)}
          ref={register as (element: HTMLButtonElement | null) => void}
          style={style}
          type="button"
        >
          {node.content}
        </button>
      );
    default:
      return null;
  }
}

type RenderBodyNodeOptions = {
  node: WorkbenchNode;
  index: number;
  selected: boolean;
  style?: CSSProperties;
  register: (element: HTMLElement | null) => void;
  syncParagraphEditorHeight: (element: HTMLTextAreaElement) => void;
  onSelectNode: (nodeId: string) => void;
  onUpdateBodyHeading: (node: WorkbenchNode, heading: string) => void;
  onUpdateBodyContent: (node: WorkbenchNode, content: string) => void;
  onRemoveBodyNode: (node: WorkbenchNode) => void;
};

function renderBodyNode({
  node,
  index,
  selected,
  style,
  register,
  syncParagraphEditorHeight,
  onSelectNode,
  onUpdateBodyHeading,
  onUpdateBodyContent,
  onRemoveBodyNode,
}: RenderBodyNodeOptions) {
  return selected ? (
    <section
      className="document-node selected"
      data-preview-node
      key={node.nodeId}
      ref={register}
      style={style}
      tabIndex={-1}
    >
      <input
        aria-label={`编辑标题：${bodyNodeLabel(node, index)}`}
        className="document-heading-editor"
        onChange={(event) => onUpdateBodyHeading(node, event.target.value)}
        placeholder="正文标题"
        value={node.heading ?? ''}
      />
      <textarea
        aria-label={`编辑段落：${bodyNodeEditorLabel(node, index)}`}
        className="document-paragraph-editor"
        onChange={(event) => {
          syncParagraphEditorHeight(event.currentTarget);
          onUpdateBodyContent(node, event.target.value);
        }}
        ref={(element) => {
          if (element) {
            syncParagraphEditorHeight(element);
          }
        }}
        style={style}
        value={node.content}
      />
      <Button icon={<Trash2 aria-hidden="true" />} onClick={() => onRemoveBodyNode(node)} variant="ghost">
        删除当前结构
      </Button>
    </section>
  ) : (
    <button
      aria-pressed={false}
      className="document-node"
      data-preview-node
      key={node.nodeId}
      onClick={() => onSelectNode(node.nodeId)}
      ref={register as (element: HTMLButtonElement | null) => void}
      style={style}
      type="button"
    >
      <span className="visually-hidden">选择正文结构：</span>
      {node.heading && <span className="document-node-heading">{node.heading}</span>}
      <span className="document-node-content">{node.content.trim() || '点击填写正文段落'}</span>
    </button>
  );
}

function legacyPreviewNodes({
  title,
  recipient,
  date,
  signature,
  attachment,
  bodySectionNodes,
  titleNode,
  recipientNode,
  attachmentNode,
  signatureNode,
  dateNode,
}: {
  title: string;
  recipient: string;
  date: string;
  signature: string;
  attachment: string;
  bodySectionNodes: WorkbenchNode[];
  titleNode: WorkbenchNode | null;
  recipientNode: WorkbenchNode | null;
  attachmentNode: WorkbenchNode | null;
  signatureNode: WorkbenchNode | null;
  dateNode: WorkbenchNode | null;
}) {
  return [
    titleNode ? { ...titleNode, content: title } : null,
    recipientNode && recipient ? { ...recipientNode, content: recipient } : null,
    ...bodySectionNodes,
    attachmentNode && attachment ? { ...attachmentNode, content: attachment } : null,
    signatureNode && signature ? { ...signatureNode, content: signature } : null,
    dateNode && date ? { ...dateNode, content: date } : null,
  ].filter((node): node is WorkbenchNode => Boolean(node));
}

function formatRecipient(recipient: string) {
  return /[:：]$/.test(recipient) ? recipient : `${recipient}：`;
}

function formatAttachment(attachment: string) {
  return /^附件[:：]/.test(attachment) ? attachment : `附件：${attachment}`;
}
