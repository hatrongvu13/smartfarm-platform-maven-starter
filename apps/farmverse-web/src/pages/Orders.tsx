import { useInfiniteQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { useFarm } from '../lib/farm'
import { Badge, Button, Card, ErrorMsg, PageHeader, statusTone } from '../components/ui'

// GET /api/v1/orders?farmId&status?&warehouseId?&size&pageToken -> { orders[], nextPageToken }
export default function Orders() {
  const { farmId } = useFarm()
  const q = useInfiniteQuery({
    queryKey: ['orders', farmId],
    initialPageParam: '' as string,
    queryFn: ({ pageParam }) => api<{ orders: any[]; nextPageToken?: string }>('/api/v1/orders', { query: { farmId, size: 20, pageToken: pageParam } }),
    getNextPageParam: l => l.nextPageToken || undefined,
  })
  const rows = q.data?.pages.flatMap(p => p.orders ?? []) ?? []

  return (
    <>
      <PageHeader title="Đơn hàng" sub={`Farm: ${farmId}`} right={<Link to="/orders/new"><Button>+ Tạo draft</Button></Link>} />
      <Card>
        <ErrorMsg error={q.error} />
        {q.isLoading && <p className="text-sm text-stone-500">Đang tải…</p>}
        {!q.isLoading && !rows.length && !q.error && <p className="text-sm text-stone-500">Chưa có đơn hàng.</p>}
        {!!rows.length && (
          <table className="w-full text-left text-sm">
            <thead className="border-b text-xs uppercase text-stone-500"><tr><th className="py-2">Mã đơn</th><th>Trạng thái</th><th>Version</th></tr></thead>
            <tbody>
              {rows.map(o => (
                <tr key={o.orderId} className="border-b last:border-0 hover:bg-stone-50">
                  <td className="py-2"><Link className="font-mono text-brand-700 underline" to={`/orders/${o.orderId}`}>{o.orderId}</Link></td>
                  <td><Badge tone={statusTone(o.status)}>{o.status}</Badge></td>
                  <td>{o.version}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {q.hasNextPage && <Button variant="ghost" className="mt-4" onClick={() => q.fetchNextPage()} disabled={q.isFetchingNextPage}>Tải thêm</Button>}
      </Card>
    </>
  )
}
