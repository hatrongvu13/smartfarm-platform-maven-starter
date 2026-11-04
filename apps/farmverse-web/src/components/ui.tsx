import { cloneElement, useEffect, useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactElement, type ReactNode, type SelectHTMLAttributes } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import { describeError } from '../lib/errors'
import { vi } from '../i18n/vi'

const cx = (...a: (string | false | undefined)[]) => a.filter(Boolean).join(' ')

export function Button({ variant = 'primary', className, ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'ghost' | 'danger' }) {
  const v = {
    // brand-700: chữ trắng ~6,5:1 (brand-600 chỉ ~4,3:1, dưới mức AA 4,5:1 — axe báo)
    primary: 'bg-brand-700 text-white hover:bg-brand-900',
    ghost: 'border border-stone-300 bg-white text-stone-700 hover:bg-stone-100',
    danger: 'bg-red-600 text-white hover:bg-red-700',
  }[variant]
  return <button {...p} className={cx('inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50', focusRing, v, className)} />
}

/** Viền focus thấy rõ khi dùng bàn phím (không hiện khi bấm chuột). */
export const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700'

/**
 * Nhãn + gợi ý cho một ô nhập. Nhãn nối bằng htmlFor/id; gợi ý nối bằng aria-describedby, để trình đọc màn hình
 * đọc tên ô ngắn gọn ("Mã") rồi mới đọc gợi ý, thay vì gộp gợi ý vào tên ô.
 */
export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactElement<{ id?: string; 'aria-describedby'?: string }> }) {
  const id = useId()
  const hintId = `${id}-hint`
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-stone-700">{label}</label>
      {cloneElement(children, { id, 'aria-describedby': hint ? hintId : undefined })}
      {hint && <p id={hintId} className="mt-1 text-xs text-stone-600">{hint}</p>}
    </div>
  )
}

const inputCls = 'w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100'
export const Input = (p: InputHTMLAttributes<HTMLInputElement>) => <input {...p} className={cx(inputCls, p.className)} />
export const Select = (p: SelectHTMLAttributes<HTMLSelectElement>) => <select {...p} className={cx(inputCls, p.className)} />

export function Card({ title, actions, children, className }: { title?: string; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={cx('rounded-xl border border-stone-200 bg-white p-5 shadow-sm', className)}>
      {(title || actions) && (
        <div className="mb-4 flex items-center justify-between gap-3">
          {title && <h2 className="text-base font-semibold">{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  )
}

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'ok' | 'warn' | 'bad' | 'info' }) {
  const t = { neutral: 'bg-stone-100 text-stone-700', ok: 'bg-green-100 text-green-800', warn: 'bg-amber-100 text-amber-800', bad: 'bg-red-100 text-red-800', info: 'bg-sky-100 text-sky-800' }[tone]
  return <span className={cx('inline-block rounded-full px-2.5 py-0.5 text-xs font-medium', t)}>{children}</span>
}

export function statusTone(s?: string): 'neutral' | 'ok' | 'warn' | 'bad' | 'info' {
  const v = (s ?? '').toUpperCase()
  if (/CONFIRM|COMPLET|DONE|SUCCE/.test(v)) return 'ok'
  if (/CANCEL|FAIL|REJECT|ERROR|COMPENSAT/.test(v)) return 'bad'
  if (/DRAFT/.test(v)) return 'neutral'
  if (/PEND|PROCESS|RUN|SUBMIT|RESERV/.test(v)) return 'warn'
  return 'info'
}

/**
 * Hộp báo lỗi thống nhất (W3). Nội dung lấy từ describeError: câu dễ hiểu, lỗi theo từng ô của form (400),
 * mã lỗi nhỏ để báo hỗ trợ. role="alert" để trình đọc màn hình đọc ngay khi lỗi xuất hiện.
 */
export function ErrorMsg({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null
  const view = describeError(error)
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900">
      <p className="font-medium">{view.title}</p>
      {view.detail && <p className="mt-0.5">{view.detail}</p>}
      {view.fields.length > 0 && (
        <ul className="mt-1 list-disc pl-5">
          {view.fields.map((f, i) => <li key={i}>{f.field && <span className="font-medium">{f.field}: </span>}{f.message}</li>)}
        </ul>
      )}
      <div className="mt-1 flex items-center gap-3">
        {onRetry && view.retryable && (
          <button type="button" onClick={onRetry} className={cx('font-medium text-red-900 underline underline-offset-2', focusRing)}>{vi.common.retry}</button>
        )}
        {view.code && <span className="text-xs text-red-800">{vi.common.errorCode(view.code)}</span>}
      </div>
    </div>
  )
}

/** Đang tải: role="status" để trình đọc màn hình báo, thay vì một vùng trống trông như "không có dữ liệu". */
export function Loading({ label = vi.app.loading }: { label?: string }) {
  return <p role="status" aria-live="polite" className="py-6 text-center text-sm text-stone-600">{label}</p>
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-6 text-center text-sm text-stone-600">{children}</p>
}

/**
 * Bốn trạng thái của một query ở một chỗ: đang tải → lỗi (có thử lại) → rỗng → có dữ liệu.
 * `isEmpty` mặc định: mảng rỗng hoặc trang (Page) không có phần tử.
 */
export function QueryState<T>({ query, empty, isEmpty = defaultIsEmpty, loadingLabel, children }: {
  query: UseQueryResult<T>
  empty?: ReactNode
  isEmpty?: (data: T) => boolean
  loadingLabel?: string
  children: (data: T) => ReactNode
}) {
  if (query.isPending) return <Loading label={loadingLabel} />
  if (query.isError) return <ErrorMsg error={query.error} onRetry={() => query.refetch()} />
  if (empty !== undefined && isEmpty(query.data)) return <Empty>{empty}</Empty>
  return <>{children(query.data)}</>
}

function defaultIsEmpty(data: unknown): boolean {
  if (Array.isArray(data)) return data.length === 0
  const content = (data as { content?: unknown[] } | null | undefined)?.content
  return Array.isArray(content) && content.length === 0
}

/** Tiêu đề trang; đồng thời đặt tiêu đề tab trình duyệt (WCAG 2.4.2), để người dùng nhiều tab và trình đọc màn hình biết đang ở đâu. */
export function PageHeader({ title, sub, right }: { title: string; sub?: ReactNode; right?: ReactNode }) {
  useEffect(() => { document.title = `${title}${vi.app.titleSuffix}` }, [title])
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {sub && <p className="mt-1 text-sm text-stone-600">{sub}</p>}
      </div>
      {right}
    </div>
  )
}
