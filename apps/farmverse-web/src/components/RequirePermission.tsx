import { type ReactNode } from 'react'
import { useAuth, hasPermission } from '../lib/auth'
import { Card, PageHeader } from './ui'

/** Ẩn trang khi thiếu permission. Chỉ để hiển thị; service phía sau vẫn tự kiểm quyền. */
export default function RequirePermission({ permission, children }: { permission: string; children: ReactNode }) {
  const { me } = useAuth()
  if (hasPermission(me, permission)) return <>{children}</>
  return (
    <>
      <PageHeader title="403 — Thiếu quyền" sub="Bạn không có quyền truy cập trang này." />
      <Card>
        <p className="text-sm text-stone-600">Cần quyền <code className="rounded bg-stone-100 px-1.5 py-0.5 text-xs">{permission}</code>. Liên hệ quản trị viên nếu bạn cần truy cập.</p>
      </Card>
    </>
  )
}
