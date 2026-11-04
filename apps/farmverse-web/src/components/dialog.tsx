import { createContext, useCallback, useContext, useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { Button } from './ui'
import { vi } from '../i18n/vi'

/**
 * Hộp thoại dùng thẻ <dialog> gốc của trình duyệt với showModal():
 *  - focus tự vào hộp và bị giữ trong hộp; phần trang phía sau thành inert (Tab không ra ngoài được);
 *  - phím Escape đóng hộp; khi đóng, trình duyệt trả focus về phần tử đã mở hộp;
 *  - aria-modal và vai trò dialog có sẵn, tên hộp nối bằng aria-labelledby.
 * Không tự viết focus trap: dễ sai và trình duyệt đã làm đúng.
 */
export function Dialog({ open, onClose, title, description, children, className = '' }: {
  open: boolean
  onClose: () => void
  title: string
  description?: ReactNode
  children: ReactNode
  className?: string
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog ref={ref} aria-labelledby={titleId} aria-describedby={description ? descId : undefined}
      onClose={onClose}
      onCancel={event => { event.preventDefault(); onClose() }}            // Escape
      onClick={event => { if (event.target === event.currentTarget) onClose() }}   // bấm vào nền mờ
      className={`m-auto w-[min(32rem,calc(100vw-2rem))] rounded-2xl border border-stone-200 bg-white p-0 text-ink shadow-2xl backdrop:bg-black/40 ${className}`}>
      <div className="p-5">
        <h2 id={titleId} className="text-base font-semibold">{title}</h2>
        {description && <div id={descId} className="mt-2 text-sm text-stone-700">{description}</div>}
        <div className="mt-4">{children}</div>
      </div>
    </dialog>
  )
}

type ConfirmOptions = {
  title: string
  body?: ReactNode
  confirmLabel: string
  /** Thao tác khó hoàn tác: nút xác nhận màu đỏ, focus mặc định ở nút Hủy. */
  danger?: boolean
}

const ConfirmContext = createContext<((options: ConfirmOptions) => Promise<boolean>) | null>(null)

/** Một hộp xác nhận dùng chung cho cả ứng dụng; gọi qua useConfirm(). */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<(ConfirmOptions & { resolve: (ok: boolean) => void }) | null>(null)

  const confirm = useCallback((options: ConfirmOptions) =>
    new Promise<boolean>(resolve => setPending({ ...options, resolve })), [])

  const finish = (ok: boolean) => {
    pending?.resolve(ok)
    setPending(null)
  }

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Dialog open={!!pending} onClose={() => finish(false)} title={pending?.title ?? ''} description={pending?.body}>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" autoFocus={pending?.danger} onClick={() => finish(false)}>{vi.common.cancel}</Button>
          <Button type="button" variant={pending?.danger ? 'danger' : 'primary'} autoFocus={!pending?.danger}
            onClick={() => finish(true)}>{pending?.confirmLabel}</Button>
        </div>
      </Dialog>
    </ConfirmContext.Provider>
  )
}

/** const confirm = useConfirm(); if (await confirm({ ... })) mutate() */
export function useConfirm() {
  const confirm = useContext(ConfirmContext)
  if (!confirm) throw new Error('useConfirm must be used inside ConfirmProvider')
  return confirm
}
