import { type ReactNode } from 'react'
import { useAuth, hasPermission } from '../lib/auth'
import { Card, PageHeader } from './ui'
import { vi } from '../i18n/vi'

/** Ẩn trang khi thiếu permission. Chỉ để hiển thị; service phía sau vẫn tự kiểm quyền. */
export default function RequirePermission({ permission, children }: { permission: string; children: ReactNode }) {
  const { me } = useAuth()
  if (hasPermission(me, permission)) return <>{children}</>
  return (
    <>
      <PageHeader title={vi.forbiddenPage.title} sub={vi.forbiddenPage.sub} />
      <Card>
        <p className="text-sm text-stone-600">{vi.forbiddenPage.needs} <code className="rounded bg-stone-100 px-1.5 py-0.5 text-xs">{permission}</code>. {vi.forbiddenPage.contact}</p>
      </Card>
    </>
  )
}
