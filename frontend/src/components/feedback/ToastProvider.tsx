import * as ToastPrimitive from '@radix-ui/react-toast';
import { createContext, ReactNode, useCallback, useContext, useMemo, useState } from 'react';

type ToastTone = 'success' | 'error' | 'info';

type ToastInput = {
  title: string;
  description?: string;
  tone?: ToastTone;
};

type ToastState = Required<Pick<ToastInput, 'title' | 'tone'>> & Pick<ToastInput, 'description'>;

type ToastContextValue = {
  showToast: (toast: ToastInput) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const [toast, setToast] = useState<ToastState>({
    title: '',
    description: '',
    tone: 'info',
  });

  const showToast = useCallback((nextToast: ToastInput) => {
    setToast({
      title: nextToast.title,
      description: nextToast.description,
      tone: nextToast.tone ?? 'info',
    });
    setOpen(true);
  }, []);

  const value = useMemo(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      <ToastPrimitive.Provider duration={3600} swipeDirection="right">
        {children}
        <ToastPrimitive.Root
          className="toast-root"
          data-tone={toast.tone}
          onOpenChange={setOpen}
          open={open}
        >
          <ToastPrimitive.Title className="toast-title">{toast.title}</ToastPrimitive.Title>
          {toast.description && (
            <ToastPrimitive.Description className="toast-description">
              {toast.description}
            </ToastPrimitive.Description>
          )}
        </ToastPrimitive.Root>
        <ToastPrimitive.Viewport className="toast-viewport" />
      </ToastPrimitive.Provider>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within ToastProvider');
  }
  return context;
}
