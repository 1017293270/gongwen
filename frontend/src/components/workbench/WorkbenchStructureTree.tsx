import { RotateCcw, Trash2 } from 'lucide-react';
import { bodyNodeLabel, bodyNodePreview } from '../../workbenchNodes';
import type { WorkbenchNode } from '../../draftTypes';
import { Button, StatusMessage } from '../ui';

export type ReinitializeNodeStatus = 'idle' | 'running' | 'success' | 'error';

type WorkbenchStructureTreeProps = {
  nodes: WorkbenchNode[];
  bodySectionNodes: WorkbenchNode[];
  selectedNodeId: string | null;
  canReinitialize: boolean;
  reinitializeStatus: ReinitializeNodeStatus;
  reinitializeMessage: string;
  onSelectNode: (nodeId: string) => void;
  onRemoveBodyNode: (node: WorkbenchNode) => void;
  onReinitialize: () => void;
};

export function WorkbenchStructureTree({
  nodes,
  bodySectionNodes,
  selectedNodeId,
  canReinitialize,
  reinitializeStatus,
  reinitializeMessage,
  onSelectNode,
  onRemoveBodyNode,
  onReinitialize,
}: WorkbenchStructureTreeProps) {
  return (
    <>
      <section className="structure-tree" aria-label="结构节点树">
        <div className="paragraph-index-header">
          <span className="field-label">结构树</span>
          <span className="paragraph-count">{nodes.length} 节点</span>
        </div>
        <div className="workbench-structure-actions">
          <p>按当前已发布映射重新生成节点，可保留用户已编辑内容。</p>
          <Button
            disabled={!canReinitialize || reinitializeStatus === 'running'}
            icon={<RotateCcw aria-hidden="true" />}
            isLoading={reinitializeStatus === 'running'}
            loadingLabel="正在重建结构"
            onClick={onReinitialize}
            variant="secondary"
          >
            从原稿重建结构
          </Button>
        </div>
        {reinitializeMessage && (
          <StatusMessage
            title={reinitializeMessage}
            tone={reinitializeStatus === 'error' ? 'warning' : 'success'}
          />
        )}
        {nodes.length > 0 ? (
          <div className="structure-tree-list">
            {nodes.map((node, index) => {
              const editable = node.editable !== false && !node.locked;
              return (
                <button
                  aria-current={selectedNodeId === node.nodeId ? 'true' : undefined}
                  aria-disabled={!editable}
                  className={`structure-tree-item ${selectedNodeId === node.nodeId ? 'selected' : ''} ${editable ? '' : 'static'}`}
                  disabled={!editable}
                  key={node.nodeId}
                  onClick={() => onSelectNode(node.nodeId)}
                  type="button"
                >
                  <span className="structure-tree-main">
                    <span className="structure-tree-title">{workbenchNodeLabel(node, index)}</span>
                    <span className="structure-tree-meta">{workbenchNodeMeta(node)}</span>
                  </span>
                  <span className={`node-status-badge ${statusBadgeTone(node.status, editable)}`}>
                    {editable ? draftNodeStatusLabel(node.status) : '静态'}
                  </span>
                </button>
              );
            })}
          </div>
        ) : (
          <p className="empty-note">暂无结构节点，先绑定模板或填写正文。</p>
        )}
      </section>

      <section className="paragraph-index" aria-label="正文段落目录">
        <div className="paragraph-index-header">
          <span className="field-label">正文</span>
          <span className="paragraph-count">{bodySectionNodes.length} 段</span>
        </div>
        {bodySectionNodes.length > 0 ? (
          <div className="paragraph-index-list">
            {bodySectionNodes.map((node, index) => (
              <div
                aria-current={selectedNodeId === node.nodeId ? 'true' : undefined}
                className={`paragraph-index-item ${selectedNodeId === node.nodeId ? 'selected' : ''}`}
                key={node.nodeId}
              >
                <button className="paragraph-index-select" onClick={() => onSelectNode(node.nodeId)} type="button">
                  <span className="paragraph-index-number">{index + 1}</span>
                  <span className="paragraph-index-copy">
                    <span className="paragraph-index-title">{bodyNodeLabel(node, index)}</span>
                    <span className="paragraph-index-preview">{bodyNodePreview(node)}</span>
                  </span>
                </button>
                <button
                  aria-label={`删除正文结构：${bodyNodeLabel(node, index)}`}
                  className="paragraph-index-delete"
                  onClick={() => onRemoveBodyNode(node)}
                  title="删除当前草稿结构，不删除模板"
                  type="button"
                >
                  <Trash2 aria-hidden="true" size={16} />
                </button>
              </div>
            ))}
          </div>
        ) : (
          <p className="empty-note">暂无正文段落，先生成或填写正文。</p>
        )}
      </section>
    </>
  );
}

function workbenchNodeMeta(node: WorkbenchNode) {
  const role = workbenchNodeRoleLabel(node.nodeType);
  return node.factNodeType && node.factNodeType !== node.nodeType
    ? `${role} · ${node.factNodeType}`
    : role;
}

function workbenchNodeRoleLabel(nodeType: WorkbenchNode['nodeType']) {
  switch (nodeType) {
    case 'TITLE':
      return '标题';
    case 'RECIPIENT':
      return '主送';
    case 'BODY_SECTION':
      return '正文';
    case 'ATTACHMENT':
      return '附件';
    case 'SIGNATURE':
      return '落款';
    case 'DATE':
      return '日期';
    case 'HEADER':
      return '页眉';
    case 'FOOTER':
      return '页脚';
    default:
      return '静态结构';
  }
}

function workbenchNodeLabel(node: WorkbenchNode, index: number) {
  if (node.nodeType === 'BODY_SECTION') {
    return bodyNodeLabel(node, index);
  }
  if (node.editable === false || node.locked) {
    return node.content || node.label || workbenchNodeRoleLabel(node.nodeType);
  }
  return node.label || workbenchNodeRoleLabel(node.nodeType);
}

function draftNodeStatusLabel(status: string | undefined) {
  switch (status) {
    case 'EMPTY':
      return '空';
    case 'USER_FILLED':
      return '已填写';
    case 'AI_GENERATED':
      return 'AI';
    case 'FORMAT_OVERRIDDEN':
      return '格式';
    case 'LOCKED':
      return '锁定';
    default:
      return '草稿';
  }
}

function statusBadgeTone(status: string | undefined, editable: boolean) {
  if (!editable || status === 'LOCKED') {
    return 'muted';
  }
  if (status === 'EMPTY') {
    return 'danger';
  }
  if (status === 'AI_GENERATED' || status === 'USER_FILLED' || status === 'FORMAT_OVERRIDDEN') {
    return 'success';
  }
  return 'warning';
}
