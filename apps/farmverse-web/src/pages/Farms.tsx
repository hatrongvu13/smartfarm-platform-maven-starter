import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, farm as farmApi } from '../lib/api'
import { hasPermission, useAuth } from '../lib/auth'
import { useFarm } from '../lib/farm'
import { Badge, Button, Card, ErrorMsg, Field, Input, PageHeader, statusTone } from '../components/ui'

type Barn = { code: string; name: string; capacity: number }
type Farm = { id: string; code: string; name: string; address?: string; status: string; totalCapacity: number; barns: Barn[] }

// /api/farm/farms (farm:farm:read / farm:farm:write). Tenant do token quyết định, không gửi lên.
export default function Farms() {
  const { me } = useAuth()
  const canWrite = hasPermission(me, 'farm:farm:write')
  const { farmId, setFarmId } = useFarm()
  const qc = useQueryClient()
  const [draft, setDraft] = useState({ code: '', name: '', address: '' })
  const [barn, setBarn] = useState({ code: '', name: '', capacity: 10 })

  const farms = useQuery({ queryKey: ['farms'], queryFn: () => api<Farm[]>(farmApi('/farms')) })
  const refresh = () => qc.invalidateQueries({ queryKey: ['farms'] })
  const selected = farms.data?.find(f => f.id === farmId || f.code === farmId)

  const create = useMutation({
    mutationFn: () => api<Farm>(farmApi('/farms'), { method: 'POST', body: draft }),
    onSuccess: f => { setDraft({ code: '', name: '', address: '' }); setFarmId(f.id); refresh() },
  })
  const addBarn = useMutation({
    mutationFn: () => api(farmApi(`/farms/${selected!.id}/barns`), { method: 'POST', body: barn }),
    onSuccess: () => { setBarn({ code: '', name: '', capacity: 10 }); refresh() },
  })
  const removeBarn = useMutation({
    mutationFn: (code: string) => api(farmApi(`/farms/${selected!.id}/barns/${code}`), { method: 'DELETE' }),
    onSuccess: refresh,
  })

  return (
    <>
      <PageHeader title="Trang trại" sub="Trang trại và chuồng của tổ chức" />
      <div className="grid gap-4 xl:grid-cols-3">
        <Card title="Danh sách" className="xl:col-span-2">
          <ErrorMsg error={farms.error} />
          <ul className="divide-y text-sm">
            {farms.data?.map(f => (
              <li key={f.id} className="flex items-center justify-between gap-3 py-2">
                <div>
                  <div className="font-medium">{f.name} <span className="text-stone-500">({f.code})</span></div>
                  <div className="text-stone-500">{f.barns.length} chuồng · sức chứa {f.totalCapacity}</div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={statusTone(f.status)}>{f.status}</Badge>
                  <Button variant={selected?.id === f.id ? 'primary' : 'ghost'} onClick={() => setFarmId(f.id)}>
                    {selected?.id === f.id ? 'Đang chọn' : 'Chọn'}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          {farms.data?.length === 0 && <p className="text-sm text-stone-500">Chưa có trang trại nào.</p>}
        </Card>

        {canWrite && (
          <Card title="Tạo trang trại">
            <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); create.mutate() }}>
              <Field label="Mã" hint="viết thường, ví dụ green-valley">
                <Input required pattern="[a-z0-9][a-z0-9-]{1,49}" value={draft.code} onChange={e => setDraft({ ...draft, code: e.target.value })} />
              </Field>
              <Field label="Tên"><Input required value={draft.name} onChange={e => setDraft({ ...draft, name: e.target.value })} /></Field>
              <Field label="Địa chỉ"><Input value={draft.address} onChange={e => setDraft({ ...draft, address: e.target.value })} /></Field>
              <ErrorMsg error={create.error} />
              <Button disabled={create.isPending}>Tạo</Button>
            </form>
          </Card>
        )}

        {selected && (
          <Card title={`Chuồng của ${selected.name}`} className="xl:col-span-2">
            <ErrorMsg error={removeBarn.error} />
            <table className="w-full text-left text-sm">
              <thead className="border-b text-xs uppercase text-stone-500"><tr><th className="py-2">Mã</th><th>Tên</th><th>Sức chứa</th><th /></tr></thead>
              <tbody>
                {selected.barns.map(b => (
                  <tr key={b.code} className="border-b last:border-0">
                    <td className="py-2">{b.code}</td><td>{b.name}</td><td>{b.capacity}</td>
                    <td className="text-right">{canWrite && <Button variant="ghost" onClick={() => removeBarn.mutate(b.code)}>Xóa</Button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        )}

        {selected && canWrite && selected.status === 'ACTIVE' && (
          <Card title="Thêm chuồng">
            <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); addBarn.mutate() }}>
              <Field label="Mã" hint="VIẾT HOA, ví dụ B-1"><Input required value={barn.code} onChange={e => setBarn({ ...barn, code: e.target.value.toUpperCase() })} /></Field>
              <Field label="Tên"><Input required value={barn.name} onChange={e => setBarn({ ...barn, name: e.target.value })} /></Field>
              <Field label="Sức chứa"><Input type="number" min={1} required value={barn.capacity} onChange={e => setBarn({ ...barn, capacity: Number(e.target.value) })} /></Field>
              <ErrorMsg error={addBarn.error} />
              <Button disabled={addBarn.isPending}>Thêm</Button>
            </form>
          </Card>
        )}
      </div>
    </>
  )
}
