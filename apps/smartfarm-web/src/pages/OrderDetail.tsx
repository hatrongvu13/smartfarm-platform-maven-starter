import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../lib/api'
import { hasScope, useAuth } from '../lib/auth'
import { Badge, Button, Card, ErrorMsg, Field, Input, Json, PageHeader, statusTone } from '../components/ui'

type Line = { itemId: string; quantity: string; unit: string; currency: string; unitPriceMinor: number; warehouseId: string }
type SagaStep = { stepKey: string; sequenceNo: number; stepType: string; status: string; orderLineId?: string; externalReferenceId?: string; attemptCount: number; nextAttemptAt?: string; claimedAt?: string; lastErrorCode?: string; lastErrorMessage?: string }
type Saga = { sagaId: string; orderId: string; status: string; terminalIntent?: string; currentStepKey?: string; attemptCount: number; nextAttemptAt?: string; claimedAt?: string; compensationDeadlineAt?: string; lastErrorCode?: string; lastErrorMessage?: string; steps: SagaStep[] }

const toLine = (l: any): Line => ({ itemId: l?.itemId ?? '', quantity: String(l?.quantity ?? ''), unit: l?.unit ?? 'KG', currency: l?.currency ?? 'VND', unitPriceMinor: Number(l?.unitPriceMinor ?? 0), warehouseId: l?.warehouseId ?? '' })
const shortStatus = (value?: string) => String(value ?? '').replace(/^ORDER_STATUS_/, '')
const terminalOrder = (value?: string) => /COMPLETED|CANCELLED/.test(shortStatus(value))
const actionableStep = (value?: string) => /FAILED|MANUAL_REVIEW/.test(String(value ?? ''))

function processing(status?: string) {
  const value = shortStatus(status)
  const values: Record<string, [string, string]> = {
    DRAFT: ['DRAFT', 'Soạn thảo'], CREATED: ['QUEUED', 'Đang chờ xử lý'], STOCK_RESERVING: ['INVENTORY_RESERVATION', 'Đang giữ tồn kho'],
    STOCK_RESERVED: ['INVENTORY_RESERVED', 'Đã giữ tồn kho'], TASK_SCHEDULED: ['TASK_SCHEDULED', 'Đã lập lịch công việc'], FINANCE_POSTING: ['FINANCE', 'Đang ghi nhận chi phí'],
    FINANCE_POSTED: ['INVENTORY_COMMIT', 'Đang chốt xuất kho'], COMPLETED: ['COMPLETED', 'Hoàn thành'], CANCELLING: ['CANCELLATION', 'Đang huỷ'],
    COMPENSATING: ['COMPENSATION', 'Đang hoàn tác'], FAILED: ['FAILED', 'Thất bại'], CANCELLED: ['CANCELLED', 'Đã huỷ'], MANUAL_REVIEW: ['MANUAL_REVIEW', 'Cần đối soát thủ công'],
  }
  return values[value] ?? ['UNKNOWN', value || 'Chưa xác định']
}

const money = (minor: number, currency?: string) => {
  try { return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: currency || 'VND', maximumFractionDigits: 0 }).format(minor) }
  catch { return `${minor.toLocaleString('vi-VN')} ${currency ?? ''}` }
}
const when = (value?: string) => value ? new Date(value).toLocaleString('vi-VN') : '—'

