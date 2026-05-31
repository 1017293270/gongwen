import { CSSProperties } from 'react';
import { Trash2 } from 'lucide-react';
import {
  bodyNodeEditorLabel,
  bodyNodeLabel,
} from '../../workbenchNodes';
import type { WorkbenchNode } from '../../draftTypes';
import { Button } from '../ui';

type WorkbenchPreviewProps = {
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
  return (
    <section aria-label="公文预览">
      <div className="document-stage">
        <div className="document-preview-label">结构化编辑预览</div>
        <article className="document-paper">
          {selectedNodeId === titleNode?.nodeId ? (
            <input
              aria-label="编辑节点：标题"
              className="document-title-editor"
              onChange={(event) => onUpdateTitle(event.target.value)}
              style={titleStyle}
              value={title}
            />
          ) : (
            <h2 className="document-title" style={titleStyle}>{title}</h2>
          )}
          {selectedNodeId === recipientNode?.nodeId ? (
            <input
              aria-label="编辑节点：主送"
              className="document-inline-editor"
              onChange={(event) => onUpdateRecipient(event.target.value)}
              style={recipientStyle}
              value={recipient}
            />
          ) : recipient ? (
            <p style={recipientStyle}>{formatRecipient(recipient)}</p>
          ) : null}
          {bodySectionNodes.length > 0 ? bodySectionNodes.map((node, index) => (
            selectedNodeId === node.nodeId ? (
              <section
                className="document-node selected"
                key={node.nodeId}
                ref={(element) => registerNodeRef(node.nodeId, element)}
                style={bodyStyleForNode(node)}
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
                  style={bodyStyleForNode(node)}
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
                key={node.nodeId}
                onClick={() => onSelectNode(node.nodeId)}
                ref={(element) => registerNodeRef(node.nodeId, element)}
                style={bodyStyleForNode(node)}
                type="button"
              >
                <span className="visually-hidden">选择正文结构：</span>
                {node.heading && <span className="document-node-heading">{node.heading}</span>}
                <span className="document-node-content">{node.content.trim() || '点击填写正文段落'}</span>
              </button>
            )
          )) : <p>请在左侧填写正文内容。</p>}
          {attachment && (selectedNodeId === attachmentNode?.nodeId ? (
            <input
              aria-label="编辑节点：附件"
              className="document-inline-editor"
              onChange={(event) => onUpdateAttachment(event.target.value)}
              style={attachmentStyle}
              value={attachment}
            />
          ) : <p style={attachmentStyle}>附件：{attachment}</p>)}
          {(signature || date || selectedNodeId === signatureNode?.nodeId || selectedNodeId === dateNode?.nodeId) && (
            <p className="signature" style={signatureStyle}>
              {selectedNodeId === signatureNode?.nodeId ? (
                <input
                  aria-label="编辑节点：落款"
                  className="document-inline-editor"
                  onChange={(event) => onUpdateSignature(event.target.value)}
                  style={signatureStyle}
                  value={signature}
                />
              ) : <span>{signature}</span>}
              <br />
              {selectedNodeId === dateNode?.nodeId ? (
                <input
                  aria-label="编辑节点：日期"
                  className="document-inline-editor"
                  onChange={(event) => onUpdateDate(event.target.value)}
                  style={dateStyle}
                  value={date}
                />
              ) : <span style={dateStyle}>{date}</span>}
            </p>
          )}
        </article>
      </div>
    </section>
  );
}

function formatRecipient(recipient: string) {
  return /[:：]$/.test(recipient) ? recipient : `${recipient}：`;
}
