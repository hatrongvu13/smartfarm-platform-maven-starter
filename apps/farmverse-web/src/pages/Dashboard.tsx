import { Link } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { Badge, Card, PageHeader } from '../components/ui'

/** Trang đầu: ai đang đăng nhập, ở tenant nào, có quyền gì (đọc từ /bff/me). */
export default function Dashboard() {
  const { me } = useAuth()
  const permissions = me?.permissions ?? []

  return (
    <>
      <PageHeader title="Tổng quan" sub={`Xin chào, ${me?.name ?? me?.email ?? ''}`} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Phiên đăng nhập">
          <dl className="grid grid-cols-[8rem_1fr] gap-y-1 text-sm">
            <dt className="text-stone-500">Email</dt><dd>{me?.email}</dd>
            <dt className="text-stone-500">Tổ chức</dt><dd>{me?.tenantCode ?? me?.tenantId}</dd>
            <dt className="text-stone-500">Role</dt>
            <dd className="flex flex-wrap gap-1">{me?.roles.map(r => <Badge key={r} tone={r === 'SUPER_ADMIN' ? 'warn' : 'info'}>{r}</Badge>)}</dd>
          </dl>
        </Card>
        <Card title={`Quyền (${permissions.length})`}>
          <div className="flex flex-wrap gap-1.5">
            {permissions.length
              ? permissions.map(p => <Badge key={p} tone="info">{p}</Badge>)
              : <span className="text-sm text-stone-500">Chưa có quyền nào. Super admin vẫn vào được mọi trang.</span>}
          </div>
        </Card>
        <Card title="Đi tới">
          <ul className="list-disc pl-5 text-sm">
            <li><Link className="underline" to="/farms">Trang trại và chuồng</Link></li>
            <li><Link className="underline" to="/livestock">Vật nuôi và công việc</Link></li>
            <li><Link className="underline" to="/profile">Hồ sơ của tôi</Link></li>
          </ul>
          <p className="mt-3 text-xs text-stone-500">Kho, đơn hàng, báo cáo sẽ có lại ở cấp 2–3 của lộ trình.</p>
        </Card>
      </div>
    </>
  )
}