export default function OrderDetail() {
  const { id = '' } = useParams()
  const { me } = useAuth()
  const canAdminSaga = hasScope(me, 'orders:saga:admin')
  const qc = useQueryClient()
  const nav = useNavigate()
  const [auto, setAuto] = useState(false)
  const [reason, setReason] = useState('')
  const [editing, setEditing] = useState(false)
  const [lines, setLines] = useState<Line[]>([])
  const [operatorReason, setOperatorReason] = useState('')

  const q = useQuery({ queryKey: ['order', id], queryFn: () => api<any>(`/api/v1/orders/${id}`), refetchInterval: auto ? 2000 : false })
  const saga = useQuery({
    queryKey: ['orderSagaByOrder', id], enabled: canAdminSaga && !!id,
    queryFn: async () => { try { return await api<Saga>(`/api/v1/order-sagas/by-order/${id}`) } catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error } },
    refetchInterval: auto ? 2000 : false,
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['order', id] }); qc.invalidateQueries({ queryKey: ['orderSagaByOrder', id] }) }
  const o = q.data
  const isDraft = shortStatus(o?.status) === 'DRAFT'
  const [stageCode, stageLabel] = processing(o?.status)

  useEffect(() => { if (editing && o?.lines) setLines(o.lines.map(toLine)) }, [editing, o])
  const updLine = (i: number, patch: Partial<Line>) => setLines(ls => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)))

  const save = useMutation({ mutationFn: () => api(`/api/v1/orders/drafts/${id}`, { method: 'PUT', body: { farmId: o.farmId, batchId: o.batchId ?? undefined, expectedVersion: o.version, lines } }), onSuccess: () => { setEditing(false); refresh() } })
  const submit = useMutation({ mutationFn: () => api(`/api/v1/orders/drafts/${id}/submit`, { method: 'POST', body: { expectedVersion: o.version } }), onSuccess: () => { setAuto(true); refresh() } })
  const del = useMutation({ mutationFn: () => api(`/api/v1/orders/drafts/${id}`, { method: 'DELETE', query: { expectedVersion: o.version } }), onSuccess: () => { qc.removeQueries({ queryKey: ['order', id] }); nav('/orders') } })
  const cancel = useMutation({ mutationFn: () => api(`/api/v1/orders/${id}/cancel`, { method: 'POST', body: { reason: reason || undefined } }), onSuccess: refresh })
  const sagaAction = useMutation({
    mutationFn: ({ path, body }: { path: string; body: unknown }) => api<Saga>(path, { method: 'POST', body }),
    onSuccess: () => { setOperatorReason(''); refresh() },
  })
  const runSagaAction = (suffix: string) => {
    if (!saga.data?.sagaId || !operatorReason.trim()) return
    sagaAction.mutate({ path: `/api/v1/order-sagas/${saga.data.sagaId}/${suffix}`, body: { reason: operatorReason.trim() } })
  }
  const retryStep = (step: SagaStep) => {
    if (!saga.data?.sagaId || !operatorReason.trim()) return
    sagaAction.mutate({ path: `/api/v1/order-sagas/${saga.data.sagaId}/steps/${encodeURIComponent(step.stepKey)}/retry`, body: { reason: operatorReason.trim() } })
  }

  const totalLines = useMemo(() => o?.lines?.reduce((sum: number, line: any) => sum + Number(line.lineTotalMinor ?? 0), 0) ?? 0, [o])

  return <>
    <PageHeader title="Chi tiết đơn" sub={<span className="font-mono">{id}</span>} right={<Link to="/orders" className="text-sm text-brand-700 underline">← Danh sách</Link>} />
    <div className="space-y-4">
      <Card title="Tổng quan" actions={<div className="flex items-center gap-3 text-sm"><label className="flex items-center gap-1.5"><input type="checkbox" checked={auto} onChange={e => setAuto(e.target.checked)} /> Tự làm mới</label><Button variant="ghost" onClick={refresh}>Làm mới</Button></div>}>
        <ErrorMsg error={q.error} />
        {q.isLoading && <p className="text-sm text-stone-500">Đang tải…</p>}
        {o && <>
          <div className="mb-4 flex flex-wrap items-center gap-3"><Badge tone={statusTone(o.status)}>{shortStatus(o.status)}</Badge><Badge tone="info">{stageLabel}</Badge><span className="text-sm text-stone-500">version {o.version}</span></div>
          <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
            <Info label="Farm" value={o.farmId} mono /><Info label="Batch" value={o.batchId ?? '—'} mono />
            <Info label="Tổng tiền" value={money(Number(o.totalMinor ?? 0), o.currency)} /><Info label="Giai đoạn" value={stageCode} />
            <Info label="Tạo lúc" value={when(o.createdAt)} /><Info label="Cập nhật" value={when(o.updatedAt)} />
            <Info label="Số dòng" value={String(o.lines?.length ?? 0)} /><Info label="Tổng theo dòng" value={money(totalLines, o.currency)} />
          </dl>
          {o.failureReason && <div className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"><strong>Lý do:</strong> {o.failureReason}</div>}
          <div className="mt-4 space-y-3">
            {isDraft && <div className="flex flex-wrap gap-2"><Button onClick={() => submit.mutate()} disabled={submit.isPending}>Submit (chạy saga)</Button><Button variant="ghost" onClick={() => setEditing(v => !v)}>{editing ? 'Đóng chỉnh sửa' : 'Sửa dòng hàng'}</Button><Button variant="danger" onClick={() => { if (confirm('Xoá draft này? Không thể hoàn tác.')) del.mutate() }} disabled={del.isPending}>{del.isPending ? 'Đang xoá…' : 'Xoá draft'}</Button></div>}
            <ErrorMsg error={submit.error} /><ErrorMsg error={del.error} />
            {!isDraft && !terminalOrder(o.status) && <div className="flex gap-2"><Input placeholder="Lý do huỷ" value={reason} onChange={e => setReason(e.target.value)} /><Button variant="danger" onClick={() => cancel.mutate()} disabled={cancel.isPending}>Huỷ đơn</Button></div>}
            <ErrorMsg error={cancel.error} />
          </div>
        </>}
      </Card>

      {isDraft && editing && <Card title="Chỉnh sửa dòng hàng" actions={<Button type="button" variant="ghost" onClick={() => setLines(ls => [...ls, toLine({})])}>+ Thêm dòng</Button>}>
        <form className="space-y-4" onSubmit={e => { e.preventDefault(); save.mutate() }}>
          {lines.map((l, i) => <div key={i} className="rounded-lg border border-stone-200 p-3"><div className="mb-2 flex items-center justify-between"><span className="text-sm font-medium">Dòng {i + 1}</span>{lines.length > 1 && <Button type="button" variant="ghost" onClick={() => setLines(ls => ls.filter((_, j) => j !== i))}>Xoá dòng</Button>}</div><div className="grid gap-3 md:grid-cols-3"><Field label="Item ID"><Input required value={l.itemId} onChange={e => updLine(i, { itemId: e.target.value })} /></Field><Field label="Warehouse ID"><Input required value={l.warehouseId} onChange={e => updLine(i, { warehouseId: e.target.value })} /></Field><Field label="Số lượng"><Input required value={l.quantity} onChange={e => updLine(i, { quantity: e.target.value })} /></Field><Field label="Đơn vị"><Input required value={l.unit} onChange={e => updLine(i, { unit: e.target.value })} /></Field><Field label="Tiền tệ"><Input required value={l.currency} onChange={e => updLine(i, { currency: e.target.value })} /></Field><Field label="Đơn giá (minor unit)"><Input type="number" min={1} required value={l.unitPriceMinor} onChange={e => updLine(i, { unitPriceMinor: Number(e.target.value) })} /></Field></div></div>)}
          <ErrorMsg error={save.error} /><Button disabled={save.isPending || !lines.length}>{save.isPending ? 'Đang lưu…' : 'Lưu draft'}</Button>
        </form>
      </Card>}

      {!!o?.lines?.length && <Card title="Dòng hàng"><div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b text-xs uppercase text-stone-500"><tr><th className="py-2">#</th><th>Item</th><th>Kho</th><th>SL</th><th>Đơn giá</th><th>Thành tiền</th><th>Version</th></tr></thead><tbody>{o.lines.map((l: any) => <tr key={l.lineId ?? l.lineNo} className="border-b last:border-0"><td className="py-2">{l.lineNo + 1}</td><td className="font-mono text-xs">{l.itemId}</td><td className="font-mono text-xs">{l.warehouseId ?? '—'}</td><td>{l.quantity} {l.unit}</td><td>{money(Number(l.unitPriceMinor ?? 0), l.currency)}</td><td className="font-medium">{money(Number(l.lineTotalMinor ?? 0), l.currency)}</td><td>{l.version}</td></tr>)}</tbody></table></div></Card>}

      {canAdminSaga && <Card title="Tiến trình Saga" actions={saga.data && <Badge tone={statusTone(saga.data.status)}>{saga.data.status}</Badge>}>
        <ErrorMsg error={saga.error} />
        {saga.isLoading && <p className="text-sm text-stone-500">Đang tải Saga…</p>}
        {!saga.isLoading && saga.data === null && <p className="text-sm text-stone-500">Order chưa có Saga. Draft chỉ tạo Saga sau khi submit.</p>}
        {saga.data && <div className="space-y-4">
          <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4"><Info label="Saga ID" value={saga.data.sagaId} mono /><Info label="Bước hiện tại" value={saga.data.currentStepKey ?? '—'} /><Info label="Số lần thử" value={String(saga.data.attemptCount)} /><Info label="Ý định kết thúc" value={saga.data.terminalIntent ?? '—'} /><Info label="Lần thử tiếp" value={when(saga.data.nextAttemptAt)} /><Info label="Đang claim từ" value={when(saga.data.claimedAt)} /><Info label="Hạn compensation" value={when(saga.data.compensationDeadlineAt)} /><Info label="Lỗi cuối" value={saga.data.lastErrorCode ?? '—'} /></dl>
          {saga.data.lastErrorMessage && <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">{saga.data.lastErrorMessage}</div>}
          <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b text-xs uppercase text-stone-500"><tr><th className="py-2">Bước</th><th>Loại</th><th>Trạng thái</th><th>Line</th><th>Thử</th><th>Lần tiếp</th><th>Tham chiếu / lỗi</th><th></th></tr></thead><tbody>{saga.data.steps.map(step => <tr key={step.stepKey} className="border-b align-top last:border-0"><td className="py-2 font-mono text-xs">{step.stepKey}</td><td>{step.stepType}</td><td><Badge tone={statusTone(step.status)}>{step.status}</Badge></td><td className="font-mono text-xs">{step.orderLineId ?? '—'}</td><td>{step.attemptCount}</td><td>{when(step.nextAttemptAt)}</td><td className="max-w-xs text-xs"><div className="break-all">{step.externalReferenceId ?? step.lastErrorCode ?? '—'}</div>{step.lastErrorMessage && <div className="mt-1 text-red-700">{step.lastErrorMessage}</div>}</td><td>{actionableStep(step.status) && <Button variant="ghost" disabled={!operatorReason.trim() || sagaAction.isPending} onClick={() => retryStep(step)}>Retry</Button>}</td></tr>)}</tbody></table></div>
          <div className="rounded-lg border border-stone-200 p-3"><Field label="Lý do thao tác vận hành" hint="Bắt buộc và nên chứa mã incident/đối soát."><Input value={operatorReason} onChange={e => setOperatorReason(e.target.value)} /></Field><div className="mt-3 flex flex-wrap gap-2"><Button variant="ghost" disabled={!operatorReason.trim() || sagaAction.isPending || !/FAILED|MANUAL_REVIEW/.test(saga.data.status)} onClick={() => runSagaAction('resume')}>Resume Saga</Button><Button variant="ghost" disabled={!operatorReason.trim() || sagaAction.isPending || !/FAILED|MANUAL_REVIEW|WAITING_MANUAL_REVIEW/.test(saga.data.status)} onClick={() => runSagaAction('mark-resolved')}>Đánh dấu đã đối soát</Button></div><ErrorMsg error={sagaAction.error} /></div>
        </div>}
      </Card>}

      {!canAdminSaga && <Card title="Tiến trình xử lý"><p className="text-sm text-stone-600">Giai đoạn hiện tại: <strong>{stageLabel}</strong>. Chi tiết Saga yêu cầu scope <code className="rounded bg-stone-100 px-1">orders:saga:admin</code>.</p></Card>}
      <details><summary className="cursor-pointer text-sm text-stone-500">Dữ liệu Order thô</summary><div className="mt-2"><Json data={o} /></div></details>
    </div>
  </>
}

function Info({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return <div><dt className="text-xs uppercase text-stone-500">{label}</dt><dd className={`mt-1 break-all text-stone-800 ${mono ? 'font-mono text-xs' : ''}`}>{value}</dd></div>
}
