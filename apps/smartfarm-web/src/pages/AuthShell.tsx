import type { ReactNode } from 'react'
export default function AuthShell({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand-50 to-stone-100 p-4">
      <div className="w-full max-w-md rounded-2xl border border-stone-200 bg-white p-8 shadow-lg">
        <div className="mb-6">
          <div className="mb-3 text-2xl">🌾</div>
          <h1 className="text-xl font-semibold">{title}</h1>
          {sub && <p className="mt-1 text-sm text-stone-500">{sub}</p>}
        </div>
        {children}
      </div>
    </div>
  )
}
