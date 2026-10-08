import { type ReactNode } from 'react'
import { useAuth, hasScope } from '../lib/auth'
import { Card, PageHeader } from './ui'

/** Chặn truy cập khi thiếu scope: hiển thị panel "403 — thiếu quyền". */
export default function RequireScope({ scope, children }: { scope: string; children: ReactNode }) {
  const { me } = useAuth()
  if (hasScope(me, scope)) return <>{children}</>
  return (
    <>
      <PageHeader title="403 — Thiếu quyền" sub="Bạn không có quyền truy cập trang này." />
      <Card>
        <p className="text-sm text-stone-600">Thao tác này yêu cầu quyền <code className="rounded bg-stone-100 px-1.5 py-0.5 text-xs">{scope}</code>. Liên hệ quản trị viên nếu bạn cần truy cập.</p>
      </Card>
    </>
  )
}
