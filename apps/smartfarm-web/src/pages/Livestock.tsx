import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useFarm } from '../lib/farm'
import { Button, Card, DevOnly, ErrorMsg, Field, Input, Json, PageHeader } from '../components/ui'

// GET /api/v1/livestock/animals?farmId&limit -> { count, animals[] }   (farm:read)
// POST /animals {farmId, tagCode, species?} (tasks:write) · POST /tasks {farmId,title,type?} (dev-only, Idempotency-Key)
export default function Livestock() {
  const { farmId } = useFarm()
  const qc = useQueryClient()
  const [animal, setAnimal] = useState({ tagCode: '', species: 'CATTLE' })
  const [task, setTask] = useState({ title: 'Morning feeding round', type: 'FEEDING' })

  const animals = useQuery({
    queryKey: ['animals', farmId],
    queryFn: () => api<{ count: number; animals: any[] }>('/api/v1/livestock/animals', { query: { farmId, limit: 50 } }),
  })
  const register = useMutation({
    mutationFn: () => api('/api/v1/livestock/animals', { method: 'POST', body: { farmId, ...animal } }),
    onSuccess: () => { setAnimal({ ...animal, tagCode: '' }); qc.invalidateQueries({ queryKey: ['animals', farmId] }) },
  })
  const createTask = useMutation({
    mutationFn: () => api('/api/v1/livestock/tasks', { method: 'POST', idempotent: true, body: { farmId, ...task } }),
  })

  const rows = animals.data?.animals ?? []
  const cols = rows.length ? Object.keys(rows[0]).slice(0, 5) : []

  return (
    <>
      <PageHeader title="Chăn nuôi" sub={`Farm: ${farmId}`} />
      <div className="grid gap-4 lg:grid-cols-3">
        <Card title="Vật nuôi" className="lg:col-span-2" actions={<span className="text-sm text-stone-500">{animals.data?.count ?? 0} con</span>}>
          <ErrorMsg error={animals.error} />
          {animals.isLoading && <p className="text-sm text-stone-500">Đang tải…</p>}
          {!!rows.length && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b text-xs uppercase text-stone-500"><tr>{cols.map(c => <th key={c} className="py-2 pr-4">{c}</th>)}</tr></thead>
                <tbody>{rows.map((r, i) => <tr key={r.animalId ?? i} className="border-b last:border-0">{cols.map(c => <td key={c} className="py-2 pr-4">{String(r[c] ?? '')}</td>)}</tr>)}</tbody>
              </table>
            </div>
          )}
          {!rows.length && !animals.isLoading && !animals.error && <p className="text-sm text-stone-500">Chưa có vật nuôi.</p>}
        </Card>
        <div className="space-y-4">
          <Card title="Đăng ký vật nuôi">
            <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); register.mutate() }}>
              <Field label="Mã tag"><Input required value={animal.tagCode} onChange={e => setAnimal({ ...animal, tagCode: e.target.value })} placeholder="COW-001" /></Field>
              <Field label="Loài"><Input value={animal.species} onChange={e => setAnimal({ ...animal, species: e.target.value })} /></Field>
              <ErrorMsg error={register.error} />
              <Button disabled={register.isPending}>Đăng ký</Button>
            </form>
          </Card>
          <Card title="Tạo task" >
            <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); createTask.mutate() }}>
              <Field label="Tiêu đề"><Input required value={task.title} onChange={e => setTask({ ...task, title: e.target.value })} /></Field>
              <Field label="Loại"><Input value={task.type} onChange={e => setTask({ ...task, type: e.target.value })} /></Field>
              <ErrorMsg error={createTask.error} />
              <Button disabled={createTask.isPending}>Tạo task</Button>
              {createTask.data && <Json data={createTask.data} />}
            </form>
          </Card>
        </div>
      </div>
    </>
  )
}
