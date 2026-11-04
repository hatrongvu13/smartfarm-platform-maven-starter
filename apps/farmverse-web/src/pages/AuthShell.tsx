import type { ReactNode } from 'react'

/**
 * Khung trang khi chưa đăng nhập. Cùng hình thức với trang đăng nhập của identity (thẻ trắng giữa nền xanh nhạt),
 * để người dùng đi từ web sang identity rồi quay lại không thấy hai giao diện khác nhau.
 */
export default function AuthShell({ title, sub, children }: { title: string; sub?: string; children: ReactNode }) {
  // grid-cols-1 = minmax(0, 1fr): cột co theo màn hình; thiếu nó cột giãn theo chữ và thẻ tràn ở màn hình hẹp.
  return (
    <main className="grid min-h-dvh grid-cols-1 place-items-center bg-surface px-4 py-6">
      <section className="w-full max-w-[420px] rounded-2xl border border-stone-300 bg-white px-7 py-8 shadow-sm">
        <div className="mb-6 flex items-center gap-3 text-lg font-bold">
          <span className="farm-brand-mark" aria-hidden="true">✿</span>
          <span>FarmVerse</span>
        </div>
        <h1 className="text-xl font-semibold">{title}</h1>
        {sub && <p className="mb-5 mt-1 text-sm text-stone-600">{sub}</p>}
        {children}
      </section>
    </main>
  )
}
