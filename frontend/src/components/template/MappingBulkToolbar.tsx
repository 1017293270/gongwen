import { CheckCircle2, FileText, Heading1, Shield, XCircle } from 'lucide-react';
import { Button } from '../ui';

type MappingBulkToolbarProps = {
  selectedCount: number;
  onApplyRole: (role: string) => void;
};

export function MappingBulkToolbar({ selectedCount, onApplyRole }: MappingBulkToolbarProps) {
  const disabled = selectedCount === 0;

  return (
    <div className="mapping-bulk-toolbar" aria-label="批量映射工具">
      <span>{selectedCount} 个已选</span>
      <Button
        disabled={disabled}
        icon={<FileText aria-hidden="true" />}
        onClick={() => onApplyRole('BODY')}
        variant="secondary"
      >
        标为正文
      </Button>
      <Button
        disabled={disabled}
        icon={<Heading1 aria-hidden="true" />}
        onClick={() => onApplyRole('BODY_HEADING_LEVEL_1')}
        variant="secondary"
      >
        标为一级标题
      </Button>
      <Button
        disabled={disabled}
        icon={<Shield aria-hidden="true" />}
        onClick={() => onApplyRole('STATIC_TEXT')}
        variant="secondary"
      >
        标为固定文本
      </Button>
      <Button
        disabled={disabled}
        icon={<XCircle aria-hidden="true" />}
        onClick={() => onApplyRole('IGNORE')}
        variant="ghost"
      >
        忽略
      </Button>
      <CheckCircle2 aria-hidden="true" className="mapping-bulk-toolbar-icon" />
    </div>
  );
}
