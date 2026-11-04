import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { Dialog } from './dialog'
import { Button } from './ui'
import { vi } from '../i18n/vi'

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
    <button type="button" aria-label={vi.notifications.button(unreadCount)} onClick={show}
      className="relative inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-stone-200 bg-white text-lg shadow-sm transition hover:bg-stone-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700">
      <span aria-hidden>🔔</span>
      {unreadCount > 0 && <span className="absolute -right-1 -top-1 min-w-5 rounded-full bg-red-600 px-1 text-center text-[11px] font-semibold leading-5 text-white">{unreadCount > 99 ? '99+' : unreadCount}</span>}
    </button>
    <Dialog open={open} onClose={() => setOpen(false)} title={vi.notifications.title}
      description={vi.notifications.description} className="sm:w-[min(42rem,calc(100vw-2rem))]">
      <div className="max-h-[60dvh] space-y-3 overflow-y-auto overscroll-contain">
        {!notifications.length && <p className="py-10 text-center text-sm text-stone-600">{vi.notifications.empty}</p>}
        {notifications.map(value => <article key={value.id} className={`min-w-0 rounded-xl border p-3 ${toneClass[value.tone]}`}>
          <div className="flex min-w-0 items-start justify-between gap-3"><strong className="min-w-0 break-words text-sm">{value.title}</strong><time className="shrink-0 text-[11px]">{new Date(value.createdAt).toLocaleTimeString('vi-VN')}</time></div>
          <p className="mt-1 break-words text-sm">{value.message}</p>
          {!!value.details?.length && <details className="mt-2"><summary className="cursor-pointer text-xs font-medium">{vi.notifications.details(value.details.length)}</summary><ul className="mt-2 max-h-52 list-disc space-y-1 overflow-auto pl-5 text-xs">{value.details.map((item, index) => <li key={`${value.id}-${index}`} className="break-all">{item}</li>)}</ul></details>}
        </article>)}
      </div>
      <div className="mt-4 flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={clearAll} disabled={!notifications.length}>{vi.notifications.clearAll}</Button>
        <Button type="button" onClick={() => setOpen(false)}>{vi.common.close}</Button>
      </div>
    </Dialog>
  </>
}
