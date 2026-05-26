import {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
  useEffect,
  useId,
} from 'react';
import './ui.css';

type Tone = 'info' | 'success' | 'warning' | 'error';

function cx(...classes: Array<string | false | null | undefined>) {
  return classes.filter(Boolean).join(' ');
}

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  icon?: ReactNode;
  iconOnly?: boolean;
  isLoading?: boolean;
  loadingLabel?: string;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
};

export function Button({
  children,
  className,
  disabled,
  icon,
  iconOnly = false,
  isLoading = false,
  loadingLabel = '处理中',
  type = 'button',
  variant = 'primary',
  ...props
}: ButtonProps) {
  const isDisabled = disabled || isLoading;
  const visibleLabel = isLoading ? loadingLabel : children;

  return (
    <button
      {...props}
      aria-busy={isLoading ? 'true' : undefined}
      className={cx(
        'ui-button',
        `ui-button-${variant}`,
        iconOnly && 'ui-button-icon-only',
        className,
      )}
      disabled={isDisabled}
      type={type}
    >
      {isLoading ? <span className="ui-spinner" aria-hidden="true" /> : icon}
      <span className={iconOnly ? 'ui-visually-hidden' : undefined}>{visibleLabel}</span>
    </button>
  );
}

type FieldChromeProps = {
  error?: string;
  hint?: string;
  id?: string;
  label: string;
  required?: boolean;
};

type FieldRenderProps = {
  describedBy?: string;
  fieldId: string;
  invalid: boolean;
};

function FieldChrome({
  children,
  error,
  hint,
  id,
  label,
  required,
}: FieldChromeProps & { children: (props: FieldRenderProps) => ReactNode }) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const hintId = hint ? `${fieldId}-hint` : undefined;
  const errorId = error ? `${fieldId}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={cx('ui-field', error && 'ui-field-error')}>
      <label className="ui-field-label" htmlFor={fieldId}>
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </label>
      {children({ describedBy, fieldId, invalid: Boolean(error) })}
      {hint ? (
        <span className="ui-field-hint" id={hintId}>
          {hint}
        </span>
      ) : null}
      {error ? (
        <span className="ui-field-error-text" id={errorId}>
          {error}
        </span>
      ) : null}
    </div>
  );
}

export type TextFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & FieldChromeProps;

export function TextField({ error, hint, id, label, required, className, ...props }: TextFieldProps) {
  return (
    <FieldChrome error={error} hint={hint} id={id} label={label} required={required}>
      {({ describedBy, fieldId, invalid }) => (
        <input
          {...props}
          aria-describedby={describedBy}
          aria-invalid={invalid ? 'true' : undefined}
          className={cx('ui-field-control', className)}
          id={fieldId}
          required={required}
        />
      )}
    </FieldChrome>
  );
}

export type TextareaFieldProps = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> &
  FieldChromeProps;

export function TextareaField({
  error,
  hint,
  id,
  label,
  required,
  className,
  ...props
}: TextareaFieldProps) {
  return (
    <FieldChrome error={error} hint={hint} id={id} label={label} required={required}>
      {({ describedBy, fieldId, invalid }) => (
        <textarea
          {...props}
          aria-describedby={describedBy}
          aria-invalid={invalid ? 'true' : undefined}
          className={cx('ui-field-control', 'ui-textarea-control', className)}
          id={fieldId}
          required={required}
        />
      )}
    </FieldChrome>
  );
}

export type SelectFieldProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> & FieldChromeProps;

export function SelectField({
  children,
  error,
  hint,
  id,
  label,
  required,
  className,
  ...props
}: SelectFieldProps) {
  return (
    <FieldChrome error={error} hint={hint} id={id} label={label} required={required}>
      {({ describedBy, fieldId, invalid }) => (
        <select
          {...props}
          aria-describedby={describedBy}
          aria-invalid={invalid ? 'true' : undefined}
          className={cx('ui-field-control', className)}
          id={fieldId}
          required={required}
        >
          {children}
        </select>
      )}
    </FieldChrome>
  );
}

export type PanelProps = {
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  eyebrow?: string;
  title: string;
};

export function Panel({ actions, children, className, eyebrow, title }: PanelProps) {
  return (
    <section aria-label={title} className={cx('ui-panel', className)}>
      <header className="ui-panel-header">
        <div>
          {eyebrow ? <p className="ui-panel-eyebrow">{eyebrow}</p> : null}
          <h2 className="ui-panel-title">{title}</h2>
        </div>
        {actions ? <div className="ui-panel-actions">{actions}</div> : null}
      </header>
      <div className="ui-panel-body">{children}</div>
    </section>
  );
}

export type StatusMessageProps = {
  children?: ReactNode;
  className?: string;
  title: string;
  tone?: Tone;
};

export function StatusMessage({
  children,
  className,
  title,
  tone = 'info',
}: StatusMessageProps) {
  return (
    <div
      className={cx('ui-status', `ui-status-${tone}`, className)}
      role={tone === 'error' ? 'alert' : 'status'}
    >
      <p className="ui-status-title">{title}</p>
      {children ? <div className="ui-status-body">{children}</div> : null}
    </div>
  );
}

export type EmptyStateProps = {
  action?: ReactNode;
  children?: ReactNode;
  className?: string;
  title: string;
};

export function EmptyState({ action, children, className, title }: EmptyStateProps) {
  return (
    <div className={cx('ui-empty', className)}>
      <p className="ui-empty-title">{title}</p>
      {children ? <div className="ui-empty-body">{children}</div> : null}
      {action ? <div className="ui-empty-action">{action}</div> : null}
    </div>
  );
}

export type DialogProps = {
  actions?: ReactNode;
  children?: ReactNode;
  className?: string;
  description?: string;
  onClose: () => void;
  open: boolean;
  title: string;
};

export function Dialog({
  actions,
  children,
  className,
  description,
  onClose,
  open,
  title,
}: DialogProps) {
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = originalOverflow;
    };
  }, [onClose, open]);

  if (!open) {
    return null;
  }

  return (
    <div className="ui-dialog-layer">
      <button aria-label="关闭弹窗" className="ui-dialog-backdrop" onClick={onClose} type="button" />
      <section
        aria-describedby={description ? descriptionId : undefined}
        aria-labelledby={titleId}
        aria-modal="true"
        className={cx('ui-dialog', className)}
        role="dialog"
      >
        <header className="ui-dialog-header">
          <h2 className="ui-dialog-title" id={titleId}>{title}</h2>
          {description ? (
            <p className="ui-dialog-description" id={descriptionId}>
              {description}
            </p>
          ) : null}
        </header>
        {children ? <div className="ui-dialog-body">{children}</div> : null}
        {actions ? <footer className="ui-dialog-actions">{actions}</footer> : null}
      </section>
    </div>
  );
}

export type ConfirmDialogProps = {
  cancelLabel?: string;
  confirmLabel?: string;
  description?: string;
  isConfirming?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  open: boolean;
  title: string;
};

export function ConfirmDialog({
  cancelLabel = '取消',
  confirmLabel = '确认',
  description,
  isConfirming = false,
  onCancel,
  onConfirm,
  open,
  title,
}: ConfirmDialogProps) {
  return (
    <Dialog
      actions={(
        <>
          <Button disabled={isConfirming} onClick={onCancel} variant="secondary">
            {cancelLabel}
          </Button>
          <Button isLoading={isConfirming} loadingLabel="处理中" onClick={onConfirm} variant="danger">
            {confirmLabel}
          </Button>
        </>
      )}
      description={description}
      onClose={onCancel}
      open={open}
      title={title}
    />
  );
}
