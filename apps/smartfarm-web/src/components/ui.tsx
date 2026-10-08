import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'
import { ApiError } from '../lib/api'

const cx = (...a: (string | false | undefined)[]) => a.filter(Boolean).join(' ')

export function Button({ variant = 'primary', className, ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'ghost' | 'danger' }) {
  const v = {
    primary: 'bg-brand-600 text-white hover:bg-brand-700',
    ghost: 'border border-stone-300 bg-white text-stone-700 hover:bg-stone-100',
    danger: 'bg-red-600 text-white hover:bg-red-700',
  }[variant]
  return <button {...p} className={cx('inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50', v, className)} />
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-stone-700">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-stone-500">{hint}</span>}
    </label>
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

export function ErrorMsg({ error }: { error: unknown }) {
  if (!error) return null
  const e = error as ApiError
  const hint =
    e.status === 403 ? ' — thiếu scope cho thao tác này.' :
    e.status === 404 ? ' — endpoint có thể là dev-only (@Profile dev&!prod) hoặc chưa chạy backend.' : ''
  return <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">{e.message}{e.status ? ` (HTTP ${e.status})` : ''}{hint}</div>
}

export const DevOnly = () => <Badge tone="warn">dev-only</Badge>

export function Json({ data }: { data: unknown }) {
  return <pre className="max-h-80 overflow-auto rounded-lg bg-stone-900 p-3 text-xs text-stone-100">{JSON.stringify(data, null, 2)}</pre>
}

export function PageHeader({ title, sub, right }: { title: string; sub?: ReactNode; right?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {sub && <p className="mt-1 text-sm text-stone-500">{sub}</p>}
      </div>
      {right}
    </div>
  )
}
