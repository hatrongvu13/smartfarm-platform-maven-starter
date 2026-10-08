import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { gql } from '../lib/api'
import { scopesOf, useAuth } from '../lib/auth'
import { Badge, Card, ErrorMsg, Json, PageHeader } from '../components/ui'

export default function Dashboard() {
  const { me } = useAuth()
  const scopes = scopesOf(me)
  // GraphQL platformStatus cần scope farm:read (super-admin "*" qua được)
  const status = useQuery({
    queryKey: ['platformStatus'],
    queryFn: () => gql<{ platformStatus: { name: string; status: string; tenantId: string } }>('query { platformStatus { name status tenantId } }'),
  })

  const links = [
    ['/orders', 'Đơn hàng', 'Tạo draft, submit, theo dõi saga'],
    ['/inventory', 'Kho', 'Tạo vật tư, nhập kho'],
    ['/livestock', 'Chăn nuôi', 'Đăng ký vật nuôi, tạo task'],
    ['/reports', 'Báo cáo', 'Yêu cầu export, tải xuống'],
  ]

  return (
    <>
      <PageHeader title="Tổng quan" sub={`Xin chào ${me?.email ?? me?.sub ?? ''}`} />
      <div className="grid gap-4 md:grid-cols-2">
        <Card title="Trạng thái nền tảng">
          {status.isLoading && <p className="text-sm text-stone-500">Đang tải…</p>}
          <ErrorMsg error={status.error} />
          {status.data && (
            <dl className="space-y-1 text-sm">
              <div className="flex justify-between"><dt className="text-stone-500">Tên</dt><dd>{status.data.platformStatus.name}</dd></div>
              <div className="flex justify-between"><dt className="text-stone-500">Trạng thái</dt><dd><Badge tone="ok">{status.data.platformStatus.status}</Badge></dd></div>
              <div className="flex justify-between"><dt className="text-stone-500">Tenant</dt><dd>{status.data.platformStatus.tenantId}</dd></div>
            </dl>
          )}
        </Card>
        <Card title="Phiên đăng nhập">
          <div className="mb-3 flex flex-wrap gap-1.5">
            {scopes.length ? scopes.map(s => <Badge key={s} tone={s === '*' ? 'warn' : 'info'}>{s}</Badge>) : <span className="text-sm text-stone-500">Không có scope</span>}
          </div>
          <details><summary className="cursor-pointer text-sm text-stone-600">Principal (GraphQL me) + scope JWT</summary><div className="mt-2"><Json data={me} /></div></details>
        </Card>
      </div>
      <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {links.map(([to, t, d]) => (
          <Link key={to} to={to} className="rounded-xl border border-stone-200 bg-white p-5 shadow-sm transition hover:border-brand-500 hover:shadow">
            <div className="font-semibold">{t}</div><div className="mt-1 text-sm text-stone-500">{d}</div>
          </Link>
        ))}
      </div>
    </>
  )
}
