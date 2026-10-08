import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { Badge, Button, Card, ErrorMsg, Field, Input, Json, PageHeader, statusTone } from '../components/ui'

// GET /orders/{id}
// PUT /orders/drafts/{id} {farmId,batchId?,expectedVersion,lines:[Line]}
// POST /orders/drafts/{id}/submit {expectedVersion}
// DELETE /orders/drafts/{id}?expectedVersion=<long>   (query param, KHÔNG phải body)
// POST /orders/{id}/cancel {reason?}
type Line = { itemId: string; quantity: string; unit: string; currency: string; unitPriceMinor: number; warehouseId: string }
const toLine = (l: any): Line => ({
  itemId: l?.itemId ?? '', quantity: String(l?.quantity ?? ''), unit: l?.unit ?? 'KG',
  currency: l?.currency ?? 'VND', unitPriceMinor: Number(l?.unitPriceMinor ?? 0), warehouseId: l?.warehouseId ?? '',
})

export default function OrderDetail() {
  const { id = '' } = useParams()
  const qc = useQueryClient()
  const nav = useNavigate()
  const [auto, setAuto] = useState(false)
  const [reason, setReason] = useState('')
  const [editing, setEditing] = useState(false)
  const [lines, setLines] = useState<Line[]>([])

  const q = useQuery({
    queryKey: ['order', id],
    queryFn: () => api<any>(`/api/v1/orders/${id}`),
    refetchInterval: auto ? 2000 : false,
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ['order', id] })

  const o = q.data
  const isDraft = String(o?.status ?? '').includes('DRAFT')

  // Nạp các dòng hiện tại vào form khi mở chỉnh sửa.
  useEffect(() => {
    if (editing && o?.lines) setLines(o.lines.map(toLine))
  }, [editing, o])

  const updLine = (i: number, patch: Partial<Line>) => setLines(ls => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)))

  const save = useMutation({
    // PUT draft: gửi expectedVersion của bản vừa đọc + toàn bộ lines.
    mutationFn: () => api(`/api/v1/orders/drafts/${id}`, {
      method: 'PUT', body: { farmId: o.farmId, batchId: o.batchId ?? undefined, expectedVersion: o.version, lines },
    }),
    onSuccess: () => { setEditing(false); refresh() },
  })
  const submit = useMutation({
    mutationFn: () => api(`/api/v1/orders/drafts/${id}/submit`, { method: 'POST', body: { expectedVersion: o.version } }),
    onSuccess: () => { setAuto(true); refresh() },
  })
  const del = useMutation({
    // DELETE draft: expectedVersion là QUERY PARAM, không có body.
    mutationFn: () => api(`/api/v1/orders/drafts/${id}`, { method: 'DELETE', query: { expectedVersion: o.version } }),
    onSuccess: () => { qc.removeQueries({ queryKey: ['order', id] }); nav('/orders') },
  })
  const cancel = useMutation({
    mutationFn: () => api(`/api/v1/orders/${id}/cancel`, { method: 'POST', body: { reason: reason || undefined } }),
    onSuccess: refresh,
  })

  return (
    <>
      <PageHeader title="Chi tiết đơn" sub={<span className="font-mono">{id}</span>}
        right={<Link to="/orders" className="text-sm text-brand-700 underline">← Danh sách</Link>} />
      <div className="space-y-4">
        <Card title="Trạng thái" actions={
          <div className="flex items-center gap-3 text-sm">
            <label className="flex items-center gap-1.5"><input type="checkbox" checked={auto} onChange={e => setAuto(e.target.checked)} /> Tự làm mới</label>
            <Button variant="ghost" onClick={refresh}>Làm mới</Button>
          </div>}>
          <ErrorMsg error={q.error} />
          {o && <div className="flex items-center gap-3"><Badge tone={statusTone(o.status)}>{o.status}</Badge><span className="text-sm text-stone-500">version {o.version}</span></div>}
          <div className="mt-4 space-y-3">
            {isDraft && (
              <div className="flex flex-wrap gap-2">
                <Button onClick={() => submit.mutate()} disabled={submit.isPending}>Submit (chạy saga)</Button>
                <Button variant="ghost" onClick={() => setEditing(v => !v)}>{editing ? 'Đóng chỉnh sửa' : 'Sửa dòng hàng'}</Button>
                <Button variant="danger" onClick={() => { if (confirm('Xoá draft này? Không thể hoàn tác.')) del.mutate() }} disabled={del.isPending}>
                  {del.isPending ? 'Đang xoá…' : 'Xoá draft'}
                </Button>
              </div>
            )}
            <ErrorMsg error={submit.error} />
            <ErrorMsg error={del.error} />
            <div className="flex gap-2">
              <Input placeholder="Lý do huỷ (tuỳ chọn)" value={reason} onChange={e => setReason(e.target.value)} />
              <Button variant="danger" onClick={() => cancel.mutate()} disabled={cancel.isPending}>Huỷ đơn</Button>
            </div>
            <ErrorMsg error={cancel.error} />
          </div>
        </Card>

        {isDraft && editing && (
          <Card title="Chỉnh sửa dòng hàng" actions={<Button type="button" variant="ghost" onClick={() => setLines(ls => [...ls, toLine({})])}>+ Thêm dòng</Button>}>
            <form className="space-y-4" onSubmit={e => { e.preventDefault(); save.mutate() }}>
              {lines.map((l, i) => (
                <div key={i} className="rounded-lg border border-stone-200 p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-sm font-medium">Dòng {i + 1}</span>
                    {lines.length > 1 && <Button type="button" variant="ghost" onClick={() => setLines(ls => ls.filter((_, j) => j !== i))}>Xoá dòng</Button>}
                  </div>
                  <div className="grid gap-3 md:grid-cols-3">
                    <Field label="Item ID"><Input required value={l.itemId} onChange={e => updLine(i, { itemId: e.target.value })} /></Field>
                    <Field label="Warehouse ID"><Input required value={l.warehouseId} onChange={e => updLine(i, { warehouseId: e.target.value })} /></Field>
                    <Field label="Số lượng"><Input required value={l.quantity} onChange={e => updLine(i, { quantity: e.target.value })} /></Field>
                    <Field label="Đơn vị"><Input required value={l.unit} onChange={e => updLine(i, { unit: e.target.value })} /></Field>
                    <Field label="Tiền tệ"><Input required value={l.currency} onChange={e => updLine(i, { currency: e.target.value })} /></Field>
                    <Field label="Đơn giá (minor unit)"><Input type="number" required value={l.unitPriceMinor} onChange={e => updLine(i, { unitPriceMinor: Number(e.target.value) })} /></Field>
                  </div>
                </div>
              ))}
              <ErrorMsg error={save.error} />
              <Button disabled={save.isPending || !lines.length}>{save.isPending ? 'Đang lưu…' : 'Lưu draft'}</Button>
            </form>
          </Card>
        )}

        {!!o?.lines?.length && (
          <Card title="Dòng hàng">
            <table className="w-full text-left text-sm">
              <thead className="border-b text-xs uppercase text-stone-500"><tr><th className="py-2">#</th><th>Item</th><th>Kho</th><th>SL</th><th>ĐV</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead>
              <tbody>
                {o.lines.map((l: any) => (
                  <tr key={l.lineId ?? l.lineNo} className="border-b last:border-0">
                    <td className="py-2">{l.lineNo}</td>
                    <td className="font-mono text-xs">{l.itemId}</td>
                    <td className="font-mono text-xs">{l.warehouseId ?? '—'}</td>
                    <td>{l.quantity}</td>
                    <td>{l.unit}</td>
                    <td>{l.unitPriceMinor} {l.currency}</td>
                    <td>{l.lineTotalMinor} {l.currency}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        )}

        <Card title="Dữ liệu thô"><Json data={o} /></Card>
      </div>
    </>
  )
}
