import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useFarm } from '../lib/farm'
import { recent } from '../lib/recent'
import { Button, Card, DevOnly, ErrorMsg, Field, Input, Json, PageHeader } from '../components/ui'

// DEV-only: POST /api/v1/inventory/items (inventory:write) · /receipts
export default function Inventory() {
  const { farmId } = useFarm()
  const [item, setItem] = useState({ sku: 'FEED-001', name: 'Starter Feed', unit: 'KG', reorderThreshold: '100' })
  const [rec, setRec] = useState({ itemId: recent.get('itemId'), warehouseId: recent.get('warehouseId', 'wh-1'), quantity: '500', unit: 'KG' })

  const createItem = useMutation({
    mutationFn: () => api('/api/v1/inventory/items', { method: 'POST', idempotent: true, body: item }),
    onSuccess: r => { if (r?.itemId) { recent.set('itemId', r.itemId); setRec(s => ({ ...s, itemId: r.itemId })) } },
  })
  const receive = useMutation({
    mutationFn: () => api('/api/v1/inventory/receipts', { method: 'POST', idempotent: true, body: { ...rec, farmId } }),
    onSuccess: () => recent.set('warehouseId', rec.warehouseId),
  })

  return (
    <>
      <PageHeader title="Kho" sub={<>Quản lý vật tư và nhập kho qua Gateway bảo mật.</>} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="1. Tạo vật tư">
          <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); createItem.mutate() }}>
            <Field label="SKU"><Input required value={item.sku} onChange={e => setItem({ ...item, sku: e.target.value })} /></Field>
            <Field label="Tên"><Input required value={item.name} onChange={e => setItem({ ...item, name: e.target.value })} /></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Đơn vị"><Input required value={item.unit} onChange={e => setItem({ ...item, unit: e.target.value })} /></Field>
              <Field label="Ngưỡng đặt lại"><Input required value={item.reorderThreshold} onChange={e => setItem({ ...item, reorderThreshold: e.target.value })} /></Field>
            </div>
            <ErrorMsg error={createItem.error} />
            <Button disabled={createItem.isPending}>Tạo vật tư</Button>
            {createItem.data && <Json data={createItem.data} />}
          </form>
        </Card>
        <Card title="2. Nhập kho">
          <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); receive.mutate() }}>
            <Field label="Item ID" hint="Tự điền sau khi tạo vật tư"><Input required value={rec.itemId} onChange={e => setRec({ ...rec, itemId: e.target.value })} /></Field>
            <Field label="Warehouse ID"><Input required value={rec.warehouseId} onChange={e => setRec({ ...rec, warehouseId: e.target.value })} /></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Số lượng"><Input required value={rec.quantity} onChange={e => setRec({ ...rec, quantity: e.target.value })} /></Field>
              <Field label="Đơn vị"><Input required value={rec.unit} onChange={e => setRec({ ...rec, unit: e.target.value })} /></Field>
            </div>
            <ErrorMsg error={receive.error} />
            <Button disabled={receive.isPending}>Nhập kho</Button>
            {receive.data && <Json data={receive.data} />}
          </form>
        </Card>
      </div>
    </>
  )
}
