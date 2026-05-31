import { ChevronRight } from 'lucide-react';
import type { DocumentStructureNode, StructureMappingItem } from '../../draftTypes';

export const MAPPING_ROLE_OPTIONS = [
  { value: 'UNKNOWN', label: '待确认' },
  { value: 'TITLE', label: '标题' },
  { value: 'ISSUING_ORGAN', label: '发文机关' },
  { value: 'DOC_NUMBER', label: '文号' },
  { value: 'RECIPIENT', label: '主送' },
  { value: 'BODY', label: '正文' },
  { value: 'BODY_HEADING_LEVEL_1', label: '一级标题' },
  { value: 'BODY_HEADING_LEVEL_2', label: '二级标题' },
  { value: 'ATTACHMENT_NOTE', label: '附件说明' },
  { value: 'SIGNATURE', label: '落款' },
  { value: 'DATE', label: '日期' },
  { value: 'STATIC_TEXT', label: '固定文本' },
  { value: 'TABLE_ATTACHMENT', label: '表格附件' },
  { value: 'IGNORE', label: '忽略' },
];

type StructureNodeTreeProps = {
  nodes: DocumentStructureNode[];
  mappingItems: StructureMappingItem[];
  selectedNodeKeys: Set<string>;
  onToggleNode: (nodeKey: string) => void;
  onRoleChange: (nodeKey: string, role: string, sortOrder: number) => void;
};

export function StructureNodeTree({
  nodes,
  mappingItems,
  selectedNodeKeys,
  onToggleNode,
  onRoleChange,
}: StructureNodeTreeProps) {
  const mappingByNodeKey = new Map(mappingItems.map((item) => [item.nodeKey, item]));

  return (
    <div className="template-node-tree" aria-label="模板结构树">
      {nodes.map((node) => {
        const preview = node.textPreview || node.text || node.nodeKey;
        const selected = selectedNodeKeys.has(node.nodeKey);
        const currentRole = mappingByNodeKey.get(node.nodeKey)?.role ?? node.roleSuggestion ?? 'UNKNOWN';

        return (
          <article className="template-node-row" key={node.nodeKey}>
            <label className="template-node-select">
              <input
                aria-label={`选择结构节点：${preview}`}
                checked={selected}
                onChange={() => onToggleNode(node.nodeKey)}
                type="checkbox"
              />
            </label>
            <ChevronRight aria-hidden="true" className="template-node-caret" />
            <div className="template-node-content">
              <div className="template-node-title-row">
                <strong>{preview}</strong>
                <span className="template-node-type-chip">{node.nodeType}</span>
              </div>
              <div className="template-node-facts">
                <span>事实：{node.nodeType}</span>
                <span>建议：{roleLabel(node.roleSuggestion || 'UNKNOWN')}</span>
                <span>{node.path}</span>
              </div>
              {node.text && node.text !== preview && <p>{node.text}</p>}
              {node.riskCodes.length > 0 && (
                <div className="template-node-risks" aria-label={`${preview} 风险`}>
                  {node.riskCodes.map((risk) => (
                    <small key={risk}>{risk}</small>
                  ))}
                </div>
              )}
            </div>
            <label className="template-node-role-control">
              <span>角色</span>
              <select
                aria-label={`映射角色：${preview}`}
                onChange={(event) => onRoleChange(node.nodeKey, event.target.value, node.orderIndex)}
                value={currentRole}
              >
                {MAPPING_ROLE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
          </article>
        );
      })}
    </div>
  );
}

export function roleLabel(role: string) {
  return MAPPING_ROLE_OPTIONS.find((option) => option.value === role)?.label ?? role;
}
