import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, farm as farmApi } from '../lib/api'
import { hasPermission, useAuth } from '../lib/auth'
import { useFarm } from '../lib/farm'
import { useConfirm } from '../components/dialog'
import { Badge, Button, Card, ErrorMsg, Field, Input, PageHeader, QueryState, Empty, statusTone } from '../components/ui'
import { vi, statusLabel } from '../i18n/vi'

type Barn = { code: string; name: string; capacity: number }
type Farm = { id: string; code: string; name: string; address?: string; status: string; totalCapacity: number; barns: Barn[] }

// /api/farm/farms (farm:farm:read / farm:farm:write). Tenant do token quyết định, không gửi lên.
export default function Farms() {
  const { me } = useAuth()
  const canWrite = hasPermission(me, 'farm:farm:write')
  const { farmId, setFarmId } = useFarm()
  const qc = useQueryClient()
  const confirm = useConfirm()
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
      <PageHeader title={vi.farms.title} sub={vi.farms.sub} />
      <div className="grid gap-4 xl:grid-cols-3">
        <Card title={vi.farms.list} className="xl:col-span-2">
          <QueryState query={farms} loadingLabel={vi.farms.loading}
            empty={canWrite ? vi.farms.emptyCanCreate : vi.farms.empty}>
            {list => (
          <ul className="divide-y text-sm">
            {list.map(f => (
              <li key={f.id} className="flex items-center justify-between gap-3 py-2">
                <div>
                  <div className="font-medium">{f.name} <span className="text-stone-600">({f.code})</span></div>
                  <div className="text-stone-600">{vi.farms.summary(f.barns.length, f.totalCapacity)}</div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={statusTone(f.status)}>{statusLabel(f.status)}</Badge>
                  <Button variant={selected?.id === f.id ? 'primary' : 'ghost'} onClick={() => setFarmId(f.id)}>
                    {selected?.id === f.id ? vi.farms.selected : vi.farms.select}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
            )}
          </QueryState>
        </Card>

        {canWrite && (
          <Card title={vi.farms.create}>
            <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); create.mutate() }}>
              <Field label={vi.common.code} hint={vi.farms.codeHint}>
                <Input required pattern="[a-z0-9][a-z0-9-]{1,49}" value={draft.code} onChange={e => setDraft({ ...draft, code: e.target.value })} />
              </Field>
              <Field label={vi.common.name}><Input required value={draft.name} onChange={e => setDraft({ ...draft, name: e.target.value })} /></Field>
              <Field label={vi.common.address}><Input value={draft.address} onChange={e => setDraft({ ...draft, address: e.target.value })} /></Field>
              <ErrorMsg error={create.error} />
              <Button disabled={create.isPending}>{vi.common.create}</Button>
            </form>
          </Card>
        )}

        {selected && (
          <Card title={vi.farms.barnsOf(selected.name)} className="xl:col-span-2">
            <ErrorMsg error={removeBarn.error} />
            {selected.barns.length === 0
              ? <Empty>{canWrite && selected.status === 'ACTIVE' ? vi.farms.noBarnsCanAdd : vi.farms.noBarns}</Empty>
              : <table className="w-full text-left text-sm">
              <thead className="border-b text-xs uppercase text-stone-600"><tr><th scope="col" className="py-2">{vi.common.code}</th><th scope="col">{vi.common.name}</th><th scope="col">{vi.farms.capacity}</th><th scope="col"><span className="sr-only">{vi.common.actions}</span></th></tr></thead>
              <tbody>
                {selected.barns.map(b => (
                  <tr key={b.code} className="border-b last:border-0">
                    <td className="py-2">{b.code}</td><td>{b.name}</td><td>{b.capacity}</td>
                    <td className="text-right">{canWrite && <Button variant="ghost" aria-label={vi.farms.deleteBarnLabel(b.code)} onClick={async () => {
                      if (await confirm({ ...vi.farms.confirmDeleteBarn(b.code, b.name, selected.name), danger: true })) removeBarn.mutate(b.code)
                    }}>{vi.common.delete}</Button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>}
          </Card>
        )}

        {selected && canWrite && selected.status === 'ACTIVE' && (
          <Card title={vi.farms.addBarn}>
            <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); addBarn.mutate() }}>
              <Field label={vi.common.code} hint={vi.farms.barnCodeHint}><Input required value={barn.code} onChange={e => setBarn({ ...barn, code: e.target.value.toUpperCase() })} /></Field>
              <Field label={vi.common.name}><Input required value={barn.name} onChange={e => setBarn({ ...barn, name: e.target.value })} /></Field>
              <Field label={vi.farms.capacity}><Input type="number" min={1} required value={barn.capacity} onChange={e => setBarn({ ...barn, capacity: Number(e.target.value) })} /></Field>
              <ErrorMsg error={addBarn.error} />
              <Button disabled={addBarn.isPending}>{vi.common.add}</Button>
            </form>
          </Card>
        )}
      </div>
    </>
  )
}
