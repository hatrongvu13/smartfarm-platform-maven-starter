import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

type NotificationTone = 'info' | 'success' | 'warning' | 'error'
export type AppNotification = {
  id: string
  title: string
  message: string
  tone: NotificationTone
  createdAt: string
  read: boolean
  details?: string[]
  key?: string
}
type NotifyInput = Omit<AppNotification, 'id' | 'createdAt' | 'read'>
type NotificationContextValue = {
  notifications: AppNotification[]
  unreadCount: number
  notify: (input: NotifyInput) => void
  markAllRead: () => void
  clearAll: () => void
}
const Context = createContext<NotificationContextValue | null>(null)
const toneClass: Record<NotificationTone, string> = {
  info: 'border-blue-200 bg-blue-50 text-blue-900',
  success: 'border-green-200 bg-green-50 text-green-900',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
  error: 'border-red-200 bg-red-50 text-red-900',
}

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const notify = useCallback((input: NotifyInput) => {
    setNotifications(current => {
      const same = input.key ? current.find(value => value.key === input.key) : undefined
      const next: AppNotification = {
        ...input,
        id: same?.id ?? crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`,
        createdAt: new Date().toISOString(),
        read: false,
      }
      return [next, ...current.filter(value => value.id !== same?.id)].slice(0, 100)
    })
  }, [])
  const markAllRead = useCallback(() => setNotifications(values => values.map(value => ({ ...value, read: true }))), [])
  const clearAll = useCallback(() => setNotifications([]), [])
  const value = useMemo(() => ({ notifications, unreadCount: notifications.filter(value => !value.read).length, notify, markAllRead, clearAll }), [notifications, notify, markAllRead, clearAll])
  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useNotifications() {
  const value = useContext(Context)
  if (!value) throw new Error('useNotifications must be used inside NotificationProvider')
  return value
}

export function NotificationBell() {
  const { notifications, unreadCount, markAllRead, clearAll } = useNotifications()
  const [open, setOpen] = useState(false)
  function show() { setOpen(true); markAllRead() }
  return <>
    <button type="button" aria-label={`Thông báo${unreadCount ? `, ${unreadCount} chưa đọc` : ''}`} onClick={show}
      className="relative inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-stone-200 bg-white text-lg shadow-sm transition hover:bg-stone-50 focus:outline-none focus:ring-2 focus:ring-brand-500">
      <span aria-hidden>🔔</span>
      {unreadCount > 0 && <span className="absolute -right-1 -top-1 min-w-5 rounded-full bg-red-600 px-1 text-center text-[11px] font-semibold leading-5 text-white">{unreadCount > 99 ? '99+' : unreadCount}</span>}
    </button>
    {open && <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 backdrop-blur-[1px] sm:items-center sm:p-4" onMouseDown={event => { if (event.target === event.currentTarget) setOpen(false) }}>
      <section role="dialog" aria-modal="true" aria-labelledby="notification-title"
        className="flex max-h-[min(88dvh,48rem)] w-full min-w-0 flex-col overflow-hidden rounded-t-2xl bg-white shadow-2xl sm:max-w-2xl sm:rounded-2xl">
        <header className="flex shrink-0 flex-wrap items-center justify-between gap-2 border-b border-stone-200 px-4 py-3 sm:px-5">
          <div><h2 id="notification-title" className="font-semibold text-stone-900">Thông báo</h2><p className="text-xs text-stone-500">Đồng bộ quyền, thao tác quản trị và lỗi hệ thống.</p></div>
          <div className="flex gap-2"><button className="rounded-lg px-3 py-2 text-xs text-stone-600 hover:bg-stone-100" onClick={clearAll}>Xóa tất cả</button><button className="rounded-lg px-3 py-2 text-sm hover:bg-stone-100" onClick={() => setOpen(false)}>Đóng</button></div>
        </header>
        <div className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain p-3 sm:p-5">
          {!notifications.length && <p className="py-10 text-center text-sm text-stone-500">Chưa có thông báo.</p>}
          {notifications.map(value => <article key={value.id} className={`min-w-0 rounded-xl border p-3 ${toneClass[value.tone]}`}>
            <div className="flex min-w-0 items-start justify-between gap-3"><strong className="min-w-0 break-words text-sm">{value.title}</strong><time className="shrink-0 text-[11px] opacity-70">{new Date(value.createdAt).toLocaleTimeString('vi-VN')}</time></div>
            <p className="mt-1 break-words text-sm">{value.message}</p>
            {!!value.details?.length && <details className="mt-2"><summary className="cursor-pointer text-xs font-medium">Xem chi tiết ({value.details.length})</summary><ul className="mt-2 max-h-52 list-disc space-y-1 overflow-auto pl-5 text-xs">{value.details.map((item, index) => <li key={`${value.id}-${index}`} className="break-all">{item}</li>)}</ul></details>}
          </article>)}
        </div>
      </section>
    </div>}
  </>
}
