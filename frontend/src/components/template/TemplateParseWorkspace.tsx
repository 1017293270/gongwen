import { CheckCircle2, ClipboardList, RotateCcw, Save } from 'lucide-react';
import { useMemo, useState } from 'react';
import { getRenderPreviewPageUrl } from '../../api';
import type {
  DocumentRenderPreview,
  DocumentStructureProfile,
  StructureMappingItem,
  StructureMappingProfile,
  TemplateDocumentKind,
  TemplateProfile,
} from '../../draftTypes';
import { Button, StatusMessage } from '../ui';
import { MappingBulkToolbar } from './MappingBulkToolbar';
import { StructureNodeTree } from './StructureNodeTree';

export type TemplateParseWorkspaceProps = {
  documentKind: TemplateDocumentKind | null;
  mappingItems: StructureMappingItem[];
  mappingMessage: string;
  mappingProfile: StructureMappingProfile | null;
  mappingStatus: 'idle' | 'loading' | 'saving' | 'publishing' | 'blocked' | 'error';
  profile: TemplateProfile;
  structureProfile: DocumentStructureProfile | null;
  renderPreview: DocumentRenderPreview | null;
  renderPreviewStatus: 'idle' | 'loading' | 'requesting' | 'error';
  renderPreviewMessage: string;
  onMappingRoleChange: (nodeKey: string, role: string, sortOrder: number) => void;
  onPublishMapping: () => void;
  onSaveMappingDraft: () => void;
  onRequestRenderPreview: () => void;
};

