import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import { useFarm } from '../lib/farm'
import { recent } from '../lib/recent'
import { Button, Card, ErrorMsg, Field, Input, PageHeader } from '../components/ui'

type Line = { itemId: string; quantity: string; unit: string; currency: string; unitPriceMinor: number; warehouseId: string }
const blank = (): Line => ({ itemId: recent.get('itemId'), quantity: '10', unit: 'KG', currency: 'VND', unitPriceMinor: 1000, warehouseId: recent.get('warehouseId', 'wh-1') })

// POST /api/v1/orders/drafts  (Idempotency-Key, scope orders:write)
export default function OrderNew() {
  const { farmId } = useFarm()
  const nav = useNavigate()
  const [lines, setLines] = useState<Line[]>([blank()])
  const [err, setErr] = useState<unknown>()
  const [busy, setBusy] = useState(false)

  const upd = (i: number, patch: Partial<Line>) => setLines(ls => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)))

  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setErr(undefined)
    try {
      const r = await api('/api/v1/orders/drafts', { method: 'POST', idempotent: true, body: { farmId, lines } })
      nav(`/orders/${r.orderId}`)
    } catch (x) { setErr(x) } finally { setBusy(false) }
  }

  return (
    <>
      <PageHeader title="Tạo draft đơn hàng" sub={`Farm: ${farmId} — cần có vật tư và tồn kho (trang Kho) trước khi submit.`} />
      <form onSubmit={submit} className="space-y-4">
        {lines.map((l, i) => (
          <Card key={i} title={`Dòng ${i + 1}`} actions={lines.length > 1 && <Button type="button" variant="ghost" onClick={() => setLines(ls => ls.filter((_, j) => j !== i))}>Xoá</Button>}>
            <div className="grid gap-3 md:grid-cols-3">
              <Field label="Item ID"><Input required value={l.itemId} onChange={e => upd(i, { itemId: e.target.value })} /></Field>
              <Field label="Warehouse ID"><Input required value={l.warehouseId} onChange={e => upd(i, { warehouseId: e.target.value })} /></Field>
              <Field label="Số lượng"><Input required value={l.quantity} onChange={e => upd(i, { quantity: e.target.value })} /></Field>
              <Field label="Đơn vị"><Input required value={l.unit} onChange={e => upd(i, { unit: e.target.value })} /></Field>
              <Field label="Tiền tệ"><Input required value={l.currency} onChange={e => upd(i, { currency: e.target.value })} /></Field>
              <Field label="Đơn giá (minor unit)"><Input type="number" required value={l.unitPriceMinor} onChange={e => upd(i, { unitPriceMinor: Number(e.target.value) })} /></Field>
            </div>
          </Card>
        ))}
        <ErrorMsg error={err} />
        <div className="flex gap-2">
          <Button type="button" variant="ghost" onClick={() => setLines(ls => [...ls, blank()])}>+ Thêm dòng</Button>
          <Button disabled={busy}>{busy ? 'Đang tạo…' : 'Tạo draft'}</Button>
        </div>
      </form>
    </>
  )
}
