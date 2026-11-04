import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, identity } from '../../lib/api'
import { hasPermission, useAuth } from '../../lib/auth'
import { useConfirm } from '../../components/dialog'
import { Badge, Button, Card, ErrorMsg, Field, Input, PageHeader, QueryState } from '../../components/ui'
import { vi } from '../../i18n/vi'

type Role = { code: string; name: string; description?: string; systemOwned: boolean; permissions: string[] }
type Permission = { code: string; resourceType: string; action: string; description?: string; systemOwned: boolean }

const empty = { code: '', name: '', description: '', permissions: [] as string[] }

// /api/identity/roles (role của tenant) · /api/identity/permissions (danh mục chung)
export default function Authorization() {
  const { me } = useAuth()
  const canManage = hasPermission(me, 'identity:role:manage')
  const qc = useQueryClient()
  const confirm = useConfirm()
  const [draft, setDraft] = useState(empty)
  const [editing, setEditing] = useState<string | null>(null)

  const roles = useQuery({ queryKey: ['roles'], queryFn: () => api<Role[]>(identity('/roles')) })
  const permissions = useQuery({ queryKey: ['permissions'], queryFn: () => api<Permission[]>(identity('/permissions')) })

  const save = useMutation({
    mutationFn: () => editing
      ? api(identity(`/roles/${editing}`), { method: 'PUT', body: draft })
      : api(identity('/roles'), { method: 'POST', body: draft }),
    onSuccess: () => { setDraft(empty); setEditing(null); qc.invalidateQueries({ queryKey: ['roles'] }) },
  })
  const remove = useMutation({
    mutationFn: (code: string) => api(identity(`/roles/${code}`), { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }),
  })

  const toggle = (code: string) => setDraft(d => ({
    ...d, permissions: d.permissions.includes(code) ? d.permissions.filter(p => p !== code) : [...d.permissions, code],
  }))
  const edit = (r: Role) => { setEditing(r.code); setDraft({ code: r.code, name: r.name, description: r.description ?? '', permissions: r.permissions }) }

  return (
    <>
      <PageHeader title={vi.authorization.title} sub={vi.authorization.sub} />
      <div className="grid gap-4 xl:grid-cols-2">
        <Card title={vi.authorization.roles}>
          <ErrorMsg error={remove.error} />
          <QueryState query={roles} loadingLabel={vi.authorization.loading} empty={vi.authorization.empty}>
            {list => (
          <ul className="divide-y text-sm">
            {list.map(r => (
              <li key={r.code} className="flex items-start justify-between gap-3 py-2">
                <div>
                  <div className="font-medium">{r.code} {r.systemOwned && <Badge tone="warn">{vi.common.system}</Badge>}</div>
                  <div className="text-stone-600">{vi.authorization.permissionCount(r.name, r.permissions.length)}</div>
                </div>
                {canManage && !r.systemOwned && (
                  <div className="space-x-1 whitespace-nowrap">
                    <Button variant="ghost" aria-label={vi.authorization.editLabel(r.code)} onClick={() => edit(r)}>{vi.common.edit}</Button>
                    <Button variant="danger" aria-label={vi.authorization.deleteLabel(r.code)} onClick={async () => {
                      if (await confirm({ ...vi.authorization.confirmDelete(r.code), danger: true })) remove.mutate(r.code)
                    }}>{vi.common.delete}</Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
            )}
          </QueryState>
        </Card>

        {canManage && (
          <Card title={editing ? vi.authorization.edit(editing) : vi.authorization.create}>
            <form className="space-y-3" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
              <Field label={vi.common.code} hint={vi.authorization.codeHint}>
                <Input required pattern="[A-Z][A-Z0-9_]{1,39}" disabled={!!editing} value={draft.code} onChange={e => setDraft({ ...draft, code: e.target.value })} />
              </Field>
              <Field label={vi.common.name}><Input required value={draft.name} onChange={e => setDraft({ ...draft, name: e.target.value })} /></Field>
              <Field label={vi.common.description}><Input value={draft.description} onChange={e => setDraft({ ...draft, description: e.target.value })} /></Field>
              <fieldset className="max-h-72 space-y-1 overflow-y-auto rounded border p-2 text-sm">
                <legend className="px-1 text-xs text-stone-600">{vi.authorization.permissions}</legend>
                {permissions.data?.map(p => (
                  <label key={p.code} className="flex items-center gap-2">
                    <input type="checkbox" checked={draft.permissions.includes(p.code)} onChange={() => toggle(p.code)} />
                    <code className="text-xs">{p.code}</code><span className="text-stone-600">{p.description}</span>
                  </label>
                ))}
              </fieldset>
              <ErrorMsg error={save.error ?? permissions.error} />
              <div className="space-x-2">
                <Button disabled={save.isPending}>{editing ? vi.common.save : vi.common.create}</Button>
                {editing && <Button type="button" variant="ghost" onClick={() => { setEditing(null); setDraft(empty) }}>{vi.common.cancel}</Button>}
              </div>
            </form>
          </Card>
        )}
      </div>
    </>
  )
}