export function TemplateParseWorkspace({
  documentKind,
  mappingItems,
  mappingMessage,
  mappingProfile,
  mappingStatus,
  profile,
  structureProfile,
  renderPreview,
  renderPreviewStatus,
  renderPreviewMessage,
  onMappingRoleChange,
  onPublishMapping,
  onSaveMappingDraft,
  onRequestRenderPreview,
}: TemplateParseWorkspaceProps) {
  const [selectedNodeKeys, setSelectedNodeKeys] = useState<Set<string>>(new Set());
  const warnings = documentKind?.blockingWarnings ?? [];
  const blocksAutoTemplate = documentKind?.recommendedWorkflow === 'BLOCK_AUTO_TEMPLATE' || warnings.length > 0;
  const nodes = structureProfile?.nodes ?? [];
  const selectedNodes = useMemo(
    () => nodes.filter((node) => selectedNodeKeys.has(node.nodeKey)),
    [nodes, selectedNodeKeys],
  );

  function handleToggleNode(nodeKey: string) {
    setSelectedNodeKeys((current) => {
      const next = new Set(current);
      if (next.has(nodeKey)) {
        next.delete(nodeKey);
      } else {
        next.add(nodeKey);
      }
      return next;
    });
  }

  function handleApplyRole(role: string) {
    selectedNodes.forEach((node) => {
      onMappingRoleChange(node.nodeKey, role, node.orderIndex);
    });
  }

  return (
    <div className="template-parse-workspace">
      <section className="template-profile-box template-profile-wide">
        <div className="template-workspace-section-header">
          <div>
            <h3>文档类型</h3>
            <p>文种仅用于分类和提示倾向，结构事实来自 DOCX 节点。</p>
          </div>
          <span className={`status-chip ${blocksAutoTemplate ? 'danger' : ''}`}>
            {documentKindLabel(documentKind?.documentKind ?? 'UNKNOWN_DOCUMENT')}
          </span>
        </div>

        {documentKind?.documentKind === 'REFERENCE_DOCUMENT' && (
          <StatusMessage title="该文件是参考范文，必须确认映射后再用于套版。" tone="warning" />
        )}
        {blocksAutoTemplate && (
          <StatusMessage
            title="该文件不适合直接作为自动套版模板"
            tone="warning"
          >
            {(warnings.length > 0 ? warnings : ['建议先换用标准模板或范文，再进入结构映射。']).map((warning) => (
              <p key={warning}>{warning}</p>
            ))}
          </StatusMessage>
        )}

        <div className="template-kind-grid">
          <div>
            <span>推荐流程</span>
            <strong>{workflowLabel(documentKind?.recommendedWorkflow ?? 'REVIEW_REQUIRED')}</strong>
          </div>
          <div>
            <span>置信度</span>
            <strong>{Math.round((documentKind?.confidence ?? 0) * 100)}%</strong>
          </div>
          <div>
            <span>来源</span>
            <strong>{documentKind?.source || 'profile'}</strong>
          </div>
          <div>
            <span>文种</span>
            <strong>{documentKind?.documentTypeCode || 'UNKNOWN'}</strong>
          </div>
        </div>
        {documentKind?.reasonCodes && documentKind.reasonCodes.length > 0 && (
          <div className="template-reason-list" aria-label="识别原因">
            {documentKind.reasonCodes.map((reason) => (
              <span key={reason}>{reason}</span>
            ))}
          </div>
        )}
      </section>

      <section className="template-profile-box template-profile-wide">
        <div className="template-workspace-section-header">
          <div>
            <h3>结构事实</h3>
            <p>{structureProfile ? `${nodes.length} 个结构节点 · ${structureProfile.extractorVersion}` : '结构 profile 暂不可用，使用模板 profile 兜底展示。'}</p>
          </div>
        </div>
        {nodes.length > 0 ? (
          <>
            <MappingBulkToolbar
              selectedCount={selectedNodeKeys.size}
              onApplyRole={handleApplyRole}
            />
            <StructureNodeTree
              mappingItems={mappingItems}
              nodes={nodes}
              selectedNodeKeys={selectedNodeKeys}
              onRoleChange={onMappingRoleChange}
              onToggleNode={handleToggleNode}
            />
          </>
        ) : (profile.structures ?? []).length > 0 ? (
          <div className="template-node-tree" aria-label="模板结构树">
            {(profile.structures ?? []).map((structure) => (
              <article className="template-node-row template-node-row-fallback" key={structure.structureKey}>
                <div className="template-node-content">
                  <div className="template-node-title-row">
                    <strong>{structure.label}</strong>
                    <span className="template-node-type-chip">{structure.structureType}</span>
                  </div>
                  <div className="template-node-facts">
                    <span>{locationLabel(structure.locationType)}</span>
                    <span>{structure.source}</span>
                  </div>
                  <p>{structure.textPreview || '该结构暂无可展示文字'}</p>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <p className="empty-note">未识别到可展示的结构节点。</p>
        )}
      </section>

      <section className="template-profile-box">
        <h3>占位符</h3>
        {profile.placeholders.length === 0 ? (
          <p className="empty-note">未识别到显式占位符。</p>
        ) : profile.placeholders.map((placeholder) => (
          <div className="template-profile-item" key={`${placeholder.key}-${placeholder.paragraphKey}`}>
            <ClipboardList aria-hidden="true" />
            <span>{placeholder.key}</span>
            {placeholder.splitAcrossRuns && <small>跨 run</small>}
          </div>
        ))}
      </section>

      <section className="template-profile-box">
        <h3>解析风险</h3>
        {profile.validationItems.length === 0 && (structureProfile?.risks ?? []).length === 0 ? (
          <p className="empty-note">未发现解析风险。</p>
        ) : (
          [...profile.validationItems, ...(structureProfile?.risks ?? [])].map((item, index) => (
            <div className="template-profile-risk" key={`${item.code}-${item.message}-${index}`}>
              <strong>{item.code}</strong>
              <span>{item.message}</span>
            </div>
          ))
        )}
      </section>

      <section className="template-profile-box template-profile-wide">
        <div className="template-workspace-section-header">
          <div>
            <h3>结构映射</h3>
            <p>{mappingProfile ? `v${mappingProfile.versionNo} · ${mappingProfile.status} · ${mappingProfile.confirmedCount} 个已确认` : mappingMessage || '映射暂不可用'}</p>
          </div>
          <div className="template-mapping-actions">
            <Button
              disabled={!mappingProfile || mappingStatus === 'saving' || mappingStatus === 'publishing'}
              icon={<Save aria-hidden="true" />}
              isLoading={mappingStatus === 'saving'}
              loadingLabel="保存中"
              onClick={onSaveMappingDraft}
              variant="secondary"
            >
              保存草稿
            </Button>
            <Button
              disabled={!mappingProfile || mappingStatus === 'saving' || mappingStatus === 'publishing'}
              icon={<CheckCircle2 aria-hidden="true" />}
              isLoading={mappingStatus === 'publishing'}
              loadingLabel="发布中"
              onClick={onPublishMapping}
            >
              发布映射
            </Button>
          </div>
        </div>
        {mappingStatus === 'error' && (
          <StatusMessage title={mappingMessage || '结构映射不可用'} tone="warning" />
        )}
        {mappingStatus === 'blocked' && (
          <StatusMessage title={mappingMessage || '映射发布被阻断'} tone="warning">
            {(mappingProfile?.validationItems ?? []).map((item) => (
              <p key={`${item.code}-${item.role ?? ''}-${item.message}`}>{item.message}</p>
            ))}
          </StatusMessage>
        )}
        {mappingStatus === 'idle' && mappingMessage && (
          <p className="empty-note">{mappingMessage}</p>
        )}
      </section>

      <RenderPreviewPanel
        message={renderPreviewMessage}
        preview={renderPreview}
        status={renderPreviewStatus}
        onRequest={onRequestRenderPreview}
      />
    </div>
  );
}

function RenderPreviewPanel({
  preview,
  status,
  message,
  onRequest,
}: {
  preview: DocumentRenderPreview | null;
  status: 'idle' | 'loading' | 'requesting' | 'error';
  message: string;
  onRequest: () => void;
}) {
  return (
    <section className="template-profile-box">
      <div className="template-workspace-section-header">
        <div>
          <h3>原貌预览</h3>
          <p>预览由后端渲染任务生成，当前只展示状态和页面入口。</p>
        </div>
        <Button
          disabled={status === 'loading' || status === 'requesting'}
          icon={<RotateCcw aria-hidden="true" />}
          isLoading={status === 'requesting'}
          loadingLabel="提交中"
          onClick={onRequest}
          variant="secondary"
        >
          生成预览
        </Button>
      </div>

      {status === 'loading' && (
        <StatusMessage title={message || '正在读取渲染预览状态'} tone="info" />
      )}
      {status === 'error' && (
        <StatusMessage title={message || '渲染预览状态加载失败'} tone="warning" />
      )}
      {preview?.status === 'RENDERING' && (
        <StatusMessage title="渲染预览生成中" tone="info" />
      )}
      {preview?.status === 'FAILED' && (
        <StatusMessage title="预览生成失败" tone="warning">
          <p>{renderPreviewIssueMessage(preview)}</p>
        </StatusMessage>
      )}
      {preview?.status === 'UNSUPPORTED' && (
        <StatusMessage title="当前环境暂不支持渲染预览" tone="warning">
          <p>{renderPreviewIssueMessage(preview)}</p>
        </StatusMessage>
      )}
      {!preview && status === 'idle' && (
        <p className="empty-note">尚未生成预览。</p>
      )}
      {preview?.status === 'PENDING' && (
        <StatusMessage title={message || '预览任务已提交，稍后可再次刷新状态。'} tone="info" />
      )}
      {preview?.status === 'READY' && (
        <div className="template-preview-page-list" aria-label="渲染预览页面">
          <span>{preview.pageCount} 页 · {preview.renderer}</span>
          {preview.id && preview.manifest.pages.length > 0 ? preview.manifest.pages.slice(0, 4).map((page) => (
            <a href={getRenderPreviewPageUrl(preview.id as number, page.pageNumber)} key={page.pageNumber} rel="noreferrer" target="_blank">
              第 {page.pageNumber} 页 · {page.widthPixels}×{page.heightPixels}
            </a>
          )) : (
            <span>暂无可下载页面。</span>
          )}
        </div>
      )}
    </section>
  );
}

function documentKindLabel(documentKind: string) {
  const labels: Record<string, string> = {
    PLACEHOLDER_TEMPLATE: '占位符模板',
    STYLE_TEMPLATE: '样式模板',
    REFERENCE_DOCUMENT: '参考范文',
    OFFICIAL_DOCUMENT: '正式公文',
    MANUAL_OR_GUIDE: '手册/说明',
    POLICY_OR_REGULATION: '制度/规范',
    ORDINARY_DOCUMENT: '普通文档',
    UNKNOWN_DOCUMENT: '待确认',
  };
  return labels[documentKind] ?? documentKind;
}

function workflowLabel(workflow: string) {
  const labels: Record<string, string> = {
    AUTO_TEMPLATE: '可进入自动套版',
    REVIEW_AND_MAP: '先审核并映射结构',
    REVIEW_AND_ADD_PLACEHOLDERS: '先审核并补占位符',
    BLOCK_AUTO_TEMPLATE: '阻断自动套版',
    REVIEW_REQUIRED: '需要人工确认',
  };
  return labels[workflow] ?? workflow;
}

function renderPreviewIssueMessage(preview: DocumentRenderPreview) {
  if (preview.errorCode === 'RENDER_PREVIEW_UNSUPPORTED'
    && (preview.errorMessage ?? '').toLowerCase().includes('libreoffice')) {
    return '本机没有可用的 LibreOffice，无法把 DOCX 渲染成原貌预览。安装 LibreOffice 后，或把 GONGWEN_LIBREOFFICE_PATH 指到 soffice.exe，再重新生成预览。';
  }
  return preview.errorMessage ?? preview.errorCode ?? '请稍后重试。';
}

function locationLabel(locationType: string) {
  const labels: Record<string, string> = {
    PARAGRAPH: '正文段落',
    HEADER: '页眉',
    FOOTER: '页脚',
    TABLE: '表格',
  };
  return labels[locationType] ?? locationType;
}
