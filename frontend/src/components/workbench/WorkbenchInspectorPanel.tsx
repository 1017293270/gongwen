import type { ReactNode } from 'react';
import type { WorkbenchNode } from '../../draftTypes';
import { StatusMessage } from '../ui';

export type WorkbenchInspectorSection = 'ai' | 'format' | 'review' | 'export';

type WorkbenchInspectorPanelProps = {
  activeSection: WorkbenchInspectorSection;
  blockCount: number;
  candidateStatus: string;
  draftId: number | null;
  exportStatus: string;
  exportSlot: ReactNode;
  formatSlot: ReactNode;
  localOperationSlot: ReactNode;
  materialCount: number;
  node: WorkbenchNode | null;
  nodeCount: number;
  nodeKicker: string;
  nodeTitle: string;
  onSectionChange: (section: WorkbenchInspectorSection) => void;
  outlineSlot: ReactNode;
  outlineStatus: string;
  primaryAction: ReactNode;
  qualitySlot: ReactNode;
  qualityStatus: string;
  renderPreviewOutdated: boolean;
  statusMessage: string;
  statusTone: 'info' | 'success' | 'warning' | 'error';
};

const SECTIONS: Array<{
  description: string;
  id: WorkbenchInspectorSection;
  label: string;
}> = [
  { id: 'ai', label: 'AI', description: '提纲与局部改写' },
  { id: 'format', label: '格式', description: '当前节点字体、行距与对齐' },
  { id: 'review', label: '质检', description: '基础质检与问题摘要' },
  { id: 'export', label: '导出', description: '真实预览与 Word 导出' },
];

