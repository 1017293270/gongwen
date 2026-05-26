import { cleanup, render, screen } from '@testing-library/react';
import { Save } from 'lucide-react';
import { afterEach, describe, expect, it } from 'vitest';
import {
  Button,
  ConfirmDialog,
  EmptyState,
  Panel,
  SelectField,
  StatusMessage,
  TextField,
  TextareaField,
} from './index';

describe('UI primitives', () => {
  afterEach(() => {
    cleanup();
  });

  it('renders a loading button with stable disabled semantics', () => {
    render(<Button isLoading loadingLabel="保存中">保存</Button>);

    const button = screen.getByRole('button', { name: '保存中' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    expect(button).toHaveClass('ui-button', 'ui-button-primary');
  });

  it('renders icon buttons without losing accessible names', () => {
    render(
      <Button icon={<Save aria-hidden="true" />} iconOnly aria-label="保存草稿">
        保存草稿
      </Button>,
    );

    expect(screen.getByRole('button', { name: '保存草稿' })).toHaveClass('ui-button-icon-only');
  });

  it('connects field labels, hints, and errors', () => {
    render(
      <TextField
        error="请输入标题"
        hint="用于生成提纲和导出文件名"
        label="标题"
        name="title"
      />,
    );

    const input = screen.getByLabelText('标题');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('用于生成提纲和导出文件名 请输入标题');
  });

  it('supports textarea and select field shells', () => {
    render(
      <>
        <TextareaField label="正文" name="body" />
        <SelectField label="文种" name="documentType">
          <option value="NOTICE">通知</option>
        </SelectField>
      </>,
    );

    expect(screen.getByLabelText('正文')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '文种' })).toBeInTheDocument();
  });

  it('renders reusable status, empty, and panel surfaces', () => {
    render(
      <Panel
        actions={<Button variant="secondary">重试</Button>}
        eyebrow="AI"
        title="局部建议"
      >
        <StatusMessage tone="warning" title="需要选择段落">
          请选择正文段落后再生成建议。
        </StatusMessage>
        <EmptyState action={<Button>新建草稿</Button>} title="暂无草稿">
          创建草稿后开始起草。
        </EmptyState>
      </Panel>,
    );

    expect(screen.getByRole('region', { name: '局部建议' })).toBeInTheDocument();
    expect(screen.getByText('需要选择段落')).toBeInTheDocument();
    expect(screen.getByText('暂无草稿')).toBeInTheDocument();
  });

  it('renders confirmation dialogs with accessible actions', () => {
    render(
      <ConfirmDialog
        cancelLabel="继续编辑"
        confirmLabel="放弃建议"
        description="建议内容不会写入草稿。"
        onCancel={() => undefined}
        onConfirm={() => undefined}
        open
        title="放弃这条建议？"
      />,
    );

    const dialog = screen.getByRole('dialog', { name: '放弃这条建议？' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByText('建议内容不会写入草稿。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '继续编辑' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '放弃建议' })).toHaveClass('ui-button-danger');
  });
});
