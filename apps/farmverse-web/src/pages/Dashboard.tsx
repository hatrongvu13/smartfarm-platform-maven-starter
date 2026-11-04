import { Link } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { Badge, Card, PageHeader } from '../components/ui'
import { vi } from '../i18n/vi'

/** Trang đầu: ai đang đăng nhập, ở tenant nào, có quyền gì (đọc từ /bff/me). */
export default function Dashboard() {
  const { me } = useAuth()
  const permissions = me?.permissions ?? []

  return (
    <>
      <PageHeader title={vi.dashboard.title} sub={vi.dashboard.greeting(me?.name ?? me?.email ?? '')} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title={vi.dashboard.session}>
          <dl className="grid grid-cols-[8rem_1fr] gap-y-1 text-sm">
            <dt className="text-stone-600">{vi.common.email}</dt><dd>{me?.email}</dd>
            <dt className="text-stone-600">{vi.dashboard.organization}</dt><dd>{me?.tenantCode ?? me?.tenantId}</dd>
            <dt className="text-stone-600">{vi.dashboard.roles}</dt>
            <dd className="flex flex-wrap gap-1">{me?.roles.map(r => <Badge key={r} tone={r === 'SUPER_ADMIN' ? 'warn' : 'info'}>{r}</Badge>)}</dd>
          </dl>
        </Card>
        <Card title={vi.dashboard.permissions(permissions.length)}>
          <div className="flex flex-wrap gap-1.5">
            {permissions.length
              ? permissions.map(p => <Badge key={p} tone="info">{p}</Badge>)
              : <span className="text-sm text-stone-600">{vi.dashboard.noPermissions}</span>}
          </div>
        </Card>
        <Card title={vi.dashboard.goTo}>
          <ul className="list-disc pl-5 text-sm">
            <li><Link className="underline" to="/farms">{vi.dashboard.goFarms}</Link></li>
            <li><Link className="underline" to="/profile">{vi.dashboard.goProfile}</Link></li>
          </ul>
        </Card>
      </div>
    </>
  )
}