export function WorkbenchInspectorPanel({
  activeSection,
  blockCount,
  candidateStatus,
  draftId,
  exportStatus,
  exportSlot,
  formatSlot,
  localOperationSlot,
  materialCount,
  node,
  nodeCount,
  nodeKicker,
  nodeTitle,
  onSectionChange,
  outlineSlot,
  outlineStatus,
  primaryAction,
  qualitySlot,
  qualityStatus,
  renderPreviewOutdated,
  statusMessage,
  statusTone,
}: WorkbenchInspectorPanelProps) {
  return (
    <section className="panel workbench-inspector-panel" aria-label="AI 建议和质检">
      <div className="workbench-side-header workbench-inspector-header">
        <div>
          <p className="panel-kicker">{draftId ? `草稿 #${draftId}` : '草稿未载入'}</p>
          <h2 className="panel-title">节点检查器</h2>
        </div>
        <span className={`workbench-side-state ${nodeStatusTone(node)}`}>
          {nodeStatusLabel(node)}
        </span>
      </div>

      <div className="workbench-side-scroll">
        <div className="workbench-inspector-focus" aria-label="当前节点">
          <div className="workbench-inspector-focus-copy">
            <div className="outline-title">{nodeTitle}</div>
            <div className="panel-kicker">{nodeKicker}</div>
          </div>
          <div className="workbench-inspector-primary-action">
            {primaryAction}
          </div>
        </div>

        <div className="workbench-inspector-meter" aria-label="工作台状态">
          <span>{nodeCount} 节点</span>
          <span>{blockCount} 兼容块</span>
          <span>{materialCount} 材料</span>
        </div>

        <StatusMessage className="workbench-compact-status" title={statusMessage} tone={statusTone} />

        <nav className="workbench-inspector-rail" aria-label="检查器分组">
          {SECTIONS.map((section) => (
            <button
              aria-current={activeSection === section.id ? 'page' : undefined}
              aria-label={section.label}
              className={`workbench-inspector-tab ${activeSection === section.id ? 'selected' : ''}`}
              key={section.id}
              onClick={() => onSectionChange(section.id)}
              type="button"
            >
              <span>
                <strong>{section.label}</strong>
                <small>{section.description}</small>
              </span>
              <span className={`workbench-tab-state ${sectionStateTone(section.id, {
                candidateStatus,
                exportStatus,
                outlineStatus,
                qualityStatus,
                renderPreviewOutdated,
              })}`}>
                {sectionStateLabel(section.id, {
                  candidateStatus,
                  exportStatus,
                  outlineStatus,
                  qualityStatus,
                  renderPreviewOutdated,
                })}
              </span>
            </button>
          ))}
        </nav>

        <div className="workbench-inspector-content">
          {activeSection === 'ai' && (
            <div className="workbench-inspector-stack" aria-label="AI 生成">
              <section className="workbench-inspector-section">
                {outlineSlot}
              </section>
              <section className="workbench-inspector-section">
                {localOperationSlot}
              </section>
            </div>
          )}
          {activeSection === 'format' && (
            <div className="workbench-inspector-stack" aria-label="节点格式">
              {formatSlot}
            </div>
          )}
          {activeSection === 'review' && (
            <div className="workbench-inspector-stack" aria-label="质检与建议">
              {qualitySlot}
            </div>
          )}
          {activeSection === 'export' && (
            <div className="workbench-inspector-stack" aria-label="预览与导出">
              {exportSlot}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

function nodeStatusLabel(node: WorkbenchNode | null) {
  if (!node) {
    return '未选中';
  }
  if (node.locked || node.editable === false) {
    return '固定';
  }
  switch (node.status) {
    case 'EMPTY':
      return '待填写';
    case 'AI_GENERATED':
      return 'AI';
    case 'FORMAT_OVERRIDDEN':
      return '已调格式';
    case 'LOCKED':
      return '锁定';
    case 'USER_FILLED':
      return '已填写';
    default:
      return '草稿';
  }
}

function nodeStatusTone(node: WorkbenchNode | null) {
  if (!node || node.locked || node.editable === false || node.status === 'LOCKED') {
    return 'muted';
  }
  if (node.status === 'EMPTY') {
    return 'warning';
  }
  if (node.status === 'AI_GENERATED' || node.status === 'FORMAT_OVERRIDDEN' || node.status === 'USER_FILLED') {
    return 'success';
  }
  return 'info';
}

type SectionStateInputs = {
  candidateStatus: string;
  exportStatus: string;
  outlineStatus: string;
  qualityStatus: string;
  renderPreviewOutdated: boolean;
};

function sectionStateLabel(section: WorkbenchInspectorSection, inputs: SectionStateInputs) {
  if (section === 'ai') {
    if (inputs.candidateStatus === 'generating' || inputs.outlineStatus === 'generating') {
      return '生成中';
    }
    if (inputs.candidateStatus === 'error' || inputs.outlineStatus === 'error') {
      return '需重试';
    }
    return '就绪';
  }
  if (section === 'format') {
    return inputs.renderPreviewOutdated ? '待预览' : '可编辑';
  }
  if (section === 'review') {
    if (inputs.qualityStatus === 'checking') {
      return '检查中';
    }
    if (inputs.qualityStatus === 'error') {
      return '失败';
    }
    if (inputs.qualityStatus === 'success') {
      return '有结果';
    }
    return '未运行';
  }
  if (inputs.exportStatus === 'exporting') {
    return '导出中';
  }
  if (inputs.exportStatus === 'error' || inputs.exportStatus === 'blocked') {
    return '受阻';
  }
  if (inputs.exportStatus === 'success') {
    return '已导出';
  }
  return inputs.renderPreviewOutdated ? '待刷新' : '可用';
}

function sectionStateTone(section: WorkbenchInspectorSection, inputs: SectionStateInputs) {
  const label = sectionStateLabel(section, inputs);
  if (label === '需重试' || label === '失败' || label === '受阻') {
    return 'warning';
  }
  if (label === '生成中' || label === '检查中' || label === '导出中' || label === '待刷新' || label === '待预览') {
    return 'info';
  }
  if (label === '就绪' || label === '有结果' || label === '已导出' || label === '可用') {
    return 'success';
  }
  return 'muted';
}
