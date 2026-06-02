import { useEffect, useState } from 'react';
import { ChevronDown, Plus, RotateCcw, Trash2 } from 'lucide-react';
import { bodyNodeLabel, bodyNodePreview } from '../../workbenchNodes';
import type { DraftNodeRoleOption, InsertDraftNodePosition, WorkbenchNode } from '../../draftTypes';
import { Button, StatusMessage } from '../ui';

export type ReinitializeNodeStatus = 'idle' | 'running' | 'success' | 'error';

type WorkbenchStructureTreeProps = {
  nodes: WorkbenchNode[];
  bodySectionNodes: WorkbenchNode[];
  selectedNodeId: string | null;
  canReinitialize: boolean;
  reinitializeStatus: ReinitializeNodeStatus;
  reinitializeMessage: string;
  canInsertBodyStructure?: boolean;
  insertableRoles?: DraftNodeRoleOption[];
  onSelectNode: (nodeId: string) => void;
  onInsertBodyStructure?: (request: { role: string; anchorNodeId: number | null; position: InsertDraftNodePosition }) => void;
  onRemoveBodyNode: (node: WorkbenchNode) => void;
  onReinitialize: (preserveUserEditedNodes: boolean) => void;
};

export function WorkbenchStructureTree({
  nodes,
  bodySectionNodes,
  selectedNodeId,
  canReinitialize,
  reinitializeStatus,
  reinitializeMessage,
  canInsertBodyStructure = false,
  insertableRoles = [],
  onSelectNode,
  onInsertBodyStructure,
  onRemoveBodyNode,
  onReinitialize,
}: WorkbenchStructureTreeProps) {
  const [bodyGroupOpen, setBodyGroupOpen] = useState(true);
  const [insertPosition, setInsertPosition] = useState<InsertDraftNodePosition>('AFTER');
  const [insertRole, setInsertRole] = useState('');
  const structureNodes = nodes.filter((node) => node.nodeType !== 'BODY_SECTION');
  const bodyGroupSelected = bodySectionNodes.some((node) => node.nodeId === selectedNodeId);
  const selectedBodyNode = bodySectionNodes.find((node) => node.nodeId === selectedNodeId) ?? null;
  const availableInsertRoles = insertableRoles.length > 0 ? insertableRoles : defaultInsertableRoles();
  const activeInsertRole = insertRole || availableInsertRoles[0]?.role || 'BODY_HEADING_LEVEL_1';

  useEffect(() => {
    if (bodyGroupSelected) {
      setBodyGroupOpen(true);
    }
  }, [bodyGroupSelected]);

  useEffect(() => {
    if (!insertRole && availableInsertRoles.length > 0) {
      setInsertRole(availableInsertRoles[0].role);
    }
  }, [availableInsertRoles, insertRole]);

  function handleInsertBodyStructure() {
    if (!onInsertBodyStructure) {
      return;
    }
    onInsertBodyStructure({
      role: activeInsertRole,
      anchorNodeId: anchorNodeIdForInsert(selectedBodyNode, insertPosition),
      position: insertPosition,
    });
  }

  return (
    <>
      <section className="structure-tree" aria-label="结构节点树">
        <div className="paragraph-index-header">
          <span className="field-label">结构树</span>
          <span className="paragraph-count">{structureNodes.length} 结构 · {bodySectionNodes.length} 正文</span>
        </div>
        <div className="workbench-structure-actions">
          <p>按当前已发布映射重新生成节点。默认使用原稿内容覆盖旧节点；需要保留已有编辑时可单独选择。</p>
          <div className="workbench-structure-action-row">
            <Button
              disabled={!canReinitialize || reinitializeStatus === 'running'}
              icon={<RotateCcw aria-hidden="true" />}
              isLoading={reinitializeStatus === 'running'}
              loadingLabel="正在重建结构"
              onClick={() => onReinitialize(false)}
              variant="secondary"
            >
              从原稿重建结构
            </Button>
            <Button
              disabled={!canReinitialize || reinitializeStatus === 'running'}
              icon={<RotateCcw aria-hidden="true" />}
              onClick={() => onReinitialize(true)}
              variant="ghost"
            >
              保留编辑重建
            </Button>
          </div>
        </div>
        {reinitializeMessage && (
          <StatusMessage
            title={reinitializeMessage}
            tone={reinitializeStatus === 'error' ? 'warning' : 'success'}
          />
        )}
        <div className="workbench-insert-structure" aria-label="新增正文结构">
          <label>
            <span>插入位置</span>
            <select
              aria-label="插入位置"
              disabled={!canInsertBodyStructure}
              onChange={(event) => setInsertPosition(event.target.value as InsertDraftNodePosition)}
              value={insertPosition}
            >
              <option value="AFTER">当前结构之后</option>
              <option value="BEFORE">当前结构之前</option>
              <option value="END_OF_BODY">正文末尾</option>
            </select>
          </label>
          <label>
            <span>新增结构</span>
            <select
              aria-label="新增结构"
              disabled={!canInsertBodyStructure}
              onChange={(event) => setInsertRole(event.target.value)}
              value={activeInsertRole}
            >
              {availableInsertRoles.map((role) => (
                <option key={role.role} value={role.role}>{role.label}</option>
              ))}
            </select>
          </label>
          <Button
            disabled={!canInsertBodyStructure || !onInsertBodyStructure || (insertPosition !== 'END_OF_BODY' && !selectedBodyNode)}
            icon={<Plus aria-hidden="true" />}
            onClick={handleInsertBodyStructure}
            variant="secondary"
          >
            新增结构
          </Button>
        </div>
        {nodes.length > 0 ? (
          <div className="structure-tree-list">
            {structureNodes.map((node, index) => {
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
            <details
              aria-label="正文段落目录"
              className={`structure-tree-body-group ${bodyGroupSelected ? 'selected' : ''}`}
              onToggle={(event) => setBodyGroupOpen(event.currentTarget.open)}
              open={bodyGroupOpen}
            >
              <summary className="structure-tree-body-summary">
                <span className="structure-tree-body-summary-main">
                  <ChevronDown aria-hidden="true" size={16} />
                  <span>
                    <span className="structure-tree-body-title">正文</span>
                    <span className="structure-tree-body-meta">展开查看正文详细节点</span>
                  </span>
                </span>
                <span className="paragraph-count">{bodySectionNodes.length} 段</span>
              </summary>
              {bodySectionNodes.length > 0 ? (
                <div className="paragraph-index-list structure-tree-body-list">
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
            </details>
          </div>
        ) : (
          <p className="empty-note">暂无结构节点，先绑定模板或填写正文。</p>
        )}
      </section>
    </>
  );
}

function defaultInsertableRoles(): DraftNodeRoleOption[] {
  return [
    { role: 'BODY', label: '正文段落', createsBodyPair: false },
  ];
}

function anchorNodeIdForInsert(node: WorkbenchNode | null, position: InsertDraftNodePosition) {
  if (position === 'END_OF_BODY' || !node) {
    return null;
  }
  if (position === 'BEFORE') {
    return node.headingDraftNodeId ?? node.draftNodeId ?? null;
  }
  return node.draftNodeId ?? node.headingDraftNodeId ?? null;
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
